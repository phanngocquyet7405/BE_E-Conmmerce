package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.common.response.PageResponse;
import com.ecommerce.document.OrderStatus;
import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.security.CustomUserDetails;
import com.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // 1. CUSTOMER: Tạo đơn hàng từ giỏ hàng
    @PostMapping(ApiRoutes.Orders.CUSTOMER_BASE)
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CheckoutRequest request) {
        OrderResponse response = orderService.checkout(userDetails.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Đặt hàng thành công", response));
    }

    // 2. CUSTOMER: Xem lịch sử đơn hàng của chính mình
    @GetMapping(ApiRoutes.Orders.CUSTOMER_BASE)
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int validatedPage = Math.max(page, 0);
        int validatedSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(validatedPage, validatedSize);
        return ResponseEntity.ok(ApiResponse.ok(orderService.getCustomerOrders(userDetails.getId(), pageable)));
    }

    // 3. CUSTOMER: Xem chi tiết 1 đơn hàng
    @GetMapping(ApiRoutes.Orders.CUSTOMER_BASE + ApiRoutes.Orders.BY_ID)
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrderById(userDetails.getId(), id)));
    }

    // 4. CUSTOMER: Hủy đơn hàng (khi đang ở trạng thái PENDING)
    @PutMapping(ApiRoutes.Orders.CUSTOMER_BASE + ApiRoutes.Orders.CANCEL)
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok("Hủy đơn hàng thành công", orderService.cancelOrder(userDetails.getId(), id)));
    }

    // 5. ADMIN: Xem danh sách toàn bộ đơn hàng của cửa hàng (có lọc theo trạng thái)
    @GetMapping(ApiRoutes.Orders.ADMIN_BASE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrdersForAdmin(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        int validatedPage = Math.max(page, 0);
        int validatedSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(validatedPage, validatedSize);
        return ResponseEntity.ok(ApiResponse.ok(orderService.getAllOrdersForAdmin(status, pageable)));
    }

    // 6. ADMIN: Cập nhật trạng thái đơn hàng (CONFIRMED, SHIPPING, DELIVERED, CANCELLED)
    @PutMapping(ApiRoutes.Orders.ADMIN_BASE + ApiRoutes.Orders.UPDATE_STATUS)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái đơn hàng thành công",
                orderService.updateOrderStatus(id, request)));
    }
}
