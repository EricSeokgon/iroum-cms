package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.VerifyConfirmRequest;
import kr.co.ircp.cms.domain.auth.dto.VerifyConfirmResponse;
import kr.co.ircp.cms.domain.auth.dto.VerifyRequestRequest;
import kr.co.ircp.cms.domain.auth.dto.VerifyRequestResponse;
import kr.co.ircp.cms.domain.auth.entity.VerificationChannel;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * VerificationServiceImpl 단위 테스트.
 *
 * <p>REQ-AUTH-017-D-1,2 — OTP 요청/검증 핵심 흐름 검증.
 */
@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock
    private VerificationRequestMapper requestMapper;
    @Mock
    private VerificationHistoryMapper historyMapper;
    @Mock
    private PasswordPolicyService passwordPolicyService;
    @Mock
    private EmailService emailService;

    private VerificationServiceImpl sut;

    private static final String IP = "192.168.1.1";
    private static final String UA = "test-agent";
    private static final String EMAIL = "user@example.com";
    private static final String PURPOSE = "PASSWORD_RESET";

    @BeforeEach
    void setUp() {
        sut = new VerificationServiceImpl(requestMapper, historyMapper,
                passwordPolicyService, emailService);
    }

    @Test
    @DisplayName("신규 이메일 인증 요청 성공")
    void request_succeeds_forNewEmail() {
        // given
        VerifyRequestRequest req = new VerifyRequestRequest("EMAIL", EMAIL, PURPOSE);
        given(historyMapper.countRecentByIp(anyString(), any())).willReturn(0);
        given(requestMapper.findLatestActiveByTarget(anyString(), anyString(), any()))
            .willReturn(Optional.empty());
        given(passwordPolicyService.hash(anyString())).willReturn("$2a$12$hash");
        // DB가 INSERT 시 requestId를 채우는 동작을 모방
        org.mockito.Mockito.doAnswer(invocation -> {
            VerificationRequest vr = invocation.getArgument(0);
            vr.setRequestId(UUID.randomUUID());
            return null;
        }).when(requestMapper).insert(any());

        // when
        VerifyRequestResponse response = sut.request(req, IP, UA);

        // then
        assertThat(response).isNotNull();
        assertThat(response.cooldownSeconds()).isEqualTo(60L);
        then(requestMapper).should().insert(any());
        then(emailService).should().sendOtp(eq(EMAIL), anyString(), eq(VerificationPurpose.PASSWORD_RESET));
    }

    @Test
    @DisplayName("1분 이내 재요청 시 쿨다운 예외 발생")
    void request_throwsCooldown_whenWithin60s() {
        // given
        VerifyRequestRequest req = new VerifyRequestRequest("EMAIL", EMAIL, PURPOSE);
        given(historyMapper.countRecentByIp(anyString(), any())).willReturn(0);

        VerificationRequest recent = VerificationRequest.builder()
            .requestId(UUID.randomUUID())
            .status(VerificationStatus.PENDING)
            .createdAt(Instant.now().minusSeconds(30))  // 30초 전 = 쿨다운 내
            .expiresAt(Instant.now().plusSeconds(270))
            .build();
        given(requestMapper.findLatestActiveByTarget(anyString(), anyString(), any()))
            .willReturn(Optional.of(recent));

        // when & then
        assertThatThrownBy(() -> sut.request(req, IP, UA))
            .isInstanceOf(VerificationCooldownException.class);
        then(requestMapper).should(never()).insert(any());
    }

    @Test
    @DisplayName("시간당 10회 초과 시 IP 차단 예외 발생")
    void request_throwsIpBlocked_whenOver10perHour() {
        // given
        VerifyRequestRequest req = new VerifyRequestRequest("EMAIL", EMAIL, PURPOSE);
        given(historyMapper.countRecentByIp(anyString(), any())).willReturn(10);

        // when & then
        assertThatThrownBy(() -> sut.request(req, IP, UA))
            .isInstanceOf(VerificationIpBlockedException.class);
    }

    @Test
    @DisplayName("SMS 채널 요청 시 UnsupportedOperationException 발생 (Q-1)")
    void request_throwsNotImplemented_forSmsChannel() {
        // given
        VerifyRequestRequest req = new VerifyRequestRequest("SMS", "01012345678", PURPOSE);

        // when & then
        assertThatThrownBy(() -> sut.request(req, IP, UA))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("v0.4+");
    }

    @Test
    @DisplayName("올바른 코드 입력 시 verifiedToken 반환")
    void confirm_returnsToken_whenCorrect() {
        // given
        UUID requestId = UUID.randomUUID();
        String code = "123456";
        VerifyConfirmRequest req = new VerifyConfirmRequest(requestId.toString(), code);

        VerificationRequest vr = VerificationRequest.builder()
            .requestId(requestId)
            .channel(VerificationChannel.EMAIL)
            .target(EMAIL)
            .purpose(VerificationPurpose.PASSWORD_RESET)
            .codeHash("$2a$12$hash")
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .attempts(0)
            .maxAttempts(3)
            .status(VerificationStatus.PENDING)
            .userAgent(UA)
            .build();
        given(requestMapper.findByRequestId(requestId)).willReturn(Optional.of(vr));
        given(passwordPolicyService.matches(code, "$2a$12$hash")).willReturn(true);

        // when
        VerifyConfirmResponse response = sut.confirm(req, IP);

        // then
        assertThat(response.verifiedToken()).hasSize(64);
        assertThat(response.purpose()).isEqualTo("PASSWORD_RESET");
        then(requestMapper).should().markVerified(eq(requestId), anyString(), any());
    }

    @Test
    @DisplayName("잘못된 코드 입력 시 불일치 예외 발생 + 시도 횟수 증가")
    void confirm_throwsMismatch_whenWrongCode_andIncrementsAttempts() {
        // given
        UUID requestId = UUID.randomUUID();
        VerifyConfirmRequest req = new VerifyConfirmRequest(requestId.toString(), "999999");

        VerificationRequest vr = VerificationRequest.builder()
            .requestId(requestId)
            .channel(VerificationChannel.EMAIL)
            .target(EMAIL)
            .purpose(VerificationPurpose.PASSWORD_RESET)
            .codeHash("$2a$12$hash")
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .attempts(0)
            .maxAttempts(3)
            .status(VerificationStatus.PENDING)
            .userAgent(UA)
            .build();
        given(requestMapper.findByRequestId(requestId)).willReturn(Optional.of(vr));
        given(passwordPolicyService.matches("999999", "$2a$12$hash")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> sut.confirm(req, IP))
            .isInstanceOf(VerificationCodeMismatchException.class);
        then(requestMapper).should().incrementAttempts(requestId);
    }

    @Test
    @DisplayName("3회 시도 초과 시 FAILED 처리 + 예외 발생")
    void confirm_throwsAttemptExceeded_after3Failures() {
        // given
        UUID requestId = UUID.randomUUID();
        VerifyConfirmRequest req = new VerifyConfirmRequest(requestId.toString(), "111111");

        VerificationRequest vr = VerificationRequest.builder()
            .requestId(requestId)
            .status(VerificationStatus.PENDING)
            .expiresAt(Instant.now().plusSeconds(300))
            .attempts(3)   // 이미 3회 시도
            .maxAttempts(3)
            .codeHash("$2a$12$hash")
            .channel(VerificationChannel.EMAIL)
            .purpose(VerificationPurpose.PASSWORD_RESET)
            .target(EMAIL)
            .userAgent(UA)
            .build();
        given(requestMapper.findByRequestId(requestId)).willReturn(Optional.of(vr));

        // when & then
        assertThatThrownBy(() -> sut.confirm(req, IP))
            .isInstanceOf(VerificationAttemptExceededException.class);
        then(requestMapper).should().markFailed(eq(requestId), any());
    }

    @Test
    @DisplayName("만료된 요청 confirm 시 EXPIRED 예외 발생")
    void confirm_throwsExpired_whenPastExp() {
        // given
        UUID requestId = UUID.randomUUID();
        VerifyConfirmRequest req = new VerifyConfirmRequest(requestId.toString(), "123456");

        VerificationRequest vr = VerificationRequest.builder()
            .requestId(requestId)
            .status(VerificationStatus.PENDING)
            .expiresAt(Instant.now().minusSeconds(1))  // 만료됨
            .attempts(0)
            .maxAttempts(3)
            .codeHash("$2a$12$hash")
            .channel(VerificationChannel.EMAIL)
            .purpose(VerificationPurpose.PASSWORD_RESET)
            .target(EMAIL)
            .userAgent(UA)
            .build();
        given(requestMapper.findByRequestId(requestId)).willReturn(Optional.of(vr));

        // when & then
        assertThatThrownBy(() -> sut.confirm(req, IP))
            .isInstanceOf(VerificationExpiredException.class);
    }

    @Test
    @DisplayName("validateVerifiedToken — 유효한 토큰 + 올바른 purpose → 요청 반환")
    void validateVerifiedToken_returnsRequest_whenValid() {
        // given
        String token = "a".repeat(64);
        VerificationRequest vr = VerificationRequest.builder()
            .status(VerificationStatus.VERIFIED)
            .verifiedAt(Instant.now().minusSeconds(10))
            .purpose(VerificationPurpose.PASSWORD_RESET)
            .target(EMAIL)
            .build();
        given(requestMapper.findByVerifiedToken(token)).willReturn(Optional.of(vr));

        // when
        Optional<VerificationRequest> result =
            sut.validateVerifiedToken(token, VerificationPurpose.PASSWORD_RESET);

        // then
        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("validateVerifiedToken — purpose 불일치 → 빈 Optional 반환")
    void validateVerifiedToken_returnsEmpty_whenWrongPurpose() {
        // given
        String token = "a".repeat(64);
        VerificationRequest vr = VerificationRequest.builder()
            .status(VerificationStatus.VERIFIED)
            .verifiedAt(Instant.now().minusSeconds(10))
            .purpose(VerificationPurpose.SIGNUP)  // 다른 목적
            .target(EMAIL)
            .build();
        given(requestMapper.findByVerifiedToken(token)).willReturn(Optional.of(vr));

        // when
        Optional<VerificationRequest> result =
            sut.validateVerifiedToken(token, VerificationPurpose.PASSWORD_RESET);

        // then
        assertThat(result).isEmpty();
    }
}
