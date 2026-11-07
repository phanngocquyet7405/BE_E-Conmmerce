package com.ecommerce.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;

@Slf4j
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "sepay")
public class SePayProperties {

    private String bankId;
    private String bankAccount;
    private String accountHolder;
    private String apiKey;
    private String webhookSecret;
    private String orderCodePrefix = "BETES";
    private String qrTemplate = "compact";

    @Autowired(required = false)
    private Environment environment;

    @PostConstruct
    public void validate() {
        if (isTestProfile()) {
            log.info("Chế độ profile 'test' đang kích hoạt: Bỏ qua kiểm tra bắt buộc cấu hình SePay.");
            return;
        }

        if (!StringUtils.hasText(bankId)) {
            throw new IllegalStateException("Cấu hình sepay.bank-id bắt buộc và không được để trống trên môi trường triển khai.");
        }
        if (!StringUtils.hasText(bankAccount)) {
            throw new IllegalStateException("Cấu hình sepay.bank-account bắt buộc và không được để trống trên môi trường triển khai.");
        }
        if (!StringUtils.hasText(accountHolder)) {
            throw new IllegalStateException("Cấu hình sepay.account-holder bắt buộc và không được để trống trên môi trường triển khai.");
        }
        if (!StringUtils.hasText(apiKey) && !StringUtils.hasText(webhookSecret)) {
            throw new IllegalStateException("Cần cấu hình ít nhất một khóa xác thực SePay webhook (sepay.api-key hoặc sepay.webhook-secret).");
        }
    }

    public boolean isTestProfile() {
        if (environment == null) {
            return false;
        }
        return Arrays.asList(environment.getActiveProfiles()).contains("test");
    }
}
