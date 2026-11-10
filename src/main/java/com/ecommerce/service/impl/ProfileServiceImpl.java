package com.ecommerce.service.impl;

import com.ecommerce.document.Address;
import com.ecommerce.document.User;
import com.ecommerce.dto.request.AddressRequest;
import com.ecommerce.dto.request.ChangePasswordRequest;
import com.ecommerce.dto.request.UpdateProfileRequest;
import com.ecommerce.dto.response.UserProfileResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.util.DataMaskingUtil;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.EmailService;
import com.ecommerce.service.PasswordPolicyService;
import com.ecommerce.service.ProfileService;
import com.ecommerce.service.RedisTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTokenService redisTokenService;
    private final EmailService emailService;
    private final PasswordPolicyService passwordPolicyService;

    @Value("${app.address.max-per-user:10}")
    private int maxAddressesPerUser = 10;

    public void setMaxAddressesPerUser(int maxAddressesPerUser) {
        this.maxAddressesPerUser = maxAddressesPerUser;
    }

    @Override
    public UserProfileResponse getProfile(String userId) {
        User user = getUser(userId);
        return mapToProfileResponse(user);
    }

    @Override
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = getUser(userId);
        user.setFullName(request.getFullName());
        if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
            user.setPhone(request.getPhone());
            user.setPhoneVerified(false);
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        return mapToProfileResponse(userRepository.save(user));
    }

    @Override
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = getUser(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.PASSWORD_NOT_MATCH);
        }

        passwordPolicyService.validatePassword(request.getNewPassword(), user.getEmail());
        passwordPolicyService.validateNewPasswordDiffersFromOld(request.getNewPassword(), user.getPassword());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // V3 & T10: Thu hồi toàn bộ phiên đăng nhập cũ trên mọi thiết bị
        try {
            redisTokenService.revokeAllSessions(user.getId());
        } catch (Exception ex) {
            log.error("Mật khẩu của user {} đã đổi nhưng lỗi khi thu hồi toàn bộ phiên đăng nhập: {}", DataMaskingUtil.maskEmail(user.getEmail()), ex.getMessage());
            throw new AppException(ErrorCode.AUTH_SERVICE_UNAVAILABLE,
                    "mật khẩu đã đổi nhưng chưa đăng xuất được các thiết bị khác, hãy thử 'Đăng xuất tất cả'");
        }

        // T8: Gửi email thông báo đổi mật khẩu
        try {
            emailService.sendPasswordChangedEmail(user.getEmail());
        } catch (Exception ignored) {
        }
    }

    @Override
    public List<Address> getAddresses(String userId) {
        User user = getUser(userId);
        return user.getAddresses() != null ? user.getAddresses() : Collections.emptyList();
    }

    @Override
    public Address addAddress(String userId, AddressRequest request) {
        User user = getUser(userId);
        if (user.getAddresses() == null) {
            user.setAddresses(new ArrayList<>());
        }

        if (user.getAddresses().size() >= maxAddressesPerUser) {
            throw new AppException(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
        }

        validateGps(request.getLatitude(), request.getLongitude());

        boolean shouldBeDefault = request.isDefault() || user.getAddresses().isEmpty();
        if (shouldBeDefault) {
            user.getAddresses().forEach(a -> a.setDefault(false));
        }

        Address address = Address.builder()
                .id(UUID.randomUUID().toString())
                .build();
        mapRequestToAddress(request, address);
        address.setDefault(shouldBeDefault);

        user.getAddresses().add(address);
        normalizeAddresses(user.getAddresses());

        userRepository.save(user);
        return address;
    }

    @Override
    public Address updateAddress(String userId, String addressId, AddressRequest request) {
        User user = getUser(userId);
        if (user.getAddresses() == null || user.getAddresses().isEmpty()) {
            throw new AppException(ErrorCode.ADDRESS_NOT_FOUND);
        }

        Address address = user.getAddresses().stream()
                .filter(a -> a.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));

        validateGps(request.getLatitude(), request.getLongitude());

        boolean currentIsDefault = address.isDefault();
        boolean requestedDefault = request.isDefault();

        mapRequestToAddress(request, address);

        if (requestedDefault) {
            user.getAddresses().forEach(a -> a.setDefault(false));
            address.setDefault(true);
        } else {
            if (currentIsDefault && user.getAddresses().size() == 1) {
                address.setDefault(true);
            } else {
                address.setDefault(false);
            }
        }

        normalizeAddresses(user.getAddresses());
        userRepository.save(user);
        return address;
    }

    @Override
    public void deleteAddress(String userId, String addressId) {
        User user = getUser(userId);
        if (user.getAddresses() == null || user.getAddresses().isEmpty()) {
            throw new AppException(ErrorCode.ADDRESS_NOT_FOUND);
        }

        boolean removed = user.getAddresses().removeIf(a -> a.getId().equals(addressId));
        if (!removed) {
            throw new AppException(ErrorCode.ADDRESS_NOT_FOUND);
        }

        normalizeAddresses(user.getAddresses());
        userRepository.save(user);
    }

    @Override
    public void setDefaultAddress(String userId, String addressId) {
        User user = getUser(userId);
        if (user.getAddresses() == null || user.getAddresses().isEmpty()) {
            throw new AppException(ErrorCode.ADDRESS_NOT_FOUND);
        }

        boolean found = false;
        for (Address a : user.getAddresses()) {
            if (a.getId().equals(addressId)) {
                a.setDefault(true);
                found = true;
            } else {
                a.setDefault(false);
            }
        }
        if (!found) {
            throw new AppException(ErrorCode.ADDRESS_NOT_FOUND);
        }

        normalizeAddresses(user.getAddresses());
        userRepository.save(user);
    }

    private void normalizeAddresses(List<Address> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return;
        }
        long defaultCount = addresses.stream().filter(Address::isDefault).count();
        if (defaultCount == 0) {
            addresses.get(0).setDefault(true);
        } else if (defaultCount > 1) {
            int lastDefaultIdx = -1;
            for (int i = 0; i < addresses.size(); i++) {
                if (addresses.get(i).isDefault()) {
                    lastDefaultIdx = i;
                }
            }
            for (int i = 0; i < addresses.size(); i++) {
                addresses.get(i).setDefault(i == lastDefaultIdx);
            }
        }
    }

    private void mapRequestToAddress(AddressRequest request, Address address) {
        address.setRecipientName(trimNonNull(request.getRecipientName()));
        address.setPhone(trimNonNull(request.getPhone()));
        address.setProvince(trimNonNull(request.getProvince()));
        address.setDistrict(trimOrNull(request.getDistrict()));
        address.setWard(trimNonNull(request.getWard()));
        address.setStreetAddress(trimNonNull(request.getStreetAddress()));
        address.setLabel(request.getLabel());
        address.setNote(trimOrNull(request.getNote()));
        address.setLatitude(request.getLatitude());
        address.setLongitude(request.getLongitude());
        address.setProvinceCode(trimOrNull(request.getProvinceCode()));
        address.setDistrictCode(trimOrNull(request.getDistrictCode()));
        address.setWardCode(trimOrNull(request.getWardCode()));
    }

    private void validateGps(Double latitude, Double longitude) {
        if (latitude == null && longitude == null) {
            return;
        }
        if (latitude == null || longitude == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Tọa độ GPS không hợp lệ: Vĩ độ và kinh độ phải cùng có hoặc cùng để trống");
        }
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90.0 || latitude > 90.0
                || longitude < -180.0 || longitude > 180.0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Tọa độ GPS không hợp lệ: Vĩ độ trong khoảng [-90, 90], kinh độ trong khoảng [-180, 180]");
        }
    }

    private String trimNonNull(String str) {
        return str != null ? str.trim() : null;
    }

    private String trimOrNull(String str) {
        if (str == null) return null;
        String trimmed = str.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private User getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }

    private UserProfileResponse mapToProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .roles(user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()))
                .addresses(user.getAddresses())
                .build();
    }
}
