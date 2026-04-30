package kr.co.ircp.cms.domain.system.setting.service;

import kr.co.ircp.cms.domain.system.setting.dto.SystemSettingRequest;
import kr.co.ircp.cms.domain.system.setting.dto.SystemSettingResponse;

import java.util.List;

/**
 * 시스템 설정 서비스 인터페이스.
 * REQ-SYSTEM-005-D
 */
public interface SystemSettingService {

    SystemSettingResponse get(String key);

    List<SystemSettingResponse> listAll();

    SystemSettingResponse put(String key, SystemSettingRequest request);
}
