package com.ecommerce.service.impl;

import com.ecommerce.common.constant.RoleEnum;
import com.ecommerce.document.User;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.RedisTokenService;
import com.ecommerce.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RedisTokenService redisTokenService;

    @Override
    public User updateUserStatus(String userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED, "Không tìm thấy người dùng"));

        user.setActive(active);
        User savedUser = userRepository.save(user);

        if (!active) {
            // active = false: blockUser (ghi epoch timestamp lúc gọi) + xóa refresh token
            redisTokenService.blockUser(userId);
            log.info("Admin đã khóa tài khoản người dùng {} (ghi blocked:user, thu hồi refresh token)", userId);
        } else {
            // active = true: unblockUser (xóa key blocked:user để user đăng nhập lại được ngay)
            redisTokenService.unblockUser(userId);
            log.info("Admin đã mở khóa tài khoản người dùng {} (xóa key blocked:user)", userId);
        }

        return savedUser;
    }

    @Override
    public User updateUserRoles(String userId, Set<RoleEnum> roles) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED, "Không tìm thấy người dùng"));

        user.setRoles(roles);
        User savedUser = userRepository.save(user);

        // Khi đổi quyền: blockUser + xóa refresh token của user đó.
        // Các token được cấp SAU thời điểm này sẽ có issuedAt >= epochSeconds và dùng được ngay lập tức.
        redisTokenService.blockUser(userId);
        log.info("Admin đã đổi vai trò người dùng {} thành {} (thu hồi các token cũ)", userId, roles);

        return savedUser;
    }
}
