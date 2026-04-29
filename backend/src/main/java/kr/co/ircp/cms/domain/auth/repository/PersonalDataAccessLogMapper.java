package kr.co.ircp.cms.domain.auth.repository;

import kr.co.ircp.cms.domain.auth.dto.PersonalDataAccessEntry;
import kr.co.ircp.cms.domain.auth.entity.PersonalDataAccessLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 개인정보 접근 로그 MyBatis Mapper.
 *
 * <p>REQ-AUTH-018-D-1~4 — personal_data_access_log 테이블의 APPEND-ONLY 삽입 및 조회.
 * DB 트리거로 UPDATE/DELETE가 차단되므로 insert 외 변경 메서드는 존재하지 않는다.
 */
// @MX:ANCHOR: [AUTO] PersonalDataAccessLogMapper — 개인정보 접근 로그 저장소 계약
// @MX:REASON: PersonalDataAccessLogServiceImpl, PersonalDataAccessController, MyPersonalDataAccessController 참조 (fan_in >= 3)
@Mapper
public interface PersonalDataAccessLogMapper {

    /**
     * 개인정보 접근 로그를 삽입한다 (APPEND-ONLY).
     *
     * <p>DB 트리거로 UPDATE/DELETE가 차단되므로 삽입 전 검증 불요.
     */
    void insert(PersonalDataAccessLog log);

    /**
     * 조건 기반 페이징 조회 (관리자용).
     *
     * <p>REQ-AUTH-018-D-2 — targetUserId, viewerId, purpose, from, to 조건을 동적으로 조합한다.
     */
    List<PersonalDataAccessEntry> findPage(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("targetUserId") Long targetUserId,
            @Param("viewerId") Long viewerId,
            @Param("purpose") String purpose,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("sort") String sort
    );

    /**
     * 전체 건수 (조건 동일).
     */
    long countAll(
            @Param("targetUserId") Long targetUserId,
            @Param("viewerId") Long viewerId,
            @Param("purpose") String purpose,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * 특정 피열람자 기준 페이징 조회 (본인 열람 이력).
     *
     * <p>REQ-AUTH-018-D-4 — 본인만 자신에 대한 열람 이력을 조회할 수 있다.
     */
    List<PersonalDataAccessEntry> findByTarget(
            @Param("targetUserId") long targetUserId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * 특정 피열람자 기준 전체 건수.
     */
    long countByTarget(@Param("targetUserId") long targetUserId);
}
