package kr.co.ircp.cms.domain.point.service;

import kr.co.ircp.cms.domain.point.dto.PointPolicyDto;
import kr.co.ircp.cms.domain.point.dto.PointPolicyUpdateRequest;

/**
 * 포인트 정책 서비스.
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-001/005/007 — system_setting 기반 정책 조회/변경.
 * 조회는 캐시 없이 매 호출마다 system_setting을 읽어 정책 변경을 즉시 반영한다(REQ-PNT-007).
 */
public interface PointPolicyService {

    /** 현재 정책 조회 (키 부재 시 안전 기본값). */
    PointPolicyDto getPolicy();

    /** 정책 변경 (system_setting 저장 + 감사 로그). */
    PointPolicyDto updatePolicy(PointPolicyUpdateRequest request);
}
