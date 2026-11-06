package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.document.OrderStatus;
import com.ecommerce.document.PaymentMethod;
import com.ecommerce.document.PaymentStatus;
import com.ecommerce.dto.response.PaymentQrResponse;
import com.ecommerce.dto.response.PaymentStatusResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.security.CustomUserDetails;
import com.ecommerce.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private CustomUserDetails testUserDetails;

    @BeforeEach
    void setUp() {
        testUserDetails = new CustomUserDetails(
                "user-100",
                "customer@ecommerce.com",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );

        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return testUserDetails;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("1. GET /api/v1/payment/sepay/qr-info/{orderId} thành công với chủ đơn")
    void getPaymentQrInfo_Success() throws Exception {
        PaymentQrResponse qrResponse = PaymentQrResponse.builder()
                .orderId("order-100")
                .orderCode("BETES11223344")
                .amount(BigDecimal.valueOf(150000))
                .bankName("MBBank")
                .accountNumber("0862483762")
                .paymentMethod(PaymentMethod.SEPAY)
                .paymentStatus(PaymentStatus.UNPAID)
                .qrImageUrl("https://qr.sepay.vn/img?acc=MBBank-0862483762&template=compact&amount=150000&des=BETES11223344")
                .paymentInstruction("Vui lòng quét mã QR")
                .build();

        when(paymentService.getPaymentQrInfo("user-100", false, "order-100")).thenReturn(qrResponse);

        mockMvc.perform(get(ApiRoutes.Payment.BASE + ApiRoutes.Payment.SEPAY_QR_INFO, "order-100")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.orderCode").value("BETES11223344"))
                .andExpect(jsonPath("$.data.amount").value(150000))
                .andExpect(jsonPath("$.data.bankName").value("MBBank"))
                .andExpect(jsonPath("$.data.qrImageUrl").isNotEmpty());

        verify(paymentService, times(1)).getPaymentQrInfo("user-100", false, "order-100");
    }

    @Test
    @DisplayName("2. GET /api/v1/payment/sepay/qr-info/{orderId} IDOR từ chối -> 404")
    void getPaymentQrInfo_IDOR_NotFound() throws Exception {
        when(paymentService.getPaymentQrInfo("user-100", false, "order-other"))
                .thenThrow(new AppException(ErrorCode.ORDER_NOT_FOUND));

        mockMvc.perform(get(ApiRoutes.Payment.BASE + ApiRoutes.Payment.SEPAY_QR_INFO, "order-other")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ORDER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("3. GET /api/v1/payment/status/{orderId} kiểm tra trạng thái thanh toán thành công")
    void checkPaymentStatus_Success() throws Exception {
        PaymentStatusResponse statusResponse = PaymentStatusResponse.builder()
                .orderId("order-100")
                .orderCode("BETES11223344")
                .paymentStatus(PaymentStatus.PAID)
                .orderStatus(OrderStatus.CONFIRMED)
                .paidAt(Instant.now())
                .sepayTransactionId("998877")
                .build();

        when(paymentService.checkPaymentStatus("user-100", false, "order-100")).thenReturn(statusResponse);

        mockMvc.perform(get(ApiRoutes.Payment.BASE + ApiRoutes.Payment.STATUS, "order-100")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.orderCode").value("BETES11223344"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        verify(paymentService, times(1)).checkPaymentStatus("user-100", false, "order-100");
    }
}
