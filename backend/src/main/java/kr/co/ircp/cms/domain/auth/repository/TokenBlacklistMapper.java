package kr.co.ircp.cms.domain.auth.repository;

import kr.co.ircp.cms.domain.auth.entity.TokenBlacklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

/**
 * Access Token 블랙리스트 MyBatis Mapper.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-003 — 로그아웃된 Access Token의 재사용 방지.
 * SQL은 mybatis/mapper/auth/TokenBlacklistMapper.xml에 정의.
 */
@Mapper
public interface TokenBlacklistMapper {

    /**
     * 블랙리스트에 토큰 해시 등록.
     *
     * <p>로그아웃 시 해당 Access Token의 SHA-256 해시를 등록.
     */
    void insert(TokenBlacklist entry);

    /**
     * 토큰 해시 블랙리스트 존재 여부 확인.
     *
     * <p>모든 인증 요청 처리 시 호출 — 성능에 민감하므로 캐시 레이어 검토 필요 (Step 3+).
     */
    boolean exists(@Param("hash") String hash);

    /**
     * 만료된 블랙리스트 항목 GC.
     *
     * <p>expires_at <= now 인 항목을 삭제한다.
     *
     * @return 삭제된 행 수
     */
    int deleteExpired(@Param("now") Instant now);
}
