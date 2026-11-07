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
    private final Brevo brevo = new Brevo();
    private final Auth auth = new Auth();

    @Getter
    @Setter
    public static class Auth {
        private int otpCooldownSeconds = 60;
        private int otpMaxPerHour = 5;
        private int unverifiedRetentionHours = 24;
    }

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

    @Getter
    @Setter
    public static class Brevo {
        private String apiKey;
        private String smsSender = "Ecommerce";
        private String senderEmail = "no-reply@ecommerce.com";
        private String senderName = "Ecommerce Store";
    }
}
