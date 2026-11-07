package com.ecommerce.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockProductResponse {
    private String id;
    private String name;
    private String sku;
    private Integer stockQuantity;
    private BigDecimal price;
    private Boolean active;
}
