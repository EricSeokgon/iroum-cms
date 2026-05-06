package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.VerifyConfirmRequest;
import kr.co.ircp.cms.domain.auth.dto.VerifyConfirmResponse;
import kr.co.ircp.cms.domain.auth.dto.VerifyRequestRequest;
import kr.co.ircp.cms.domain.auth.dto.VerifyRequestResponse;
import kr.co.ircp.cms.domain.auth.entity.VerificationChannel;
import kr.co.ircp.cms.domain.auth.entity.VerificationHistory;
import kr.co.ircp.cms.domain.auth.entity.VerificationPurpose;
import kr.co.ircp.cms.domain.auth.entity.VerificationRequest;
import kr.co.ircp.cms.domain.auth.entity.VerificationStatus;
import kr.co.ircp.cms.domain.auth.exception.VerificationAttemptExceededException;
import kr.co.ircp.cms.domain.auth.exception.VerificationCodeMismatchException;
import kr.co.ircp.cms.domain.auth.exception.VerificationCooldownException;
import kr.co.ircp.cms.domain.auth.exception.VerificationExpiredException;
import kr.co.ircp.cms.domain.auth.exception.VerificationIpBlockedException;
import kr.co.ircp.cms.domain.auth.repository.VerificationHistoryMapper;
import kr.co.ircp.cms.domain.auth.repository.VerificationRequestMapper;
import kr.co.ircp.cms.domain.auth.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * 본인인증 서비스 구현체.
 *
 * <p>REQ-AUTH-017-D-1,2 — OTP 발송/검증 통합 흐름.
 * 쿨다운(1분), IP 차단(시간당 10회), 최대 시도 횟수(3회), 만료(5분) 정책을 모두 적용한다.
 */
