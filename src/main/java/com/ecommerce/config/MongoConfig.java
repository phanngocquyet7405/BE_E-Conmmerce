package com.ecommerce.config;

import com.ecommerce.document.SePayTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Slf4j
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }

    @Bean
    public CommandLineRunner initMongoIndices(MongoTemplate mongoTemplate) {
        return args -> {
            try {
                mongoTemplate.indexOps(SePayTransaction.class)
                        .ensureIndex(new Index().on("sepay_id", Sort.Direction.ASC).unique());
                log.info("Khởi tạo index duy nhất trên sepay_id cho SePayTransaction thành công.");
            } catch (Exception e) {
                log.warn("Không thể tạo index sepay_id khi khởi động (có thể DB chưa sẵn sàng): {}", e.getMessage());
            }
        };
    }
}
