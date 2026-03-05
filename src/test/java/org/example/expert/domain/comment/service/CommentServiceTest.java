package org.example.expert.domain.comment.service;

import org.example.expert.domain.comment.dto.request.CommentSaveRequest;
import org.example.expert.domain.comment.dto.response.CommentResponse;
import org.example.expert.domain.comment.dto.response.CommentSaveResponse;
import org.example.expert.domain.comment.entity.Comment;
import org.example.expert.domain.comment.repository.CommentRepository;
import org.example.expert.domain.common.dto.AuthUser;
import org.example.expert.domain.common.exception.NotFoundException;
import org.example.expert.domain.todo.entity.Todo;
import org.example.expert.domain.todo.service.TodoService;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private TodoService todoService;
    @InjectMocks
    private CommentService commentService;

    @Test
    @DisplayName("[Case 2] CommentServiceTest 예외처리 테스트")
    public void comment_등록_중_할일을_찾지_못해_에러가_발생한다() {
        // given
        long todoId = 1;
        CommentSaveRequest request = new CommentSaveRequest("contents");
        AuthUser authUser = new AuthUser(1L, "email", UserRole.USER);

        given(todoService.getTodoById(todoId)).willThrow(new NotFoundException("Todo not found"));

        // when & then
        assertThatThrownBy(() -> commentService.saveComment(authUser, todoId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Todo not found");
    }

    @Test
    public void comment를_정상적으로_등록한다() {
        // given
        long todoId = 1;
        CommentSaveRequest request = new CommentSaveRequest("contents");
        AuthUser authUser = new AuthUser(1L, "email", UserRole.USER);
        User user = User.fromAuthUser(authUser);
        Todo todo = new Todo("title", "contents", "weather", user);
        Comment comment = new Comment(request.getContents(), user, todo);

        given(todoService.getTodoById(todoId)).willReturn(todo);
        given(commentRepository.save(any())).willReturn(comment);

        // when
        CommentSaveResponse result = commentService.saveComment(authUser, todoId, request);

        // then
        assertNotNull(result);
    }
    
    @Test
    @DisplayName("댓글이 정상적으로 조회된다.")
    void 댓글_목록을_정상적으로_조회한다() {
        //given
        long todoId = 1L;
        CommentSaveRequest request = new CommentSaveRequest("contents");
        AuthUser authUser = new AuthUser(1L, "email", UserRole.USER);
        User user = User.fromAuthUser(authUser);
        Todo todo = new Todo("title", "contents", "weather", user);
        Comment comment = new Comment(request.getContents(), user, todo);

        given(commentRepository.findByTodoIdWithUser(todoId)).willReturn(List.of(comment));

        //when
        List<CommentResponse> comments = commentService.getComments(todoId);

        //then
        assertThat(comments).isNotNull();
        assertThat(comments).hasSize(1);
    }

    @Test
    @DisplayName("댓글이 없으면 빈 리스트를 반환한다.")
    void 댓글이_없으면_빈_리스트를_반환한다() {
        // given
        long todoId = 1L;
        given(commentRepository.findByTodoIdWithUser(todoId))
                .willReturn(List.of()); // 빈 리스트 반환

        // when
        List<CommentResponse> comments = commentService.getComments(todoId);

        // then
        assertThat(comments).isEmpty();
    }
}
