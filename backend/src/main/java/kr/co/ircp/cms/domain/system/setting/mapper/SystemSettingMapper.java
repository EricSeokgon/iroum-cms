package kr.co.ircp.cms.domain.system.setting.mapper;

import kr.co.ircp.cms.domain.system.setting.entity.SystemSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 시스템 설정 MyBatis Mapper.
 * REQ-SYSTEM-005-D
 */
@Mapper
public interface SystemSettingMapper {

    Optional<SystemSetting> findByKey(@Param("key") String key);

    List<SystemSetting> findAll();

    void upsert(SystemSetting setting);
}
