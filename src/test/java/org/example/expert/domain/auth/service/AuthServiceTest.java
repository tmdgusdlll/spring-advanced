package org.example.expert.domain.auth.service;

import io.jsonwebtoken.Claims;
import org.example.expert.config.JwtUtil;
import org.example.expert.config.PasswordEncoder;
import org.example.expert.domain.auth.dto.request.SigninRequest;
import org.example.expert.domain.auth.dto.request.SignupRequest;
import org.example.expert.domain.auth.dto.response.TokenPairResponse;
import org.example.expert.domain.auth.entity.RefreshToken;
import org.example.expert.domain.auth.exception.AuthException;
import org.example.expert.domain.auth.repository.RefreshTokenRepository;
import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.enums.UserRole;
import org.example.expert.domain.user.repository.UserRepository;
import org.example.expert.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("signup 테스트")
    class SignupTest {

        @Test
        @DisplayName("정상 회원가입 - AccessToken + RefreshToken 반환")
        void 회원가입에_성공한다() {
            SignupRequest request = new SignupRequest("a@a.com", "Password1", "USER");
            User savedUser = new User("a@a.com", "encoded", UserRole.USER);
            ReflectionTestUtils.setField(savedUser, "id", 1L);

            given(userRepository.existsByEmail("a@a.com")).willReturn(false);
            given(passwordEncoder.encode("Password1")).willReturn("encoded");
            given(userRepository.save(any(User.class))).willReturn(savedUser);
            given(jwtUtil.createAccessToken(1L, "a@a.com", UserRole.USER)).willReturn("Bearer accessToken");
            given(jwtUtil.createRefreshToken(1L)).willReturn("Bearer refreshToken");
            given(jwtUtil.substringToken("Bearer refreshToken")).willReturn("refreshToken");
            given(jwtUtil.getRefreshTokenExpirySeconds()).willReturn(1209600L);
            given(refreshTokenRepository.findByUser(savedUser)).willReturn(Optional.empty());

            TokenPairResponse response = authService.signup(request);

            assertThat(response.getAccessToken()).isEqualTo("Bearer accessToken");
            assertThat(response.getRefreshToken()).isEqualTo("Bearer refreshToken");
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("이미 존재하는 이메일로 가입 시 예외 발생")
        void 이미_존재하는_이메일이면_예외가_발생한다() {
            SignupRequest request = new SignupRequest("a@a.com", "Password1", "USER");
            given(userRepository.existsByEmail("a@a.com")).willReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("이미 존재하는 이메일입니다.");

            verify(passwordEncoder, never()).encode(anyString());
        }
    }

    @Nested
    @DisplayName("signin 테스트")
    class SigninTest {

        @Test
        @DisplayName("정상 로그인 - AccessToken + RefreshToken 반환")
        void 로그인에_성공한다() {
            SigninRequest request = new SigninRequest("a@a.com", "Password1");
            User user = new User("a@a.com", "encoded", UserRole.USER);
            ReflectionTestUtils.setField(user, "id", 1L);

            given(userRepository.findByEmail("a@a.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("Password1", "encoded")).willReturn(true);
            given(jwtUtil.createAccessToken(1L, "a@a.com", UserRole.USER)).willReturn("Bearer accessToken");
            given(jwtUtil.createRefreshToken(1L)).willReturn("Bearer refreshToken");
            given(jwtUtil.substringToken("Bearer refreshToken")).willReturn("refreshToken");
            given(jwtUtil.getRefreshTokenExpirySeconds()).willReturn(1209600L);
            given(refreshTokenRepository.findByUser(user)).willReturn(Optional.empty());

            TokenPairResponse response = authService.signin(request);

            assertThat(response.getAccessToken()).isEqualTo("Bearer accessToken");
            assertThat(response.getRefreshToken()).isEqualTo("Bearer refreshToken");
        }

        @Test
        @DisplayName("가입되지 않은 이메일로 로그인 시 예외 발생")
        void 가입되지_않은_이메일이면_예외가_발생한다() {
            SigninRequest request = new SigninRequest("none@a.com", "Password1");
            given(userRepository.findByEmail("none@a.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.signin(request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("가입되지 않은 유저입니다.");
        }

        @Test
        @DisplayName("비밀번호 불일치 시 예외 발생")
        void 비밀번호가_틀리면_예외가_발생한다() {
            SigninRequest request = new SigninRequest("a@a.com", "wrongPass");
            User user = new User("a@a.com", "encoded", UserRole.USER);

            given(userRepository.findByEmail("a@a.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrongPass", "encoded")).willReturn(false);

            assertThatThrownBy(() -> authService.signin(request))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("잘못된 비밀번호입니다.");
        }
    }

    @Nested
    @DisplayName("refresh 테스트")
    class RefreshTest {

        @Test
        @DisplayName("정상 토큰 재발급 - 새 AccessToken + RefreshToken 반환")
        void 토큰_재발급에_성공한다() {
            String bearerRefreshToken = "Bearer oldRefreshToken";
            User user = new User("a@a.com", "encoded", UserRole.USER);
            ReflectionTestUtils.setField(user, "id", 1L);

            Claims claims = mock(Claims.class);
            given(claims.getSubject()).willReturn("1");
            given(jwtUtil.substringToken(bearerRefreshToken)).willReturn("oldRefreshToken");
            given(jwtUtil.extractClaims("oldRefreshToken")).willReturn(claims);
            given(jwtUtil.isRefreshToken(claims)).willReturn(true);
            given(userService.getUserById(1L)).willReturn(user);

            RefreshToken savedRefreshToken = new RefreshToken(user, "oldRefreshToken", LocalDateTime.now().plusDays(14));
            given(refreshTokenRepository.findByUser(user)).willReturn(Optional.of(savedRefreshToken));

            given(jwtUtil.createAccessToken(1L, "a@a.com", UserRole.USER)).willReturn("Bearer newAccessToken");
            given(jwtUtil.createRefreshToken(1L)).willReturn("Bearer newRefreshToken");
            given(jwtUtil.substringToken("Bearer newRefreshToken")).willReturn("newRefreshToken");
            given(jwtUtil.getRefreshTokenExpirySeconds()).willReturn(1209600L);

            TokenPairResponse response = authService.refresh(bearerRefreshToken);

            assertThat(response.getAccessToken()).isEqualTo("Bearer newAccessToken");
            assertThat(response.getRefreshToken()).isEqualTo("Bearer newRefreshToken");
        }

        @Test
        @DisplayName("RefreshToken 타입이 아닌 토큰으로 재발급 요청 시 예외 발생")
        void 리프레시_토큰이_아니면_예외가_발생한다() {
            String bearerToken = "Bearer accessToken";
            Claims claims = mock(Claims.class);

            given(jwtUtil.substringToken(bearerToken)).willReturn("accessToken");
            given(jwtUtil.extractClaims("accessToken")).willReturn(claims);
            given(jwtUtil.isRefreshToken(claims)).willReturn(false);

            assertThatThrownBy(() -> authService.refresh(bearerToken))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("리프레시 토큰이 아닙니다.");
        }

        @Test
        @DisplayName("DB에 저장된 토큰과 불일치 시 예외 발생")
        void 저장된_토큰과_다르면_예외가_발생한다() {
            String bearerRefreshToken = "Bearer differentToken";
            User user = new User("a@a.com", "encoded", UserRole.USER);
            ReflectionTestUtils.setField(user, "id", 1L);

            Claims claims = mock(Claims.class);
            given(claims.getSubject()).willReturn("1");
            given(jwtUtil.substringToken(bearerRefreshToken)).willReturn("differentToken");
            given(jwtUtil.extractClaims("differentToken")).willReturn(claims);
            given(jwtUtil.isRefreshToken(claims)).willReturn(true);
            given(userService.getUserById(1L)).willReturn(user);

            RefreshToken savedRefreshToken = new RefreshToken(user, "savedToken", LocalDateTime.now().plusDays(14));
            given(refreshTokenRepository.findByUser(user)).willReturn(Optional.of(savedRefreshToken));

            assertThatThrownBy(() -> authService.refresh(bearerRefreshToken))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("리프레시 토큰이 일치하지 않습니다.");
        }

        @Test
        @DisplayName("DB에 저장된 토큰이 없을 시 예외 발생")
        void 저장된_토큰이_없으면_예외가_발생한다() {
            String bearerRefreshToken = "Bearer refreshToken";
            User user = new User("a@a.com", "encoded", UserRole.USER);
            ReflectionTestUtils.setField(user, "id", 1L);

            Claims claims = mock(Claims.class);
            given(claims.getSubject()).willReturn("1");
            given(jwtUtil.substringToken(bearerRefreshToken)).willReturn("refreshToken");
            given(jwtUtil.extractClaims("refreshToken")).willReturn(claims);
            given(jwtUtil.isRefreshToken(claims)).willReturn(true);
            given(userService.getUserById(1L)).willReturn(user);
            given(refreshTokenRepository.findByUser(user)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh(bearerRefreshToken))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("저장된 리프레시 토큰이 없습니다.");
        }
    }

    @Nested
    @DisplayName("logout 테스트")
    class LogoutTest {

        @Test
        @DisplayName("정상 로그아웃 - RefreshToken 삭제")
        void 로그아웃에_성공한다() {
            Long userId = 1L;
            User user = new User("a@a.com", "encoded", UserRole.USER);
            given(userService.getUserById(userId)).willReturn(user);

            authService.logout(userId);

            verify(refreshTokenRepository).deleteByUser(user);
        }

        @Test
        @DisplayName("존재하지 않는 유저 로그아웃 시 예외 발생")
        void 존재하지_않는_유저면_예외가_발생한다() {
            Long userId = 999L;
            given(userService.getUserById(userId))
                    .willThrow(new InvalidRequestException("User not found"));

            assertThatThrownBy(() -> authService.logout(userId))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("User not found");

            verify(refreshTokenRepository, never()).deleteByUser(any());
        }
    }
}
