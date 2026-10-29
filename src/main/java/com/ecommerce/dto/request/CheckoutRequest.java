package com.ecommerce.dto.request;

import com.ecommerce.document.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckoutRequest {
    @NotBlank(message = "Vui lòng chọn địa chỉ giao hàng")
    private String addressId;

    @NotNull(message = "Vui lòng chọn phương thức thanh toán")
    private PaymentMethod paymentMethod;

    private String note;
}
