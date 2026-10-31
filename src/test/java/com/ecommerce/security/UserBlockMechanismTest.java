package com.ecommerce.security;

import com.ecommerce.common.constant.RoleEnum;
import com.ecommerce.config.AppProperties;
import com.ecommerce.controller.AdminUserController;
import com.ecommerce.document.User;
import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.response.TokenResponse;
import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.impl.AuthServiceImpl;
import com.ecommerce.service.impl.RedisTokenServiceImpl;
import com.ecommerce.service.impl.UserServiceImpl;
import com.ecommerce.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserBlockMechanismTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtil jwtUtil;

    private AppProperties appProperties;
    private RedisTokenServiceImpl redisTokenService;
    private UserServiceImpl userService;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getJwt().setAccessExpirationMs(3600000L); // 1h
        appProperties.getJwt().setRefreshExpirationMs(604800000L); // 7d

        redisTokenService = new RedisTokenServiceImpl(redisTemplate, appProperties);
        userService = new UserServiceImpl(userRepository, redisTokenService);
        authService = new AuthServiceImpl(
                userRepository, passwordEncoder, authenticationManager,
                jwtUtil, redisTokenService, appProperties
        );
    }

    @Test
    @DisplayName("(a) Token cấp trước mốc khóa bị chặn: isUserBlocked trả về true khi iat < blockedAt")
    void token_IssuedBeforeBlock_IsBlocked() {
        String userId = "user-123";
        long blockedAtEpochSeconds = 1700000100L;
        long tokenIssuedAtSeconds = 1700000050L; // Cấp trước mốc khóa 50s

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("blocked:user:" + userId)).thenReturn(String.valueOf(blockedAtEpochSeconds));

        boolean isBlocked = redisTokenService.isUserBlocked(userId, tokenIssuedAtSeconds);

        assertTrue(isBlocked, "Token cấp trước mốc khóa phải bị chặn");
    }

    @Test
    @DisplayName("(b) Token cấp sau khi đổi role được qua: isUserBlocked trả về false khi iat >= blockedAt")
    void token_IssuedAfterRoleChange_IsAllowed() {
        String userId = "user-123";
        long roleChangeEpochSeconds = 1700000100L;
        long tokenIssuedAtSeconds = 1700000150L; // Cấp sau mốc đổi role 50s

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("blocked:user:" + userId)).thenReturn(String.valueOf(roleChangeEpochSeconds));

        boolean isBlocked = redisTokenService.isUserBlocked(userId, tokenIssuedAtSeconds);

        assertFalse(isBlocked, "Token cấp sau khi đổi role phải được cho qua ngay lập tức");
    }

    @Test
    @DisplayName("(c) Mở khóa xong đăng nhập lại được: đang khóa bị từ chối 403, mở khóa xong đăng nhập thành công")
    void unblock_User_AllowsLoginAgain() {
        String userId = "user-123";
        String email = "test@ecommerce.com";
        String rawPassword = "password123";

        User lockedUser = User.builder()
                .id(userId)
                .email(email)
                .password("encoded_pass")
                .active(false) // Đang bị khóa
                .roles(Set.of(RoleEnum.ROLE_CUSTOMER))
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(lockedUser));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword(rawPassword);

        // 1. Khi đang bị khóa: Đăng nhập phải ném ACCESS_DENIED
        AppException exception = assertThrows(AppException.class, () -> authService.login(loginRequest));
        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());

        // 2. Admin mở khóa: active = true
        when(userRepository.findById(userId)).thenReturn(Optional.of(lockedUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User unlockedUser = userService.updateUserStatus(userId, true);
        assertTrue(unlockedUser.isActive());

        // Xác nhận đã xóa key blocked:user trên Redis
        verify(redisTemplate, times(1)).delete("blocked:user:" + userId);

        // 3. User đăng nhập lại sau khi mở khóa
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(unlockedUser));
        CustomUserDetails userDetails = new CustomUserDetails(
                userId, email, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(jwtUtil.generateAccessToken(any())).thenReturn("new_access_token");
        when(jwtUtil.generateRefreshToken(any(), anyString())).thenReturn("new_refresh_token");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        TokenResponse tokenResponse = authService.login(loginRequest);

        assertNotNull(tokenResponse);
        assertEquals("new_access_token", tokenResponse.getAccessToken());
        assertEquals("new_refresh_token", tokenResponse.getRefreshToken());
    }

    @Test
    @DisplayName("(d) Redis lỗi khi blockUser: phải ném AppException với mã 5xx và message rõ ràng")
    void blockUser_WhenRedisFails_ThrowsAppException500() {
        String userId = "user-123";
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));

        AppException exception = assertThrows(AppException.class, () -> redisTokenService.blockUser(userId));

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
        assertEquals("Không thể áp dụng khóa/đổi quyền ngay, vui lòng thử lại", exception.getMessage());
    }

    @Test
    @DisplayName("(e) Khóa user khi Redis lỗi: ném lỗi, không cập nhật DB (không rơi vào trạng thái nửa vời)")
    void updateUserStatus_WhenRedisFails_ThrowsErrorAndDoesNotModifyDB() {
        String userId = "user-123";
        User existingUser = User.builder()
                .id(userId)
                .email("test@ecommerce.com")
                .active(true)
                .roles(Set.of(RoleEnum.ROLE_CUSTOMER))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis timeout"));

        AppException exception = assertThrows(AppException.class, () -> userService.updateUserStatus(userId, false));

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
        assertEquals("Không thể áp dụng khóa/đổi quyền ngay, vui lòng thử lại", exception.getMessage());
        // Xác nhận DB KHÔNG bị save trạng thái active=false
        verify(userRepository, never()).save(any(User.class));
        assertTrue(existingUser.isActive(), "Trạng thái người dùng trong bộ nhớ/DB không bị đổi sang false");
    }

    @Test
    @DisplayName("(f) Đổi quyền khi Redis lỗi: ném lỗi, không cập nhật DB")
    void updateUserRoles_WhenRedisFails_ThrowsErrorAndDoesNotModifyDB() {
        String userId = "user-123";
        User existingUser = User.builder()
                .id(userId)
                .email("test@ecommerce.com")
                .active(true)
                .roles(Set.of(RoleEnum.ROLE_CUSTOMER))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis timeout"));

        AppException exception = assertThrows(AppException.class, () ->
                userService.updateUserRoles(userId, Set.of(RoleEnum.ROLE_ADMIN)));

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
        assertEquals(Set.of(RoleEnum.ROLE_CUSTOMER), existingUser.getRoles(), "Vai trò người dùng không bị thay đổi");
    }

    @Test
    @DisplayName("(g) API Admin: Redis lỗi khi khóa user thì API trả lỗi 500, không trả 200")
    void adminApi_WhenLockUserAndRedisFails_Returns500Not200() throws Exception {
        String userId = "user-123";
        User existingUser = User.builder()
                .id(userId)
                .email("test@ecommerce.com")
                .active(true)
                .roles(Set.of(RoleEnum.ROLE_CUSTOMER))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection down"));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(patch("/api/v1/admin/users/" + userId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isInternalServerError())
                .andExpect(status().is(500))
                .andExpect(jsonPath("$.code").value(9999))
                .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Không thể áp dụng khóa/đổi quyền ngay, vui lòng thử lại"));

        verify(userRepository, never()).save(any(User.class));
    }
}
