package com.ecommerce.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataMaskingUtilTest {

    @Test
    @DisplayName("Che email thông thường giữ ký tự đầu và tên miền")
    void maskEmail_StandardEmail_MasksCorrectly() {
        assertEquals("c***@gmail.com", DataMaskingUtil.maskEmail("customer@gmail.com"));
        assertEquals("a***@ecommerce.com", DataMaskingUtil.maskEmail("admin@ecommerce.com"));
    }

    @Test
    @DisplayName("Che email với local part có 1 ký tự")
    void maskEmail_SingleCharLocalPart_MasksCorrectly() {
        assertEquals("a***@domain.com", DataMaskingUtil.maskEmail("a@domain.com"));
    }

    @Test
    @DisplayName("Xử lý an toàn khi email null, rỗng hoặc không có domain")
    void maskEmail_EdgeCases_NullOrMalformed() {
        assertEquals("***", DataMaskingUtil.maskEmail(null));
        assertEquals("***", DataMaskingUtil.maskEmail("   "));
        assertEquals("u***", DataMaskingUtil.maskEmail("userwithoutdomain"));
    }
}
