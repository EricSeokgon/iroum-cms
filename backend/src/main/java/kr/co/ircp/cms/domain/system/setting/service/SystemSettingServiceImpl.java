package kr.co.ircp.cms.domain.system.setting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
import kr.co.ircp.cms.domain.system.setting.dto.SystemSettingRequest;
import kr.co.ircp.cms.domain.system.setting.dto.SystemSettingResponse;
import kr.co.ircp.cms.domain.system.setting.entity.SystemSetting;
import kr.co.ircp.cms.domain.system.setting.exception.InvalidSettingValueException;
import kr.co.ircp.cms.domain.system.setting.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 시스템 설정 서비스 구현체.
 *
 * <p>REQ-SYSTEM-005-D — GET/PUT 단일 키, value_type 검증(STRING/INT/BOOL/JSON).
 * 변경(PUT)은 {@code @AuditLog(action="UPDATE")}로 감사 적재된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemSettingServiceImpl implements SystemSettingService {

    private final SystemSettingMapper settingMapper;
    private final ObjectMapper objectMapper;

    @Override
    public SystemSettingResponse get(String key) {
        return settingMapper.findByKey(key)
                .map(SystemSettingResponse::from)
                .orElseThrow(() -> new NoSuchElementException("시스템 설정을 찾을 수 없습니다. key=" + key));
    }

    @Override
    public List<SystemSettingResponse> listAll() {
        return settingMapper.findAll().stream()
                .map(SystemSettingResponse::from)
                .toList();
    }

    @Override
    @Transactional
    @AuditLog(action = "UPDATE", entityType = "SystemSetting", captureArgs = true)
    public SystemSettingResponse put(String key, SystemSettingRequest request) {
        // 기존 설정 조회 (valueType 확인용)
        SystemSetting existing = settingMapper.findByKey(key).orElse(null);
        String valueType = existing != null ? existing.getValueType() : "STRING";

        // value_type 검증
        validateValue(key, valueType, request.value());

        SystemSetting setting = SystemSetting.builder()
                .key(key)
                .value(request.value())
                .valueType(valueType)
                .description(request.description() != null
                        ? request.description()
                        : (existing != null ? existing.getDescription() : null))
                .build();
        settingMapper.upsert(setting);
        return settingMapper.findByKey(key)
                .map(SystemSettingResponse::from)
                .orElseThrow();
    }

    /**
     * value_type별 값 유효성 검증.
     *
     * <p>INT: Integer.parseInt, BOOL: true/false, JSON: Jackson 파싱 시도
     */
    private void validateValue(String key, String valueType, String value) {
        try {
            switch (valueType) {
                case "INT"  -> Integer.parseInt(value);
                case "BOOL" -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new InvalidSettingValueException(key, valueType, value);
                    }
                }
                case "JSON" -> objectMapper.readTree(value);
                // STRING: 제한 없음
                default -> { /* no-op */ }
            }
        } catch (InvalidSettingValueException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidSettingValueException(key, valueType, value);
        }
    }
}
