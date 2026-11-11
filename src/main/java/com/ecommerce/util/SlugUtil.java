package com.ecommerce.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SlugUtil {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern MULTI_HYPHEN = Pattern.compile("-+");

    private SlugUtil() {}

    /**
     * Chuyển đổi chuỗi tiếng Việt hoặc tiếng Anh có dấu thành slug URL thân thiện.
     * Ví dụ: "Sony" -> "sony", "Điện Thoại & Phụ Kiện" -> "dien-thoai-phu-kien"
     */
    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // 1. Chuyển chữ 'đ' / 'Đ' thành 'd'
        String text = input.replace('đ', 'd').replace('Đ', 'd');

        // 2. Tách dấu Unicode và xóa các dấu diacritical
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(normalized).replaceAll("");

        // 3. Chuyển thành chữ thường
        String lower = withoutDiacritics.toLowerCase(Locale.ROOT);

        // 4. Thay thế mọi ký tự không phải chữ số latin bằng dấu gạch ngang '-'
        String withHyphens = NON_ALPHANUMERIC.matcher(lower).replaceAll("-");

        // 5. Rút gọn nhiều dấu gạch ngang liên tiếp thành 1 dấu
        String singleHyphen = MULTI_HYPHEN.matcher(withHyphens).replaceAll("-");

        // 6. Cắt bỏ dấu gạch ngang ở đầu và cuối chuỗi
        return singleHyphen.replaceAll("^-+|-+$", "");
    }
}
