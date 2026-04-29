package kr.co.ircp.cms.domain.auth.repository;

import kr.co.ircp.cms.domain.auth.entity.VerificationRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 본인인증 요청 MyBatis Mapper.
 *
 * <p>REQ-AUTH-017-D-1,2 — verification_request 테이블 접근.
 * SQL은 mybatis/mapper/auth/VerificationRequestMapper.xml에 정의.
 */
// @MX:ANCHOR: [AUTO] VerificationRequestMapper — 본인인증 핵심 DB 접근점 (fan_in >= 3)
// @MX:REASON: VerificationServiceImpl.request/confirm/validateVerifiedToken 및 테스트에서 참조
@Mapper
public interface VerificationRequestMapper {

    /**
     * 인증 요청 삽입.
     *
     * <p>request_id는 DB DEFAULT gen_random_uuid()로 자동 생성 후 엔티티에 채워진다.
     */
    void insert(VerificationRequest req);

    /**
     * requestId(UUID)로 단건 조회.
     */
    Optional<VerificationRequest> findByRequestId(@Param("requestId") UUID requestId);

    /**
     * verifiedToken으로 단건 조회.
     */
    Optional<VerificationRequest> findByVerifiedToken(@Param("token") String token);

    /**
     * 시도 횟수 1 증가.
     */
    void incrementAttempts(@Param("requestId") UUID requestId);

    /**
     * VERIFIED 상태로 전환 + verifiedToken 설정.
     */
    void markVerified(
            @Param("requestId") UUID requestId,
            @Param("verifiedToken") String verifiedToken,
            @Param("now") Instant now);

    /**
     * FAILED 상태로 전환 (최대 시도 횟수 초과).
     */
    void markFailed(@Param("requestId") UUID requestId, @Param("now") Instant now);

    /**
     * 동일 target+purpose의 가장 최근 PENDING 요청 조회 (쿨다운 검사용).
     */
    Optional<VerificationRequest> findLatestActiveByTarget(
            @Param("target") String target,
            @Param("purpose") String purpose,
            @Param("now") Instant now);
}
