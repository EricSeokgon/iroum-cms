package kr.co.ircp.cms.domain.audit.repository;

import kr.co.ircp.cms.domain.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;

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
     * action/severity는 다중 값 IN 필터; null이거나 빈 리스트인 조건은 무시.
     */
    List<AuditLog> search(
            @Param("action") List<String> action,
            @Param("entityType") String entityType,
            @Param("severity") List<String> severity,
            @Param("result") String result,
            @Param("actorId") Long actorId,
            @Param("fromTime") String fromTime,
            @Param("toTime") String toTime,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /** 검색 결과 건수 */
    long countSearch(
            @Param("action") List<String> action,
            @Param("entityType") String entityType,
            @Param("severity") List<String> severity,
            @Param("result") String result,
            @Param("actorId") Long actorId,
            @Param("fromTime") String fromTime,
            @Param("toTime") String toTime
    );

    /** severity=CRITICAL 최신 N건 */
    List<AuditLog> findCritical(@Param("limit") int limit);

    /**
     * CSV 내보내기용 커서 기반 스트리밍 조회.
     *
     * <p>대용량 결과를 메모리에 적재하지 않고, MyBatis ResultHandler를 통해
     * 행 단위로 콜백 처리한다. XML 매퍼에서 fetchSize=1000, FORWARD_ONLY 커서를
     * 사용하여 JDBC 드라이버가 청크 단위로 행을 가져오도록 한다.
     *
     * <p>search()와 동일한 동적 필터를 사용하나 LIMIT/OFFSET 없이 전체 매칭 행을
     * event_time ASC 순으로 흘려보낸다.
     */
    void searchForExport(
            @Param("action") List<String> action,
            @Param("entityType") String entityType,
            @Param("severity") List<String> severity,
            @Param("result") String result,
            @Param("actorId") Long actorId,
            @Param("fromTime") String fromTime,
            @Param("toTime") String toTime,
            ResultHandler<AuditLog> handler
    );

    /** 최근 24시간 감사 로그 건수 */
    long countLast24h();

    /** 최근 24시간 CRITICAL 감사 로그 건수 */
    long countCriticalLast24h();
}
