package kr.co.ircp.cms.domain.point.service;

import kr.co.ircp.cms.domain.point.dto.PointPolicyDto;
import kr.co.ircp.cms.domain.point.dto.PointPolicyUpdateRequest;
import kr.co.ircp.cms.domain.system.setting.entity.SystemSetting;
import kr.co.ircp.cms.domain.system.setting.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 정책 서비스 구현체.
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-001/005/007.
 *
 * <p>// @MX:NOTE: [AUTO] getPolicy()는 @Cacheable 미사용 — system_setting을 매번 조회하여
 * POINTS:ENABLED 토글 등 정책 변경을 즉시 반영(REQ-PNT-007).
 */
// @MX:ANCHOR: [AUTO] PointPolicyServiceImpl.getPolicy — 모든 적립 경로의 정책 진입점
// @MX:REASON: UserPointServiceImpl(post/comment/like 3경로) + PointPolicyController 참조 (fan_in >= 3)
// @MX:SPEC: SPEC-CMS-POINTS-001#REQ-PNT-001
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointPolicyServiceImpl implements PointPolicyService {

    static final String KEY_ENABLED = "POINTS:ENABLED";
    static final String KEY_POST = "POINTS:POST_CREATED";
    static final String KEY_COMMENT = "POINTS:COMMENT_CREATED";
    static final String KEY_LIKE = "POINTS:LIKE_GIVEN";

    private final SystemSettingMapper systemSettingMapper;

    @Override
    public PointPolicyDto getPolicy() {
        boolean enabled = readBool(KEY_ENABLED);
        int post = readInt(KEY_POST);
        int comment = readInt(KEY_COMMENT);
        int like = readInt(KEY_LIKE);
        return new PointPolicyDto(enabled, post, comment, like);
    }

    @Override
    @Transactional
    // 감사 로깅은 컨트롤러(@AuditLog) + AuditLogAspect 의 *Service.update* 와일드카드 포인트컷으로 처리됨.
    public PointPolicyDto updatePolicy(PointPolicyUpdateRequest request) {
        upsert(KEY_ENABLED, Boolean.toString(request.enabled()), "BOOL", "참여 포인트 시스템 활성화 여부");
        upsert(KEY_POST, Integer.toString(request.postPoints()), "INT", "게시글 작성 시 적립 포인트");
        upsert(KEY_COMMENT, Integer.toString(request.commentPoints()), "INT", "댓글 작성 시 적립 포인트");
        upsert(KEY_LIKE, Integer.toString(request.likePoints()), "INT", "게시글 좋아요(최초 1회) 시 적립 포인트");
        return getPolicy();
    }

    private void upsert(String key, String value, String type, String description) {
        systemSettingMapper.upsert(SystemSetting.builder()
                .key(key).value(value).valueType(type).description(description)
                .build());
    }

    /** 키 부재/파싱 실패 시 false (REQ-PNT-001 안전 기본값). */
    private boolean readBool(String key) {
        return systemSettingMapper.findByKey(key)
                .map(SystemSetting::getValue)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    /** 키 부재/파싱 실패 시 0 (REQ-PNT-001 안전 기본값). */
    private int readInt(String key) {
        return systemSettingMapper.findByKey(key)
                .map(SystemSetting::getValue)
                .map(v -> {
                    try {
                        return Integer.parseInt(v.trim());
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .orElse(0);
    }
}
