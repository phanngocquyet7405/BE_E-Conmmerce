package com.ecommerce.security;

import com.ecommerce.exception.AppException;
import com.ecommerce.exception.ErrorCode;
import com.ecommerce.service.RedisTokenService;
import com.ecommerce.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    public static final String ATTR_AUTH_ERROR_CODE = "AUTH_ERROR_CODE";

    private final JwtUtil jwtUtil;
    private final RedisTokenService redisTokenService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(BEARER_PREFIX.length());
        try {
            boolean isBlacklisted = false;
            try {
                isBlacklisted = redisTokenService.isAccessTokenBlacklisted(jwt);
            } catch (Exception redisEx) {
                // An toàn khi mất kết nối Redis: tiếp tục xác thực chữ ký JWT (fail-open)
            }

            if (isBlacklisted) {
                throw new AppException(ErrorCode.TOKEN_REVOKED);
            }

            Claims claims = jwtUtil.verifyAccessToken(jwt);
            String email = claims.getSubject();
            String userId = claims.get(JwtUtil.CLAIM_USER_ID, String.class);

            // Lấy iat (epoch giây) từ token
            Date issuedAt = claims.getIssuedAt();
            long iatSeconds = (issuedAt != null) ? (issuedAt.getTime() / 1000) : 0;

            // Kiểm tra user có bị khóa hoặc đổi quyền trước mốc iat này không
            boolean isUserBlocked = false;
            try {
                isUserBlocked = redisTokenService.isUserBlocked(userId, iatSeconds);
            } catch (Exception redisEx) {
                // Lỗi Redis thì cho qua (giữ fail-open)
                log.warn("Lỗi kiểm tra Redis blocked user, bỏ qua: {}", redisEx.getMessage());
            }

            if (isUserBlocked) {
                throw new AppException(ErrorCode.TOKEN_REVOKED, "Tài khoản hoặc quyền của bạn đã bị cập nhật, vui lòng đăng nhập lại");
            }

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get(JwtUtil.CLAIM_ROLES, List.class);
                List<SimpleGrantedAuthority> authorities = (roles != null)
                        ? roles.stream().map(SimpleGrantedAuthority::new).toList()
                        : List.of();

                CustomUserDetails userDetails = new CustomUserDetails(userId, email, authorities);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (AppException ex) {
            request.setAttribute(ATTR_AUTH_ERROR_CODE, ex.getErrorCode());
            // Trả JSON 401 trực tiếp trong filter khi token bị từ chối, không để rơi ra 500
            writeUnauthorizedResponse(response, request, ex);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, HttpServletRequest request, AppException ex) throws IOException {
        ErrorCode ec = ex.getErrorCode();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String json = String.format(
                "{\"success\":false,\"code\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                ec.getCode(),
                ec.name(),
                ex.getMessage() != null ? ex.getMessage() : ec.getMessage(),
                request.getRequestURI()
        );
        response.getWriter().write(json);
    }
}
