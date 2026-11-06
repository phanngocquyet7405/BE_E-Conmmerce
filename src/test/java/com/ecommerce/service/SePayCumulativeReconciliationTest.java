package com.ecommerce.service;

import com.ecommerce.config.SePayProperties;
import com.ecommerce.document.Order;
import com.ecommerce.document.OrderStatus;
import com.ecommerce.document.PaymentMethod;
import com.ecommerce.document.PaymentStatus;
import com.ecommerce.document.SePayTransaction;
import com.ecommerce.document.SePayTransactionStatus;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.SePayTransactionRepository;
import com.ecommerce.service.dto.WebhookResult;
import com.ecommerce.service.impl.SePayWebhookServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SePayCumulativeReconciliationTest {

    @Mock
    private SePayTransactionRepository sePayTransactionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    private SePayWebhookServiceImpl webhookService;
    private SePayProperties properties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new SePayProperties();
        properties.setBankId("MBBank");
        properties.setBankAccount("0123456789");
        properties.setAccountHolder("NGUYEN VAN A");
        properties.setApiKey("test-key");
        properties.setOrderCodePrefix("BETES");

        webhookService = new SePayWebhookServiceImpl(
                sePayTransactionRepository,
                orderRepository,
                mongoTemplate,
                objectMapper,
                properties
        );
    }

    @Test
    @DisplayName("Chuyển thiếu lần 1 -> Đơn giữ UNPAID, tích lũy paidAmount, tx MISMATCH; Chuyển bù lần 2 -> Đủ tiền sang PAID")
    void cumulativePayment_UnderpaidThenCompleted_TransitionsToPaid() {
        String orderCode = "BETES12345678";
        BigDecimal finalAmount = BigDecimal.valueOf(100000);

        Order initialOrder = Order.builder()
                .id("order-1")
                .orderCode(orderCode)
                .paymentMethod(PaymentMethod.SEPAY)
                .paymentStatus(PaymentStatus.UNPAID)
                .status(OrderStatus.PENDING)
                .finalAmount(finalAmount)
                .paidAmount(BigDecimal.ZERO)
                .build();

        when(orderRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(initialOrder));
        when(sePayTransactionRepository.save(any(SePayTransaction.class))).thenAnswer(i -> i.getArgument(0));

        // Lần 1: Chuyển 40,000 đ (Thiếu 60,000 đ)
        String rawBody1 = String.format("""
                {
                    "id": 1001,
                    "accountNumber": "0123456789",
                    "code": "%s",
                    "transferType": "in",
                    "transferAmount": 40000
                }
                """, orderCode);

        WebhookResult result1 = webhookService.processWebhook(rawBody1, "Apikey test-key", null, null);
        assertTrue(result1.success());

        // Kiểm tra sau lần 1: Đơn vẫn UNPAID, paidAmount = 40000, isUnderpaid = true
        assertEquals(BigDecimal.valueOf(40000), initialOrder.getPaidAmount());
        assertTrue(initialOrder.getIsUnderpaid());
        assertEquals(PaymentStatus.UNPAID, initialOrder.getPaymentStatus());

        // Lần 2: Chuyển bù 60,000 đ
        String rawBody2 = String.format("""
                {
                    "id": 1002,
                    "accountNumber": "0123456789",
                    "code": "%s",
                    "transferType": "in",
                    "transferAmount": 60000
                }
                """, orderCode);

        UpdateResult successResult = mock(UpdateResult.class);
        when(successResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Order.class)))
                .thenReturn(successResult);

        WebhookResult result2 = webhookService.processWebhook(rawBody2, "Apikey test-key", null, null);
        assertTrue(result2.success());

        // Xác nhận mongoTemplate updateFirst được gọi với PAID và status CONFIRMED
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), updateCaptor.capture(), eq(Order.class));
        Update capturedUpdate = updateCaptor.getValue();
        assertEquals(PaymentStatus.PAID, capturedUpdate.getUpdateObject().get("$set", org.bson.Document.class).get("paymentStatus"));
        assertEquals(OrderStatus.CONFIRMED, capturedUpdate.getUpdateObject().get("$set", org.bson.Document.class).get("status"));
        assertEquals(BigDecimal.valueOf(100000), capturedUpdate.getUpdateObject().get("$set", org.bson.Document.class).get("paidAmount"));
    }

    @Test
    @DisplayName("Khách chuyển thừa tiền: Đơn chuyển sang PAID, ghi nhận amountDiscrepancy dương để admin hoàn tiền")
    void overpaidPayment_RecordsDiscrepancyAndConfirmsPaid() {
        String orderCode = "BETES88889999";
        BigDecimal finalAmount = BigDecimal.valueOf(100000);

        Order order = Order.builder()
                .id("order-overpaid")
                .orderCode(orderCode)
                .paymentMethod(PaymentMethod.SEPAY)
                .paymentStatus(PaymentStatus.UNPAID)
                .status(OrderStatus.PENDING)
                .finalAmount(finalAmount)
                .paidAmount(BigDecimal.ZERO)
                .build();

        when(orderRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(order));
        when(sePayTransactionRepository.save(any(SePayTransaction.class))).thenAnswer(i -> i.getArgument(0));

        UpdateResult successResult = mock(UpdateResult.class);
        when(successResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Order.class)))
                .thenReturn(successResult);

        // Chuyển 150,000 đ (thừa 50,000 đ)
        String rawBody = String.format("""
                {
                    "id": 1003,
                    "accountNumber": "0123456789",
                    "code": "%s",
                    "transferType": "in",
                    "transferAmount": 150000
                }
                """, orderCode);

        WebhookResult result = webhookService.processWebhook(rawBody, "Apikey test-key", null, null);
        assertTrue(result.success());

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), updateCaptor.capture(), eq(Order.class));
        Update capturedUpdate = updateCaptor.getValue();
        org.bson.Document setDoc = capturedUpdate.getUpdateObject().get("$set", org.bson.Document.class);
        assertEquals(PaymentStatus.PAID, setDoc.get("paymentStatus"));
        assertEquals(Boolean.TRUE, setDoc.get("isOverpaid"));
        assertEquals(BigDecimal.valueOf(50000), setDoc.get("amountDiscrepancy"));
    }
}
