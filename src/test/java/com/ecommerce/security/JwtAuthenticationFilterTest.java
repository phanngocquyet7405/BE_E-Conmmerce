package com.ecommerce.security;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.service.RedisTokenService;
import com.ecommerce.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RedisTokenService redisTokenService;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("shouldNotFilter trả về true cho endpoint /login để không bị chặn bởi token cũ")
    void shouldNotFilter_LoginEndpoint_ReturnsTrue() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", ApiRoutes.Auth.BASE + ApiRoutes.Auth.LOGIN);
        assertTrue(jwtAuthenticationFilter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("shouldNotFilter trả về true cho endpoint /register, /verify-otp, /refresh-token, /logout")
    void shouldNotFilter_PublicAuthEndpoints_ReturnsTrue() {
        List<String> paths = List.of(
                ApiRoutes.Auth.BASE + ApiRoutes.Auth.REGISTER,
                ApiRoutes.Auth.BASE + ApiRoutes.Auth.REFRESH,
                ApiRoutes.Auth.BASE + ApiRoutes.Auth.VERIFY_OTP,
                ApiRoutes.Auth.BASE + ApiRoutes.Auth.RESEND_VERIFICATION_OTP,
                ApiRoutes.Auth.BASE + ApiRoutes.Auth.FORGOT_PASSWORD,
                ApiRoutes.Auth.BASE + ApiRoutes.Auth.VERIFY_RESET_OTP,
                ApiRoutes.Auth.BASE + ApiRoutes.Auth.RESET_PASSWORD,
                ApiRoutes.Auth.BASE + ApiRoutes.Auth.LOGOUT
        );

        for (String path : paths) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            assertTrue(jwtAuthenticationFilter.shouldNotFilter(request), "Đường dẫn " + path + " phải được bỏ qua");
        }
    }

    @Test
    @DisplayName("shouldNotFilter trả về true cho các tài nguyên công khai như /health và /public/**")
    void shouldNotFilter_PublicResources_ReturnsTrue() {
        MockHttpServletRequest healthReq = new MockHttpServletRequest("GET", "/health");
        assertTrue(jwtAuthenticationFilter.shouldNotFilter(healthReq));

        MockHttpServletRequest productsReq = new MockHttpServletRequest("GET", ApiRoutes.Products.PUBLIC_BASE);
        assertTrue(jwtAuthenticationFilter.shouldNotFilter(productsReq));
    }

    @Test
    @DisplayName("shouldNotFilter trả về false cho endpoint cần xác thực như /profile/me và /logout-all")
    void shouldNotFilter_ProtectedEndpoints_ReturnsFalse() {
        MockHttpServletRequest profileReq = new MockHttpServletRequest("GET", ApiRoutes.Profile.BASE + ApiRoutes.Profile.ME);
        assertFalse(jwtAuthenticationFilter.shouldNotFilter(profileReq));

        MockHttpServletRequest logoutAllReq = new MockHttpServletRequest("POST", ApiRoutes.Auth.BASE + ApiRoutes.Auth.LOGOUT_ALL);
        assertFalse(jwtAuthenticationFilter.shouldNotFilter(logoutAllReq));
    }

    @Test
    @DisplayName("shouldNotFilter trả về true cho OPTIONS preflight request")
    void shouldNotFilter_OptionsRequest_ReturnsTrue() {
        MockHttpServletRequest optionsReq = new MockHttpServletRequest("OPTIONS", ApiRoutes.Profile.BASE + ApiRoutes.Profile.ME);
        assertTrue(jwtAuthenticationFilter.shouldNotFilter(optionsReq));
    }

    @Test
    @DisplayName("doFilterInternal: Token hết hạn trên endpoint bảo vệ thì trả HTTP 401 TOKEN_EXPIRED")
    void doFilterInternal_ExpiredTokenOnProtectedEndpoint_Returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ApiRoutes.Profile.BASE + ApiRoutes.Profile.ME);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer expired_jwt_token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(redisTokenService.isAccessTokenBlacklisted(anyString())).thenReturn(false);
        when(jwtUtil.verifyAccessToken("expired_jwt_token")).thenThrow(new AppException(ErrorCode.TOKEN_EXPIRED));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("TOKEN_EXPIRED"));
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilterInternal: Token hợp lệ trên endpoint bảo vệ thì lưu Authentication vào SecurityContext")
    void doFilterInternal_ValidToken_SetsAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ApiRoutes.Profile.BASE + ApiRoutes.Profile.ME);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid_jwt_token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("user@ecommerce.com");
        when(claims.get(JwtUtil.CLAIM_USER_ID, String.class)).thenReturn("user-123");
        when(claims.getIssuedAt()).thenReturn(new Date(System.currentTimeMillis() - 1000));
        when(claims.get(JwtUtil.CLAIM_ROLES, List.class)).thenReturn(List.of("ROLE_CUSTOMER"));

        when(redisTokenService.isAccessTokenBlacklisted("valid_jwt_token")).thenReturn(false);
        when(jwtUtil.verifyAccessToken("valid_jwt_token")).thenReturn(claims);
        when(redisTokenService.isUserBlocked(eq("user-123"), anyLong())).thenReturn(false);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("user@ecommerce.com", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
