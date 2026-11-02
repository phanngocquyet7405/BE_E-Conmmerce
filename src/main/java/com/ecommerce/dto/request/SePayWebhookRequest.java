package com.ecommerce.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SePayWebhookRequest {

    private Long id;

    private String gateway;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime transactionDate;

    private String accountNumber;

    private String subAccount;

    private String code;

    private String content;

    private String description;

    private String transferType;

    private Long transferAmount;

    private Long accumulated;

    private String referenceCode;
}
