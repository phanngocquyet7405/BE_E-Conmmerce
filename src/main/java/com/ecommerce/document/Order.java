package com.ecommerce.document;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "orders")
@CompoundIndexes({
    @CompoundIndex(name = "userId_createdAt_idx", def = "{'user_id': 1, 'created_at': -1}"),
    @CompoundIndex(name = "status_createdAt_idx", def = "{'status': 1, 'created_at': -1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("order_code")
    private String orderCode;

    @Indexed
    @Field("user_id")
    private String userId;

    @Field("customer_name")
    private String customerName;

    @Field("customer_email")
    private String customerEmail;

    @Field("customer_phone")
    private String customerPhone;

    @Field("shipping_address")
    private Address shippingAddress;

    @Builder.Default
    @Field("items")
    private List<OrderItem> items = new ArrayList<>();

    @Field("total_amount")
    private BigDecimal totalAmount;

    @Field("shipping_fee")
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Field("final_amount")
    private BigDecimal finalAmount;

    @Field("payment_method")
    private PaymentMethod paymentMethod;

    @Field("status")
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Field("note")
    private String note;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
