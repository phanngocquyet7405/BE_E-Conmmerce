package com.ecommerce.service.impl;

import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.service.PasswordPolicyService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordPolicyServiceImpl implements PasswordPolicyService {

    private final PasswordEncoder passwordEncoder;

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 64;
    private static final int MAX_UTF8_BYTES = 72;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(0|\\+84)(3|5|7|8|9)\\d{8}$");

    private final Set<String> commonPasswords = new HashSet<>();

    @PostConstruct
    public void initCommonPasswords() {
        try {
            ClassPathResource resource = new ClassPathResource("common-passwords.txt");
            if (resource.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            commonPasswords.add(line.toLowerCase(Locale.ROOT));
                        }
                    }
                }
                log.info("Đã tải {} mật khẩu phổ biến từ common-passwords.txt để bảo vệ tài khoản.", commonPasswords.size());
            } else {
                log.warn("Không tìm thấy common-passwords.txt trong resources.");
            }
        } catch (Exception ex) {
            log.error("Lỗi khi đọc common-passwords.txt: {}", ex.getMessage());
        }
    }

    @Override
    public void validatePassword(String password, String email) {
        if (!StringUtils.hasText(password)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Mật khẩu không được để trống");
        }

        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Mật khẩu phải có độ dài từ " + MIN_LENGTH + " đến " + MAX_LENGTH + " ký tự");
        }

        byte[] utf8Bytes = password.getBytes(StandardCharsets.UTF_8);
        if (utf8Bytes.length > MAX_UTF8_BYTES) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Mật khẩu không được vượt quá " + MAX_UTF8_BYTES + " bytes theo chuẩn UTF-8");
        }

        if (StringUtils.hasText(email)) {
            String normEmail = normalizeEmail(email);
            if (password.equalsIgnoreCase(normEmail)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Mật khẩu không được trùng với địa chỉ email");
            }
        }

        if (commonPasswords.contains(password.toLowerCase(Locale.ROOT))) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Mật khẩu quá đơn giản và dễ đoán. Vui lòng chọn mật khẩu an toàn hơn.");
        }
    }

    @Override
    public void validateNewPasswordDiffersFromOld(String newPassword, String oldEncodedPassword) {
        if (StringUtils.hasText(oldEncodedPassword) && passwordEncoder.matches(newPassword, oldEncodedPassword)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Mật khẩu mới phải khác với mật khẩu hiện tại");
        }
    }

    @Override
    public void validateFullName(String fullName) {
        if (!StringUtils.hasText(fullName) || fullName.trim().length() < 2 || fullName.trim().length() > 100) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Họ và tên phải từ 2 đến 100 ký tự");
        }
    }

    @Override
    public void validatePhone(String phone) {
        if (StringUtils.hasText(phone)) {
            String trimmed = phone.trim();
            if (!PHONE_PATTERN.matcher(trimmed).matches()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Số điện thoại không đúng định dạng di động Việt Nam (ví dụ 0987654321 hoặc +84987654321)");
            }
        }
    }

    @Override
    public String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase(Locale.ROOT) : "";
    }
}
