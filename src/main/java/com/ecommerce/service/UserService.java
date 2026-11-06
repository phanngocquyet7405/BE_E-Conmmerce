package com.ecommerce.service;

import com.ecommerce.common.constant.RoleEnum;
import com.ecommerce.common.response.PageResponse;
import com.ecommerce.dto.request.AdminUserCriteria;
import com.ecommerce.dto.response.AdminUserResponse;

import java.util.Set;

public interface UserService {
    PageResponse<AdminUserResponse> getUsersForAdmin(AdminUserCriteria criteria);
    AdminUserResponse getUserDetailForAdmin(String userId);
    AdminUserResponse updateUserStatus(String currentAdminId, String userId, boolean active);
    AdminUserResponse updateUserRoles(String currentAdminId, String userId, Set<RoleEnum> roles);
}
