package com.ecommerce.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyResetOtpResponse {
    private String resetToken;
    private long expiresIn;
}
