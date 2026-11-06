package com.ecommerce.service;

import com.ecommerce.common.constant.RoleEnum;
import com.ecommerce.common.response.PageResponse;
import com.ecommerce.document.Order;
import com.ecommerce.document.OrderStatus;
import com.ecommerce.document.PaymentStatus;
import com.ecommerce.document.User;
import com.ecommerce.dto.request.AdminUserCriteria;
import com.ecommerce.dto.response.AdminUserResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RedisTokenService redisTokenService;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleAdmin;
    private User sampleCustomer;

    @BeforeEach
    void setUp() {
        sampleAdmin = User.builder()
                .id("admin-1")
                .email("admin@ecommerce.com")
                .fullName("Quản trị viên 1")
                .roles(Set.of(RoleEnum.ROLE_ADMIN))
                .active(true)
                .build();

        sampleCustomer = User.builder()
                .id("cust-1")
                .email("cust@ecommerce.com")
                .fullName("Khách hàng 1")
                .roles(Set.of(RoleEnum.ROLE_CUSTOMER))
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Admin không thể tự khóa tài khoản của chính mình -> Ném ACCESS_DENIED")
    void updateUserStatus_SelfLock_ThrowsAccessDenied() {
        AppException ex = assertThrows(AppException.class, () ->
                userService.updateUserStatus("admin-1", "admin-1", false));

        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("không thể tự khóa"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Admin khóa người dùng khác -> Thành công, gọi Redis blockUser")
    void updateUserStatus_LockOtherUser_Success() {
        when(userRepository.findById("cust-1")).thenReturn(Optional.of(sampleCustomer));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        AdminUserResponse response = userService.updateUserStatus("admin-1", "cust-1", false);

        assertNotNull(response);
        assertFalse(response.isActive());
        verify(redisTokenService, times(1)).blockUser("cust-1");
        verify(userRepository, times(1)).save(sampleCustomer);
    }

    @Test
    @DisplayName("Admin cập nhật roles rỗng -> Ném VALIDATION_ERROR")
    void updateUserRoles_EmptyRoles_ThrowsValidationError() {
        AppException ex = assertThrows(AppException.class, () ->
                userService.updateUserRoles("admin-1", "cust-1", Collections.emptySet()));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Admin không thể tự hạ quyền admin của chính mình -> Ném ACCESS_DENIED")
    void updateUserRoles_SelfDemote_ThrowsAccessDenied() {
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(sampleAdmin));

        AppException ex = assertThrows(AppException.class, () ->
                userService.updateUserRoles("admin-1", "admin-1", Set.of(RoleEnum.ROLE_CUSTOMER)));

        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("không thể tự hạ quyền"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Không thể hạ quyền Admin cuối cùng của hệ thống -> Ném ACCESS_DENIED")
    void updateUserRoles_DemoteLastAdmin_ThrowsAccessDenied() {
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(sampleAdmin));
        // Chỉ còn 1 admin duy nhất
        when(userRepository.countByRolesContaining(RoleEnum.ROLE_ADMIN)).thenReturn(1L);

        AppException ex = assertThrows(AppException.class, () ->
                userService.updateUserRoles("other-superadmin", "admin-1", Set.of(RoleEnum.ROLE_CUSTOMER)));

        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Admin cuối cùng"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hạ quyền admin khi có nhiều hơn 1 admin -> Thành công")
    void updateUserRoles_DemoteAdminWhenMultipleExist_Success() {
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(sampleAdmin));
        when(userRepository.countByRolesContaining(RoleEnum.ROLE_ADMIN)).thenReturn(3L);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        AdminUserResponse response = userService.updateUserRoles("other-superadmin", "admin-1", Set.of(RoleEnum.ROLE_CUSTOMER));

        assertNotNull(response);
        assertEquals(Set.of(RoleEnum.ROLE_CUSTOMER), response.getRoles());
        verify(redisTokenService, times(1)).blockUser("admin-1");
    }

    @Test
    @DisplayName("Xem chi tiết người dùng: Tính toán số đơn hàng và tổng chi tiêu theo D9")
    void getUserDetailForAdmin_CalculatesOrdersAndSpent() {
        when(userRepository.findById("cust-1")).thenReturn(Optional.of(sampleCustomer));
        when(orderRepository.countByUserId("cust-1")).thenReturn(3L);

        Order paidOrder1 = Order.builder()
                .paymentStatus(PaymentStatus.PAID)
                .status(OrderStatus.DELIVERED)
                .finalAmount(BigDecimal.valueOf(200000))
                .build();

        Order paidOrder2 = Order.builder()
                .paymentStatus(PaymentStatus.PAID)
                .status(OrderStatus.CONFIRMED)
                .finalAmount(BigDecimal.valueOf(300000))
                .build();

        Order cancelledPaidOrder = Order.builder()
                .paymentStatus(PaymentStatus.PAID)
                .status(OrderStatus.CANCELLED) // Hủy -> Không tính vào doanh thu theo D9
                .finalAmount(BigDecimal.valueOf(150000))
                .build();

        Order unpaidOrder = Order.builder()
                .paymentStatus(PaymentStatus.UNPAID)
                .status(OrderStatus.PENDING)
                .finalAmount(BigDecimal.valueOf(50000))
                .build();

        when(orderRepository.findByUserId("cust-1"))
                .thenReturn(List.of(paidOrder1, paidOrder2, cancelledPaidOrder, unpaidOrder));

        AdminUserResponse detail = userService.getUserDetailForAdmin("cust-1");

        assertNotNull(detail);
        assertEquals(3L, detail.getTotalOrders());
        // 200,000 + 300,000 = 500,000
        assertEquals(BigDecimal.valueOf(500000), detail.getTotalSpent());
    }

    @Test
    @DisplayName("Lấy danh sách người dùng cho Admin: Phân trang và lọc thành công")
    void getUsersForAdmin_FiltersAndPaginates() {
        AdminUserCriteria criteria = AdminUserCriteria.builder()
                .keyword("admin")
                .role(RoleEnum.ROLE_ADMIN)
                .active(true)
                .page(0)
                .size(10)
                .build();

        when(mongoTemplate.count(any(Query.class), eq(User.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(User.class))).thenReturn(List.of(sampleAdmin));

        PageResponse<AdminUserResponse> response = userService.getUsersForAdmin(criteria);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals("admin-1", response.getItems().get(0).getId());
    }
}
