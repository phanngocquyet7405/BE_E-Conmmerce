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

        if (!active) {
            // Khi khóa tài khoản: Ghi Redis TRƯỚC để thu hồi các phiên hiện tại.
            // Nếu Redis lỗi -> ném AppException ngay, MongoDB chưa bị sửa đổi (tránh trạng thái nửa vời).
            redisTokenService.blockUser(userId);
            log.info("Admin đã khóa tài khoản người dùng {} (ghi blocked:user, thu hồi refresh token)", userId);
        } else {
            // Khi mở khóa: Xóa key blocked:user trên Redis
            redisTokenService.unblockUser(userId);
            log.info("Admin đã mở khóa tài khoản người dùng {} (xóa key blocked:user)", userId);
        }

        user.setActive(active);
        try {
            return userRepository.save(user);
        } catch (Exception e) {
            // Hoàn tác Redis nếu lưu DB thất bại
            if (!active) {
                redisTokenService.unblockUser(userId);
            }
            throw e;
        }
    }

    @Override
    public User updateUserRoles(String userId, Set<RoleEnum> roles) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED, "Không tìm thấy người dùng"));

        // Khi đổi quyền: Ghi Redis TRƯỚC để thu hồi các token cũ của user.
        // Nếu Redis lỗi -> ném AppException ngay, MongoDB chưa bị sửa đổi vai trò (tránh trạng thái nửa vời).
        redisTokenService.blockUser(userId);
        log.info("Admin đã đổi vai trò người dùng {} thành {} (thu hồi các token cũ)", userId, roles);

        user.setRoles(roles);
        try {
            return userRepository.save(user);
        } catch (Exception e) {
            // Hoàn tác Redis nếu lưu DB thất bại
            redisTokenService.unblockUser(userId);
            throw e;
        }
    }
}
