package kr.co.ircp.cms.domain.media.entity;

/**
 * 미디어 자산 라이선스 유형.
 * REQ-MEDIA-004-D-4: CC_BY·CC_BY_NC인 경우 copyright_holder 입력 강제
 */
public enum LicenseType {
    CC0,
    CC_BY,
    CC_BY_NC,
    PROPRIETARY,
    INTERNAL;

    /** CC_BY 또는 CC_BY_NC인 경우 copyright_holder 필수 */
    public boolean requiresCopyrightHolder() {
        return this == CC_BY || this == CC_BY_NC;
    }
}
