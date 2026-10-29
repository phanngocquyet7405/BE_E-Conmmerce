package com.ecommerce.service;

import com.ecommerce.document.Address;
import com.ecommerce.dto.request.AddressRequest;
import com.ecommerce.dto.request.ChangePasswordRequest;
import com.ecommerce.dto.request.UpdateProfileRequest;
import com.ecommerce.dto.response.UserProfileResponse;

import java.util.List;

public interface ProfileService {
    UserProfileResponse getProfile(String userId);
    UserProfileResponse updateProfile(String userId, UpdateProfileRequest request);
    void changePassword(String userId, ChangePasswordRequest request);
    List<Address> getAddresses(String userId);
    Address addAddress(String userId, AddressRequest request);
    Address updateAddress(String userId, String addressId, AddressRequest request);
    void deleteAddress(String userId, String addressId);
    void setDefaultAddress(String userId, String addressId);
}
