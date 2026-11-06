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
import com.ecommerce.service.SePayService;
import com.mongodb.MongoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final SePayService sePayService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired(required = false)
    private MongoTransactionManager transactionManager;

    @Value("${app.order.shipping-fee:30000}")
    private BigDecimal configuredShippingFee;

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
        List<OrderItem> decrementedItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        try {
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
                    if (existing == null) {
                        throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
                    }
                    throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                            "Sản phẩm '" + existing.getName() + "' không đủ số lượng trong kho");
                }

                BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
                totalAmount = totalAmount.add(itemTotal);

                OrderItem orderItem = OrderItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .sku(product.getSku())
                        .price(product.getPrice())
                        .quantity(cartItem.getQuantity())
                        .thumbnailUrl(product.getThumbnailUrl())
                        .subTotal(itemTotal)
                        .build();

                orderItems.add(orderItem);
                decrementedItems.add(orderItem);
            }

            BigDecimal shippingFee = (configuredShippingFee != null)
                    ? configuredShippingFee
                    : BigDecimal.valueOf(30000);
            BigDecimal finalAmount = totalAmount.add(shippingFee);

            // Sinh mã đơn hàng dạng prefix + 8 số ngẫu nhiên (vd: BETES12345678)
            String orderCode = generateUniqueOrderCode();

            PaymentMethod paymentMethod = request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.COD;

            // Kiểm tra phương thức thanh toán hợp lệ (Theo D1: Chỉ cho phép COD và SEPAY, VNPAY/MOMO/BANKING bị từ chối rõ ràng)
            if (paymentMethod != PaymentMethod.COD && paymentMethod != PaymentMethod.SEPAY) {
                if (paymentMethod == PaymentMethod.MOMO || paymentMethod == PaymentMethod.VNPAY) {
                    throw new AppException(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED,
                            "Phương thức thanh toán ví điện tử " + paymentMethod + " hiện chưa được tích hợp.");
                }
                if (paymentMethod == PaymentMethod.BANKING) {
                    throw new AppException(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED,
                            "Chuyển khoản thủ công (BANKING) hiện không khả dụng. Vui lòng chọn SEPAY để quét mã VietQR tự động.");
                }
                throw new AppException(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED,
                        "Phương thức thanh toán " + paymentMethod + " không được hỗ trợ.");
            }

            Order order = Order.builder()
                    .orderCode(orderCode)
                    .userId(userId)
                    .customerName(user.getFullName())
                    .customerEmail(user.getEmail())
                    .customerPhone(user.getPhone())
                    .shippingAddress(shippingAddress)
                    .items(orderItems)
                    .totalAmount(totalAmount)
                    .shippingFee(shippingFee)
                    .finalAmount(finalAmount)
                    .paymentMethod(paymentMethod)
                    .paymentStatus(PaymentStatus.UNPAID)
                    .status(OrderStatus.PENDING)
                    .note(request.getNote())
                    .build();

            Order savedOrder = orderRepository.save(order);

            // Xóa giỏ hàng sau khi đặt thành công
            cart.getItems().clear();
            cartRepository.save(cart);

            log.info("Khách hàng {} đặt thành công đơn hàng: {}, Tổng tiền: {}", userId, orderCode, finalAmount);
            return mapToOrderResponse(savedOrder);

        } catch (Exception ex) {
            // Cơ chế bù tồn kho (Compensating Stock Rollback):
            // Hoàn lại số lượng đã trừ cho các sản phẩm đã trừ thành công trước khi phát sinh lỗi
            if (!decrementedItems.isEmpty()) {
                log.warn("Phát sinh lỗi trong quá trình checkout ({}). Đang thực hiện hoàn tồn kho bù cho {} mặt hàng...",
                        ex.getMessage(), decrementedItems.size());
                restoreProductStock(decrementedItems);
            }
            throw ex;
        }
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

    /**
     * Hủy đơn hàng nguyên tử (Chống Race Condition):
     * Chỉ hủy khi đơn hàng ở trạng thái PENDING và UNPAID.
     * CHỈ hoàn tồn kho khi thao tác cập nhật nguyên tử khớp và thành công.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse cancelOrder(String userId, String orderId) {
        Query query = Query.query(Criteria.where("_id").is(orderId)
                .and("userId").is(userId)
                .and("status").is(OrderStatus.PENDING)
                .and("paymentStatus").is(PaymentStatus.UNPAID));

        Update update = new Update()
                .set("status", OrderStatus.CANCELLED)
                .set("updatedAt", Instant.now());

        Order cancelledOrder = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Order.class);

        if (cancelledOrder == null) {
            Order existing = orderRepository.findById(orderId)
                    .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

            if (!existing.getUserId().equals(userId)) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }

            throw new AppException(ErrorCode.ORDER_CANNOT_BE_CANCELLED,
                    "Đơn hàng không ở trạng thái PENDING/UNPAID để hủy (Hiện tại: "
                            + existing.getStatus() + ", Thanh toán: " + existing.getPaymentStatus() + ")");
        }

        // CHỈ hoàn lại tồn kho khi cập nhật nguyên tử thành công
        restoreProductStock(cancelledOrder.getItems());
        log.info("Khách hàng {} đã hủy thành công đơn hàng {}", userId, cancelledOrder.getOrderCode());
        return mapToOrderResponse(cancelledOrder);
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

        // Nếu COD giao thành công thì tự động chuyển paymentStatus sang PAID
        if (next == OrderStatus.DELIVERED && order.getPaymentMethod() == PaymentMethod.COD) {
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setPaidAt(Instant.now());
        }

        if (next == OrderStatus.CANCELLED) {
            restoreProductStock(order.getItems());
        }

        Order saved = orderRepository.save(order);
        log.info("Admin cập trạng thái đơn {} từ {} sang {}", order.getOrderCode(), current, next);
        return mapToOrderResponse(saved);
    }

    /**
     * Tự động quét và hủy các đơn hàng chưa thanh toán đã quá thời hạn timeoutMinutes nguyên tử,
     * CHỈ hoàn tồn kho khi lệnh cập nhật nguyên tử findAndModify thành công (chống race condition).
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelExpiredOrders(int timeoutMinutes) {
        Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);

        Query candidateQuery = Query.query(Criteria.where("status").is(OrderStatus.PENDING)
                .and("paymentStatus").is(PaymentStatus.UNPAID)
                .and("paymentMethod").ne(PaymentMethod.COD)
                .and("createdAt").lte(cutoff));

        List<Order> candidates = mongoTemplate.find(candidateQuery, Order.class);
        if (candidates.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Order candidate : candidates) {
            Query atomicQuery = Query.query(Criteria.where("_id").is(candidate.getId())
                    .and("status").is(OrderStatus.PENDING)
                    .and("paymentStatus").is(PaymentStatus.UNPAID));
            Update update = new Update()
                    .set("status", OrderStatus.CANCELLED)
                    .set("updatedAt", Instant.now());

            Order cancelled = mongoTemplate.findAndModify(atomicQuery, update,
                    FindAndModifyOptions.options().returnNew(true), Order.class);

            if (cancelled != null) {
                restoreProductStock(cancelled.getItems());
                count++;
                log.info("Tự động hủy đơn hàng quá hạn thanh toán: {} (tạo lúc {})",
                        cancelled.getOrderCode(), cancelled.getCreatedAt());
            }
        }
        return count;
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

    private String generateUniqueOrderCode() {
        String prefix = StringUtils.hasText(sePayService.getOrderCodePrefix())
                ? sePayService.getOrderCodePrefix()
                : "BETES";
        String code;
        do {
            code = prefix + (10000000 + secureRandom.nextInt(90000000));
        } while (orderRepository.findByOrderCode(code).isPresent());
        return code;
    }

    private OrderResponse mapToOrderResponse(Order order) {
        String qrImageUrl = null;
        String bank = null;
        String accNumber = null;
        String holder = null;
        String transferContent = null;

        if (order.getPaymentMethod() == PaymentMethod.SEPAY || order.getPaymentMethod() == PaymentMethod.BANKING) {
            bank = sePayService.getBankId();
            accNumber = sePayService.getBankAccount();
            holder = sePayService.getAccountHolder();
            transferContent = order.getOrderCode();

            long amount = order.getFinalAmount() != null ? order.getFinalAmount().longValue() : 0L;
            qrImageUrl = sePayService.buildQrUrl(amount, transferContent);
        }

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
                .paymentStatus(order.getPaymentStatus())
                .paidAt(order.getPaidAt())
                .status(order.getStatus())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .qrImageUrl(qrImageUrl)
                .bankName(bank)
                .accountNumber(accNumber)
                .accountHolder(holder)
                .transferContent(transferContent)
                .build();
    }
}
