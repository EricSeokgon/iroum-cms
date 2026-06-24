package kr.co.ircp.cms.domain.point.service;

import kr.co.ircp.cms.domain.point.dto.PointPolicyResponse;
import kr.co.ircp.cms.domain.point.dto.PointPolicyUpdateRequest;
import kr.co.ircp.cms.domain.system.setting.entity.SystemSetting;
import kr.co.ircp.cms.domain.system.setting.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 정책 서비스 구현체.
 * SPEC-CMS-POINTS-001 REQ-PNT-001, REQ-PNT-006
 *
 * // @MX:NOTE: [AUTO] 캐시 없이 system_setting 을 매 호출마다 읽는다 — toggle 즉시 반영 보장
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointPolicyServiceImpl implements PointPolicyService {

    private static final String KEY_ENABLED           = "POINTS:ENABLED";
    private static final String KEY_POST_CREATED      = "POINTS:POST_CREATED";
    private static final String KEY_COMMENT_CREATED   = "POINTS:COMMENT_CREATED";
    private static final String KEY_LIKE_GIVEN        = "POINTS:LIKE_GIVEN";

    private final SystemSettingMapper settingMapper;

    @Override
    public PointPolicyResponse getPolicy() {
        boolean enabled         = boolVal(KEY_ENABLED, false);
        int postCreated         = intVal(KEY_POST_CREATED, 0);
        int commentCreated      = intVal(KEY_COMMENT_CREATED, 0);
        int likeGiven           = intVal(KEY_LIKE_GIVEN, 0);
        return new PointPolicyResponse(enabled, postCreated, commentCreated, likeGiven);
    }

    @Override
    @Transactional
    public PointPolicyResponse updatePolicy(PointPolicyUpdateRequest req) {
        if (req.enabled() != null) {
            upsert(KEY_ENABLED, String.valueOf(req.enabled()), "BOOL");
        }
        if (req.postCreated() != null) {
            upsert(KEY_POST_CREATED, String.valueOf(req.postCreated()), "INT");
        }
        if (req.commentCreated() != null) {
            upsert(KEY_COMMENT_CREATED, String.valueOf(req.commentCreated()), "INT");
        }
        if (req.likeGiven() != null) {
            upsert(KEY_LIKE_GIVEN, String.valueOf(req.likeGiven()), "INT");
        }
        return getPolicy();
    }

    private void upsert(String key, String value, String type) {
        settingMapper.upsert(SystemSetting.builder()
                .key(key)
                .value(value)
                .valueType(type)
                .build());
    }

    private boolean boolVal(String key, boolean defaultValue) {
        return settingMapper.findByKey(key)
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(defaultValue);
    }

    private int intVal(String key, int defaultValue) {
        return settingMapper.findByKey(key)
                .map(s -> {
                    try { return Integer.parseInt(s.getValue()); }
                    catch (NumberFormatException e) { return defaultValue; }
                })
                .orElse(defaultValue);
    }
}
