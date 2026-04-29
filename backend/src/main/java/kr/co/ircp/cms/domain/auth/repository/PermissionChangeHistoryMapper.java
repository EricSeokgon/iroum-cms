package kr.co.ircp.cms.domain.auth.repository;

import kr.co.ircp.cms.domain.auth.dto.PermissionChangeEntry;
import kr.co.ircp.cms.domain.auth.entity.PermissionChangeHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 권한 변경 이력 MyBatis 매퍼.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-016-D-1 — APPEND-ONLY 이력 적재 + 페이징 조회.
 */
@Mapper
public interface PermissionChangeHistoryMapper {

    /**
     * 권한 변경 이력 단건 삽입.
     *
     * @param entry 삽입할 이력 엔티티
     */
    void insert(PermissionChangeHistory entry);

    /**
     * 전체 이력 페이징 조회 (관리자용, 동적 필터).
     *
     * @param offset       조회 시작 행
     * @param limit        최대 행 수
     * @param targetUserId 대상 사용자 필터 (null 시 전체)
     * @param changeType   변경 유형 필터 (null 시 전체)
     * @param changedBy    변경 수행자 필터 (null 시 전체)
     * @param from         시작 시각 필터 (null 시 전체)
     * @param to           종료 시각 필터 (null 시 전체)
     * @param sort         정렬 (예: changedAt,desc)
     * @return 이력 목록
     */
    List<PermissionChangeEntry> findPage(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("targetUserId") Long targetUserId,
            @Param("changeType") String changeType,
            @Param("changedBy") Long changedBy,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("sort") String sort
    );

    /**
     * 전체 이력 건수 (필터 적용).
     */
    long countAll(
            @Param("targetUserId") Long targetUserId,
            @Param("changeType") String changeType,
            @Param("changedBy") Long changedBy,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * 특정 사용자의 이력 페이징 조회.
     *
     * @param userId 대상 사용자 ID
     * @param offset 조회 시작 행
     * @param limit  최대 행 수
     * @return 이력 목록
     */
    List<PermissionChangeEntry> findByTargetUser(
            @Param("userId") long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * 특정 사용자의 이력 건수.
     *
     * @param userId 대상 사용자 ID
     */
    long countByTargetUser(@Param("userId") long userId);
}
