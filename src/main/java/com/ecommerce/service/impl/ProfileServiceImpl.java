package com.ecommerce.service.impl;

import com.ecommerce.document.Address;
import com.ecommerce.document.User;
import com.ecommerce.dto.request.AddressRequest;
import com.ecommerce.dto.request.ChangePasswordRequest;
import com.ecommerce.dto.request.UpdateProfileRequest;
import com.ecommerce.dto.response.UserProfileResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserProfileResponse getProfile(String userId) {
        User user = getUser(userId);
        return mapToProfileResponse(user);
    }

    @Override
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = getUser(userId);
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
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
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    public List<Address> getAddresses(String userId) {
        return getUser(userId).getAddresses();
    }

    @Override
    public Address addAddress(String userId, AddressRequest request) {
        User user = getUser(userId);
        if (user.getAddresses() == null) {
            user.setAddresses(new ArrayList<>());
        }

        if (request.isDefault() || user.getAddresses().isEmpty()) {
            user.getAddresses().forEach(a -> a.setDefault(false));
            request.setDefault(true);
        }

        Address address = Address.builder()
                .id(UUID.randomUUID().toString())
                .recipientName(request.getRecipientName())
                .phone(request.getPhone())
                .province(request.getProvince())
                .district(request.getDistrict())
                .ward(request.getWard())
                .streetAddress(request.getStreetAddress())
                .isDefault(request.isDefault())
                .build();

        user.getAddresses().add(address);
        userRepository.save(user);
        return address;
    }

    @Override
    public Address updateAddress(String userId, String addressId, AddressRequest request) {
        User user = getUser(userId);
        Address address = user.getAddresses().stream()
                .filter(a -> a.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));

        if (request.isDefault()) {
            user.getAddresses().forEach(a -> a.setDefault(false));
        }

        address.setRecipientName(request.getRecipientName());
        address.setPhone(request.getPhone());
        address.setProvince(request.getProvince());
        address.setDistrict(request.getDistrict());
        address.setWard(request.getWard());
        address.setStreetAddress(request.getStreetAddress());
        address.setDefault(request.isDefault());

        userRepository.save(user);
        return address;
    }

    @Override
    public void deleteAddress(String userId, String addressId) {
        User user = getUser(userId);
        user.getAddresses().removeIf(a -> a.getId().equals(addressId));
        userRepository.save(user);
    }

    @Override
    public void setDefaultAddress(String userId, String addressId) {
        User user = getUser(userId);
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
        userRepository.save(user);
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
                .roles(user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()))
                .addresses(user.getAddresses())
                .build();
    }
}
