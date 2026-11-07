package com.ecommerce.task;

import com.ecommerce.config.AppProperties;
import com.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnverifiedUserCleanupTaskTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private UnverifiedUserCleanupTask cleanupTask;

    @BeforeEach
    void setUp() {
        AppProperties.Auth auth = new AppProperties.Auth();
        auth.setUnverifiedRetentionHours(24);
        when(appProperties.getAuth()).thenReturn(auth);
    }

    @Test
    @DisplayName("Dọn dẹp thành công các user chưa xác thực quá hạn 24 giờ")
    void cleanupUnverifiedUsers_Success() {
        when(userRepository.deleteByActiveFalseAndEmailVerifiedFalseAndCreatedAtBefore(any(Instant.class)))
                .thenReturn(3L);

        cleanupTask.cleanupUnverifiedUsers();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(userRepository, times(1)).deleteByActiveFalseAndEmailVerifiedFalseAndCreatedAtBefore(captor.capture());

        Instant cutoff = captor.getValue();
        Instant expectedCutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        // Chênh lệch không quá vài giây
        assertTrue(Math.abs(ChronoUnit.SECONDS.between(expectedCutoff, cutoff)) < 5);
    }
}
