package kr.co.ircp.cms.domain.point.service;

import kr.co.ircp.cms.domain.point.dto.PointPolicyDto;
import kr.co.ircp.cms.domain.point.entity.UserPointLedger;
import kr.co.ircp.cms.domain.point.mapper.UserPointLedgerMapper;
import kr.co.ircp.cms.domain.point.mapper.UserPointSummaryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserPointServiceImpl} 단위 테스트 — SPEC-CMS-POINTS-001 REQ-PNT-002/003/004/007.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserPointServiceImpl 단위 테스트")
class UserPointServiceImplTest {

    @Mock
    private PointPolicyService pointPolicyService;
    @Mock
    private UserPointLedgerMapper ledgerMapper;
    @Mock
    private UserPointSummaryMapper summaryMapper;

    @InjectMocks
    private UserPointServiceImpl service;

    @Test
    @DisplayName("REQ-PNT-002: 활성화 상태에서 게시글 작성 적립 → 원장 insert + 요약 upsert")
    void awardForPost_insertsLedger_whenEnabled() {
        when(pointPolicyService.getPolicy()).thenReturn(new PointPolicyDto(true, 10, 5, 2));

        service.awardForPost(100L, 7L);

        ArgumentCaptor<UserPointLedger> captor = ArgumentCaptor.forClass(UserPointLedger.class);
        verify(ledgerMapper).insert(captor.capture());
        UserPointLedger l = captor.getValue();
        assertThat(l.getUserId()).isEqualTo(100L);
        assertThat(l.getEventType()).isEqualTo("POST_CREATED");
        assertThat(l.getReferenceId()).isEqualTo(7L);
        assertThat(l.getPoints()).isEqualTo(10);
        verify(summaryMapper).upsertSummary(100L, 10);
    }

    @Test
    @DisplayName("REQ-PNT-007: 비활성화 상태에서는 적립하지 않음 (원장/요약 미변경)")
    void awardForPost_skips_whenDisabled() {
        when(pointPolicyService.getPolicy()).thenReturn(PointPolicyDto.disabled());

        service.awardForPost(100L, 7L);

        verify(ledgerMapper, never()).insert(any());
        verify(summaryMapper, never()).upsertSummary(anyLong(), anyInt());
    }

    @Test
    @DisplayName("REQ-PNT-002: 활성화돼 있어도 포인트가 0이면 적립하지 않음")
    void awardForPost_skips_whenPointsZero() {
        when(pointPolicyService.getPolicy()).thenReturn(new PointPolicyDto(true, 0, 0, 0));

        service.awardForPost(100L, 7L);

        verify(ledgerMapper, never()).insert(any());
        verify(summaryMapper, never()).upsertSummary(anyLong(), anyInt());
    }

    @Test
    @DisplayName("REQ-PNT-003: 댓글 작성 적립 → COMMENT_CREATED 원장 기록")
    void awardForComment_insertsLedger() {
        when(pointPolicyService.getPolicy()).thenReturn(new PointPolicyDto(true, 10, 5, 2));

        service.awardForComment(100L, 33L);

        ArgumentCaptor<UserPointLedger> captor = ArgumentCaptor.forClass(UserPointLedger.class);
        verify(ledgerMapper).insert(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("COMMENT_CREATED");
        assertThat(captor.getValue().getPoints()).isEqualTo(5);
        verify(summaryMapper).upsertSummary(100L, 5);
    }

    @Test
    @DisplayName("REQ-PNT-004: 좋아요 적립 → LIKE_GIVEN 원장 기록")
    void awardForLike_insertsLedger() {
        when(pointPolicyService.getPolicy()).thenReturn(new PointPolicyDto(true, 10, 5, 2));

        service.awardForLike(100L, 9L);

        ArgumentCaptor<UserPointLedger> captor = ArgumentCaptor.forClass(UserPointLedger.class);
        verify(ledgerMapper).insert(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("LIKE_GIVEN");
        assertThat(captor.getValue().getPoints()).isEqualTo(2);
        verify(summaryMapper).upsertSummary(100L, 2);
    }
}
