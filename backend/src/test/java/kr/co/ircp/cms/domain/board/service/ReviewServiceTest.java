package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.ReviewAggregate;
import kr.co.ircp.cms.domain.board.dto.ReviewCreateRequest;
import kr.co.ircp.cms.domain.board.dto.ReviewResponse;
import kr.co.ircp.cms.domain.board.entity.BbsPost;
import kr.co.ircp.cms.domain.board.entity.BbsPostReview;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostReviewMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPEC-CMS-REVIEW-001 Phase D1 — {@link ReviewServiceImpl} 단위 테스트.
 *
 * <p>{@code BbsPostReviewMapper} / {@code BbsPostMapper} 를 Mock 으로 대체하여
 * 서비스 계층의 비즈니스 규칙만 격리 검증한다 (DataQualityServiceTest 패턴 동일):
 * <ul>
 *   <li>별점 1~5 범위 방어 검증 (REQ-REV-008)</li>
 *   <li>동일 사용자·동일 게시물 다중 리뷰 허용 — UNIQUE 제약 없음 (REQ-REV-002)</li>
 *   <li>집계 재계산 — VISIBLE 모수 위임 + 0건 시 0.0 처리 (REQ-REV-003/009/010)</li>
 * </ul>
 *
 * <p>VISIBLE-only 집계의 실제 DB 질의 거동(hide 후 모수 제외)은
 * {@code ReviewAdminControllerIT} 가 실 DB 로 end-to-end 검증한다. 본 단위 테스트는
 * 서비스가 mapper 집계 결과를 그대로 게시물에 반영하는 계약을 검증한다.
 */
// @MX:NOTE: [AUTO] ReviewServiceTest — SPEC-CMS-REVIEW-001 서비스 계층 규칙 격리 검증 (fan_in=0)
// @MX:SPEC: SPEC-CMS-REVIEW-001#REQ-REV-002/003/008/009
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewServiceImpl 단위 테스트 (SPEC-CMS-REVIEW-001)")
class ReviewServiceTest {

    @Mock private BbsPostReviewMapper reviewMapper;
    @Mock private BbsPostMapper postMapper;

    @InjectMocks private ReviewServiceImpl service;

    // ─── 공통 스텁 빌더 ─────────────────────────────────────────────────────

    /**
     * insert 시 엔티티 id 를 generatedId 로 채우고(MyBatis useGeneratedKeys 모사),
     * 동일 id 재조회 시 VISIBLE 리뷰를 반환하도록 mapper 를 스텁한다.
     * insert 는 void 반환이므로 {@code doAnswer().when()} 사용.
     */
    private void stubInsertAndReload(long generatedId) {
        doAnswer(inv -> {
            inv.<BbsPostReview>getArgument(0).setId(generatedId);
            return null;
        }).when(reviewMapper).insert(any(BbsPostReview.class));

        when(reviewMapper.findById(generatedId)).thenReturn(Optional.of(
                BbsPostReview.builder()
                        .id(generatedId)
                        .postId(100L)
                        .authorId(7L)
                        .rating(3)
                        .content("내용")
                        .status("VISIBLE")
                        .build()));
    }

    private void stubPostExists(long postId) {
        when(postMapper.findById(postId))
                .thenReturn(Optional.of(BbsPost.builder().id(postId).build()));
    }

    private void stubAggregate(long postId, int count, BigDecimal avg) {
        when(reviewMapper.aggregateVisible(postId))
                .thenReturn(new ReviewAggregate(count, avg));
    }

    // ─── 1. 정상 별점 생성 ──────────────────────────────────────────────────

    @Test
    @DisplayName("createReview: 유효 별점(3) → VISIBLE 리뷰 생성")
    void createReview_validRating_createsReview() {
        long postId = 100L;
        stubPostExists(postId);
        stubInsertAndReload(11L);
        stubAggregate(postId, 1, new BigDecimal("3.0"));

        ReviewResponse res = service.createReview(
                postId, new ReviewCreateRequest(3, "좋아요"), 7L, "127.0.0.1");

        assertThat(res.id()).isEqualTo(11L);
        assertThat(res.rating()).isEqualTo(3);
        verify(reviewMapper).insert(any(BbsPostReview.class));
    }

    // ─── 2~3. 별점 범위 방어 검증 ───────────────────────────────────────────

