package com.ecommerce.service.impl;

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
import com.ecommerce.service.RedisTokenService;
import com.ecommerce.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final RedisTokenService redisTokenService;
    private final MongoTemplate mongoTemplate;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "email", "fullName", "phone", "createdAt", "updatedAt"
    );

    private String validateSortBy(String sortBy) {
        if (StringUtils.hasText(sortBy) && ALLOWED_SORT_FIELDS.contains(sortBy)) {
            return sortBy;
        }
        return "createdAt";
    }

    @Override
    public PageResponse<AdminUserResponse> getUsersForAdmin(AdminUserCriteria criteria) {
        Query query = new Query();

        if (criteria.getActive() != null) {
            query.addCriteria(Criteria.where("is_active").is(criteria.getActive()));
        }

        if (criteria.getRole() != null) {
            query.addCriteria(Criteria.where("roles").is(criteria.getRole()));
        }

        if (StringUtils.hasText(criteria.getKeyword())) {
            String safeRegex = ".*" + Pattern.quote(criteria.getKeyword().trim()) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("email").regex(safeRegex, "i"),
                    Criteria.where("full_name").regex(safeRegex, "i"),
                    Criteria.where("phone").regex(safeRegex, "i")
            ));
        }

        long totalElements = mongoTemplate.count(query, User.class);

        int validatedSize = Math.min(Math.max(criteria.getSize(), 1), 50);
        int validatedPage = Math.max(criteria.getPage(), 0);

        String safeSortBy = validateSortBy(criteria.getSortBy());
        Sort sort = "asc".equalsIgnoreCase(criteria.getSortDir())
                ? Sort.by(safeSortBy).ascending()
                : Sort.by(safeSortBy).descending();
        Pageable pageable = PageRequest.of(validatedPage, validatedSize, sort);
        query.with(pageable);

        List<User> users = mongoTemplate.find(query, User.class);
        List<AdminUserResponse> responses = users.stream().map(AdminUserResponse::fromEntity).toList();
        Page<AdminUserResponse> page = new PageImpl<>(responses, pageable, totalElements);

        return PageResponse.fromPage(page);
    }

    @Override
    public AdminUserResponse getUserDetailForAdmin(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED, "Không tìm thấy người dùng"));

        long totalOrders = orderRepository.countByUserId(userId);
        List<Order> orders = orderRepository.findByUserId(userId);

        // Doanh thu tính theo D9: paymentStatus == PAID và status != CANCELLED
        BigDecimal totalSpent = orders.stream()
                .filter(o -> o.getPaymentStatus() == PaymentStatus.PAID && o.getStatus() != OrderStatus.CANCELLED)
                .map(Order::getFinalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AdminUserResponse.fromEntityWithStats(user, totalOrders, totalSpent);
    }

    @Override
    public AdminUserResponse updateUserStatus(String currentAdminId, String userId, boolean active) {
        // Chặn admin tự khóa chính mình
        if (userId.equals(currentAdminId) && !active) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Admin không thể tự khóa tài khoản của chính mình");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED, "Không tìm thấy người dùng"));

        if (!active) {
            // Khi khóa: Ghi Redis TRƯỚC để thu hồi các phiên làm việc hiện tại
            redisTokenService.blockUser(userId);
            log.info("Admin {} đã khóa tài khoản người dùng {} (thu hồi refresh token)", currentAdminId, userId);
        } else {
            // Khi mở khóa: Xóa key blocked trên Redis
            redisTokenService.unblockUser(userId);
            log.info("Admin {} đã mở khóa tài khoản người dùng {}", currentAdminId, userId);
        }

        user.setActive(active);
        try {
            User saved = userRepository.save(user);
            return AdminUserResponse.fromEntity(saved);
        } catch (Exception e) {
            if (!active) {
                redisTokenService.unblockUser(userId);
            }
            throw e;
        }
    }

    @Override
    public AdminUserResponse updateUserRoles(String currentAdminId, String userId, Set<RoleEnum> roles) {
        // Roles không được rỗng
        if (roles == null || roles.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Danh sách vai trò (roles) không được để trống");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED, "Không tìm thấy người dùng"));

        // Chặn admin tự hạ quyền admin của chính mình
        if (userId.equals(currentAdminId) && !roles.contains(RoleEnum.ROLE_ADMIN)) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Admin không thể tự hạ quyền quản trị của chính mình");
        }

        // Chặn hạ quyền admin cuối cùng trong hệ thống
        if (user.getRoles() != null && user.getRoles().contains(RoleEnum.ROLE_ADMIN) && !roles.contains(RoleEnum.ROLE_ADMIN)) {
            long adminCount = userRepository.countByRolesContaining(RoleEnum.ROLE_ADMIN);
            if (adminCount <= 1) {
                log.warn("Cảnh báo: Admin {} cố gắng hạ quyền của Admin cuối cùng {}", currentAdminId, userId);
                throw new AppException(ErrorCode.ACCESS_DENIED, "Không thể hạ quyền Admin cuối cùng trong hệ thống");
            }
        }

        // Khi đổi quyền: Ghi Redis TRƯỚC để thu hồi token cũ của user
        redisTokenService.blockUser(userId);
        log.info("Admin {} đã đổi vai trò người dùng {} thành {}", currentAdminId, userId, roles);

        user.setRoles(roles);
        try {
            User saved = userRepository.save(user);
            return AdminUserResponse.fromEntity(saved);
        } catch (Exception e) {
            redisTokenService.unblockUser(userId);
            throw e;
        }
    }
}