// @MX:WARN: [AUTO] VerificationServiceImpl — 보안 핵심 로직: IP 차단/쿨다운/시도 횟수 다중 분기
// @MX:REASON: 각 체크를 우회하면 OTP brute-force/enumeration 공격 가능. cyclomatic complexity 높음
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationServiceImpl implements VerificationService {

    /** OTP 만료 시간 (초) */
    private static final long OTP_EXPIRES_SECONDS = 300L;
    /** 쿨다운 시간 (초) */
    private static final long COOLDOWN_SECONDS = 60L;
    /** IP 차단 임계값 (시간당) */
    private static final int IP_BLOCK_THRESHOLD = 10;
    /** IP 차단 집계 윈도우 (초) */
    private static final long IP_WINDOW_SECONDS = 3600L;
    /** verifiedToken 유효 시간 (초, OTP 만료와 동일) */
    private static final long VERIFIED_TOKEN_TTL_SECONDS = 300L;

    private final VerificationRequestMapper requestMapper;
    private final VerificationHistoryMapper historyMapper;
    private final PasswordPolicyService passwordPolicyService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * OTP 발송 요청.
     *
     * <p>처리 순서:
     * 1. 채널 유효성 검사 (EMAIL만 허용)
     * 2. purpose 유효성 검사
     * 3. IP 부정 시도 차단 (시간당 10회 초과 → 423)
     * 4. 쿨다운 검사 (동일 target+purpose 1분 이내 → 429)
     * 5. OTP 생성(6자리) + BCrypt 해시
     * 6. DB 적재 + 이메일 비동기 발송
     * 7. 이력 기록
     */
    @Override
    @Transactional
    public VerifyRequestResponse request(VerifyRequestRequest req, String ipAddress, String userAgent) {
        Instant now = Instant.now();

        // 1. 채널 검증 — SMS는 v0.4+
        VerificationChannel channel;
        // SMS 채널은 v0.4+ 기능 (Q-1 2026-04-29) - enum에 SMS가 없으므로 별도 체크
        if ("SMS".equalsIgnoreCase(req.channel())) {
            throw new UnsupportedOperationException(
                "SMS 채널은 v0.4+ 기능입니다 (Q-1 사용자 결정 2026-04-29).");
        }
        try {
            channel = VerificationChannel.valueOf(req.channel().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException(
                "알 수 없는 채널: " + req.channel() + ". 현재 지원 채널: EMAIL");
        }
        if (channel != VerificationChannel.EMAIL) {
            throw new UnsupportedOperationException(
                "SMS 채널은 v0.4+ 기능입니다 (Q-1 사용자 결정 2026-04-29).");
        }

        // 2. purpose 검증
        VerificationPurpose purpose;
        try {
            purpose = VerificationPurpose.valueOf(req.purpose().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 purpose: " + req.purpose());
        }

        // 3. IP 차단 검사
        String ipHash = HashUtil.sha256Hex(ipAddress);
        Instant windowStart = now.minusSeconds(IP_WINDOW_SECONDS);
        int recentCount = historyMapper.countRecentByIp(ipHash, windowStart);
        if (recentCount >= IP_BLOCK_THRESHOLD) {
            log.warn("IP 차단: ipHash={}, count={}", ipHash, recentCount);
            throw new VerificationIpBlockedException();
        }

        // 4. 쿨다운 검사 (동일 target+purpose PENDING 요청이 1분 이내에 존재)
        Optional<VerificationRequest> latestOpt =
            requestMapper.findLatestActiveByTarget(req.target(), req.purpose(), now);
        if (latestOpt.isPresent()) {
            VerificationRequest latest = latestOpt.get();
            long elapsedSeconds = now.getEpochSecond() - latest.getCreatedAt().getEpochSecond();
            if (elapsedSeconds < COOLDOWN_SECONDS) {
                long retryAfter = COOLDOWN_SECONDS - elapsedSeconds;
                throw new VerificationCooldownException(retryAfter);
            }
        }

        // 5. OTP 생성 (6자리 숫자)
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        String codeHash = passwordPolicyService.hash(code);

        // 6. DB 적재
        Instant expiresAt = now.plusSeconds(OTP_EXPIRES_SECONDS);
        VerificationRequest entity = VerificationRequest.builder()
            .channel(channel)
            .target(req.target())
            .purpose(purpose)
            .codeHash(codeHash)
            .createdAt(now)
            .expiresAt(expiresAt)
            .maxAttempts(3)
            .status(VerificationStatus.PENDING)
            .requesterIpHash(ipHash)
            .userAgent(userAgent)
            .build();
        requestMapper.insert(entity);

        // 7. 이메일 비동기 발송 (실패해도 예외 미전파 — EmailServiceImpl 참고)
        emailService.sendOtp(req.target(), code, purpose);

        // 8. 이력 기록 (성공)
        historyMapper.insert(VerificationHistory.builder()
            .target(req.target())
            .channel(channel.name())
            .purpose(purpose.name())
            .success(true)
            .requesterIpHash(ipHash)
            .userAgent(userAgent)
            .occurredAt(now)
            .build());

        log.debug("OTP 요청 완료: requestId={}, target={}", entity.getRequestId(), req.target());
        return new VerifyRequestResponse(
            entity.getRequestId().toString(),
            expiresAt,
            COOLDOWN_SECONDS
        );
    }

    /**
     * OTP 코드 검증.
     *
     * <p>처리 순서:
     * 1. requestId로 요청 조회 (미존재 → 403)
     * 2. status != PENDING → 403
     * 3. 만료 확인 → EXPIRED + 403
     * 4. 시도 횟수 초과 → FAILED + 403
     * 5. BCrypt 코드 검증 → 불일치: incrementAttempts + 이력 + 401
     * 6. 성공: markVerified + verifiedToken 발급 + 이력 + 200
     */
    @Override
    @Transactional
    public VerifyConfirmResponse confirm(VerifyConfirmRequest req, String ipAddress) {
        Instant now = Instant.now();
        String ipHash = HashUtil.sha256Hex(ipAddress);

        UUID requestId = UUID.fromString(req.requestId());
        VerificationRequest vr = requestMapper.findByRequestId(requestId)
            .orElseThrow(VerificationExpiredException::new);

        // 2. 상태 확인 (PENDING 외는 403)
        if (vr.getStatus() != VerificationStatus.PENDING) {
            throw new VerificationExpiredException();
        }

        // 3. 만료 확인
        if (now.isAfter(vr.getExpiresAt())) {
            requestMapper.markFailed(requestId, now);
            recordHistory(vr, ipHash, false, "EXPIRED");
            throw new VerificationExpiredException();
        }

        // 4. 시도 횟수 초과 확인
        if (vr.getAttempts() >= vr.getMaxAttempts()) {
            requestMapper.markFailed(requestId, now);
            recordHistory(vr, ipHash, false, "ATTEMPT_EXCEEDED");
            throw new VerificationAttemptExceededException();
        }

        // 5. 코드 검증
        if (!passwordPolicyService.matches(req.code(), vr.getCodeHash())) {
            requestMapper.incrementAttempts(requestId);
            recordHistory(vr, ipHash, false, "CODE_MISMATCH");
            throw new VerificationCodeMismatchException();
        }

        // 6. 성공 처리 — verifiedToken(64자 random hex) 발급
        String verifiedToken = generateVerifiedToken();
        requestMapper.markVerified(requestId, verifiedToken, now);
        recordHistory(vr, ipHash, true, null);

        log.debug("OTP 검증 성공: requestId={}, purpose={}", requestId, vr.getPurpose());
        return new VerifyConfirmResponse(verifiedToken, vr.getPurpose().name());
    }

    /**
     * verifiedToken 유효성 검증.
     *
     * <p>VERIFIED 상태 + 발급 후 5분 이내 + purpose 일치 조건을 모두 확인한다.
     */
    @Override
    public Optional<VerificationRequest> validateVerifiedToken(
            String token, VerificationPurpose expectedPurpose) {
        return requestMapper.findByVerifiedToken(token)
            .filter(vr -> vr.getStatus() == VerificationStatus.VERIFIED)
            .filter(vr -> vr.getVerifiedAt() != null
                && Instant.now().isBefore(vr.getVerifiedAt().plusSeconds(VERIFIED_TOKEN_TTL_SECONDS)))
            .filter(vr -> vr.getPurpose() == expectedPurpose);
    }

    // ─── 내부 헬퍼 ────────────────────────────────────────────────────────────

    private void recordHistory(VerificationRequest vr, String ipHash,
            boolean success, String failureReason) {
        historyMapper.insert(VerificationHistory.builder()
            .target(vr.getTarget())
            .channel(vr.getChannel().name())
            .purpose(vr.getPurpose().name())
            .success(success)
            .failureReason(failureReason)
            .requesterIpHash(ipHash)
            .userAgent(vr.getUserAgent())
            .occurredAt(Instant.now())
            .build());
    }

    /** 64자 random hex verifiedToken 생성 */
    private String generateVerifiedToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
