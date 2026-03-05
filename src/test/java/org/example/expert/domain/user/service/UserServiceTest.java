package org.example.expert.domain.user.service;

import org.example.expert.config.PasswordEncoder;
import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.common.exception.NotFoundException;
import org.example.expert.domain.user.dto.request.UserChangePasswordRequest;
import org.example.expert.domain.user.dto.response.UserResponse;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.enums.UserRole;
import org.example.expert.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    UserService userService;

    @Test
    @DisplayName("getUserById: 존재하면 유저 반환")
    void 유저를_정상적으로_조회한다() {
        // given
        Long userId = 1L;
        User user = new User("a@a.com", "abc1234", UserRole.USER);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        User result = userService.getUserById(userId);

        // then
        assertThat(result).isSameAs(user);
        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("getUserById: 없으면 NotFoundException")
    void 유저가_없으면_예외가_발생한다() {
        // given
        Long userId = 1L;
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUserById(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");
    }


    @Test
    @DisplayName("getUserById(userId, message): 존재하면 반환")
    void 유저를_정상적으로_조회한다2() {
        // given
        Long userId = 1L;
        String message = "임의 에러메시지";
        User user = new User("a@a.com", "abc1234", UserRole.USER);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        User result = userService.getUserById(userId, message);

        // then
        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("getUserById(userId, message): 없으면 InvalidRequestException(메시지 확인)")
    void 유저가_없으면_예외가_발생한다2() {
        // given
        Long userId = 404L;
        String message = "등록하려고 하는 담당자 유저가 존재하지 않습니다.";
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
         assertThatThrownBy(() -> userService.getUserById(userId, message))
                 .isInstanceOf(InvalidRequestException.class)
                 .hasMessage(message);
    }


    @Test
    @DisplayName("getUser: 존재하면 UserResponse 반환")
    void 유저가_존재하면_응답을_반환한다() {
        // given
        Long userId = 1L;
        User user = new User("a@a.com", "abc1234", UserRole.USER);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        UserResponse response = userService.getUser(userId);

        // then
        assertThat(response.getEmail()).isEqualTo("a@a.com");
    }

    @Test
    @DisplayName("changePassword: 기존 비밀번호 불일치면 예외")
    void 기존_비밀번호가_틀리면_예외가_발생한다() {
        // given
        Long userId = 1L;
        User user = new User("a@a.com", "abc1234", UserRole.USER);
        UserChangePasswordRequest request = new UserChangePasswordRequest("wrongOld", "NewPass123");

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongOld", "abc1234")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.changePassword(userId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("잘못된 비밀번호입니다.");

        // 에러가 나면 암호화가 안 되니 이 테스트에선 패스인코더가 동작하지 않았음을 확인
        verify(passwordEncoder, never()).encode(anyString());

    }

    @Test
    @DisplayName("changePassword: 새 비밀번호가 기존과 동일하면 예외")
    void 새비밀번호가_기존과_동일하면_예외가_발생한다() {
        // given
        Long userId = 1L;
        User user = new User("a@a.com", "abc1234", UserRole.USER);
        UserChangePasswordRequest request = new UserChangePasswordRequest("oldRaw", "sameRaw");

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("oldRaw", "abc1234")).willReturn(true);
        given(passwordEncoder.matches("sameRaw", "abc1234")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.changePassword(userId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("새 비밀번호는 기존 비밀번호와 같을 수 없습니다.");

        // 에러가 나면 암호화가 안 되니 이 테스트에선 패스인코더가 동작하지 않았음을 확인
        verify(passwordEncoder, never()).encode(anyString());

    }

    @Test
    @DisplayName("changePassword: 정상 변경")
    void 비밀번호가_정상적으로_변경된다() {
        // given
        Long userId = 1L;
        User user = new User("a@a.com", "abc1234", UserRole.USER);
        UserChangePasswordRequest request = new UserChangePasswordRequest("oldRaw", "NewPass123");
        String encodedPassword = "encodedNew";

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("oldRaw", "abc1234")).willReturn(true);
        given(passwordEncoder.matches("NewPass123", "abc1234")).willReturn(false);
        given(passwordEncoder.encode("NewPass123")).willReturn(encodedPassword);

        // when
        userService.changePassword(userId, request);

        // then
        assertThat(user.getPassword()).isEqualTo(encodedPassword);

        // 정상적으로 변경이 되면 패스인코더가 암호화를 하니 "NewPass123"로 암호화해줬을 것이다.
        verify(passwordEncoder).encode("NewPass123");
    }
}
