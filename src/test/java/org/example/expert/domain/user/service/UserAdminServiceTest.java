package org.example.expert.domain.user.service;

import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.common.exception.NotFoundException;
import org.example.expert.domain.user.dto.request.UserRoleChangeRequest;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserService userService;
    @InjectMocks
    private UserAdminService userAdminService;

    @Test
    @DisplayName("changeUserRole: 역할이 정상적으로 변경된다.")
    void 역할이_정상적으로_변경된다() {
        // given
        Long userId = 1L;
        User user = new User("a@a.com", "abc1234", UserRole.USER);
        UserRoleChangeRequest request = new UserRoleChangeRequest("ADMIN");
        given(userService.getUserById(userId)).willReturn(user);

        // when
        userAdminService.changeUserRole(userId, request);

        // then
        assertThat(user.getUserRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("유저가 없으면 예외가 발생한다.")
    void 유저가_없으면_예외가_발생한다() {
        // given
        Long userId = 1L;
        UserRoleChangeRequest request = new UserRoleChangeRequest("ADMIN");

        given(userService.getUserById(userId))
                .willThrow(NotFoundException.class);

        // when & then
        assertThatThrownBy(() -> userAdminService.changeUserRole(userId, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("유효하지 않은 role이면 예외가 발생한다.")
    void 유효하지_않은_role이면_예외가_발생한다() {
        // given
        Long userId = 1L;
        User user = new User("a@a.com", "abc1234", UserRole.USER);
        UserRoleChangeRequest request = new UserRoleChangeRequest("aaa");

        given(userService.getUserById(userId)).willReturn(user);

        // when & then
        assertThatThrownBy(() -> userAdminService.changeUserRole(userId, request))
                .isInstanceOf(InvalidRequestException.class);
    }
}