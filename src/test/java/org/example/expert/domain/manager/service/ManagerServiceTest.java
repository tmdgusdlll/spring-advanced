package org.example.expert.domain.manager.service;

import org.example.expert.domain.common.dto.AuthUser;
import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.common.exception.NotFoundException;
import org.example.expert.domain.manager.dto.request.ManagerSaveRequest;
import org.example.expert.domain.manager.dto.response.ManagerResponse;
import org.example.expert.domain.manager.dto.response.ManagerSaveResponse;
import org.example.expert.domain.manager.entity.Manager;
import org.example.expert.domain.manager.repository.ManagerRepository;
import org.example.expert.domain.todo.entity.Todo;
import org.example.expert.domain.todo.repository.TodoRepository;
import org.example.expert.domain.todo.service.TodoService;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.enums.UserRole;
import org.example.expert.domain.user.repository.UserRepository;
import org.example.expert.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerServiceTest {

    @Mock
    private ManagerRepository managerRepository;
    @Mock
    private UserService userService;
    @Mock
    private TodoService todoService;
    @InjectMocks
    private ManagerService managerService;

    @Test
    @DisplayName("[Case 1] ManagerServiceTest 예외처리 테스트")
    void manager_목록_조회_시_Todo가_없다면_NotFound_에러를_던진다() {
        // given
        long todoId = 1L;
        User user = new User("a@a.com", "password", UserRole.USER);
        Todo todo = new Todo("Title", "Contents", "Sunny", user);
        ReflectionTestUtils.setField(todo, "id", todoId);

        given(todoService.getTodoById(todoId)).willThrow(new NotFoundException("Todo not found"));

        // when & then
        assertThatThrownBy(() -> managerService.getManagers(todoId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Todo not found");
    }

    @Test
    @DisplayName("[Case 3] ManagerServiceTest 예외처리 테스트(2)")
    void todo의_user가_null인_경우_예외가_발생한다() {
        // given
        AuthUser authUser = new AuthUser(1L, "a@a.com", UserRole.USER);
        long todoId = 1L;
        long managerUserId = 2L;

        Todo todo = new Todo();

        ManagerSaveRequest managerSaveRequest = new ManagerSaveRequest(managerUserId);
        given(todoService.getTodoById(todoId)).willReturn(todo);

        // when & then
        assertThatThrownBy(() -> managerService.saveManager(authUser, todoId, managerSaveRequest))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("일정을 생성한 유저만 담당자를 지정할 수 있습니다.");
    }

    @Test
    @DisplayName("validateTodoOwner: userId가 다른 경우 예외가 발생한다.")
    void todo의_userId가_다른_경우_예외가_발생한다() {
        // given
        AuthUser authUser = new AuthUser(1L, "a@a.com", UserRole.USER);
        User anotherUser = new User("b@b.com", "password", UserRole.USER);
        ReflectionTestUtils.setField(anotherUser, "id", 2L); // 👈 id가 달라!

        long todoId = 1L;
        Todo todo = new Todo("Title", "Contents", "Sunny", anotherUser);

        ManagerSaveRequest managerSaveRequest = new ManagerSaveRequest(3L);
        given(todoService.getTodoById(todoId)).willReturn(todo);

        // when & then
        assertThatThrownBy(() -> managerService.saveManager(authUser, todoId, managerSaveRequest))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("일정을 생성한 유저만 담당자를 지정할 수 있습니다.");
    }

    @Test
    @DisplayName("본인을 로 등록하면 예외가 발생한다.")
    void 본인을_담당자로_등록하면_예외가_발생한다() {
        // given
        AuthUser authUser = new AuthUser(1L, "a@a.com", UserRole.USER);
        User user = User.fromAuthUser(authUser);
        long todoId = 1L;
        Todo todo = new Todo("Title", "Contents", "Sunny", user);

        ManagerSaveRequest request = new ManagerSaveRequest(1L);

        given(todoService.getTodoById(todoId)).willReturn(todo);
        given(userService.getUserById(1L, "등록하려고 하는 담당자 유저가 존재하지 않습니다."))
                .willReturn(user);

        // when & then
        assertThatThrownBy(() -> managerService.saveManager(authUser, todoId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("일정 작성자는 본인을 담당자로 등록할 수 없습니다.");
    }

    @Test
    public void 담당자_목록_조회에_성공한다() {
        // given
        long todoId = 1L;
        User user = new User("user1@example.com", "password", UserRole.USER);
        Todo todo = new Todo("Title", "Contents", "Sunny", user);
        ReflectionTestUtils.setField(todo, "id", todoId);

        Manager mockManager = new Manager(todo.getUser(), todo);
        List<Manager> managerList = List.of(mockManager);

        given(todoService.getTodoById(todoId)).willReturn(todo);
        given(managerRepository.findByTodoIdWithUser(todoId)).willReturn(managerList);

        // when
        List<ManagerResponse> managerResponses = managerService.getManagers(todoId);

        // then
        assertEquals(1, managerResponses.size());
        assertEquals(mockManager.getId(), managerResponses.get(0).getId());
        assertEquals(mockManager.getUser().getEmail(), managerResponses.get(0).getUser().getEmail());
    }

    @Test
    @DisplayName("saveManager: 담당자가 정상적으로 등록된다.")
    void 담당자가_정상적으로_등록된다() {
        // given
        AuthUser authUser = new AuthUser(1L, "a@a.com", UserRole.USER);
        User user = User.fromAuthUser(authUser);  // 일정을 만든 유저

        long todoId = 1L;
        Todo todo = new Todo("Test Title", "Test Contents", "Sunny", user);

        long managerUserId = 2L;
        User managerUser = new User("b@b.com", "password", UserRole.USER);  // 매니저로 등록할 유저
        ReflectionTestUtils.setField(managerUser, "id", managerUserId);

        ManagerSaveRequest managerSaveRequest = new ManagerSaveRequest(managerUserId); // request dto 생성

        given(todoService.getTodoById(todoId)).willReturn(todo);
        given(userService.getUserById(managerUserId, "등록하려고 하는 담당자 유저가 존재하지 않습니다.")).willReturn(managerUser);
        given(managerRepository.save(any(Manager.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        ManagerSaveResponse response = managerService.saveManager(authUser, todoId, managerSaveRequest);

        // then
        assertNotNull(response);
        assertEquals(managerUser.getId(), response.getUser().getId());
        assertEquals(managerUser.getEmail(), response.getUser().getEmail());
    }

    @Test
    @DisplayName("deleteManager: 담당자가 정상적으로 삭제된다.")
    void 담당자가_정상적으로_삭제된다() {
        // given
        AuthUser authUser = new AuthUser(1L, "a@a.com", UserRole.USER);
        User user = User.fromAuthUser(authUser);
        long todoId = 1L;
        long managerId = 1L;

        Todo todo = new Todo("Title", "Contents", "Sunny", user);
        ReflectionTestUtils.setField(todo, "id", todoId);

        Manager manager = new Manager(user, todo);
        ReflectionTestUtils.setField(manager, "id", managerId);

        given(todoService.getTodoById(todoId)).willReturn(todo);
        given(managerRepository.findById(managerId)).willReturn(Optional.of(manager));

        // when
        managerService.deleteManager(authUser, todoId, managerId);

        // then
        // delete 메서드가 1번 호출되었는 지 검증할거다.
        verify(managerRepository).delete(manager);
    }

    @Test
    @DisplayName("deleteManager: 해당 일정의 담당자가 아니면 예외가 발생한다.")
    void 해당_일정의_담당자가_아니면_예외가_발생한다() {
        // given
        AuthUser authUser = new AuthUser(1L, "a@a.com", UserRole.USER);
        User user = User.fromAuthUser(authUser);
        long todoId = 1L;
        long managerId = 1L;

        Todo todo = new Todo("Title", "Contents", "Sunny", user);
        ReflectionTestUtils.setField(todo, "id", todoId);

        Todo anotherTodo = new Todo("Another", "Contents", "Sunny", user);
        ReflectionTestUtils.setField(anotherTodo, "id", 2L);

        Manager manager = new Manager(user, anotherTodo);
        ReflectionTestUtils.setField(manager, "id", managerId);

        given(todoService.getTodoById(todoId)).willReturn(todo);
        given(managerRepository.findById(managerId)).willReturn(Optional.of(manager));

        // when & then
        assertThatThrownBy(() -> managerService.deleteManager(authUser, todoId, managerId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("해당 일정에 등록된 담당자가 아닙니다.");
    }
}
