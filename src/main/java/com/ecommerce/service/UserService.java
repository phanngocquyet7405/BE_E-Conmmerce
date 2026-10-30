package com.ecommerce.service;

import com.ecommerce.common.constant.RoleEnum;
import com.ecommerce.document.User;

import java.util.Set;

public interface UserService {
    User updateUserStatus(String userId, boolean active);
    User updateUserRoles(String userId, Set<RoleEnum> roles);
}
