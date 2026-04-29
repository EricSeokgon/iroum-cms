package kr.co.ircp.cms.domain.audit.repository;

import kr.co.ircp.cms.domain.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 감사 로그 MyBatis Mapper.
 *
 * <p>SPEC-CMS-005 §4 — APPEND-ONLY 감사 로그 저장소.
 * SQL은 mybatis/mapper/audit/AuditLogMapper.xml에 정의.
 */
@Mapper
public interface AuditLogMapper {

    /**
     * 감사 로그 항목 삽입.
     *
     * <p>DB 트리거가 UPDATE/DELETE를 차단하므로 insert만 허용.
     *
     * @param entry 감사 로그 엔티티
     */
    void insert(AuditLog entry);
}
