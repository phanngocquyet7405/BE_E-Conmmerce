package com.ecommerce.config;

import io.lettuce.core.RedisURI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.url:}")
    private String redisUrl;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${spring.data.redis.timeout:5000ms}")
    private Duration timeout;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfigBuilder = LettuceClientConfiguration.builder()
                .commandTimeout(timeout);

        if (StringUtils.hasText(redisUrl)) {
            RedisURI uri = RedisURI.create(redisUrl);
            RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
            standaloneConfig.setHostName(uri.getHost());
            standaloneConfig.setPort(uri.getPort());
            if (StringUtils.hasText(uri.getUsername())) {
                standaloneConfig.setUsername(uri.getUsername());
            }
            if (uri.getPassword() != null && uri.getPassword().length > 0) {
                standaloneConfig.setPassword(RedisPassword.of(new String(uri.getPassword())));
            }
            standaloneConfig.setDatabase(uri.getDatabase());

            if (uri.isSsl() || redisUrl.startsWith("rediss://")) {
                clientConfigBuilder.useSsl();
            }

            return new LettuceConnectionFactory(standaloneConfig, clientConfigBuilder.build());
        }

        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (StringUtils.hasText(redisPassword)) {
            serverConfig.setPassword(RedisPassword.of(redisPassword));
        }

        if (sslEnabled) {
            clientConfigBuilder.useSsl();
        }

        return new LettuceConnectionFactory(serverConfig, clientConfigBuilder.build());
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
