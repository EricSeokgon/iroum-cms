package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.entity.VerificationPurpose;
import org.springframework.stereotype.Service;

/**
 * SMS 채널 미구현 Placeholder.
 *
 * <p>Q-1 사용자 결정(2026-04-29): SMS 채널은 v0.4+에서 구현.
 * 현재 VerificationChannel.SMS 요청이 들어오면 서비스 계층에서
 * 이 구현체에 도달하기 전에 이미 예외를 던지지만, 방어 계층으로 배치한다.
 */
@Service
public class NoOpSmsProvider implements SmsProvider {

    @Override
    public void sendOtp(String phoneNumber, String code, VerificationPurpose purpose) {
        throw new UnsupportedOperationException(
            "SMS 채널은 v0.4+ 기능입니다 (Q-1 사용자 결정 2026-04-29). " +
            "현재는 EMAIL OTP만 지원됩니다."
        );
    }
}
