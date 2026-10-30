package com.ecommerce.service;

import com.ecommerce.document.*;
import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.impl.OrderServiceImpl;
import com.mongodb.MongoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User testUser;
    private Address testAddress;
    private Cart testCart;
    private CheckoutRequest checkoutRequest;

    @BeforeEach
    void setUp() {
        testAddress = Address.builder()
                .id("addr-1")
                .recipientName("Nguyễn Văn A")
                .phone("0987654321")
                .province("Hà Nội")
                .district("Cầu Giấy")
                .ward("Dịch Vọng")
                .streetAddress("Số 1 Cầu Giấy")
                .isDefault(true)
                .build();

        testUser = User.builder()
                .id("user-1")
                .email("test@ecommerce.com")
                .fullName("Nguyễn Văn A")
                .addresses(List.of(testAddress))
                .build();

        testCart = Cart.builder()
                .id("cart-1")
                .userId("user-1")
                .items(new ArrayList<>())
                .build();

        checkoutRequest = new CheckoutRequest();
        checkoutRequest.setAddressId("addr-1");
        checkoutRequest.setPaymentMethod(PaymentMethod.COD);
        checkoutRequest.setNote("Giao giờ hành chính");
    }

    @Test
    @DisplayName("Checkout thành công: Trừ kho nguyên tử, tạo đơn hàng và làm rỗng giỏ hàng")
    void checkout_Success() {
        CartItem item = CartItem.builder()
                .productId("prod-1")
                .productName("Áo thun nam")
                .unitPrice(BigDecimal.valueOf(150000))
                .quantity(2)
                .build();
        testCart.getItems().add(item);

        Product dbProduct = Product.builder()
                .id("prod-1")
                .name("Áo thun nam")
                .sku("SKU-AO-01")
                .price(BigDecimal.valueOf(150000))
                .stockQuantity(10)
                .active(true)
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserId("user-1")).thenReturn(Optional.of(testCart));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(Product.class)))
                .thenReturn(dbProduct);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.checkout("user-1", checkoutRequest);

        assertNotNull(response);
        assertEquals("user-1", response.getUserId());
        assertEquals(BigDecimal.valueOf(300000), response.getTotalAmount()); // 150000 * 2
        assertEquals(BigDecimal.valueOf(330000), response.getFinalAmount()); // 300000 + 30000 shipping

        // Giỏ hàng phải được xóa sạch sau khi checkout thành công
        assertTrue(testCart.getItems().isEmpty());
        verify(cartRepository, times(1)).save(testCart);
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Checkout thất bại khi sản phẩm hết hàng: Ném INSUFFICIENT_STOCK và để Transaction tự rollback")
    void checkout_InsufficientStock_RollsBackAndThrowsException() {
        CartItem item1 = CartItem.builder()
                .productId("prod-1")
                .productName("Sản phẩm còn hàng")
                .unitPrice(BigDecimal.valueOf(100000))
                .quantity(1)
                .build();

        CartItem item2 = CartItem.builder()
                .productId("prod-2")
                .productName("Sản phẩm cháy hàng")
                .unitPrice(BigDecimal.valueOf(200000))
                .quantity(5)
                .build();

        testCart.getItems().addAll(List.of(item1, item2));

        Product dbProduct1 = Product.builder()
                .id("prod-1")
                .name("Sản phẩm còn hàng")
                .sku("SKU-1")
                .price(BigDecimal.valueOf(100000))
                .stockQuantity(5)
                .active(true)
                .build();

        Product dbProduct2 = Product.builder()
                .id("prod-2")
                .name("Sản phẩm cháy hàng")
                .sku("SKU-2")
                .price(BigDecimal.valueOf(200000))
                .stockQuantity(1) // Chỉ còn 1 mà giỏ đòi 5
                .active(true)
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserId("user-1")).thenReturn(Optional.of(testCart));

        // prod-1 trừ kho thành công, prod-2 thất bại vì stock < 5
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(Product.class)))
                .thenReturn(dbProduct1) // Lần 1 thành công
                .thenReturn(null);      // Lần 2 thất bại (hết hàng)

        when(productRepository.findById("prod-2")).thenReturn(Optional.of(dbProduct2));

        AppException exception = assertThrows(AppException.class, () ->
                orderService.checkout("user-1", checkoutRequest));

        assertEquals(ErrorCode.INSUFFICIENT_STOCK, exception.getErrorCode());

        // Đã bỏ bù kho thủ công (vì Transaction tự rollback), không được gọi updateFirst
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Product.class));

        // Đơn hàng KHÔNG được lưu và giỏ hàng KHÔNG bị xóa
        verify(orderRepository, never()).save(any(Order.class));
        assertFalse(testCart.getItems().isEmpty());
    }

    @Test
    @DisplayName("Checkout gặp TransientTransactionError: Tự động retry và thành công ở lần sau")
    void checkout_TransientTransactionError_RetriesAndSucceeds() {
        CartItem item = CartItem.builder()
                .productId("prod-1")
                .productName("Sản phẩm thử lại")
                .unitPrice(BigDecimal.valueOf(50000))
                .quantity(1)
                .build();
        testCart.getItems().add(item);

        Product dbProduct = Product.builder()
                .id("prod-1")
                .name("Sản phẩm thử lại")
                .sku("SKU-RETRY")
                .price(BigDecimal.valueOf(50000))
                .stockQuantity(10)
                .active(true)
                .build();

        MongoException transientException = new MongoException(112, "Write conflict");
        transientException.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserId("user-1")).thenReturn(Optional.of(testCart));

        // Lần 1 ném TransientTransactionError, Lần 2 thành công
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(Product.class)))
                .thenThrow(transientException)
                .thenReturn(dbProduct);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.checkout("user-1", checkoutRequest);

        assertNotNull(response);
        verify(mongoTemplate, times(2)).findAndModify(any(Query.class), any(Update.class), any(), eq(Product.class));
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Checkout thất bại khi giỏ hàng trống")
    void checkout_EmptyCart_ThrowsCartEmpty() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserId("user-1")).thenReturn(Optional.of(testCart));

        AppException exception = assertThrows(AppException.class, () ->
                orderService.checkout("user-1", checkoutRequest));

        assertEquals(ErrorCode.CART_EMPTY, exception.getErrorCode());
        verify(orderRepository, never()).save(any());
    }
}
