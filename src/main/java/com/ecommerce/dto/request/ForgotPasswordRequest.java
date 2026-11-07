package com.ecommerce.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO yêu cầu quên mật khẩu.
 * Hệ thống chỉ hỗ trợ gửi mã OTP qua Email (SMS channel không được hỗ trợ).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 254, message = "Email không được vượt quá 254 ký tự")
    private String email;

    public String getIdentity() {
        return email;
    }

    public void setIdentity(String identity) {
        this.email = identity;
    }
}
