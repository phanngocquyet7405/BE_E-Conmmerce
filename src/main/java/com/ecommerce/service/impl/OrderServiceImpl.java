package com.ecommerce.service.impl;

import com.ecommerce.common.response.PageResponse;
import com.ecommerce.document.*;
import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.OrderService;
import com.mongodb.MongoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    @Autowired(required = false)
    private MongoTransactionManager transactionManager;

    private static final int MAX_TRANSACTION_RETRY_ATTEMPTS = 3;

    @Override
    public OrderResponse checkout(String userId, CheckoutRequest request) {
        int attempt = 0;

        while (true) {
            attempt++;
            try {
                if (transactionManager != null) {
                    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
                    return txTemplate.execute(status -> executeCheckout(userId, request));
                } else {
                    return executeCheckout(userId, request);
                }
            } catch (Exception ex) {
                if (isTransientTransactionError(ex) && attempt < MAX_TRANSACTION_RETRY_ATTEMPTS) {
                    log.warn("Gặp lỗi TransientTransactionError tại lần thử {}/{}. Đang tự động thử lại checkout...",
                            attempt, MAX_TRANSACTION_RETRY_ATTEMPTS);
                    try {
                        Thread.sleep(50L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    }
                    continue;
                }
                // Giữ nguyên exception gốc ném ra ngoài
                throw ex;
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderResponse executeCheckout(String userId, CheckoutRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        Address shippingAddress = user.getAddresses().stream()
                .filter(a -> a.getId().equals(request.getAddressId()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_EMPTY));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new AppException(ErrorCode.CART_EMPTY);
        }

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            // Trừ tồn kho nguyên tử (Atomic Check-and-Set) chống bán vượt tồn kho (Overselling)
            Query query = new Query(Criteria.where("_id").is(cartItem.getProductId())
                    .and("stock_quantity").gte(cartItem.getQuantity())
                    .and("active").is(true));
            Update update = new Update().inc("stock_quantity", -cartItem.getQuantity());

            Product product = mongoTemplate.findAndModify(
                    query, update, FindAndModifyOptions.options().returnNew(false), Product.class);

            if (product == null) {
                Product existing = productRepository.findById(cartItem.getProductId()).orElse(null);
                if (existing == null || !Boolean.TRUE.equals(existing.getActive())) {
                    throw new AppException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại hoặc đã ngừng kinh doanh");
                }
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                        "Sản phẩm " + existing.getName() + " không đủ số lượng tồn kho");
            }

            BigDecimal subTotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(subTotal);

            orderItems.add(OrderItem.builder()
                    .productId(product.getId())
                    .sku(product.getSku())
                    .productName(product.getName())
                    .thumbnailUrl(product.getThumbnailUrl())
                    .brandName(product.getBrandName())
                    .price(product.getPrice())
                    .quantity(cartItem.getQuantity())
                    .subTotal(subTotal)
                    .build());
        }

        BigDecimal shippingFee = BigDecimal.valueOf(30000);
        BigDecimal finalAmount = totalAmount.add(shippingFee);
        String orderCode = "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Order order = Order.builder()
                .orderCode(orderCode)
                .userId(userId)
                .customerName(shippingAddress.getRecipientName())
                .customerEmail(user.getEmail())
                .customerPhone(shippingAddress.getPhone())
                .shippingAddress(shippingAddress)
                .items(orderItems)
                .totalAmount(totalAmount)
                .shippingFee(shippingFee)
                .finalAmount(finalAmount)
                .paymentMethod(request.getPaymentMethod())
                .status(OrderStatus.PENDING)
                .note(request.getNote())
                .build();

        Order savedOrder = orderRepository.save(order);

        cart.getItems().clear();
        cartRepository.save(cart);

        log.info("Đặt hàng thành công: orderCode={}, userId={}, finalAmount={}", orderCode, userId, finalAmount);
        return mapToOrderResponse(savedOrder);
    }

    private boolean isTransientTransactionError(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof MongoException mongoEx
                    && mongoEx.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("TransientTransactionError")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Override
    public OrderResponse getOrderById(String userId, String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        return mapToOrderResponse(order);
    }

    @Override
    public PageResponse<OrderResponse> getCustomerOrders(String userId, Pageable pageable) {
        Page<OrderResponse> page = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToOrderResponse);
        return PageResponse.fromPage(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse cancelOrder(String userId, String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new AppException(ErrorCode.ORDER_CANNOT_BE_CANCELLED);
        }

        order.setStatus(OrderStatus.CANCELLED);
        restoreProductStock(order.getItems());

        Order saved = orderRepository.save(order);
        log.info("Khách hàng {} đã hủy đơn hàng {}", userId, order.getOrderCode());
        return mapToOrderResponse(saved);
    }

    @Override
    public PageResponse<OrderResponse> getAllOrdersForAdmin(OrderStatus status, Pageable pageable) {
        Page<Order> orderPage = (status != null)
                ? orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : orderRepository.findAll(pageable);
        return PageResponse.fromPage(orderPage.map(this::mapToOrderResponse));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse updateOrderStatus(String orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        OrderStatus current = order.getStatus();
        OrderStatus next = request.getStatus();

        if (current == OrderStatus.DELIVERED || current == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                    "Đơn hàng đã kết thúc (Đã giao hoặc Đã hủy), không thể chuyển trạng thái tiếp");
        }

        order.setStatus(next);

        if (next == OrderStatus.CANCELLED) {
            restoreProductStock(order.getItems());
        }

        Order saved = orderRepository.save(order);
        log.info("Admin cập nhật trạng thái đơn {} từ {} sang {}", order.getOrderCode(), current, next);
        return mapToOrderResponse(saved);
    }

    private void restoreProductStock(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (OrderItem item : items) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(item.getProductId())),
                    new Update().inc("stock_quantity", item.getQuantity()),
                    Product.class
            );
        }
        log.info("Đã hoàn kho thành công cho {} mặt hàng", items.size());
    }

    private OrderResponse mapToOrderResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .userId(order.getUserId())
                .customerName(order.getCustomerName())
                .customerEmail(order.getCustomerEmail())
                .customerPhone(order.getCustomerPhone())
                .shippingAddress(order.getShippingAddress())
                .items(order.getItems())
                .totalAmount(order.getTotalAmount())
                .shippingFee(order.getShippingFee())
                .finalAmount(order.getFinalAmount())
                .paymentMethod(order.getPaymentMethod())
                .status(order.getStatus())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
