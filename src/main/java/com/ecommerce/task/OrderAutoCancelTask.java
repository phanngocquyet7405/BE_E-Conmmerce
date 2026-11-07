package com.ecommerce.task;

import com.ecommerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAutoCancelTask {

    private final OrderService orderService;

    @Value("${app.order.unpaid-timeout-minutes:15}")
    private int unpaidTimeoutMinutes;

    /**
     * Quét và tự động hủy các đơn hàng chưa thanh toán quá 15 phút,
     * chạy định kỳ mỗi 5 phút (mặc định cron = "0 * / 5 * * * *").
     */
    @Scheduled(cron = "${app.order.auto-cancel-cron:0 */5 * * * *}")
    public void autoCancelExpiredOrders() {
        log.info("Bắt đầu tiến trình kiểm tra và tự động hủy đơn hàng UNPAID quá hạn ({} phút)...", unpaidTimeoutMinutes);
        try {
            int cancelledCount = orderService.cancelExpiredOrders(unpaidTimeoutMinutes);
            if (cancelledCount > 0) {
                log.info("Hoàn tất tự động hủy {} đơn hàng hết hạn thanh toán.", cancelledCount);
            }
        } catch (Exception e) {
            log.error("Lỗi khi chạy tác vụ tự động hủy đơn hàng hết hạn: {}", e.getMessage(), e);
        }
    }
}
