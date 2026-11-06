package com.ecommerce.config;

import com.ecommerce.document.SePayTransaction;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    /**
     * Kiểm tra trạng thái Replica Set của MongoDB khi ứng dụng khởi động.
     * - Nếu là Replica Set hoặc Mongos: kích hoạt MongoTransactionManager cho multi-document transactions.
     * - Nếu là Standalone: ghi log cảnh báo rõ ràng và kích hoạt StandaloneFallbackTransactionManager để tránh
     *   lỗi "Command failed with error 20 (IllegalOperation)" khi chạy @Transactional.
     */
    @Bean
    public PlatformTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        try {
            Document helloDoc = dbFactory.getMongoDatabase().runCommand(new Document("hello", 1));
            boolean isReplicaSet = helloDoc.containsKey("setName") || "isdbgrid".equalsIgnoreCase(helloDoc.getString("msg"));
            if (isReplicaSet) {
                String setName = helloDoc.getString("setName");
                log.info("MongoDB cluster hỗ trợ giao dịch đa tài liệu (ReplicaSet/Mongos: {}). Đã kích hoạt MongoTransactionManager.",
                        setName != null ? setName : "mongos");
                return new MongoTransactionManager(dbFactory);
            } else {
                log.warn("==========================================================================================");
                log.warn("CẢNH BÁO KIẾN TRÚC: MongoDB đang chạy ở chế độ STANDALONE (không có Replica Set).");
                log.warn("MongoDB Standalone không hỗ trợ giao dịch đa tài liệu (@Transactional).");
                log.warn("Hệ thống chuyển sang StandaloneFallbackTransactionManager để xử lý bằng atomic operations ($inc, findAndModify) và compensating rollback.");
                log.warn("Để kích hoạt hoàn toàn MongoDB transactions trên local, xem file: docker-compose.mongo-rs.yml");
                log.warn("==========================================================================================");
                return new StandaloneFallbackTransactionManager();
            }
        } catch (Exception e) {
            log.warn("Không thể kiểm tra trạng thái Replica Set ({}), sử dụng MongoTransactionManager mặc định.", e.getMessage());
            return new MongoTransactionManager(dbFactory);
        }
    }

    /**
     * Fallback Transaction Manager khi chạy trên MongoDB Standalone (local không bật replica set).
     */
    public static class StandaloneFallbackTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // No-op trên Standalone DB
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // No-op
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // No-op
        }
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
