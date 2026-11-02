package com.ecommerce.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendOtpRequest {
    // "SMS" hoặc "EMAIL"
    private String channel = "EMAIL";
}
