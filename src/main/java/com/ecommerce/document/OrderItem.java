package com.ecommerce.document;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {
    private String productId;
    private String sku;
    private String productName;
    private String thumbnailUrl;
    private String brandName;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subTotal;
}
