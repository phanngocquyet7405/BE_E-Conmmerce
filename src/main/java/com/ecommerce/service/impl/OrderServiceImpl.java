package com.ecommerce.service.impl;

import com.ecommerce.common.response.PageResponse;
import com.ecommerce.config.AppProperties;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;

    @Override
    public OrderResponse checkout(String userId, CheckoutRequest request) {
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
            Product product = productRepository.findById(cartItem.getProductId())
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                        "Sản phẩm " + product.getName() + " không đủ số lượng tồn kho");
            }

            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            productRepository.save(product);

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

        BigDecimal shippingFee = BigDecimal.valueOf(30000); // 30,000 VND
        BigDecimal finalAmount = totalAmount.add(shippingFee);
        String orderCode = "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        PaymentMethod method = request.getPaymentMethod();
        String note = request.getNote();
        if (method == PaymentMethod.COD && (note == null || !note.contains("COD"))) {
            note = (note != null ? note + " | " : "") + "Thanh toán bằng tiền mặt khi nhận hàng (COD)";
        }

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
                .paymentMethod(method)
                .paymentStatus(PaymentStatus.UNPAID)
                .status(OrderStatus.PENDING)
                .note(note)
                .build();

        Order savedOrder = orderRepository.save(order);

        cart.getItems().clear();
        cartRepository.save(cart);

        return mapToOrderResponse(savedOrder);
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
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        for (OrderItem item : order.getItems()) {
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                productRepository.save(product);
            });
        }

        return mapToOrderResponse(orderRepository.save(order));
    }

    @Override
    public PageResponse<OrderResponse> getAllOrdersForAdmin(OrderStatus status, Pageable pageable) {
        Page<Order> orderPage = (status != null)
                ? orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : orderRepository.findAll(pageable);
        return PageResponse.fromPage(orderPage.map(this::mapToOrderResponse));
    }

    @Override
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
            for (OrderItem item : order.getItems()) {
                productRepository.findById(item.getProductId()).ifPresent(product -> {
                    product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                    productRepository.save(product);
                });
            }
        }

        return mapToOrderResponse(orderRepository.save(order));
    }

    private OrderResponse mapToOrderResponse(Order order) {
        String qrImageUrl = null;
        String bank = null;
        String accNumber = null;
        String holder = null;
        String transferContent = null;

        if (order.getPaymentMethod() == PaymentMethod.SEPAY || order.getPaymentMethod() == PaymentMethod.BANKING) {
            bank = appProperties.getSepay().getBankName();
            accNumber = appProperties.getSepay().getAccountNumber();
            holder = appProperties.getSepay().getAccountHolder();
            transferContent = order.getOrderCode();

            String encodedContent = URLEncoder.encode(transferContent, StandardCharsets.UTF_8);
            qrImageUrl = String.format(
                    "https://qr.sepay.vn/img?acc=%s&bank=%s&amount=%s&des=%s&template=%s",
                    accNumber, bank, order.getFinalAmount().toBigInteger().toString(),
                    encodedContent, appProperties.getSepay().getQrTemplate()
            );
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
