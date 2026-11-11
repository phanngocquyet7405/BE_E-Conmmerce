package com.ecommerce.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlugUtilTest {

    @Test
    @DisplayName("Chuyển chuỗi tiếng Anh đơn giản thành slug")
    void testSimpleEnglishSlug() {
        assertEquals("sony", SlugUtil.toSlug("Sony"));
        assertEquals("apple-inc", SlugUtil.toSlug("Apple Inc."));
        assertEquals("samsung-galaxy-s24", SlugUtil.toSlug("Samsung Galaxy S24"));
    }

    @Test
    @DisplayName("Chuyển chuỗi tiếng Việt có dấu và đ/Đ thành slug chuẩn")
    void testVietnameseSlug() {
        assertEquals("dien-thoai-phu-kien", SlugUtil.toSlug("Điện Thoại & Phụ Kiện"));
        assertEquals("ao-thun-nam-cotton", SlugUtil.toSlug("Áo Thun Nam - Cotton"));
        assertEquals("dong-ho-thong-minh", SlugUtil.toSlug("Đồng Hồ Thông Minh"));
    }

    @Test
    @DisplayName("Xử lý chuỗi có ký tự đặc biệt, nhiều khoảng trắng hoặc dấu gạch ngang liên tiếp")
    void testSpecialCharacters() {
        assertEquals("test-special-slug", SlugUtil.toSlug("--- Test !@#$ Special  _  Slug ---"));
        assertEquals("", SlugUtil.toSlug(null));
        assertEquals("", SlugUtil.toSlug("   "));
        assertEquals("", SlugUtil.toSlug("!@#$%^&*()"));
    }
}
