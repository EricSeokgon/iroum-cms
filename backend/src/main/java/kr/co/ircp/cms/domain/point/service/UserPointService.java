package kr.co.ircp.cms.domain.point.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.point.dto.PointLedgerResponse;
import kr.co.ircp.cms.domain.point.dto.PointSummaryResponse;

import java.time.Instant;

/**
 * 사용자 포인트 적립/조회 서비스.
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-002/003/004/006/007/008.
 *
 * <p>적립 메서드(award*)는 {@code REQUIRES_NEW} 트랜잭션으로 분리되어 원인 행위
 * 트랜잭션과 격리된다. 호출 측은 적립 예외를 catch하여 핵심 행위를 보장한다(REQ-PNT-008).
 */
public interface UserPointService {

    /** 게시글 작성 적립 (REQ-PNT-002). 비활성/0점이면 무동작. */
    void awardForPost(Long userId, Long postId);

    /** 댓글 작성 적립 (REQ-PNT-003). */
    void awardForComment(Long userId, Long commentId);

    /** 좋아요 적립 (REQ-PNT-004). 좋아요 중복 방지는 BbsPostLikeService에서 처리. */
    void awardForLike(Long userId, Long postId);

    /** 사용자 본인 누적 총액 조회 (REQ-PNT-006). */
    PointSummaryResponse getSummary(Long userId);

    /** 사용자 본인 적립 내역 페이징 조회 (REQ-PNT-006). */
    PageResponse<PointLedgerResponse> getHistory(Long userId, int page, int size);

    /** 관리자 필터 조회 (REQ-PNT-006). */
    PageResponse<PointLedgerResponse> searchLedger(Long userId, String eventType,
                                                   Instant from, Instant to, int page, int size);
}
