package kr.co.ircp.cms.domain.point.service;

import kr.co.ircp.cms.domain.point.dto.PointLedgerResponse;
import kr.co.ircp.cms.domain.point.dto.PointLedgerSearchRequest;
import kr.co.ircp.cms.domain.point.entity.UserPointLedger;
import kr.co.ircp.cms.domain.point.mapper.UserPointLedgerMapper;
import kr.co.ircp.cms.domain.point.mapper.UserPointSummaryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사용자 포인트 서비스 구현체.
 * SPEC-CMS-POINTS-001 REQ-PNT-002~004, REQ-PNT-007, REQ-PNT-008
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPointServiceImpl implements UserPointService {

    private final UserPointLedgerMapper ledgerMapper;
    private final UserPointSummaryMapper summaryMapper;
    private final PointPolicyService policyService;

    /**
     * 포인트 지급 — REQUIRES_NEW 트랜잭션으로 메인 로직과 격리.
     * // @MX:NOTE: [AUTO] 정책 조회 후 delta=0 이면 즉시 반환 — DB 기록 없음
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void awardPoints(Long userId, String reason, String refType, Long refId) {
        var policy = policyService.getPolicy();
        if (!policy.enabled()) {
            return;
        }
        int delta = resolveDelta(policy, reason);
        if (delta <= 0) {
            return;
        }

        ledgerMapper.insert(UserPointLedger.builder()
                .userId(userId)
                .delta(delta)
                .reason(reason)
                .refType(refType)
                .refId(refId)
                .build());
        summaryMapper.upsertDelta(userId, delta);
    }

    @Override
    public List<PointLedgerResponse> listLedger(PointLedgerSearchRequest request) {
        return ledgerMapper.findAll(request.userId(), request.offset(), request.size())
                .stream().map(PointLedgerResponse::from).toList();
    }

    @Override
    public int countLedger(PointLedgerSearchRequest request) {
        return ledgerMapper.countAll(request.userId());
    }

    private int resolveDelta(kr.co.ircp.cms.domain.point.dto.PointPolicyResponse policy, String reason) {
        return switch (reason) {
            case "POST_CREATED"     -> policy.postCreated();
            case "COMMENT_CREATED"  -> policy.commentCreated();
            case "LIKE_GIVEN"       -> policy.likeGiven();
            default                  -> 0;
        };
    }
}
