package kr.co.ircp.cms.domain.dashboard.preference.service;

import kr.co.ircp.cms.domain.dashboard.preference.dto.PreferenceResponse;
import kr.co.ircp.cms.domain.dashboard.preference.dto.PreferenceUpdateRequest;

/**
 * SPEC-CMS-DASHBOARD-PERSONALIZE-001 — 사용자별 대시보드 개인화 서비스.
 *
 * <p>모든 메서드는 호출자(userId)의 본인 데이터만 다룬다. 권한 검사는 컨트롤러의
 * {@code @AuthenticationPrincipal} 단계에서 완료된 상태로 호출된다.
 */
public interface UserDashboardPreferenceService {

    /** REQ-DP-002-1~3 / AC-DP-API-1: lazy 생성 후 응답. */
    PreferenceResponse getOrCreate(Long userId);

    /** REQ-DP-002-4 / AC-DP-API-2: 부분 갱신. */
    PreferenceResponse update(Long userId, PreferenceUpdateRequest req);

    /** REQ-DP-002-5 / AC-DP-002-5: 스타일만 DEFAULT 로 초기화 (hidden 보존). */
    PreferenceResponse reset(Long userId);

    /** REQ-DP-001-1 / 001-2: 단건 위젯 가시성 토글. */
    PreferenceResponse toggleVisibility(Long userId, Long layoutId, String instanceId, boolean hidden);

    /** REQ-DP-001-5 / AC-DP-001-5: 특정 레이아웃의 hidden 배열을 빈 배열로 초기화. */
    PreferenceResponse showAllWidgets(Long userId, Long layoutId);

    /** REQ-DP-001-4 / AC-DP-001-3: 레이아웃 삭제 시 hidden 에서 해당 키 제거 (orphan 정리). */
    void cleanupForLayout(Long userId, Long layoutId);
}
