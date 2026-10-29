package com.ecommerce.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();

    @Getter
    @Setter
    public static class Jwt {
        private String issuer;
        private String accessSecret;
        private String refreshSecret;
        private long accessExpirationMs;
        private long refreshExpirationMs;
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins;
    }
}
