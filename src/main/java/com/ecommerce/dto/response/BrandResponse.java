package com.ecommerce.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class BrandResponse {
    private final String id;
    private final String name;
    private final String slug;
    private final String description;
    private final String logoUrl;
    private final Instant createdAt;
}
