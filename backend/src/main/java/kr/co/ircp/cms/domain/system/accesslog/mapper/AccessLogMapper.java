package kr.co.ircp.cms.domain.system.accesslog.mapper;

import kr.co.ircp.cms.domain.system.accesslog.dto.AccessLogSearchRequest;
import kr.co.ircp.cms.domain.system.accesslog.entity.AccessLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 접속 로그 MyBatis Mapper.
 * REQ-SYSTEM-001-D
 */
@Mapper
public interface AccessLogMapper {

    void insert(AccessLog accessLog);

    List<AccessLog> findBySearch(@Param("req") AccessLogSearchRequest req);

    long countBySearch(@Param("req") AccessLogSearchRequest req);
}
