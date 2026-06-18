package kr.co.ircp.cms.domain.policy.dispatch.executor;

import jakarta.mail.internet.MimeMessage;
import kr.co.ircp.cms.domain.policy.dispatch.dto.NotificationDispatchTargetWithUser;
import kr.co.ircp.cms.domain.policy.dispatch.entity.NotificationDispatchSchedule;
import kr.co.ircp.cms.domain.policy.dispatch.repository.NotificationDispatchTargetMapper;
import kr.co.ircp.cms.domain.policy.subscription.repository.NotificationSubscriptionMapper;
import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import kr.co.ircp.cms.domain.security.pii.EncryptedEmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * EMAIL 채널 발송 실행기.
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — 대상별 암호화 이메일 복호화 → HTML 메일 발송.
 * 옵트아웃 대상은 SKIPPED_OPTOUT, 발송 실패는 FAILED로 마킹한다.
 * 평문 이메일은 로그에 남기지 않는다(PII 보호).
 */
// @MX:WARN: [AUTO] EmailDispatchExecutor — 대상별 메일 발송, 실패는 대상 단위로 격리되며 로그에 PII 미기록
// @MX:REASON: 대량 발송 중 일부 실패가 전체 배치를 중단시키지 않도록 try/catch로 격리. 복호화 평문 이메일 로깅 금지
// @MX:SPEC: SPEC-CMS-NOTI-EXT-001
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailDispatchExecutor implements DispatchChannelExecutor {

    private final JavaMailSender mailSender;
    private final EmailEncryptionService emailEncryptionService;
    private final NotificationSubscriptionMapper subscriptionMapper;
    private final NotificationDispatchTargetMapper targetMapper;

    @Override
    public String getSupportedChannel() {
        return "EMAIL";
    }

    @Override
    public void execute(NotificationDispatchSchedule schedule,
                        List<NotificationDispatchTargetWithUser> targets) {
        String category = categoryFor(schedule.getDispatchType());
        String subject = subjectFor(schedule);
        String bodyHtml = bodyFor(schedule);

        for (NotificationDispatchTargetWithUser target : targets) {
            try {
                if (!subscriptionMapper.isOptedIn(target.getUserId(), "EMAIL", category)) {
                    targetMapper.updateStatus(target.getId(), "SKIPPED_OPTOUT", "수신 거부");
                    continue;
                }
                String to = decryptEmail(target);
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(bodyHtml, true);
                mailSender.send(message);
                targetMapper.updateStatus(target.getId(), "SENT", null);
            } catch (Exception e) {
                // PII(이메일 평문)는 로그에 남기지 않는다 — targetId/userId만 기록
                log.error("EMAIL 발송 실패: targetId={}, userId={}",
                        target.getId(), target.getUserId(), e);
                targetMapper.updateStatus(target.getId(), "FAILED", "발송 오류");
            }
        }
    }

    private String decryptEmail(NotificationDispatchTargetWithUser target) {
        EncryptedEmail enc = new EncryptedEmail(
                target.getEmailEncrypted(), target.getEmailIv(),
                target.getEmailTag(),
                target.getKeyVersion() != null ? target.getKeyVersion() : 1);
        return emailEncryptionService.decrypt(enc);
    }

    /** dispatch_type → 수신 동의 카테고리 매핑. */
    private String categoryFor(String dispatchType) {
        if (dispatchType == null) {
            return "ANNOUNCEMENT";
        }
        return switch (dispatchType) {
            case "APPLICATION_OPEN", "CLOSING_SOON", "RESULT_ANNOUNCED" -> "POLICY_MATCH";
            case "ANNOUNCEMENT" -> "ANNOUNCEMENT";
            case "REMINDER" -> "REMINDER";
            default -> "ANNOUNCEMENT";
        };
    }

    private String subjectFor(NotificationDispatchSchedule schedule) {
        return "[이루움 CMS] 알림 (" + schedule.getDispatchType() + ")";
    }

    private String bodyFor(NotificationDispatchSchedule schedule) {
        return "<p>새로운 알림이 도착했습니다.</p>";
    }
}
