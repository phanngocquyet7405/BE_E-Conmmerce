package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.document.Address;
import com.ecommerce.dto.request.AddressRequest;
import com.ecommerce.dto.request.ChangePasswordRequest;
import com.ecommerce.dto.request.UpdateProfileRequest;
import com.ecommerce.dto.response.UserProfileResponse;
import com.ecommerce.security.CustomUserDetails;
import com.ecommerce.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiRoutes.Profile.BASE)
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping(ApiRoutes.Profile.ME)
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.getProfile(userDetails.getId())));
    }

    @PutMapping(ApiRoutes.Profile.ME)
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật thông tin thành công", profileService.updateProfile(userDetails.getId(), request)));
    }

    @PostMapping(ApiRoutes.Profile.CHANGE_PASSWORD)
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(userDetails.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok("Đổi mật khẩu thành công", null));
    }

    @GetMapping(ApiRoutes.Profile.ADDRESSES)
    public ResponseEntity<ApiResponse<List<Address>>> getAddresses(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.getAddresses(userDetails.getId())));
    }

    @PostMapping(ApiRoutes.Profile.ADDRESSES)
    public ResponseEntity<ApiResponse<Address>> addAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AddressRequest request) {
        Address created = profileService.addAddress(userDetails.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Thêm địa chỉ thành công", created));
    }

    @PutMapping(ApiRoutes.Profile.ADDRESS_BY_ID)
    public ResponseEntity<ApiResponse<Address>> updateAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String addressId,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật địa chỉ thành công",
                profileService.updateAddress(userDetails.getId(), addressId, request)));
    }

    @DeleteMapping(ApiRoutes.Profile.ADDRESS_BY_ID)
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String addressId) {
        profileService.deleteAddress(userDetails.getId(), addressId);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa địa chỉ", null));
    }

    @PutMapping(ApiRoutes.Profile.SET_DEFAULT_ADDRESS)
    public ResponseEntity<ApiResponse<Void>> setDefaultAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String addressId) {
        profileService.setDefaultAddress(userDetails.getId(), addressId);
        return ResponseEntity.ok(ApiResponse.ok("Đã đặt làm địa chỉ mặc định", null));
    }
}
