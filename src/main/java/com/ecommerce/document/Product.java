package com.ecommerce.document;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sku;

    @Field("name")
    private String name;

    @Indexed(unique = true)
    private String slug;

    private String description;

    @Field("price")
    private BigDecimal price;

    @Field("stock_quantity")
    private Integer stockQuantity;

    @Field("thumbnail_url")
    private String thumbnailUrl;

    @Builder.Default
    @Field("images")
    private List<String> images = new ArrayList<>();

    @Field("active")
    @Builder.Default
    private Boolean active = true;

    @Indexed
    @Field("category_id")
    private String categoryId;

    @Field("category_name")
    private String categoryName;

    @Indexed
    @Field("brand_id")
    private String brandId;

    @Field("brand_name")
    private String brandName;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
