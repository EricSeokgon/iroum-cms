package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.entity.QnaNotificationLog;
import kr.co.ircp.cms.domain.board.repository.QnaNotificationLogMapper;
import kr.co.ircp.cms.domain.board.repository.QnaNotificationOptoutMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link QnaNotificationServiceImpl} 단위 테스트 — REQ-BOARD-014-D.
 *
 * <p>커버리지 갭 P1: 59.0% → ≥ 85% (LINE)
 * <p>대상 ANCHOR: notifyAnswered — Q&A 답변 알림 공개 진입점 (fan_in ≥ 2)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QnaNotificationServiceImpl — Q&A 답변 알림 정책")
class QnaNotificationServiceImplTest {

    @Mock
    QnaNotificationLogMapper logMapper;

    @Mock
    QnaNotificationOptoutMapper optoutMapper;

    @InjectMocks
    QnaNotificationServiceImpl service;

    private static final Long QNA_ID = 100L;
    private static final Long QUESTIONER_ID = 1L;
    private static final Long ANSWERER_ID = 2L;

    @BeforeEach
    void noop() {
        // Mockito가 @Mock/@InjectMocks를 자동 와이어링
    }

    // ------------------------------------------------------------------
    // notifyAnswered
    // ------------------------------------------------------------------

    @Test
    @DisplayName("notifyAnswered — INAPP 채널은 항상 발송, EMAIL 옵트아웃 미설정 시 EMAIL 도 발송")
    void shouldSendBothChannelsWhenNoEmailOptout() {
        when(optoutMapper.existsByUserAndChannel(QUESTIONER_ID, "EMAIL")).thenReturn(false);

        service.notifyAnswered(QNA_ID, QUESTIONER_ID, ANSWERER_ID);

        ArgumentCaptor<QnaNotificationLog> captor =
                ArgumentCaptor.forClass(QnaNotificationLog.class);
        verify(logMapper, times(2)).insert(captor.capture());

        List<QnaNotificationLog> logs = captor.getAllValues();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getChannel()).isEqualTo("INAPP");
        assertThat(logs.get(1).getChannel()).isEqualTo("EMAIL");
        // 모든 발송은 markSent 호출까지 도달 (stub 발송 성공)
        verify(logMapper, times(2)).markSent(any());
    }

    @Test
    @DisplayName("notifyAnswered — EMAIL 옵트아웃 사용자에게는 EMAIL 발송 생략, INAPP 만 발송")
    void shouldSkipEmailWhenOptedOut() {
        when(optoutMapper.existsByUserAndChannel(QUESTIONER_ID, "EMAIL")).thenReturn(true);

        service.notifyAnswered(QNA_ID, QUESTIONER_ID, ANSWERER_ID);

        ArgumentCaptor<QnaNotificationLog> captor =
                ArgumentCaptor.forClass(QnaNotificationLog.class);
        verify(logMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getChannel()).isEqualTo("INAPP");
    }

    @Test
    @DisplayName("notifyAnswered — INSERT 시 DuplicateKeyException 발생 시 중복 발송 차단 (멱등성)")
    void shouldSkipWhenDuplicateKeyOnInsert() {
        when(optoutMapper.existsByUserAndChannel(QUESTIONER_ID, "EMAIL")).thenReturn(true);
        doThrow(new DuplicateKeyException("unique violation"))
                .when(logMapper).insert(any(QnaNotificationLog.class));

        service.notifyAnswered(QNA_ID, QUESTIONER_ID, ANSWERER_ID);

        // INSERT 1번 시도 후 DuplicateKeyException 으로 markSent 호출 없음
        verify(logMapper, times(1)).insert(any(QnaNotificationLog.class));
        verify(logMapper, never()).markSent(any());
    }

    // ------------------------------------------------------------------
    // updateEmailOptout
    // ------------------------------------------------------------------

    @Test
    @DisplayName("updateEmailOptout — optout=true 시 upsert 호출")
    void shouldUpsertWhenOptoutTrue() {
        service.updateEmailOptout(QUESTIONER_ID, true);

        verify(optoutMapper).upsert(QUESTIONER_ID, "EMAIL");
        verify(optoutMapper, never()).delete(anyLong(), anyString());
    }

    @Test
    @DisplayName("updateEmailOptout — optout=false 시 delete 호출")
    void shouldDeleteWhenOptoutFalse() {
        service.updateEmailOptout(QUESTIONER_ID, false);

        verify(optoutMapper).delete(QUESTIONER_ID, "EMAIL");
        verify(optoutMapper, never()).upsert(anyLong(), anyString());
    }

    // ------------------------------------------------------------------
    // retryFailed
    // ------------------------------------------------------------------

    @Test
    @DisplayName("retryFailed — PENDING/FAILED 로그가 없으면 mapper insert 호출 없음")
    void shouldDoNothingWhenNoPending() {
        when(logMapper.findPendingOrFailed()).thenReturn(List.of());

        service.retryFailed();

        verify(logMapper).findPendingOrFailed();
        verify(logMapper, never()).markSent(any());
        verify(logMapper, never()).markFailed(any(), anyString());
    }

    @Test
    @DisplayName("retryFailed — 재시도 대상 각 항목에 대해 markSent 호출 (stub 발송 성공)")
    void shouldMarkSentForEachPendingItem() {
        QnaNotificationLog item1 = QnaNotificationLog.builder()
                .id(10L).qnaId(QNA_ID).channel("EMAIL").retryCount((short) 0).build();
        QnaNotificationLog item2 = QnaNotificationLog.builder()
                .id(11L).qnaId(QNA_ID).channel("INAPP").retryCount((short) 1).build();
        when(logMapper.findPendingOrFailed()).thenReturn(List.of(item1, item2));

        service.retryFailed();

        verify(logMapper).markSent(eq(10L));
        verify(logMapper).markSent(eq(11L));
        verify(logMapper, never()).markFailed(any(), anyString());
        verify(logMapper, never()).markDeadLetter(any(), anyString());
    }
}
