package org.example.expert.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.condition.RequestConditionHolder;

import java.lang.reflect.Parameter;
import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ApiLoggingAspect {

    private final ObjectMapper objectMapper;

    @Around("@within(org.example.expert.aop.annotation.AdminLog)")
    public Object logApi(ProceedingJoinPoint joinPoint) throws Throwable {

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder
                .currentRequestAttributes()).getRequest();

        // 요청한 사용자의 ID
        Long userId = (Long) request.getAttribute("userId");

        // API 요청 시각
        LocalDateTime requestTime = LocalDateTime.now();

        // API 요청 URL
        String requestURI = request.getRequestURI();

        // 요청 본문(RequestBody)
        Object[] args = joinPoint.getArgs();
        String requestBodyJson = "{}";
        for (Object arg : args) {
            if (arg != null && arg.getClass().getSimpleName().endsWith("Request")) {
                requestBodyJson = objectMapper.writeValueAsString(arg);
                break;
            }
        }
        // 메서드 실행
        Object result = joinPoint.proceed();

        // 응답 본문(ResponseBody)
        String responseBodyJson = result != null ? objectMapper.writeValueAsString(result) : "{}";

        // 로그
        log.info("요청한 사용자 ID: {}, 요청 시각: {}, URL: {}, 요청 본문: {}, 응답 본문: {}",
                userId, requestTime, requestURI, requestBodyJson, responseBodyJson);

        return result;
    }

}
