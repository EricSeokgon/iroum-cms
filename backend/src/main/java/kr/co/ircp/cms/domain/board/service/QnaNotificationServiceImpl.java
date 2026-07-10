package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.domain.board.entity.Qna;
import kr.co.ircp.cms.domain.board.entity.QnaNotificationLog;
import kr.co.ircp.cms.domain.board.entity.UserNotificationInbox;
import kr.co.ircp.cms.domain.board.repository.QnaMapper;
import kr.co.ircp.cms.domain.board.repository.QnaNotificationLogMapper;
import kr.co.ircp.cms.domain.board.repository.QnaNotificationOptoutMapper;
import kr.co.ircp.cms.domain.board.repository.UserNotificationInboxMapper;
import kr.co.ircp.cms.domain.email.template.admin.dto.RenderResult;
import kr.co.ircp.cms.domain.email.template.admin.service.EmailTemplateResolver;
import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import kr.co.ircp.cms.domain.security.pii.EncryptedEmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Q&A 답변 알림 서비스 구현체.
 * REQ-BOARD-014-D: 멱등성·재시도·옵트아웃 정책 준수.
 *
 * // @MX:NOTE: [AUTO] INAPP 채널은 옵트아웃 불가(서비스 운영 정보 분류).
 *                       EMAIL은 qna_notification_optout 확인 후 발송.
 *                       INAPP: user_notification_inbox INSERT 구현 완료 (REQ-BOARD-014-D-2).
 * // @MX:SPEC: REQ-BOARD-014-D
 * // @MX:ANCHOR: [AUTO] notifyAnswered — QnaServiceImpl.answerQna에서 호출되는 공개 알림 진입점.
 * // @MX:REASON: fan_in >= 2: QnaServiceImpl(직접) + QnaNotificationRetryJob(간접)
 * // @MX:SPEC: REQ-BOARD-014-D-2
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QnaNotificationServiceImpl implements QnaNotificationService {

    private final QnaNotificationLogMapper logMapper;
    private final QnaNotificationOptoutMapper optoutMapper;
    private final UserMapper userMapper;
    private final QnaMapper qnaMapper;
    private final UserNotificationInboxMapper inboxMapper;
    private final JavaMailSender mailSender;
    private final EmailEncryptionService emailEncryptionService;
    // SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-031/033 — QNA_ANSWER 템플릿 우선, 미존재 시 하드코딩 fallback.
    private final EmailTemplateResolver templateResolver;

    @Value("${spring.mail.username:noreply@iroum-cms.kr}")
    private String fromAddress;

    @Override
    @Transactional
    public void notifyAnswered(Long qnaId, Long questionerId, Long answererId) {
        // INAPP 채널: 항상 발송 (옵트아웃 불가)
        sendChannel(qnaId, questionerId, answererId, "INAPP");

        // EMAIL 채널: 옵트아웃 확인 후 발송
        if (!optoutMapper.existsByUserAndChannel(questionerId, "EMAIL")) {
            sendChannel(qnaId, questionerId, answererId, "EMAIL");
        }
    }

    @Override
    @Transactional
    public void retryFailed() {
        List<QnaNotificationLog> pending = logMapper.findPendingOrFailed();
        for (QnaNotificationLog item : pending) {
            attemptSend(item);
        }
    }

    @Override
    @Transactional
    public void updateEmailOptout(Long userId, boolean optout) {
        if (optout) {
            optoutMapper.upsert(userId, "EMAIL");
        } else {
            optoutMapper.delete(userId, "EMAIL");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEmailEnabled(Long userId) {
        return !optoutMapper.existsByUserAndChannel(userId, "EMAIL");
    }

    private void sendChannel(Long qnaId, Long recipientId, Long answererId, String channel) {
        // 멱등성 보장: 이미 PENDING 또는 SENT인 로그가 있으면 DB unique index가 차단
        QnaNotificationLog item = QnaNotificationLog.builder()
                .qnaId(qnaId)
                .answererId(answererId)
                .recipientId(recipientId)
                .channel(channel)
                .build();
        try {
            logMapper.insert(item);
        } catch (DuplicateKeyException e) {
            // unique index 충돌 → 이미 발송됨 또는 발송 중
            log.warn("Q&A 알림 중복 발송 차단: qnaId={}, channel={}", qnaId, channel);
            return;
        }
        attemptSend(item);
    }

    private void attemptSend(QnaNotificationLog item) {
        try {
            switch (item.getChannel()) {
                case "EMAIL" -> sendEmail(item);
                case "INAPP" -> sendInapp(item);
                default -> log.warn("알 수 없는 채널: {}, qnaId={}", item.getChannel(), item.getQnaId());
            }
            logMapper.markSent(item.getId());
        } catch (Exception e) {
            if (item.getRetryCount() >= 2) {
                // 3회째(0-indexed 2) 실패 → DEAD_LETTER
                logMapper.markDeadLetter(item.getId(), e.getMessage());
                log.error("Q&A 알림 DEAD_LETTER: id={}, error={}", item.getId(), e.getMessage());
            } else {
                logMapper.markFailed(item.getId(), e.getMessage());
                log.warn("Q&A 알림 발송 실패(재시도 예정): id={}, retryCount={}",
                        item.getId(), item.getRetryCount() + 1);
            }
        }
    }

    /** INAPP 채널 발송 — user_notification_inbox 테이블에 알림 항목 삽입. */
    private void sendInapp(QnaNotificationLog item) {
        Qna qna = qnaMapper.findById(item.getQnaId())
                .orElseThrow(() -> new IllegalStateException(
                        "Q&A 질문 없음: qnaId=" + item.getQnaId()));

        UserNotificationInbox inbox = UserNotificationInbox.builder()
                .userId(item.getRecipientId())
                .type("QNA_ANSWERED")
                .title("Q&A 답변이 등록되었습니다: " + qna.getTitle())
                .body("질문하신 '" + qna.getTitle() + "'에 답변이 등록되었습니다. 확인해 주세요.")
                .refId(item.getQnaId())
                .refType("QNA")
                .build();
        inboxMapper.insert(inbox);
        log.debug("Q&A INAPP 알림 저장: inboxId={}, recipientId={}, qnaId={}",
                inbox.getId(), item.getRecipientId(), item.getQnaId());
    }

    /** EMAIL 채널 실 발송 — 수신자 이메일 PII 복호화 후 JavaMailSender로 전송. */
    private void sendEmail(QnaNotificationLog item) {
        User recipient = userMapper.findById(item.getRecipientId())
                .orElseThrow(() -> new IllegalStateException(
                        "Q&A 알림 수신자 없음: recipientId=" + item.getRecipientId()));

        // 이메일 PII 복호화
        String toEmail;
        if (recipient.getEmailEncrypted() != null && recipient.getEmailEncrypted().length > 0) {
            EncryptedEmail enc = new EncryptedEmail(
                    recipient.getEmailEncrypted(),
                    recipient.getEmailIv(),
                    recipient.getEmailTag(),
                    recipient.getEmailKeyVersion());
            toEmail = emailEncryptionService.decrypt(enc);
        } else {
            // V24 마이그레이션 이전 레거시 평문 컬럼
            toEmail = recipient.getEmail();
        }
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Q&A EMAIL 알림 생략: 수신자 이메일 없음, recipientId={}", item.getRecipientId());
            return;
        }

        Qna qna = qnaMapper.findById(item.getQnaId())
                .orElseThrow(() -> new IllegalStateException(
                        "Q&A 질문 없음: qnaId=" + item.getQnaId()));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);

        // QNA_ANSWER 템플릿 우선 시도 (REQ-ET-031). 미존재/실패 시 하드코딩 fallback (REQ-ET-033).
        Optional<RenderResult> rendered = templateResolver.resolveAndRender(
                "QNA_ANSWER", "ko", Map.of("title", qna.getTitle()));
        if (rendered.isPresent()) {
            message.setSubject(rendered.get().subject());
            message.setText(rendered.get().bodyText() != null && !rendered.get().bodyText().isBlank()
                    ? rendered.get().bodyText() : rendered.get().bodyHtml());
        } else {
            message.setSubject("[iroum-cms] Q&A 답변이 등록되었습니다: " + qna.getTitle());
            message.setText(String.format(
                    "안녕하세요.\n\n" +
                    "Q&A 질문 '%s'에 답변이 등록되었습니다.\n\n" +
                    "서비스에 접속하여 답변을 확인해 주세요.\n\n" +
                    "iroum-cms 시스템",
                    qna.getTitle()
            ));
        }
        mailSender.send(message);
        log.debug("Q&A EMAIL 알림 발송 완료: recipientId={}, qnaId={}", item.getRecipientId(), item.getQnaId());
    }
}
