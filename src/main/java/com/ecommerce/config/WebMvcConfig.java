package com.ecommerce.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath.toString() + "/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }

    /**
     * Đăng ký ShallowEtagHeaderFilter qua FilterRegistrationBean:
     * - Chỉ áp dụng cho các route đọc danh mục, thương hiệu, sản phẩm công khai.
     * - Không áp dụng cho /uploads/**.
     * - Không áp dụng cho các endpoint có header Authorization (yêu cầu xác thực cá nhân).
     */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> shallowEtagHeaderFilter() {
        ShallowEtagHeaderFilter filter = new ShallowEtagHeaderFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                // Bỏ qua không tính ETag cho bất kỳ request nào có Authorization header
                String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                return StringUtils.hasText(authHeader);
            }
        };

        FilterRegistrationBean<ShallowEtagHeaderFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns(
                "/api/products/*",
                "/api/categories/*",
                "/api/brands/*",
                "/api/v1/public/products/*",
                "/api/v1/public/categories/*",
                "/api/v1/public/brands/*"
        );
        registration.setName("customShallowEtagHeaderFilter");
        registration.setOrder(1);

        return registration;
    }
}
