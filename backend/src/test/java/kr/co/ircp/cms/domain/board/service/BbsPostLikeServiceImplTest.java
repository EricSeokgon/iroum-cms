package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.entity.BbsPostLike;
import kr.co.ircp.cms.domain.board.mapper.BbsPostLikeMapper;
import kr.co.ircp.cms.domain.point.service.UserPointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link BbsPostLikeServiceImpl} 단위 테스트 — SPEC-CMS-POINTS-001 REQ-PNT-004/008.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BbsPostLikeServiceImpl 단위 테스트")
class BbsPostLikeServiceImplTest {

    @Mock
    private BbsPostLikeMapper bbsPostLikeMapper;
    @Mock
    private UserPointService userPointService;

    @InjectMocks
    private BbsPostLikeServiceImpl service;

    @Test
    @DisplayName("REQ-PNT-004: 최초 좋아요 → 좋아요 insert + 포인트 적립, true 반환")
    void like_insertsLike_andAwards() {
        doNothing().when(bbsPostLikeMapper).insert(any(BbsPostLike.class));

        boolean result = service.like(9L, 100L);

        assertThat(result).isTrue();
        verify(bbsPostLikeMapper).insert(any(BbsPostLike.class));
        verify(userPointService).awardForLike(100L, 9L);
    }

    @Test
    @DisplayName("REQ-PNT-004: 중복 좋아요(UNIQUE 위반) → 적립 없이 false 반환")
    void like_skipsDuplicate() {
        doThrow(new DuplicateKeyException("uq_bbs_post_like"))
                .when(bbsPostLikeMapper).insert(any(BbsPostLike.class));

        boolean result = service.like(9L, 100L);

        assertThat(result).isFalse();
        verify(userPointService, never()).awardForLike(anyLong(), anyLong());
    }

    @Test
    @DisplayName("REQ-PNT-008: 포인트 적립 실패해도 좋아요 등록은 정상 완료(true)")
    void like_swallowsAwardFailure() {
        doNothing().when(bbsPostLikeMapper).insert(any(BbsPostLike.class));
        doThrow(new RuntimeException("적립 실패"))
                .when(userPointService).awardForLike(anyLong(), anyLong());

        boolean result = service.like(9L, 100L);

        assertThat(result).isTrue();
        verify(bbsPostLikeMapper).insert(any(BbsPostLike.class));
    }

    @Test
    @DisplayName("REQ-PNT-004: 좋아요 취소 → 삭제만 수행, 포인트 회수 없음")
    void unlike_deletesLike_noPointReversal() {
        service.unlike(9L, 100L);

        verify(bbsPostLikeMapper).deleteByPostIdAndUserId(9L, 100L);
        // 포인트 회수 호출이 전혀 없어야 함
        verify(userPointService, never()).awardForLike(anyLong(), anyLong());
    }
}
