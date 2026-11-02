package com.ecommerce.document;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document(collection = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SePayTransaction {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("sepay_id")
    private Long sepayId;

    @Field("gateway")
    private String gateway;

    @Field("transaction_date")
    private LocalDateTime transactionDate;

    @Field("account_number")
    private String accountNumber;

    @Field("sub_account")
    private String subAccount;

    @Field("code")
    private String code;

    @Field("amount_in")
    private Long amountIn;

    @Field("amount_out")
    private Long amountOut;

    @Field("accumulated")
    private Long accumulated;

    @Field("content")
    private String content;

    @Field("reference_code")
    private String referenceCode;

    @Field("raw_body")
    private String rawBody;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;
}
