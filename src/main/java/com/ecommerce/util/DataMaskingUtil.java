package com.ecommerce.util;

public final class DataMaskingUtil {

    private DataMaskingUtil() {
    }

    /**
     * Che dữ liệu nhạy cảm của email khi ghi log, giữ lại ký tự đầu tiên và phần domain.
     * Ví dụ: customer@example.com -> c***@example.com
     *
     * @param email Email gốc
     * @return Email đã che
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        String trimmed = email.trim();
        int atIdx = trimmed.indexOf('@');
        if (atIdx <= 0) {
            return trimmed.charAt(0) + "***";
        }
        String local = trimmed.substring(0, atIdx);
        String domain = trimmed.substring(atIdx);
        return local.charAt(0) + "***" + domain;
    }
}
