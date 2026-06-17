package kr.co.ircp.cms.domain.point.dto;

/**
 * 포인트 정책 값 객체.
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-001 — system_setting 키에서 로드한 정책.
 * 키 부재 시 안전 기본값(enabled=false, 각 포인트 0)을 적용한다.
 */
public record PointPolicyDto(
        boolean enabled,
        int postPoints,
        int commentPoints,
        int likePoints
) {
    /** 안전 기본값(비활성·0점). REQ-PNT-001 IF 분기. */
    public static PointPolicyDto disabled() {
        return new PointPolicyDto(false, 0, 0, 0);
    }
}
