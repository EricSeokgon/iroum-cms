package kr.co.ircp.cms.domain.point.service;

import kr.co.ircp.cms.domain.point.dto.PointPolicyDto;
import kr.co.ircp.cms.domain.point.dto.PointPolicyUpdateRequest;
import kr.co.ircp.cms.domain.system.setting.entity.SystemSetting;
import kr.co.ircp.cms.domain.system.setting.mapper.SystemSettingMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PointPolicyServiceImpl} 단위 테스트 — SPEC-CMS-POINTS-001 REQ-PNT-001/005.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PointPolicyServiceImpl 단위 테스트")
class PointPolicyServiceImplTest {

    @Mock
    private SystemSettingMapper systemSettingMapper;

    @InjectMocks
    private PointPolicyServiceImpl service;

    private SystemSetting setting(String key, String value, String type) {
        return SystemSetting.builder().key(key).value(value).valueType(type).build();
    }

    @Test
    @DisplayName("REQ-PNT-001: 정책 키 부재 시 안전 기본값(비활성·0점) 반환")
    void getPolicy_returnsDefaults_whenKeysAbsent() {
        when(systemSettingMapper.findByKey(anyString())).thenReturn(Optional.empty());

        PointPolicyDto policy = service.getPolicy();

        assertThat(policy.enabled()).isFalse();
        assertThat(policy.postPoints()).isZero();
        assertThat(policy.commentPoints()).isZero();
        assertThat(policy.likePoints()).isZero();
    }

    @Test
    @DisplayName("REQ-PNT-001: 정책 키 존재 시 저장된 값을 정책으로 반환")
    void getPolicy_returnsValues_whenKeysPresent() {
        when(systemSettingMapper.findByKey("POINTS:ENABLED"))
                .thenReturn(Optional.of(setting("POINTS:ENABLED", "true", "BOOL")));
        when(systemSettingMapper.findByKey("POINTS:POST_CREATED"))
                .thenReturn(Optional.of(setting("POINTS:POST_CREATED", "10", "INT")));
        when(systemSettingMapper.findByKey("POINTS:COMMENT_CREATED"))
                .thenReturn(Optional.of(setting("POINTS:COMMENT_CREATED", "5", "INT")));
        when(systemSettingMapper.findByKey("POINTS:LIKE_GIVEN"))
                .thenReturn(Optional.of(setting("POINTS:LIKE_GIVEN", "2", "INT")));

        PointPolicyDto policy = service.getPolicy();

        assertThat(policy.enabled()).isTrue();
        assertThat(policy.postPoints()).isEqualTo(10);
        assertThat(policy.commentPoints()).isEqualTo(5);
        assertThat(policy.likePoints()).isEqualTo(2);
    }

    @Test
    @DisplayName("REQ-PNT-001: 정수 파싱 실패 시 0점으로 폴백")
    void getPolicy_fallsBackToZero_onMalformedInt() {
        when(systemSettingMapper.findByKey("POINTS:ENABLED"))
                .thenReturn(Optional.of(setting("POINTS:ENABLED", "false", "BOOL")));
        when(systemSettingMapper.findByKey("POINTS:POST_CREATED"))
                .thenReturn(Optional.of(setting("POINTS:POST_CREATED", "not-a-number", "INT")));
        when(systemSettingMapper.findByKey("POINTS:COMMENT_CREATED")).thenReturn(Optional.empty());
        when(systemSettingMapper.findByKey("POINTS:LIKE_GIVEN")).thenReturn(Optional.empty());

        PointPolicyDto policy = service.getPolicy();

        assertThat(policy.postPoints()).isZero();
    }

    @Test
    @DisplayName("REQ-PNT-005: 정책 변경 시 4개 키 모두 system_setting에 upsert")
    void updatePolicy_savesAllKeys() {
        // updatePolicy는 마지막에 getPolicy()로 재조회하므로 findByKey stub 필요
        when(systemSettingMapper.findByKey(anyString())).thenReturn(Optional.empty());
        PointPolicyUpdateRequest request = new PointPolicyUpdateRequest(true, 10, 5, 2);

        service.updatePolicy(request);

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingMapper, times(4)).upsert(captor.capture());

        List<SystemSetting> saved = captor.getAllValues();
        assertThat(saved).extracting(SystemSetting::getKey)
                .containsExactlyInAnyOrder(
                        "POINTS:ENABLED", "POINTS:POST_CREATED",
                        "POINTS:COMMENT_CREATED", "POINTS:LIKE_GIVEN");
        assertThat(saved).filteredOn(s -> "POINTS:ENABLED".equals(s.getKey()))
                .extracting(SystemSetting::getValue).containsExactly("true");
        assertThat(saved).filteredOn(s -> "POINTS:POST_CREATED".equals(s.getKey()))
                .extracting(SystemSetting::getValue).containsExactly("10");
    }
}
