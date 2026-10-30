package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.constant.RoleEnum;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.document.User;
import com.ecommerce.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping(ApiRoutes.API_V1 + "/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @PatchMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUserStatus(
            @PathVariable String userId,
            @RequestBody Map<String, Boolean> body) {
        boolean active = body.getOrDefault("active", true);
        User user = userService.updateUserStatus(userId, active);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái người dùng thành công", Map.of(
                "userId", user.getId(),
                "active", user.isActive()
        )));
    }

    @PatchMapping("/{userId}/roles")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUserRoles(
            @PathVariable String userId,
            @RequestBody Set<RoleEnum> roles) {
        User user = userService.updateUserRoles(userId, roles);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật quyền người dùng thành công", Map.of(
                "userId", user.getId(),
                "roles", user.getRoles()
        )));
    }
}
