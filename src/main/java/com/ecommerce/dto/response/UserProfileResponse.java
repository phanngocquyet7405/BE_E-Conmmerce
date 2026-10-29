package com.ecommerce.dto.response;

import com.ecommerce.document.Address;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;

@Getter
@Builder
public class UserProfileResponse {
    private final String id;
    private final String email;
    private final String fullName;
    private final String phone;
    private final String avatarUrl;
    private final Set<String> roles;
    private final List<Address> addresses;
}
