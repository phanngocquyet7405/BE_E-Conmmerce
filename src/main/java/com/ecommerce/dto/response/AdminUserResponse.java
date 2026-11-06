package com.ecommerce.dto.response;

import com.ecommerce.common.constant.RoleEnum;
import com.ecommerce.document.Address;
import com.ecommerce.document.User;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Getter
@Builder
public class AdminUserResponse {
    private final String id;
    private final String email;
    private final String fullName;
    private final String phone;
    private final String avatarUrl;
    private final Set<RoleEnum> roles;
    private final boolean active;
    private final boolean emailVerified;
    private final boolean phoneVerified;
    private final List<Address> addresses;
    private final Instant createdAt;
    private final Instant updatedAt;

    // Các trường thống kê hồ sơ cho GET /admin/users/{id}
    private final Long totalOrders;
    private final BigDecimal totalSpent;

    public static AdminUserResponse fromEntity(User user) {
        if (user == null) return null;
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .roles(user.getRoles())
                .active(user.isActive())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .addresses(user.getAddresses())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public static AdminUserResponse fromEntityWithStats(User user, long totalOrders, BigDecimal totalSpent) {
        if (user == null) return null;
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .roles(user.getRoles())
                .active(user.isActive())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .addresses(user.getAddresses())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .totalOrders(totalOrders)
                .totalSpent(totalSpent)
                .build();
    }
}
