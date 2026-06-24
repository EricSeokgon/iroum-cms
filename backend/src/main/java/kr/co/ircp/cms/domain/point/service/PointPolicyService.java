package kr.co.ircp.cms.domain.point.service;

import kr.co.ircp.cms.domain.point.dto.PointPolicyResponse;
import kr.co.ircp.cms.domain.point.dto.PointPolicyUpdateRequest;

/**
 * 포인트 정책 서비스.
 * SPEC-CMS-POINTS-001 REQ-PNT-001, REQ-PNT-006
 */
public interface PointPolicyService {

    /** system_setting 에서 포인트 정책을 즉시 조회한다 (캐시 없음). */
    PointPolicyResponse getPolicy();

    /** POINTS:* system_setting 키들을 갱신한다. */
    PointPolicyResponse updatePolicy(PointPolicyUpdateRequest request);
}
