package com.ecommerce.document;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * OTP Entity để lưu trữ và quản lý mã OTP xác thực email / đặt lại mật khẩu.
 */
@Document(collection = "otp_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpToken {

    @Id
    private String id;

    @Indexed
    @Field("email")
    private String email;

    @Field("otp")
    private String otp;

    @Field("expiry_time")
    private Instant expiryTime;

    @Builder.Default
    @Field("is_used")
    private boolean used = false;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    public boolean isExpired() {
        return expiryTime != null && expiryTime.isBefore(Instant.now());
    }
}
