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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                .key(key).value(value).valueType(type)
                .description("설명")
                .build();
    }

    // ──────────────────────────────────────────────
    // get()
    // ──────────────────────────────────────────────

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
    @DisplayName("get() — 존재하는 키면 SystemSettingResponse 반환")
    void get_returns_response_when_found() {
        // given
        when(settingMapper.findByKey("max.size"))
                .thenReturn(Optional.of(setting("max.size", "100", "INT")));

        // when
        SystemSettingResponse result = settingService.get("max.size");

        // then
        assertThat(result.key()).isEqualTo("max.size");
        assertThat(result.value()).isEqualTo("100");
        assertThat(result.valueType()).isEqualTo("INT");
    }

    // ──────────────────────────────────────────────
    // listAll()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("listAll() — 모든 설정 반환")
    void listAll_returnsAll() {
        // given
        when(settingMapper.findAll()).thenReturn(List.of(
                setting("k1", "v1", "STRING"),
                setting("k2", "5", "INT")));

        // when
        List<SystemSettingResponse> result = settingService.listAll();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("listAll() — 빈 결과")
    void listAll_empty() {
        // given
        when(settingMapper.findAll()).thenReturn(List.of());

        // when
        List<SystemSettingResponse> result = settingService.listAll();

        // then
        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    // put() — value_type 검증 (INT/BOOL/JSON/STRING)
    // ──────────────────────────────────────────────

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
        verify(settingMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("put() — INT 타입 + 정수 값이면 정상 upsert")
    void put_validInt_callsUpsert() {
        // given
        when(settingMapper.findByKey("max.size"))
                .thenReturn(Optional.of(setting("max.size", "100", "INT")))
                .thenReturn(Optional.of(setting("max.size", "200", "INT")));

        // when
        SystemSettingResponse result = settingService.put("max.size",
                new SystemSettingRequest("200", "수정 설명"));

        // then
        verify(settingMapper).upsert(any());
        assertThat(result.value()).isEqualTo("200");
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

    @Test
    @DisplayName("put() — BOOL 타입 + true/false 값이면 정상 upsert (대소문자 무시)")
    void put_validBool_caseInsensitive() {
        // given
        when(settingMapper.findByKey("feature.flag"))
                .thenReturn(Optional.of(setting("feature.flag", "true", "BOOL")))
                .thenReturn(Optional.of(setting("feature.flag", "FALSE", "BOOL")));

        // when
        settingService.put("feature.flag", new SystemSettingRequest("FALSE", null));

        // then
        verify(settingMapper).upsert(any());
    }

    @Test
    @DisplayName("put() — JSON 타입 + 잘못된 JSON 값이면 InvalidSettingValueException")
    void put_throws_invalid_for_json_type() {
        // given
        when(settingMapper.findByKey("config"))
                .thenReturn(Optional.of(setting("config", "{}", "JSON")));

        // when / then
        assertThatThrownBy(() -> settingService.put("config",
                new SystemSettingRequest("not-json{", null)))
                .isInstanceOf(InvalidSettingValueException.class);
    }

    @Test
    @DisplayName("put() — JSON 타입 + 유효한 JSON이면 정상 upsert")
    void put_validJson() {
        // given
        when(settingMapper.findByKey("config"))
                .thenReturn(Optional.of(setting("config", "{}", "JSON")))
                .thenReturn(Optional.of(setting("config", "{\"a\":1}", "JSON")));

        // when
        settingService.put("config", new SystemSettingRequest("{\"a\":1}", null));

        // then
        verify(settingMapper).upsert(any());
    }

    @Test
    @DisplayName("put() — STRING 타입은 모든 값 허용")
    void put_stringType_acceptsAnything() {
        // given
        when(settingMapper.findByKey("title"))
                .thenReturn(Optional.of(setting("title", "원래 제목", "STRING")))
                .thenReturn(Optional.of(setting("title", "임의의<>!@값", "STRING")));

        // when
        settingService.put("title", new SystemSettingRequest("임의의<>!@값", null));

        // then
        verify(settingMapper).upsert(any());
    }

    @Test
    @DisplayName("put() — 신규 키(기존 설정 없음) 시 STRING 기본값 검증")
    void put_newKey_defaultsToStringValueType() {
        // given — 기존 설정 없음
        when(settingMapper.findByKey("new.key")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(setting("new.key", "임의값", "STRING")));

        // when
        settingService.put("new.key", new SystemSettingRequest("임의값", "신규"));

        // then — STRING이므로 검증 통과 + upsert 호출, valueType=STRING 으로 저장
        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(settingMapper).upsert(captor.capture());
        assertThat(captor.getValue().getValueType()).isEqualTo("STRING");
    }

    @Test
    @DisplayName("put() — description null 시 기존 description 유지")
    void put_nullDescription_keepsExisting() {
        // given
        when(settingMapper.findByKey("k"))
                .thenReturn(Optional.of(setting("k", "v", "STRING")))
                .thenReturn(Optional.of(setting("k", "new-v", "STRING")));

        // when
        settingService.put("k", new SystemSettingRequest("new-v", null));

        // then — description = 기존 "설명"
        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(settingMapper).upsert(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("설명");
    }

    @Test
    @DisplayName("put() — 신규 키 + description null 시 description도 null")
    void put_newKey_nullDescription() {
        // given
        when(settingMapper.findByKey("new.key2")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(setting("new.key2", "v", "STRING")));

        // when
        settingService.put("new.key2", new SystemSettingRequest("v", null));

        // then
        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(settingMapper).upsert(captor.capture());
        assertThat(captor.getValue().getDescription()).isNull();
    }

    @Test
    @DisplayName("put() — description 명시 시 명시된 description으로 저장")
    void put_explicitDescription() {
        // given
        when(settingMapper.findByKey("k"))
                .thenReturn(Optional.of(setting("k", "v", "STRING")))
                .thenReturn(Optional.of(setting("k", "v2", "STRING")));

        // when
        settingService.put("k", new SystemSettingRequest("v2", "신규 설명"));

        // then
        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(settingMapper).upsert(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("신규 설명");
    }
}
