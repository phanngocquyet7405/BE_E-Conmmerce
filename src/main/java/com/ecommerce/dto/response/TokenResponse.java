package com.ecommerce.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenResponse {
    private final String accessToken;
    private final String refreshToken;
    @Builder.Default
    private final String tokenType = "Bearer";
    private final long expiresIn;
}
