package kr.co.ircp.cms.domain.auth.repository;

import kr.co.ircp.cms.domain.auth.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Refresh Token MyBatis Mapper.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-002 — Refresh Token Rotation 지원.
 * SQL은 mybatis/mapper/auth/RefreshTokenMapper.xml에 정의.
 */
@Mapper
public interface RefreshTokenMapper {

    /**
     * 새 Refresh Token 삽입.
     *
     * <p>로그인 성공 또는 토큰 갱신(rotation) 시 호출.
     */
    void insert(RefreshToken token);

    /**
     * 토큰 해시로 Refresh Token 조회.
     *
     * <p>갱신 요청 수신 시 유효성 검증에 사용.
     */
    Optional<RefreshToken> findByTokenHash(String hash);

    /**
     * 특정 토큰 회수 (단건 revoke).
     *
     * <p>REQ-AUTH-002 — 사용된 Refresh Token은 즉시 회수.
     */
    void revoke(@Param("hash") String hash, @Param("revokedAt") Instant when);

    /**
     * 사용자의 모든 유효 Refresh Token 회수.
     *
     * <p>REQ-AUTH-002 — Token Reuse 탐지 시 / REQ-AUTH-003 로그아웃 시 / REQ-AUTH-012 강제 로그아웃.
     */
    void revokeAllForUser(@Param("userId") long userId, @Param("revokedAt") Instant when);

    /**
     * 만료된 Refresh Token GC.
     *
     * <p>스케줄러에 의해 주기적으로 호출 (Step 2+에서 구현).
     *
     * @return 삭제된 행 수
     */
    int deleteExpired(@Param("now") Instant now);
}
