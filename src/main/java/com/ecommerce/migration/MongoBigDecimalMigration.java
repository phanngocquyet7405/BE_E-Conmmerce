package com.ecommerce.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Script di chuyển (migration) dữ liệu số tiền từ dạng String sang Decimal128 trong MongoDB.
 * CHÚ Ý QUY TẮC: Script này KHÔNG tự động thực thi khi khởi động.
 * Chỉ chạy khi có chỉ đạo và xác nhận từ chủ shop / quản trị viên cơ sở dữ liệu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoBigDecimalMigration {

    private final MongoTemplate mongoTemplate;

    /**
     * Quét và chuyển đổi các trường số tiền lưu dạng String sang Decimal128.
     * Áp dụng cho:
     * - products: price
     * - orders: total_amount, shipping_fee, final_amount, paid_amount, amount_discrepancy, items[].price, items[].sub_total
     * - carts: items[].unit_price, items[].sub_total
     */
    public void executeMigration() {
        log.info("=== BẮT ĐẦU QUÁ TRÌNH DI CHUYỂN DỮ LIỆU SỐ TIỀN (STRING -> DECIMAL128) ===");

        migrateProducts();
        migrateOrders();
        migrateCarts();

        log.info("=== HOÀN TẤT QUÁ TRÌNH DI CHUYỂN DỮ LIỆU SỐ TIỀN ===");
    }

    private void migrateProducts() {
        int updatedCount = 0;
        List<Document> rawProducts = mongoTemplate.getCollection("products").find().into(new java.util.ArrayList<>());

        for (Document doc : rawProducts) {
            Object priceVal = doc.get("price");
            if (priceVal instanceof String strPrice) {
                try {
                    BigDecimal bd = new BigDecimal(strPrice);
                    mongoTemplate.getCollection("products").updateOne(
                            new Document("_id", doc.get("_id")),
                            new Document("$set", new Document("price", new Decimal128(bd)))
                    );
                    updatedCount++;
                } catch (Exception ex) {
                    log.warn("Lỗi chuyển đổi price cho product {}: {}", doc.get("_id"), ex.getMessage());
                }
            }
        }
        log.info("Đã chuyển đổi {} sản phẩm sang kiểu Decimal128", updatedCount);
    }

    private void migrateOrders() {
        int updatedCount = 0;
        List<Document> rawOrders = mongoTemplate.getCollection("orders").find().into(new java.util.ArrayList<>());

        for (Document doc : rawOrders) {
            Document updateFields = new Document();

            convertFieldIfString(doc, "total_amount", updateFields);
            convertFieldIfString(doc, "shipping_fee", updateFields);
            convertFieldIfString(doc, "final_amount", updateFields);
            convertFieldIfString(doc, "paid_amount", updateFields);
            convertFieldIfString(doc, "amount_discrepancy", updateFields);

            // Xử lý các items trong order
            Object itemsObj = doc.get("items");
            if (itemsObj instanceof List<?> items) {
                boolean itemModified = false;
                for (Object itemObj : items) {
                    if (itemObj instanceof Document itemDoc) {
                        if (itemDoc.get("price") instanceof String sp) {
                            itemDoc.put("price", new Decimal128(new BigDecimal(sp)));
                            itemModified = true;
                        }
                        if (itemDoc.get("sub_total") instanceof String sst) {
                            itemDoc.put("sub_total", new Decimal128(new BigDecimal(sst)));
                            itemModified = true;
                        }
                    }
                }
                if (itemModified) {
                    updateFields.put("items", items);
                }
            }

            if (!updateFields.isEmpty()) {
                mongoTemplate.getCollection("orders").updateOne(
                        new Document("_id", doc.get("_id")),
                        new Document("$set", updateFields)
                );
                updatedCount++;
            }
        }
        log.info("Đã chuyển đổi {} đơn hàng sang kiểu Decimal128", updatedCount);
    }

    private void migrateCarts() {
        int updatedCount = 0;
        List<Document> rawCarts = mongoTemplate.getCollection("carts").find().into(new java.util.ArrayList<>());

        for (Document doc : rawCarts) {
            Object itemsObj = doc.get("items");
            if (itemsObj instanceof List<?> items) {
                boolean itemModified = false;
                for (Object itemObj : items) {
                    if (itemObj instanceof Document itemDoc) {
                        if (itemDoc.get("unit_price") instanceof String sup) {
                            itemDoc.put("unit_price", new Decimal128(new BigDecimal(sup)));
                            itemModified = true;
                        }
                        if (itemDoc.get("sub_total") instanceof String sst) {
                            itemDoc.put("sub_total", new Decimal128(new BigDecimal(sst)));
                            itemModified = true;
                        }
                    }
                }
                if (itemModified) {
                    mongoTemplate.getCollection("carts").updateOne(
                            new Document("_id", doc.get("_id")),
                            new Document("$set", new Document("items", items))
                    );
                    updatedCount++;
                }
            }
        }
        log.info("Đã chuyển đổi {} giỏ hàng sang kiểu Decimal128", updatedCount);
    }

    private void convertFieldIfString(Document doc, String fieldName, Document updateFields) {
        Object val = doc.get(fieldName);
        if (val instanceof String strVal && !strVal.isBlank()) {
            try {
                updateFields.put(fieldName, new Decimal128(new BigDecimal(strVal)));
            } catch (Exception ex) {
                log.warn("Không thể convert trường {} giá trị {}: {}", fieldName, strVal, ex.getMessage());
            }
        }
    }
}
