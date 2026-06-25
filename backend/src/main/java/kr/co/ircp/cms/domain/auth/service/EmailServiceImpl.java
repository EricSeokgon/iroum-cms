package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.entity.VerificationPurpose;
import kr.co.ircp.cms.domain.email.template.admin.dto.RenderResult;
import kr.co.ircp.cms.domain.email.template.admin.service.EmailTemplateResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 이메일 발송 서비스 구현체.
 *
 * <p>REQ-AUTH-017-D-1 — Spring Mail(JavaMailSender) 기반 OTP 이메일 발송.
 * 모든 발송은 @Async(auditExecutor)로 비동기 처리되며, 발송 실패는 로깅만 한다
 * (사용자 플로우를 차단하지 않음 — 보안).
 */
// @MX:WARN: [AUTO] EmailServiceImpl — @Async 비동기 발송으로 실패 시 예외가 호출자에게 전파되지 않음
// @MX:REASON: 이메일 발송 실패는 로그로만 기록됨. 운영 SMTP 장애 시 OTP 미발송 가능 — 모니터링 필수
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    // SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-032/033 — 템플릿 우선, 미존재 시 하드코딩 fallback.
    private final EmailTemplateResolver templateResolver;

    @Value("${spring.mail.username:noreply@iroum-cms.kr}")
    private String fromAddress;

    /**
     * OTP 코드 이메일 비동기 발송.
     *
     * <p>발송 실패는 예외를 외부로 전파하지 않는다. OTP 발송 실패 사실을 응답에 포함하면
     * 공격자에게 이메일 존재 여부를 노출할 수 있음.
     */
    @Async("auditExecutor")
    @Override
    public void sendOtp(String to, String code, VerificationPurpose purpose) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);

            // 템플릿 우선 시도 (REQ-ET-032). 미존재/실패 시 하드코딩 fallback (REQ-ET-033).
            Optional<RenderResult> rendered = templateResolver.resolveAndRender(
                    "OTP", "ko", Map.of("code", code, "purpose", String.valueOf(purpose)));
            if (rendered.isPresent()) {
                message.setSubject(rendered.get().subject());
                message.setText(plainBody(rendered.get()));
            } else {
                message.setSubject("[iroum-cms] 본인인증 코드");
                message.setText(String.format(
                    "안녕하세요.\n\n" +
                    "본인인증 코드는 %s 입니다.\n" +
                    "5분 이내에 입력해 주세요.\n\n" +
                    "이 코드를 요청하지 않으셨다면 이 이메일을 무시해 주세요.\n\n" +
                    "iroum-cms 시스템",
                    code
                ));
            }
            mailSender.send(message);
            log.debug("OTP 이메일 발송 완료: to={}, purpose={}", to, purpose);
        } catch (Exception e) {
            // 발송 실패는 사용자 응답에 노출하지 않음 (보안 — enumeration 방지)
            log.error("OTP 이메일 발송 실패 (non-blocking): to={}, purpose={}", to, purpose, e);
        }
    }

    /**
     * 비밀번호 재설정 완료 안내 이메일 비동기 발송.
     */
    @Async("auditExecutor")
    @Override
    public void sendPasswordResetNotice(String to) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);

            // 템플릿 우선 시도 (REQ-ET-032). 미존재/실패 시 하드코딩 fallback (REQ-ET-033).
            Optional<RenderResult> rendered = templateResolver.resolveAndRender(
                    "PASSWORD_RESET", "ko", Map.of());
            if (rendered.isPresent()) {
                message.setSubject(rendered.get().subject());
                message.setText(plainBody(rendered.get()));
            } else {
                message.setSubject("[iroum-cms] 비밀번호 재설정 완료 안내");
                message.setText(
                    "안녕하세요.\n\n" +
                    "비밀번호가 성공적으로 재설정되었습니다.\n\n" +
                    "본인이 요청하지 않았다면 즉시 고객센터에 문의해 주세요.\n\n" +
                    "iroum-cms 시스템"
                );
            }
            mailSender.send(message);
            log.debug("비밀번호 재설정 안내 이메일 발송 완료: to={}", to);
        } catch (Exception e) {
            log.error("비밀번호 재설정 안내 이메일 발송 실패: to={}", to, e);
        }
    }

    /**
     * 가입 승인 확정 안내 이메일 비동기 발송.
     *
     * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-017/019 — USER_APPROVAL_CONFIRMED 템플릿 우선,
     * 미존재/실패 시 하드코딩 fallback. 발송 실패는 예외를 전파하지 않는다.
     */
    @Async("auditExecutor")
    @Override
    public void sendApprovalConfirmed(String to, String userName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);

            Optional<RenderResult> rendered = templateResolver.resolveAndRender(
                    "USER_APPROVAL_CONFIRMED", "ko", Map.of("userName", nullToEmpty(userName)));
            if (rendered.isPresent()) {
                message.setSubject(rendered.get().subject());
                message.setText(plainBody(rendered.get()));
            } else {
                message.setSubject("[이루움 CMS] 가입이 승인되었습니다");
                message.setText(String.format(
                    "안녕하세요, %s님.\n\n회원 가입 신청이 승인되었습니다.\n" +
                    "지금 바로 로그인하여 서비스를 이용하실 수 있습니다.\n\n이루움 CMS",
                    nullToEmpty(userName)));
            }
            mailSender.send(message);
            log.debug("가입 승인 안내 이메일 발송 완료: to={}", to);
        } catch (Exception e) {
            log.error("가입 승인 안내 이메일 발송 실패 (non-blocking): to={}", to, e);
        }
    }

    /**
     * 가입 거절 안내 이메일 비동기 발송.
     *
     * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-018/019 — USER_APPROVAL_REJECTED 템플릿에 거절 사유 주입,
     * 미존재/실패 시 하드코딩 fallback. 발송 실패는 예외를 전파하지 않는다.
     */
    @Async("auditExecutor")
    @Override
    public void sendApprovalRejected(String to, String userName, String rejectionReason) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);

            Optional<RenderResult> rendered = templateResolver.resolveAndRender(
                    "USER_APPROVAL_REJECTED", "ko",
                    Map.of("userName", nullToEmpty(userName),
                           "rejectionReason", nullToEmpty(rejectionReason)));
            if (rendered.isPresent()) {
                message.setSubject(rendered.get().subject());
                message.setText(plainBody(rendered.get()));
            } else {
                message.setSubject("[이루움 CMS] 가입 신청이 거절되었습니다");
                message.setText(String.format(
                    "안녕하세요, %s님.\n\n회원 가입 신청이 거절되었습니다.\n사유: %s\n\n이루움 CMS",
                    nullToEmpty(userName), nullToEmpty(rejectionReason)));
            }
            mailSender.send(message);
            log.debug("가입 거절 안내 이메일 발송 완료: to={}", to);
        } catch (Exception e) {
            log.error("가입 거절 안내 이메일 발송 실패 (non-blocking): to={}", to, e);
        }
    }

    /**
     * 가입 승인 대기 리마인더 이메일 비동기 발송.
     *
     * <p>SPEC-CMS-USER-APPROVAL-002 REQ-UA2-003/007 — USER_APPROVAL_REMINDER 템플릿 우선,
     * 미존재/실패 시 하드코딩 fallback. 발송 실패는 예외를 전파하지 않는다.
     */
    @Async("auditExecutor")
    @Override
    public void sendApprovalReminder(String to, String name, long pendingDays) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);

            Optional<RenderResult> rendered = templateResolver.resolveAndRender(
                    "USER_APPROVAL_REMINDER", "ko",
                    Map.of("name", nullToEmpty(name), "pendingDays", String.valueOf(pendingDays)));
            if (rendered.isPresent()) {
                message.setSubject(rendered.get().subject());
                message.setText(plainBody(rendered.get()));
            } else {
                message.setSubject("[이루움 CMS] 가입 승인 대기 안내");
                message.setText(String.format(
                    "안녕하세요, %s님.\n\n가입 신청이 %d일째 승인 대기 중입니다.\n" +
                    "관리자 승인 후 서비스를 이용하실 수 있습니다.\n\n이루움 CMS",
                    nullToEmpty(name), pendingDays));
            }
            mailSender.send(message);
            log.debug("가입 승인 대기 리마인더 발송 완료: to={}, pendingDays={}", to, pendingDays);
        } catch (Exception e) {
            log.error("가입 승인 대기 리마인더 발송 실패 (non-blocking): to={}", to, e);
        }
    }

    /**
     * 가입 자동 거절 안내 이메일 비동기 발송.
     *
     * <p>SPEC-CMS-USER-APPROVAL-002 REQ-UA2-004/007 — USER_APPROVAL_AUTO_REJECTED 템플릿 우선,
     * 미존재/실패 시 하드코딩 fallback. 발송 실패는 예외를 전파하지 않는다.
     */
    @Async("auditExecutor")
    @Override
    public void sendApprovalAutoRejected(String to, String name, String rejectionReason) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);

            Optional<RenderResult> rendered = templateResolver.resolveAndRender(
                    "USER_APPROVAL_AUTO_REJECTED", "ko",
                    Map.of("name", nullToEmpty(name),
                           "rejectionReason", nullToEmpty(rejectionReason)));
            if (rendered.isPresent()) {
                message.setSubject(rendered.get().subject());
                message.setText(plainBody(rendered.get()));
            } else {
                message.setSubject("[이루움 CMS] 가입 신청이 자동 거절되었습니다");
                message.setText(String.format(
                    "안녕하세요, %s님.\n\n가입 신청이 자동 거절되었습니다.\n사유: %s\n\n이루움 CMS",
                    nullToEmpty(name), nullToEmpty(rejectionReason)));
            }
            mailSender.send(message);
            log.debug("가입 자동 거절 안내 발송 완료: to={}", to);
        } catch (Exception e) {
            log.error("가입 자동 거절 안내 발송 실패 (non-blocking): to={}", to, e);
        }
    }

    /** 렌더링 결과에서 평문 본문을 추출한다(평문 없으면 HTML 사용 — SimpleMailMessage는 평문). */
    private String plainBody(RenderResult result) {
        return result.bodyText() != null && !result.bodyText().isBlank()
                ? result.bodyText() : result.bodyHtml();
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
