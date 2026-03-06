package org.example.expert.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.expert.domain.auth.exception.AuthException;
import org.example.expert.domain.user.enums.UserRole;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

@Slf4j
@Component
public class AdminAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = (Long) request.getAttribute("userId");
        String userRoleValue = (String) request.getAttribute("userRole");

        if (userId == null || userRoleValue == null) {
            throw new AuthException("인증되지 않은 사용자입니다.");
        }

        UserRole userRole = UserRole.of(userRoleValue);
        if (!UserRole.ADMIN.equals(userRole)) {
            throw new AuthException("관리자 권한이 필요합니다.");
        }

        LocalDateTime requestTime = LocalDateTime.now();
        String requestUrl = request.getRequestURI();
        log.info("관리자 인증 성공: userId={}, requestTime={}, url={}", userId, requestTime, requestUrl);

        return true;
    }
}
