package com.ecommerce.config;

import com.ecommerce.document.SePayTransaction;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.Decimal128;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.Index;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }

    /**
     * Cấu hình chuyển đổi BigDecimal <-> Decimal128 trong MongoDB:
     * Đảm bảo mọi trường số tiền được lưu dạng Decimal128 chuẩn BSON số học,
     * cho phép sắp xếp (sort), tính tổng (aggregation sum/avg) và so sánh đúng thứ tự số thay vì chuỗi.
     */
    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new BigDecimalToDecimal128Converter(),
                new Decimal128ToBigDecimalConverter()
        ));
    }

    @WritingConverter
    public static class BigDecimalToDecimal128Converter implements Converter<BigDecimal, Decimal128> {
        @Override
        public Decimal128 convert(BigDecimal source) {
            return source != null ? new Decimal128(source) : null;
        }
    }

    @ReadingConverter
    public static class Decimal128ToBigDecimalConverter implements Converter<Decimal128, BigDecimal> {
        @Override
        public BigDecimal convert(Decimal128 source) {
            return source != null ? source.bigDecimalValue() : null;
        }
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
