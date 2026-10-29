package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BrandRequest {
    @NotBlank(message = "Tên thương hiệu không được để trống")
    private String name;

    @NotBlank(message = "Slug thương hiệu không được để trống")
    private String slug;

    private String description;
    private String logoUrl;
}
