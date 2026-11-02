package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordRequest {
    @NotBlank(message = "Vui lòng nhập Email hoặc Số điện thoại")
    private String identity;

    // "EMAIL" hoặc "SMS" (nếu để trống hệ thống sẽ tự nhận diện theo định dạng)
    private String channel;
}
