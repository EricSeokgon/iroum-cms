package kr.co.ircp.cms.domain.auth.repository;

import kr.co.ircp.cms.domain.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * 사용자 MyBatis Mapper.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001~006 — users 테이블 접근.
 * SQL은 mybatis/mapper/auth/UserMapper.xml에 정의.
 */
// @MX:ANCHOR: [AUTO] UserMapper — 인증·계정 관리의 핵심 DB 접근 계층
// @MX:REASON: login, refresh, lockAccount 등 3개 이상 서비스에서 참조 (fan_in >= 3)
@Mapper
public interface UserMapper {

    /**
     * username으로 사용자 조회.
     *
     * <p>로그인 시 자격증명 검증에 사용. deleted_at IS NULL 조건 포함.
     */
    Optional<User> findByUsername(String username);

    /**
     * 연속 로그인 실패 횟수 증가.
     *
     * <p>REQ-AUTH-005 — 실패 1회당 fail_count += 1, updated_at 갱신.
     */
    void incrementFailCount(@Param("username") String username, @Param("now") Instant now);

    /**
     * 로그인 성공 시 실패 횟수 초기화.
     *
     * <p>REQ-AUTH-005 — fail_count=0, last_login_at=now, updated_at=now 갱신.
     */
    void resetFailCount(@Param("username") String username, @Param("now") Instant now);

    /**
     * 계정 잠금 처리.
     *
     * <p>REQ-AUTH-005 — status='LOCKED', locked_until=until, updated_at=now 갱신.
     */
    void lockAccount(@Param("username") String username, @Param("until") Instant until);

    /**
     * 마지막 로그인 시각 갱신.
     *
     * <p>REQ-AUTH-001 — 로그인 성공 시 last_login_at=now, updated_at=now.
     */
    void updateLastLoginAt(@Param("id") long id, @Param("now") Instant now);
}
