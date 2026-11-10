package com.ecommerce.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityCorsTest {

    private SecurityConfig securityConfig;
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        // Giả lập cấu hình CORS từ biến môi trường hoặc application.yml
        appProperties.getCors().setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "http://localhost:8081",
                "https://nhom3qms.vercel.app",
                "https://*.vercel.app"
        ));
        securityConfig = new SecurityConfig(null, null, null, null, appProperties);
    }

    @Test
    @DisplayName("1. CORS cho phép domain quản trị Vercel chính thức (https://nhom3qms.vercel.app)")
    void cors_AllowsOfficialAdminVercelDomain() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        request.addHeader("Origin", "https://nhom3qms.vercel.app");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config, "CorsConfiguration không được null");

        String matchedOrigin = config.checkOrigin("https://nhom3qms.vercel.app");
        assertEquals("https://nhom3qms.vercel.app", matchedOrigin, "Domain admin Vercel phải được chấp thuận");
        assertTrue(config.getAllowCredentials(), "AllowCredentials phải là true");
        assertTrue(config.getAllowedMethods().contains("OPTIONS"), "Cần cho phép method OPTIONS");
        assertTrue(config.getAllowedMethods().contains("POST"), "Cần cho phép method POST");
    }

    @Test
    @DisplayName("2. CORS cho phép các subdomain preview động trên Vercel qua wildcard pattern (*.vercel.app)")
    void cors_AllowsVercelPreviewSubdomains() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        request.addHeader("Origin", "https://shopdev-admin-preview-abc.vercel.app");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config);

        String matchedOrigin = config.checkOrigin("https://shopdev-admin-preview-abc.vercel.app");
        assertEquals("https://shopdev-admin-preview-abc.vercel.app", matchedOrigin, "Subdomain vercel.app phải khớp pattern");
    }

    @Test
    @DisplayName("3. CORS cho phép các domain phát triển cục bộ (localhost 3000, 5173, 8081)")
    void cors_AllowsLocalDevelopmentOrigins() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config);

        assertEquals("http://localhost:3000", config.checkOrigin("http://localhost:3000"));
        assertEquals("http://localhost:5173", config.checkOrigin("http://localhost:5173"));
        assertEquals("http://localhost:8081", config.checkOrigin("http://localhost:8081"));
    }

    @Test
    @DisplayName("4. CORS từ chối domain lạ không được ủy quyền (fail-closed chống rò rỉ dữ liệu)")
    void cors_RejectsUntrustedOrigins() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        request.addHeader("Origin", "https://malicious-attacker-site.com");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config);

        String matchedOrigin = config.checkOrigin("https://malicious-attacker-site.com");
        assertNull(matchedOrigin, "Domain lạ không được phép khớp CORS");
    }

    @Test
    @DisplayName("5. CORS phân tách chính xác khi biến môi trường truyền chuỗi gộp ngăn cách bằng dấu phẩy")
    void cors_CorrectlyParsesCommaSeparatedOriginsString() {
        AppProperties props = new AppProperties();
        // Trường hợp Render hoặc .env truyền một chuỗi đơn gộp nhiều origin
        props.getCors().setAllowedOrigins(List.of(
                "http://localhost:3000, http://localhost:5173, https://nhom3qms.vercel.app, https://*.vercel.app"
        ));
        SecurityConfig configInstance = new SecurityConfig(null, null, null, null, props);

        CorsConfigurationSource source = configInstance.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config);

        assertEquals("https://nhom3qms.vercel.app", config.checkOrigin("https://nhom3qms.vercel.app"));
        assertEquals("http://localhost:3000", config.checkOrigin("http://localhost:3000"));
        assertEquals("http://localhost:5173", config.checkOrigin("http://localhost:5173"));
    }

    @Test
    @DisplayName("6. CORS loại bỏ dấu sao đơn độc (*) để không xung đột với allowCredentials = true")
    void cors_FiltersOutStandaloneStarToPreventCredentialsConflict() {
        AppProperties props = new AppProperties();
        props.getCors().setAllowedOrigins(List.of("*", "  *  ", "https://nhom3qms.vercel.app"));
        SecurityConfig configInstance = new SecurityConfig(null, null, null, null, props);

        CorsConfigurationSource source = configInstance.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config);

        // Đảm bảo không chứa dấu sao đơn độc trong allowedOrigins
        if (config.getAllowedOrigins() != null) {
            assertFalse(config.getAllowedOrigins().contains("*"), "AllowedOrigins không được chứa dấu * đơn độc");
        }
        assertEquals("https://nhom3qms.vercel.app", config.checkOrigin("https://nhom3qms.vercel.app"));
    }
}
