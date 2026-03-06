package org.example.expert.domain.auth.service;

import org.example.expert.config.JwtUtil;
import org.example.expert.config.PasswordEncoder;
import org.example.expert.domain.auth.dto.request.SigninRequest;
import org.example.expert.domain.auth.dto.request.SignupRequest;
import org.example.expert.domain.auth.dto.response.SigninResponse;
import org.example.expert.domain.auth.dto.response.SignupResponse;
import org.example.expert.domain.auth.exception.AuthException;
import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.enums.UserRole;
import org.example.expert.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("signup 실패 - 이미 존재하는 이메일")
    void signup_fail_whenEmailAlreadyExists() {
        SignupRequest request = new SignupRequest("test@example.com", "Password1", "USER");
        given(userRepository.existsByEmail(request.getEmail())).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("이미 존재하는 이메일입니다.");

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("signup 성공 - 토큰 반환")
    void signup_success() {
        SignupRequest request = new SignupRequest("test@example.com", "Password1", "USER");
        User savedUser = new User("test@example.com", "encoded-password", UserRole.USER);

        given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
        given(passwordEncoder.encode(request.getPassword())).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willReturn(savedUser);
        given(jwtUtil.createToken(any(), any(), any())).willReturn("Bearer access-token");

        SignupResponse response = authService.signup(request);

        assertThat(response.getBearerToken()).isEqualTo("Bearer access-token");
    }

    @Test
    @DisplayName("signin 실패 - 가입되지 않은 이메일")
    void signin_fail_whenUserNotFound() {
        SigninRequest request = new SigninRequest("none@example.com", "Password1");
        given(userRepository.findByEmail(request.getEmail())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.signin(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("가입되지 않은 유저입니다.");

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("signin 실패 - 비밀번호 불일치")
    void signin_fail_whenPasswordNotMatches() {
        SigninRequest request = new SigninRequest("test@example.com", "wrong-password");
        User user = new User("test@example.com", "encoded-password", UserRole.USER);

        given(userRepository.findByEmail(request.getEmail())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.getPassword(), user.getPassword())).willReturn(false);

        assertThatThrownBy(() -> authService.signin(request))
                .isInstanceOf(AuthException.class)
                .hasMessage("잘못된 비밀번호입니다.");
    }

    @Test
    @DisplayName("signin 성공 - 토큰 반환")
    void signin_success() {
        SigninRequest request = new SigninRequest("test@example.com", "Password1");
        User user = new User("test@example.com", "encoded-password", UserRole.USER);

        given(userRepository.findByEmail(request.getEmail())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.getPassword(), user.getPassword())).willReturn(true);
        given(jwtUtil.createToken(any(), any(), any())).willReturn("Bearer access-token");

        SigninResponse response = authService.signin(request);

        assertThat(response.getBearerToken()).isEqualTo("Bearer access-token");
    }
}
