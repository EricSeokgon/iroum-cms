package kr.co.ircp.cms.domain.system.setting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.system.setting.dto.SystemSettingRequest;
import kr.co.ircp.cms.domain.system.setting.dto.SystemSettingResponse;
import kr.co.ircp.cms.domain.system.setting.entity.SystemSetting;
import kr.co.ircp.cms.domain.system.setting.exception.InvalidSettingValueException;
import kr.co.ircp.cms.domain.system.setting.mapper.SystemSettingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SystemSettingService GREEN 테스트.
 * REQ-SYSTEM-005-D: value_type 검증 (INT/BOOL/JSON/STRING)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SystemSettingService GREEN 테스트 (REQ-SYSTEM-005-D)")
class SystemSettingServiceTest {

    @Mock private SystemSettingMapper settingMapper;

    private SystemSettingServiceImpl settingService;

    @BeforeEach
    void setUp() {
        settingService = new SystemSettingServiceImpl(settingMapper, new ObjectMapper());
    }

    private SystemSetting setting(String key, String value, String type) {
        return SystemSetting.builder()
                .key(key).value(value).valueType(type).build();
    }

    @Test
    @DisplayName("get() — 존재하지 않는 키면 NoSuchElementException")
    void get_throws_when_not_found() {
        // given
        when(settingMapper.findByKey("missing.key")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> settingService.get("missing.key"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("put() — INT 타입에 문자열 값이면 InvalidSettingValueException")
    void put_throws_invalid_for_int_type_with_string_value() {
        // given — 기존 설정이 INT 타입
        when(settingMapper.findByKey("max.size"))
                .thenReturn(Optional.of(setting("max.size", "100", "INT")));

        // when / then
        assertThatThrownBy(() -> settingService.put("max.size",
                new SystemSettingRequest("not-a-number", null)))
                .isInstanceOf(InvalidSettingValueException.class);
    }

    @Test
    @DisplayName("put() — BOOL 타입에 유효하지 않은 값이면 InvalidSettingValueException")
    void put_throws_invalid_for_bool_type_with_invalid_value() {
        // given
        when(settingMapper.findByKey("feature.flag"))
                .thenReturn(Optional.of(setting("feature.flag", "true", "BOOL")));

        // when / then
        assertThatThrownBy(() -> settingService.put("feature.flag",
                new SystemSettingRequest("yes", null)))
                .isInstanceOf(InvalidSettingValueException.class);
    }
}
