package org.example.expert.domain.manager.service;

import lombok.RequiredArgsConstructor;
import org.example.expert.domain.common.dto.AuthUser;
import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.common.exception.NotFoundException;
import org.example.expert.domain.manager.dto.request.ManagerSaveRequest;
import org.example.expert.domain.manager.dto.response.ManagerResponse;
import org.example.expert.domain.manager.dto.response.ManagerSaveResponse;
import org.example.expert.domain.manager.entity.Manager;
import org.example.expert.domain.manager.repository.ManagerRepository;
import org.example.expert.domain.todo.entity.Todo;
import org.example.expert.domain.todo.service.TodoService;
import org.example.expert.domain.user.dto.response.UserResponse;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ManagerService {

    private final ManagerRepository managerRepository;
    private final UserService userService;
    private final TodoService todoService;

    // 일정을 생성한 유저인지 확인
    private void validateTodoOwner(AuthUser authUser, Todo todo, String errorMessage) {
        if (todo.getUser() == null || !ObjectUtils.nullSafeEquals(authUser.getId(), todo.getUser().getId())) {
            throw new InvalidRequestException(errorMessage);
        }
    }

    @Transactional
    public ManagerSaveResponse saveManager(AuthUser authUser, long todoId, ManagerSaveRequest managerSaveRequest) {
        // 일정을 만든 유저
        Todo todo = todoService.getTodoById(todoId);
        validateTodoOwner(authUser, todo, "일정을 생성한 유저만 담당자를 지정할 수 있습니다.");

        User managerUser = userService.getUserById(managerSaveRequest.getManagerUserId(),
                "등록하려고 하는 담당자 유저가 존재하지 않습니다.");

        if (ObjectUtils.nullSafeEquals(authUser.getId(), managerUser.getId())) {
            throw new InvalidRequestException("일정 작성자는 본인을 담당자로 등록할 수 없습니다.");
        }

        Manager newManagerUser = new Manager(managerUser, todo);
        Manager savedManagerUser = managerRepository.save(newManagerUser);

        return new ManagerSaveResponse(
                savedManagerUser.getId(),
                new UserResponse(managerUser.getId(), managerUser.getEmail())
        );
    }

    @Transactional(readOnly = true)
    public List<ManagerResponse> getManagers(long todoId) {
        Todo todo = todoService.getTodoById(todoId);

        List<Manager> managerList = managerRepository.findByTodoIdWithUser(todo.getId());
        return managerList.stream()
                .map(manager -> {
                    User user = manager.getUser();
                    return new ManagerResponse(manager.getId(),
                            UserResponse.from(user));
                })
                .toList();
    }

    @Transactional
    public void deleteManager(AuthUser authUser, long todoId, long managerId) {
        Todo todo = todoService.getTodoById(todoId);
        validateTodoOwner(authUser, todo, "해당 일정을 만든 유저가 유효하지 않습니다.");

        Manager manager = managerRepository.findById(managerId)
                .orElseThrow(() -> new NotFoundException("Manager not found"));

        if (!ObjectUtils.nullSafeEquals(todo.getId(), manager.getTodo().getId())) {
            throw new InvalidRequestException("해당 일정에 등록된 담당자가 아닙니다.");
        }

        managerRepository.delete(manager);
    }
}