    @Test
    @DisplayName("createReview: 별점 0 → IllegalArgumentException (REQ-REV-008)")
    void createReview_invalidRatingZero_throws() {
        long postId = 100L;
        stubPostExists(postId);

        assertThatThrownBy(() -> service.createReview(
                postId, new ReviewCreateRequest(0, "내용"), 7L, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);

        // 검증 실패 → 삽입·집계 미수행.
        verify(reviewMapper, never()).insert(any());
        verify(postMapper, never()).updateReviewAggregate(anyLong(), eq(0), any());
    }

    @Test
    @DisplayName("createReview: 별점 6 → IllegalArgumentException (REQ-REV-008)")
    void createReview_invalidRatingOverFive_throws() {
        long postId = 100L;
        stubPostExists(postId);

        assertThatThrownBy(() -> service.createReview(
                postId, new ReviewCreateRequest(6, "내용"), 7L, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reviewMapper, never()).insert(any());
    }

    // ─── 4. 동일 사용자·동일 게시물 다중 리뷰 허용 (REQ-REV-002) ──────────────

    @Test
    @DisplayName("createReview: 동일 사용자·동일 게시물 2회 작성 → 둘 다 성공(UNIQUE 제약 없음)")
    void createReview_sameUserSamePost_allowsMultiple() {
        long postId = 100L;
        long authorId = 7L;
        stubPostExists(postId);
        // insert 두 번 — id 1001, 1002 순차 부여 (void → doAnswer).
        doAnswer(inv -> { inv.<BbsPostReview>getArgument(0).setId(1001L); return null; })
                .doAnswer(inv -> { inv.<BbsPostReview>getArgument(0).setId(1002L); return null; })
                .when(reviewMapper).insert(any(BbsPostReview.class));
        when(reviewMapper.findById(anyLong())).thenAnswer(inv ->
                Optional.of(BbsPostReview.builder().id(inv.getArgument(0))
                        .postId(postId).authorId(authorId).rating(4).status("VISIBLE").build()));
        stubAggregate(postId, 2, new BigDecimal("4.0"));

        ReviewResponse first = service.createReview(
                postId, new ReviewCreateRequest(4, "첫 번째"), authorId, "127.0.0.1");
        ReviewResponse second = service.createReview(
                postId, new ReviewCreateRequest(4, "두 번째"), authorId, "127.0.0.1");

        // 두 호출 모두 성공 — 예외 없이 별개 리뷰 반환.
        assertThat(first.id()).isEqualTo(1001L);
        assertThat(second.id()).isEqualTo(1002L);
        verify(reviewMapper, times(2)).insert(any(BbsPostReview.class));
    }

    // ─── 5. 생성 후 집계 반영 (REQ-REV-003/009) ─────────────────────────────

    @Test
    @DisplayName("recalculateAggregate: 생성 후 게시물 집계(count/avg) 반영")
    void recalculateAggregate_afterCreate_updatesPost() {
        long postId = 100L;
        stubPostExists(postId);
        stubInsertAndReload(11L);
        // VISIBLE 2건, 평균 4.0 (rating 3 + 5).
        stubAggregate(postId, 2, new BigDecimal("4.0"));

        service.createReview(postId, new ReviewCreateRequest(5, "내용"), 7L, "127.0.0.1");

        ArgumentCaptor<Integer> countCap = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<BigDecimal> avgCap = ArgumentCaptor.forClass(BigDecimal.class);
        verify(postMapper).updateReviewAggregate(eq(postId), countCap.capture(), avgCap.capture());
        assertThat(countCap.getValue()).isEqualTo(2);
        assertThat(avgCap.getValue()).isEqualByComparingTo("4.0");
    }

    // ─── 6. hide/delete 후 VISIBLE 모수 재집계 위임 (REQ-REV-010) ────────────

    @Test
    @DisplayName("recalculateAggregate: VISIBLE 모수만 반영 — mapper 집계 결과 그대로 위임")
    void recalculateAggregate_usesVisibleOnly_forwardsAggregate() {
        long postId = 100L;
        // mapper 가 HIDDEN 제외 후 VISIBLE 1건·평균 4.0 만 집계했다고 가정.
        stubAggregate(postId, 1, new BigDecimal("4.0"));

        service.recalculateAggregate(postId);

        verify(postMapper).updateReviewAggregate(eq(postId), eq(1), eq(new BigDecimal("4.0")));
    }

    // ─── 7. 전부 숨김/0건 → 0.0 처리 (REQ-REV-003) ──────────────────────────

    @Test
    @DisplayName("recalculateAggregate: VISIBLE 0건(null 평균) → count=0, avg=0.0")
    void recalculateAggregate_allHidden_setsZero() {
        long postId = 100L;
        // VISIBLE 0건 — count=0, average=null.
        stubAggregate(postId, 0, null);

        service.recalculateAggregate(postId);

        ArgumentCaptor<BigDecimal> avgCap = ArgumentCaptor.forClass(BigDecimal.class);
        verify(postMapper).updateReviewAggregate(eq(postId), eq(0), avgCap.capture());
        assertThat(avgCap.getValue()).isEqualByComparingTo("0.0");
    }
}
