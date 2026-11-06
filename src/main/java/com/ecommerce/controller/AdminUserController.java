package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.constant.RoleEnum;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.common.response.PageResponse;
import com.ecommerce.dto.request.AdminUserCriteria;
import com.ecommerce.dto.response.AdminUserResponse;
import com.ecommerce.security.CustomUserDetails;
import com.ecommerce.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping(ApiRoutes.API_V1 + "/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    // 1. ADMIN: Lấy danh sách người dùng có lọc và phân trang
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) RoleEnum role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        AdminUserCriteria criteria = AdminUserCriteria.builder()
                .keyword(keyword)
                .role(role)
                .active(active)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDir(sortDir)
                .build();

        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách người dùng thành công",
                userService.getUsersForAdmin(criteria)));
    }

    // 2. ADMIN: Xem chi tiết hồ sơ người dùng (kèm số đơn hàng, tổng chi tiêu)
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUserDetail(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin người dùng thành công",
                userService.getUserDetailForAdmin(userId)));
    }

    // 3. ADMIN: Khóa / Mở khóa người dùng (chặn tự khóa bản thân)
    @PatchMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserStatus(
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            @PathVariable String userId,
            @RequestBody Map<String, Boolean> body) {
        String currentAdminId = adminDetails != null ? adminDetails.getId() : "";
        boolean active = body.getOrDefault("active", true);

        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái người dùng thành công",
                userService.updateUserStatus(currentAdminId, userId, active)));
    }

    // 4. ADMIN: Cập nhật vai trò người dùng (chặn tự hạ quyền, chặn hạ quyền Admin cuối cùng)
    @PatchMapping("/{userId}/roles")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserRoles(
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            @PathVariable String userId,
            @RequestBody Set<RoleEnum> roles) {
        String currentAdminId = adminDetails != null ? adminDetails.getId() : "";

        return ResponseEntity.ok(ApiResponse.ok("Cập nhật vai trò người dùng thành công",
                userService.updateUserRoles(currentAdminId, userId, roles)));
    }
}
