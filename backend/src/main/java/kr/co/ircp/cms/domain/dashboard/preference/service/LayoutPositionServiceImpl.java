package kr.co.ircp.cms.domain.dashboard.preference.service;

import kr.co.ircp.cms.domain.dashboard.entity.DashboardLayout;
import kr.co.ircp.cms.domain.dashboard.exception.DashboardLayoutNotFoundException;
import kr.co.ircp.cms.domain.dashboard.preference.dto.PositionPatchRequest;
import kr.co.ircp.cms.domain.dashboard.preference.exception.PreferenceConflictException;
import kr.co.ircp.cms.domain.dashboard.preference.repository.LayoutPositionMapper;
import kr.co.ircp.cms.domain.dashboard.repository.DashboardLayoutMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * SPEC-CMS-DASHBOARD-PERSONALIZE-001 — DnD 결과 영속화 서비스.
 *
 * <p>겹침 검증은 클라이언트 1차 + 본 서버 2차 (REQ-DP-003-3). 본 구현은 요청 페이로드 내부의
 * 위젯들끼리만 검증한다 — 기존 dashboard_layout_widget 의 다른 인스턴스와의 겹침은 별도 SPEC.
 */
// @MX:ANCHOR: [AUTO] LayoutPositionServiceImpl — Controller 단일 진입점이지만 REQ-DP-003 의 핵심 invariant 보유
// @MX:REASON: 소유권 + 낙관적 잠금 + 겹침 검증의 3중 invariant 가 본 메서드에 집중 (계약 보존 필수)
// @MX:SPEC: SPEC-CMS-DASHBOARD-PERSONALIZE-001 REQ-DP-003
@Service
@RequiredArgsConstructor
public class LayoutPositionServiceImpl implements LayoutPositionService {

    private final DashboardLayoutMapper layoutMapper;
    private final LayoutPositionMapper positionMapper;

    @Override
    @Transactional
    public void patchPositions(Long layoutId, Long ownerId, PositionPatchRequest req) {
        DashboardLayout layout = layoutMapper.findById(layoutId)
                .orElseThrow(() -> new DashboardLayoutNotFoundException(layoutId));

        if (!Objects.equals(layout.getOwnerId(), ownerId)) {
            throw new SecurityException("레이아웃 소유자가 아닙니다. id=" + layoutId);
        }

        // REQ-DP-003-5: 낙관적 잠금 — DB updated_at 과 요청 expected_updated_at 비교
        if (req.expectedUpdatedAt() != null) {
            Instant dbUpdatedAt = layout.getUpdatedAt();
            if (dbUpdatedAt == null || !dbUpdatedAt.equals(req.expectedUpdatedAt())) {
                throw new PreferenceConflictException(
                        "다른 탭에서 레이아웃이 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
            }
        }

        // REQ-DP-003-3: 요청 페이로드 내부 겹침 검증
        validateNoOverlap(req.entries());

        for (PositionPatchRequest.PositionEntry entry : req.entries()) {
            PositionPatchRequest.Position p = entry.position();
            String json = String.format("{\"x\":%d,\"y\":%d,\"w\":%d,\"h\":%d}",
                    p.x(), p.y(), p.w(), p.h());
            positionMapper.updatePosition(layoutId, entry.instanceId(), json);
        }
        positionMapper.touchLayoutUpdatedAt(layoutId);
    }

    /**
     * 요청 내 위젯 쌍을 모두 비교하여 사각형 겹침 여부를 검증한다.
     *
     * <p>O(N^2) — N <= 20 가정 (SPEC §8 비기능 임계값 기준) 이므로 충분히 작다.
     */
    private static void validateNoOverlap(List<PositionPatchRequest.PositionEntry> entries) {
        int n = entries.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (rectanglesOverlap(entries.get(i).position(), entries.get(j).position())) {
                    throw new IllegalArgumentException(
                            "위젯 위치 겹침: " + entries.get(i).instanceId()
                                    + " <-> " + entries.get(j).instanceId());
                }
            }
        }
    }

    private static boolean rectanglesOverlap(
            PositionPatchRequest.Position a, PositionPatchRequest.Position b) {
        int aLeft = a.x(), aRight = a.x() + a.w();
        int aTop = a.y(), aBottom = a.y() + a.h();
        int bLeft = b.x(), bRight = b.x() + b.w();
        int bTop = b.y(), bBottom = b.y() + b.h();
        return aLeft < bRight && bLeft < aRight && aTop < bBottom && bTop < aBottom;
    }
}
