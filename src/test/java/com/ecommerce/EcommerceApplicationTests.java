package com.ecommerce;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.data.mongodb.uri=mongodb://localhost:27017/ecommerce_test_db",
    "spring.data.mongodb.auto-index-creation=false",
    "app.jwt.access-secret=bXktc3VwZXItc2VjcmV0LWaccessLtleS1mb3ItZWNvbW1lcmNlLTIwMjYtYmFja2VuZA==",
    "app.jwt.refresh-secret=bXktc3VwZXItc2VjcmV0LXJlZnJlc2gta2V5LWZvci1lY29tbWVyY2UtMjAyNi1iYWNrZW5k",
    "sepay.bank-id=MBBank",
    "sepay.bank-account=0862483762",
    "sepay.account-holder=NGUYEN VAN A",
    "sepay.webhook-secret=whsec_test_secret_123"
})
@ActiveProfiles("test")
class EcommerceApplicationTests {

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    @DisplayName("Kiểm tra Spring context và SecurityFilterChain khởi tạo thành công")
    void contextLoads() {
        assertNotNull(securityFilterChain, "SecurityFilterChain phải được khởi tạo thành công");
    }
}
