package kr.co.ircp.cms.domain.audit.repository;

import kr.co.ircp.cms.domain.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 감사 로그 MyBatis Mapper.
 *
 * <p>SPEC-CMS-005 §4 — APPEND-ONLY 감사 로그 저장소.
 * SQL은 mybatis/mapper/audit/AuditLogMapper.xml에 정의.
 * 조회 메서드는 ROLE_ADMIN 전용 감사 대시보드에서만 사용.
 */
@Mapper
public interface AuditLogMapper {

    /**
     * 감사 로그 항목 삽입.
     * DB 트리거가 UPDATE/DELETE를 차단하므로 insert만 허용.
     */
    void insert(AuditLog entry);

    /** id로 단건 조회 */
    Optional<AuditLog> findById(@Param("id") Long id);

    /**
     * 조건 검색 (동적 WHERE).
     * action/entityType/severity/actorId/fromTime/toTime 중 null인 조건은 무시.
     */
    List<AuditLog> search(
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("severity") String severity,
            @Param("actorId") Long actorId,
            @Param("fromTime") String fromTime,
            @Param("toTime") String toTime,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /** 검색 결과 건수 */
    long countSearch(
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("severity") String severity,
            @Param("actorId") Long actorId,
            @Param("fromTime") String fromTime,
            @Param("toTime") String toTime
    );

    /** severity=CRITICAL 최신 N건 */
    List<AuditLog> findCritical(@Param("limit") int limit);

    /** 최근 24시간 감사 로그 건수 */
    long countLast24h();

    /** 최근 24시간 CRITICAL 감사 로그 건수 */
    long countCriticalLast24h();
}
