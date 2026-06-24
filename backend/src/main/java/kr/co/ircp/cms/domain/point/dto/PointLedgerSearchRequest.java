package kr.co.ircp.cms.domain.point.dto;

/**
 * 포인트 이력 조회 요청 파라미터.
 * SPEC-CMS-POINTS-001 REQ-PNT-007
 *
 * @param userId null 이면 전체 사용자 조회
 * @param page   0-based 페이지 번호
 * @param size   페이지 크기 (기본 20)
 */
public record PointLedgerSearchRequest(
        Long userId,
        int page,
        int size
) {
    public PointLedgerSearchRequest {
        if (size <= 0) {
            size = 20;
        }
        if (page < 0) {
            page = 0;
        }
    }

    public int offset() {
        return page * size;
    }
}
