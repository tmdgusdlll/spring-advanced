package org.example.expert.domain.auth.service;

import io.jsonwebtoken.Claims;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.example.expert.config.JwtUtil;
import org.example.expert.config.PasswordEncoder;
import org.example.expert.domain.auth.dto.request.SigninRequest;
import org.example.expert.domain.auth.dto.request.SignupRequest;
import org.example.expert.domain.auth.dto.response.SigninResponse;
import org.example.expert.domain.auth.dto.response.SignupResponse;
import org.example.expert.domain.auth.dto.response.TokenPairResponse;
import org.example.expert.domain.auth.entity.RefreshToken;
import org.example.expert.domain.auth.exception.AuthException;
import org.example.expert.domain.auth.repository.RefreshTokenRepository;
import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.enums.UserRole;
import org.example.expert.domain.user.repository.UserRepository;
import org.example.expert.domain.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static java.util.Collections.rotate;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public TokenPairResponse signup(SignupRequest signupRequest) {

        if (userRepository.existsByEmail(signupRequest.getEmail())) {
            throw new InvalidRequestException("이미 존재하는 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(signupRequest.getPassword());
        UserRole userRole = UserRole.of(signupRequest.getUserRole());

        User newUser = new User(
                signupRequest.getEmail(),
                encodedPassword,
                userRole
        );
        User savedUser = userRepository.save(newUser);

        String accessToken = jwtUtil.createAccessToken(savedUser.getId(), savedUser.getEmail(), userRole);
        String refreshToken = jwtUtil.createRefreshToken(savedUser.getId());

        upsertRefreshToken(savedUser, refreshToken);

        return new TokenPairResponse(accessToken, refreshToken);
    }

    @Transactional
    public TokenPairResponse signin(SigninRequest signinRequest) {
        User user = userRepository.findByEmail(signinRequest.getEmail()).orElseThrow(
                () -> new InvalidRequestException("가입되지 않은 유저입니다."));

        // 로그인 시 이메일과 비밀번호가 일치하지 않을 경우 401을 반환합니다.
        if (!passwordEncoder.matches(signinRequest.getPassword(), user.getPassword())) {
            throw new AuthException("잘못된 비밀번호입니다.");
        }

        String accessToken = jwtUtil.createAccessToken(user.getId(), user.getEmail(), user.getUserRole());
        String refreshToken = jwtUtil.createRefreshToken(user.getId());

        upsertRefreshToken(user, refreshToken);

        return new TokenPairResponse(accessToken, refreshToken);
    }

    public TokenPairResponse refresh(String bearerRefreshToken) {
        String refreshToken = jwtUtil.substringToken(bearerRefreshToken);
        Claims claims = jwtUtil.extractClaims(refreshToken);

        if (!jwtUtil.isRefreshToken(claims)) {
            throw new InvalidRequestException("리프레시 토큰이 아닙니다.");
        }

        Long userId = Long.parseLong(claims.getSubject());
        User user = userService.getUserById(userId);

        RefreshToken savedRefreshToken = refreshTokenRepository.findByUser(user).orElseThrow(
                () -> new InvalidRequestException("저장된 리프레시 토큰이 없습니다.")
        );

        if (!savedRefreshToken.getToken().equals(refreshToken)) {
            throw new InvalidRequestException("리프레시 토큰이 일치하지 않습니다.");
        }

        String newAccessToken = jwtUtil.createAccessToken(user.getId(), user.getEmail(), user.getUserRole());
        String newRefreshToken = jwtUtil.createRefreshToken(user.getId());

        upsertRefreshToken(user, newRefreshToken);

        return new TokenPairResponse(newAccessToken, newRefreshToken);
    }

    private void upsertRefreshToken(User user, String bearerRefreshToken) {
        String token = jwtUtil.substringToken(bearerRefreshToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(jwtUtil.getRefreshTokenExpirySeconds());

        refreshTokenRepository.findByUser(user)
                .ifPresentOrElse(
                        refreshToken -> refreshToken.rotate(token, expiresAt),
                        () -> refreshTokenRepository.save(new RefreshToken(user, token, expiresAt))
                );
    }

    @Transactional
    public void logout(Long userId) {
        User user = userService.getUserById(userId);
        refreshTokenRepository.deleteByUser(user);
    }
}
