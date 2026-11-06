package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProcessTransactionRequest {

    @NotBlank(message = "Ghi chú xử lý không được để trống")
    private String note;

    private String action; // e.g. "RESOLVED", "REFUNDED"
}
