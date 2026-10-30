package com.ecommerce.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Builder
@Jacksonized
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class CategoryResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String slug;
    private final String description;
    private final Instant createdAt;
}
