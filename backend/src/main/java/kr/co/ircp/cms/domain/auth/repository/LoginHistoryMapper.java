package kr.co.ircp.cms.domain.auth.repository;

import kr.co.ircp.cms.domain.auth.dto.LoginHistoryEntry;
import kr.co.ircp.cms.domain.auth.entity.LoginHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 로그인 이력 MyBatis Mapper.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-011 — 성공/실패 로그인 이력 기록 및 조회.
 * SQL은 mybatis/mapper/auth/LoginHistoryMapper.xml에 정의.
 */
// @MX:WARN: [AUTO] LoginHistoryMapper.findPage — sort 파라미터 화이트리스트로 SQL 인젝션 방지
// @MX:REASON: 동적 ORDER BY는 파라미터 바인딩 불가; XML 내 &lt;choose&gt; 화이트리스트로만 허용된 컬럼만 사용
@Mapper
public interface LoginHistoryMapper {

    /**
     * 로그인 이력 삽입.
     *
     * <p>성공/실패 무관하게 모든 로그인 시도를 기록한다.
     */
    void insert(LoginHistory history);

    /**
     * 특정 사용자의 최근 이력 조회.
     *
     * <p>관리자 조회 또는 연속 실패 분석에 활용.
     */
    List<LoginHistory> findRecentByUsername(
            @Param("username") String username,
            @Param("limit") int limit);

    /**
     * 관리자용 전체 이력 페이징 조회.
     *
     * <p>모든 파라미터는 null 허용 — null 이면 해당 조건 제외.
     * sort 는 화이트리스트 값만 허용 (SQL 인젝션 방지).
     */
    List<LoginHistoryEntry> findPage(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("success") Boolean success,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("ipAddress") String ipAddress,
            @Param("sort") String sort);

    /**
     * 관리자용 전체 이력 건수 조회.
     */
    long countAll(
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("success") Boolean success,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("ipAddress") String ipAddress);

    /**
     * 특정 사용자 본인 이력 페이징 조회.
     */
    List<LoginHistoryEntry> findByUserId(
            @Param("userId") long userId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 특정 사용자 이력 전체 건수.
     */
    long countByUserId(@Param("userId") long userId);
}
