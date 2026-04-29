package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.VerifyConfirmRequest;
import kr.co.ircp.cms.domain.auth.dto.VerifyConfirmResponse;
import kr.co.ircp.cms.domain.auth.dto.VerifyRequestRequest;
import kr.co.ircp.cms.domain.auth.dto.VerifyRequestResponse;
import kr.co.ircp.cms.domain.auth.entity.VerificationPurpose;
import kr.co.ircp.cms.domain.auth.entity.VerificationRequest;

import java.util.Optional;

/**
 * 본인인증 서비스 인터페이스.
 *
 * <p>REQ-AUTH-017-D-1,2,3 — OTP 요청/검증 및 verifiedToken 검증.
 */
// @MX:ANCHOR: [AUTO] VerificationService.request — OTP 발송의 진입점 (fan_in >= 3)
// @MX:REASON: AuthController, AuthServiceImpl(reset-request), 테스트에서 참조
public interface VerificationService {

    /**
     * 본인인증 OTP 발송 요청.
     *
     * @param req        요청 DTO (channel, target, purpose)
     * @param ipAddress  요청자 IP (IP 차단 판단)
     * @param userAgent  요청자 User-Agent
     * @return 요청 ID, 만료 시각, 쿨다운 초
     */
    VerifyRequestResponse request(VerifyRequestRequest req, String ipAddress, String userAgent);

    /**
     * OTP 코드 검증.
     *
     * @param req        요청 DTO (requestId, code)
     * @param ipAddress  요청자 IP (이력 기록용)
     * @return verifiedToken과 purpose
     */
    // @MX:ANCHOR: [AUTO] VerificationService.confirm — OTP 검증의 진입점 (fan_in >= 3)
    // @MX:REASON: AuthController, AuthServiceImpl, 테스트에서 참조
    VerifyConfirmResponse confirm(VerifyConfirmRequest req, String ipAddress);

    /**
     * verifiedToken 유효성 검증.
     *
     * <p>비밀번호 재설정 등 후속 작업에서 verifiedToken의 유효성과 목적을 확인한다.
     * 만료(5분 초과) 또는 목적 불일치 시 빈 Optional 반환.
     *
     * @param token           verifiedToken 문자열
     * @param expectedPurpose 기대하는 인증 목적
     * @return 유효한 VerificationRequest (빈 경우 토큰 무효)
     */
    Optional<VerificationRequest> validateVerifiedToken(String token, VerificationPurpose expectedPurpose);
}
