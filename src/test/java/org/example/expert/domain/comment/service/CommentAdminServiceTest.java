package org.example.expert.domain.comment.service;

import org.example.expert.domain.comment.repository.CommentRepository;
import org.example.expert.domain.common.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommentAdminServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @InjectMocks
    private CommentAdminService commentAdminService;

    @Test
    @DisplayName("deleteComment: 댓글이 정상적으로 삭제된다.")
    void 댓글이_정상적으로_삭제된다() {
        // given
        long commentId = 1L;
        given(commentRepository.existsById(commentId)).willReturn(true); // 👈 채워봐!

        // when
        commentAdminService.deleteComment(commentId);

        // then
        verify(commentRepository).deleteById(commentId); // 👈 채워봐!
    }

    @Test
    @DisplayName("deleteComment: 댓글이 없으면 예외가 발생한다.")
    void 댓글이_없으면_예외가_발생한다() {
        // given
        long commentId = 1L;
        given(commentRepository.existsById(commentId)).willReturn(false); // 👈 채워봐!

        // when & then
        assertThatThrownBy(() -> commentAdminService.deleteComment(commentId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("댓글이 존재하지 않습니다.");
    }
}