package com.ecommerce.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class SePayPropertiesFailFastTest {

    @Test
    @DisplayName("Fail-fast khi thiếu sepay.bank-id trên môi trường triển khai")
    void validate_MissingBankId_ThrowsIllegalStateException() {
        SePayProperties props = new SePayProperties();
        props.setEnvironment(new MockEnvironment().withProperty("spring.profiles.active", "prod"));
        props.setBankAccount("0862483762");
        props.setAccountHolder("NGUYEN VAN A");
        props.setApiKey("test_api_key");

        IllegalStateException ex = assertThrows(IllegalStateException.class, props::validate);
        assertTrue(ex.getMessage().contains("sepay.bank-id"));
    }

    @Test
    @DisplayName("Fail-fast khi thiếu sepay.bank-account trên môi trường triển khai")
    void validate_MissingBankAccount_ThrowsIllegalStateException() {
        SePayProperties props = new SePayProperties();
        props.setEnvironment(new MockEnvironment().withProperty("spring.profiles.active", "prod"));
        props.setBankId("MBBank");
        props.setAccountHolder("NGUYEN VAN A");
        props.setApiKey("test_api_key");

        IllegalStateException ex = assertThrows(IllegalStateException.class, props::validate);
        assertTrue(ex.getMessage().contains("sepay.bank-account"));
    }

    @Test
    @DisplayName("Fail-fast khi thiếu sepay.account-holder trên môi trường triển khai")
    void validate_MissingAccountHolder_ThrowsIllegalStateException() {
        SePayProperties props = new SePayProperties();
        props.setEnvironment(new MockEnvironment().withProperty("spring.profiles.active", "prod"));
        props.setBankId("MBBank");
        props.setBankAccount("0862483762");
        props.setApiKey("test_api_key");

        IllegalStateException ex = assertThrows(IllegalStateException.class, props::validate);
        assertTrue(ex.getMessage().contains("sepay.account-holder"));
    }

    @Test
    @DisplayName("Fail-fast khi thiếu cả api-key và webhook-secret trên môi trường triển khai")
    void validate_MissingWebhookCredentials_ThrowsIllegalStateException() {
        SePayProperties props = new SePayProperties();
        props.setEnvironment(new MockEnvironment().withProperty("spring.profiles.active", "prod"));
        props.setBankId("MBBank");
        props.setBankAccount("0862483762");
        props.setAccountHolder("NGUYEN VAN A");

        IllegalStateException ex = assertThrows(IllegalStateException.class, props::validate);
        assertTrue(ex.getMessage().contains("khóa xác thực SePay webhook"));
    }

    @Test
    @DisplayName("Khởi động thành công khi có đủ cấu hình bắt buộc trên môi trường triển khai")
    void validate_FullConfiguration_Success() {
        SePayProperties props = new SePayProperties();
        props.setEnvironment(new MockEnvironment().withProperty("spring.profiles.active", "prod"));
        props.setBankId("MBBank");
        props.setBankAccount("0862483762");
        props.setAccountHolder("NGUYEN VAN A");
        props.setApiKey("sec_key_12345");

        assertDoesNotThrow(props::validate);
    }

    @Test
    @DisplayName("Cho phép profile test bỏ qua kiểm tra bắt buộc cấu hình SePay")
    void validate_TestProfile_BypassesValidation() {
        MockEnvironment mockEnv = new MockEnvironment();
        mockEnv.setActiveProfiles("test");

        SePayProperties props = new SePayProperties();
        props.setEnvironment(mockEnv);

        assertDoesNotThrow(props::validate);
    }
}
