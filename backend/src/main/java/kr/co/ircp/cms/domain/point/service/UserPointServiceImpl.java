package kr.co.ircp.cms.domain.point.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.point.dto.PointLedgerResponse;
import kr.co.ircp.cms.domain.point.dto.PointSummaryResponse;
import kr.co.ircp.cms.domain.point.entity.UserPointLedger;
import kr.co.ircp.cms.domain.point.mapper.UserPointLedgerMapper;
import kr.co.ircp.cms.domain.point.mapper.UserPointSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 사용자 포인트 서비스 구현체.
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-002/003/004/006/007/008.
 *
 * <p>// @MX:NOTE: [AUTO] award* 메서드는 propagation=REQUIRES_NEW — 원인 행위(게시글/댓글/좋아요)
 * 트랜잭션과 독립된 새 트랜잭션에서 적립을 수행하여, 적립 실패가 원인 행위를 롤백시키지 않도록 격리한다(REQ-PNT-008).
 */
// @MX:ANCHOR: [AUTO] UserPointServiceImpl.award* — 적립 격리 경계(REQUIRES_NEW)
// @MX:REASON: PostServiceImpl/CommentServiceImpl/BbsPostLikeServiceImpl 3경로에서 호출 (fan_in >= 3)
// @MX:SPEC: SPEC-CMS-POINTS-001#REQ-PNT-008
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPointServiceImpl implements UserPointService {

    private final PointPolicyService pointPolicyService;
    private final UserPointLedgerMapper ledgerMapper;
    private final UserPointSummaryMapper summaryMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void awardForPost(Long userId, Long postId) {
        var policy = pointPolicyService.getPolicy();
        award(userId, "POST_CREATED", postId, policy.enabled() ? policy.postPoints() : 0);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void awardForComment(Long userId, Long commentId) {
        var policy = pointPolicyService.getPolicy();
        award(userId, "COMMENT_CREATED", commentId, policy.enabled() ? policy.commentPoints() : 0);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void awardForLike(Long userId, Long postId) {
        var policy = pointPolicyService.getPolicy();
        award(userId, "LIKE_GIVEN", postId, policy.enabled() ? policy.likePoints() : 0);
    }

    /**
     * 공통 적립 로직: 비활성(points==0)이면 무동작, 양수면 원장 insert + 요약 upsert.
     * REQ-PNT-007: enabled=false이면 호출 측에서 points=0으로 전달되어 적립 스킵.
     */
    private void award(Long userId, String eventType, Long referenceId, int points) {
        if (points <= 0) {
            return;
        }
        ledgerMapper.insert(UserPointLedger.builder()
                .userId(userId)
                .eventType(eventType)
                .referenceId(referenceId)
                .points(points)
                .build());
        summaryMapper.upsertSummary(userId, points);
    }

    @Override
    public PointSummaryResponse getSummary(Long userId) {
        return summaryMapper.findByUserId(userId)
                .map(s -> new PointSummaryResponse(s.getUserId(), s.getTotalPoints(), s.getUpdatedAt()))
                .orElse(new PointSummaryResponse(userId, 0L, null));
    }

    @Override
    public PageResponse<PointLedgerResponse> getHistory(Long userId, int page, int size) {
        int offset = page * size;
        List<UserPointLedger> rows = ledgerMapper.findByUserId(userId, offset, size);
        long total = ledgerMapper.countByUserId(userId);
        List<PointLedgerResponse> content = rows.stream().map(PointLedgerResponse::from).toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    public PageResponse<PointLedgerResponse> searchLedger(Long userId, String eventType,
                                                          Instant from, Instant to, int page, int size) {
        int offset = page * size;
        List<UserPointLedger> rows = ledgerMapper.findByFilter(userId, eventType, from, to, offset, size);
        long total = ledgerMapper.countByFilter(userId, eventType, from, to);
        List<PointLedgerResponse> content = rows.stream().map(PointLedgerResponse::from).toList();
        return PageResponse.of(content, page, size, total);
    }
}
