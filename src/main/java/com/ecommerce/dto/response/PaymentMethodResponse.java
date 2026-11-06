package com.ecommerce.dto.response;

import com.ecommerce.document.PaymentMethod;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentMethodResponse {
    private final PaymentMethod code;
    private final String name;
    private final boolean enabled;
    private final String description;
    private final String condition;
}
