package com.ecommerce.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductSearchCriteria {
    private String keyword;
    private String categoryId;
    private String brandId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private int page = 0;
    private int size = 12;
    private String sortBy = "createdAt";
    private String sortDir = "desc";
}
