package com.ecommerce;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class EcommerceApplicationTests {

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    @DisplayName("Kiểm tra Spring context và SecurityFilterChain khởi tạo thành công")
    void contextLoads() {
        assertNotNull(securityFilterChain, "SecurityFilterChain phải được khởi tạo thành công");
    }
}
