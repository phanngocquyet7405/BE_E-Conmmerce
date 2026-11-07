package com.ecommerce.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProductCriteria {
    private String keyword;
    private Boolean active;
    private String categoryId;
    private String brandId;
    private Boolean lowStock;
    @Builder.Default
    private Integer lowStockThreshold = 10;
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 15;
    @Builder.Default
    private String sortBy = "createdAt";
    @Builder.Default
    private String sortDir = "desc";
}
