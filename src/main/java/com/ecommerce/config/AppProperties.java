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
    private final Sepay sepay = new Sepay();
    private final Brevo brevo = new Brevo();

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
    public static class Sepay {
        private String apiKey;
        private String accountNumber;
        private String bankName;
        private String accountHolder;
        private String qrTemplate = "compact";
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
