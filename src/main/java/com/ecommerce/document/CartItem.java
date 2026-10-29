package com.ecommerce.document;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {
    private String productId;
    private String sku;
    private String productName;
    private String thumbnailUrl;
    private BigDecimal unitPrice;
    private Integer quantity;
    private String categoryName;
    private String brandName;
}
