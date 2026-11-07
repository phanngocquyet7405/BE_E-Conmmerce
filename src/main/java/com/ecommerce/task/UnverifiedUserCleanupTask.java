package com.ecommerce.task;

import com.ecommerce.config.AppProperties;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnverifiedUserCleanupTask {

    private final UserRepository userRepository;
    private final AppProperties appProperties;

    @Scheduled(cron = "${app.auth.unverified-cleanup-cron:0 0 * * * *}")
    public void cleanupUnverifiedUsers() {
        int retentionHours = (appProperties.getAuth() != null)
                ? appProperties.getAuth().getUnverifiedRetentionHours()
                : 24;

        Instant cutoff = Instant.now().minus(retentionHours, ChronoUnit.HOURS);
        try {
            long deletedCount = userRepository.deleteByActiveFalseAndEmailVerifiedFalseAndCreatedAtBefore(cutoff);
            if (deletedCount > 0) {
                log.info("Đã dọn dẹp thành công {} tài khoản chưa xác thực tạo trước {}", deletedCount, cutoff);
            }
        } catch (Exception ex) {
            log.error("Lỗi khi chạy tiến trình dọn dẹp tài khoản chưa kích hoạt: {}", ex.getMessage(), ex);
        }
    }
}
