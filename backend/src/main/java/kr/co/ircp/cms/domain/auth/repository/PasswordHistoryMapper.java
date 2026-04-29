package kr.co.ircp.cms.domain.auth.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 비밀번호 이력 MyBatis Mapper.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-010 — 직전 5개 비밀번호 재사용 금지 정책 지원.
 * SQL은 mybatis/mapper/auth/PasswordHistoryMapper.xml에 정의.
 */
// @MX:WARN: [AUTO] findRecentHashes — limit 파라미터로 5개 고정; 정책 변경 시 호출부 수정 필요
// @MX:REASON: 재사용 금지 개수(5)는 SPEC-CMS-002 §13.B에 명시된 비즈니스 규칙; DB 쿼리 부담도 고려
@Mapper
public interface PasswordHistoryMapper {

    /**
     * 비밀번호 변경 이력 신규 삽입.
     *
     * <p>비밀번호 변경 성공 시 새 해시를 이력에 기록.
     *
     * @param userId       사용자 PK
     * @param passwordHash BCrypt 해시 (평문 아님)
     * @param changedAt    변경 시각
     */
    void insert(
            @Param("userId") long userId,
            @Param("passwordHash") String passwordHash,
            @Param("changedAt") Instant changedAt);

    /**
     * 최근 N개 비밀번호 해시 조회 (changed_at DESC).
     *
     * <p>REQ-AUTH-010 — 재사용 금지 검사 시 호출.
     * 반환 순서: 최신 변경 순.
     *
     * @param userId 사용자 PK
     * @param limit  조회할 최대 개수 (기본값 5)
     * @return 해시 문자열 목록 (최신 순)
     */
    List<String> findRecentHashes(
            @Param("userId") long userId,
            @Param("limit") int limit);
}
