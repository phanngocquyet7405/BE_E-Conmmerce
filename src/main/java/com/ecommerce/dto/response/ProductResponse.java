package com.ecommerce.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class ProductResponse {
    private final String id;
    private final String sku;
    private final String name;
    private final String slug;
    private final String description;
    private final BigDecimal price;
    private final Integer stockQuantity;
    private final String thumbnailUrl;
    private final List<String> images;
    private final Boolean active;
    private final String categoryId;
    private final String categoryName;
    private final String brandId;
    private final String brandName;
    private final Instant createdAt;
}
