package kr.co.ircp.cms.domain.email.template.admin.service;

import kr.co.ircp.cms.domain.email.template.admin.dto.PagedResponse;
import kr.co.ircp.cms.domain.email.template.admin.dto.SendLogResponse;
import kr.co.ircp.cms.domain.email.template.admin.dto.SendLogSearchCriteria;
import kr.co.ircp.cms.domain.email.template.admin.entity.EmailTemplateSendLog;
import kr.co.ircp.cms.domain.email.template.admin.repository.EmailTemplateSendLogMapper;
import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 발송 로그 기록·조회 서비스.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-050/051 — 수신자 이메일은 암호화+HMAC 저장(PII).
 */
// @MX:NOTE: [AUTO] SPEC-CMS-EMAIL-TEMPLATE-001 — 발송 로그 적재 시 수신자 PII 암호화/HMAC 격상
// @MX:SPEC: SPEC-CMS-EMAIL-TEMPLATE-001#REQ-ET-050
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateSendLogService {

    private final EmailTemplateSendLogMapper logMapper;
    private final EmailEncryptionService emailEncryptionService;

    /**
     * 발송 결과를 로그에 기록한다.
     *
     * <p>REQUIRES_NEW — 발송 트랜잭션 롤백과 무관하게 로그는 항상 남도록 분리한다.
     * 로그 적재 실패가 발송 흐름을 막지 않도록 예외를 삼킨다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long templateId, String templateCode, String recipientPlain,
                       String subject, String status, String errorMessage) {
        try {
            String enc = recipientPlain != null && !recipientPlain.isBlank()
                    ? encrypt(recipientPlain) : "";
            String hmac = recipientPlain != null && !recipientPlain.isBlank()
                    ? emailEncryptionService.computeHmac(recipientPlain) : "";
            EmailTemplateSendLog log = EmailTemplateSendLog.builder()
                    .templateId(templateId)
                    .templateCode(templateCode)
                    .recipientEnc(enc)
                    .recipientHmac(hmac)
                    .subject(subject)
                    .status(status)
                    .errorMessage(errorMessage)
                    .retryCount(0)
                    .build();
            logMapper.insert(log);
        } catch (Exception e) {
            // 로그 적재 실패는 발송 흐름을 막지 않는다.
            log.error("이메일 발송 로그 적재 실패: code={}, status={}", templateCode, status, e);
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<SendLogResponse> search(SendLogSearchCriteria criteria) {
        long total = logMapper.countAll(criteria);
        var content = logMapper.findAll(criteria).stream()
                .map(SendLogResponse::from)
                .toList();
        return new PagedResponse<>(content, criteria.page(), criteria.effectiveSize(), total);
    }

    /** 수신자 이메일을 base64 결합 형태로 암호화한다(복호화는 본 SPEC 범위 밖). */
    private String encrypt(String plain) {
        var enc = emailEncryptionService.encrypt(plain);
        return java.util.Base64.getEncoder().encodeToString(enc.ciphertext())
                + ":" + java.util.Base64.getEncoder().encodeToString(enc.iv())
                + ":" + java.util.Base64.getEncoder().encodeToString(enc.tag())
                + ":" + enc.keyVersion();
    }
}
