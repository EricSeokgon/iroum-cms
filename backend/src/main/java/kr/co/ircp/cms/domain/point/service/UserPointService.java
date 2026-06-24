package kr.co.ircp.cms.domain.point.service;

import kr.co.ircp.cms.domain.point.dto.PointLedgerResponse;
import kr.co.ircp.cms.domain.point.dto.PointLedgerSearchRequest;

import java.util.List;

/**
 * 사용자 포인트 서비스.
 * SPEC-CMS-POINTS-001 REQ-PNT-002~004, REQ-PNT-007, REQ-PNT-008
 */
public interface UserPointService {

    // @MX:ANCHOR: [AUTO] awardPoints — PostServiceImpl, CommentServiceImpl, BbsPostLikeServiceImpl 에서 호출 (fan_in >= 3)
    // @MX:REASON: 포인트 지급 핵심 경로; REQUIRES_NEW 트랜잭션으로 격리 — 변경 시 best-effort 보장 검토 필요
    /**
     * 포인트를 지급한다. best-effort: REQUIRES_NEW 트랜잭션으로 격리.
     * POINTS:ENABLED = false 이거나 해당 포인트 = 0 이면 무시한다.
     */
    void awardPoints(Long userId, String reason, String refType, Long refId);

    List<PointLedgerResponse> listLedger(PointLedgerSearchRequest request);

    int countLedger(PointLedgerSearchRequest request);
}
