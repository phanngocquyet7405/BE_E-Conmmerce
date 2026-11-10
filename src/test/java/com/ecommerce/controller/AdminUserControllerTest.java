package com.ecommerce.controller;

import com.ecommerce.common.constant.ApiRoutes;
import com.ecommerce.common.constant.RoleEnum;
import com.ecommerce.dto.response.AdminUserResponse;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.security.CustomUserDetails;
import com.ecommerce.service.UserService;
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

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminUserController adminUserController;

    private CustomUserDetails adminUserDetails;

    @BeforeEach
    void setUp() {
        adminUserDetails = new CustomUserDetails(
                "admin-1",
                "admin@ecommerce.com",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return adminUserDetails;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(adminUserController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Admin cập nhật trạng thái người dùng thiếu trường active bị từ chối 400")
    void updateUserStatus_MissingActive_ThrowsValidationError() throws Exception {
        mockMvc.perform(patch(ApiRoutes.API_V1 + "/admin/users/{userId}/status", "user-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("Trường 'active' là bắt buộc"));
    }

    @Test
    @DisplayName("Admin cập nhật trạng thái người dùng thành công")
    void updateUserStatus_Success() throws Exception {
        AdminUserResponse response = AdminUserResponse.builder()
                .id("user-123")
                .email("test@example.com")
                .active(false)
                .build();

        when(userService.updateUserStatus(anyString(), eq("user-123"), eq(false)))
                .thenReturn(response);

        mockMvc.perform(patch(ApiRoutes.API_V1 + "/admin/users/{userId}/status", "user-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái người dùng thành công"));
    }

    @Test
    @DisplayName("Admin cập nhật vai trò người dùng với danh sách rỗng bị từ chối 400")
    void updateUserRoles_EmptyRoles_ThrowsValidationError() throws Exception {
        mockMvc.perform(patch(ApiRoutes.API_V1 + "/admin/users/{userId}/roles", "user-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("Danh sách vai trò không được để trống"));
    }

    @Test
    @DisplayName("Admin cập nhật vai trò người dùng thành công")
    void updateUserRoles_Success() throws Exception {
        AdminUserResponse response = AdminUserResponse.builder()
                .id("user-123")
                .email("test@example.com")
                .roles(Set.of(RoleEnum.ROLE_ADMIN))
                .build();

        when(userService.updateUserRoles(anyString(), eq("user-123"), any()))
                .thenReturn(response);

        mockMvc.perform(patch(ApiRoutes.API_V1 + "/admin/users/{userId}/roles", "user-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"ROLE_ADMIN\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.message").value("Cập nhật vai trò người dùng thành công"));
    }

    @Test
    @DisplayName("Gọi phương thức HTTP không được hỗ trợ trả về mã 405 METHOD_NOT_ALLOWED")
    void methodNotAllowed_Returns405() throws Exception {
        mockMvc.perform(post(ApiRoutes.API_V1 + "/admin/users/{userId}/status", "user-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(4005))
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }
}
