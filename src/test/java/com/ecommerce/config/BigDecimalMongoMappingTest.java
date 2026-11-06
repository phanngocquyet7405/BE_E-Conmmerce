package com.ecommerce.config;

import org.bson.types.Decimal128;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BigDecimalMongoMappingTest {

    private final MongoConfig.BigDecimalToDecimal128Converter toDecimal128Converter =
            new MongoConfig.BigDecimalToDecimal128Converter();

    private final MongoConfig.Decimal128ToBigDecimalConverter toBigDecimalConverter =
            new MongoConfig.Decimal128ToBigDecimalConverter();

    @Test
    @DisplayName("1. BigDecimalToDecimal128Converter: Chuyển đổi BigDecimal sang Decimal128 chuẩn xác")
    void convert_BigDecimalToDecimal128_Success() {
        BigDecimal input = new BigDecimal("150000.50");
        Decimal128 result = toDecimal128Converter.convert(input);

        assertNotNull(result);
        assertEquals(input, result.bigDecimalValue());
    }

    @Test
    @DisplayName("2. Decimal128ToBigDecimalConverter: Chuyển đổi Decimal128 sang BigDecimal chuẩn xác")
    void convert_Decimal128ToBigDecimal_Success() {
        Decimal128 input = new Decimal128(new BigDecimal("29990000"));
        BigDecimal result = toBigDecimalConverter.convert(input);

        assertNotNull(result);
        assertEquals(new BigDecimal("29990000"), result);
    }

    @Test
    @DisplayName("3. Null safety khi convert")
    void convert_NullSafety() {
        assertNull(toDecimal128Converter.convert(null));
        assertNull(toBigDecimalConverter.convert(null));
    }

    @Test
    @DisplayName("4. So sánh số học: Decimal128 đảm bảo 2000000 > 15000000 là false (khác với chuỗi)")
    void decimal128_NumericSort_IsCorrect() {
        // Trong so sánh chuỗi: "2000000" > "15000000" là TRUE (sai số học)
        // Trong Decimal128: 2000000 so với 15000000 là NHỎ HƠN (đúng số học)
        Decimal128 smallNum = new Decimal128(new BigDecimal("2000000"));
        Decimal128 bigNum = new Decimal128(new BigDecimal("15000000"));

        assertTrue(smallNum.compareTo(bigNum) < 0,
                "Decimal128 phải so sánh số học đúng: 2.000.000 < 15.000.000");
    }
}
