package kr.co.ircp.cms.domain.email.template.admin.service;

import kr.co.ircp.cms.domain.email.template.admin.dto.SmtpConfigRequest;
import kr.co.ircp.cms.domain.email.template.admin.dto.SmtpConfigResponse;
import kr.co.ircp.cms.domain.email.template.admin.entity.SmtpConfig;
import kr.co.ircp.cms.domain.email.template.admin.repository.SmtpConfigMapper;
import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * SMTP 동적 설정 서비스 구현체.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-040/041/042 — 단일 활성 행 운용, 변경 시
 * {@link JavaMailSenderImpl}을 재구성하여 서버 재시작 없이 다음 발송부터 적용한다.
 */
// @MX:NOTE: [AUTO] SPEC-CMS-EMAIL-TEMPLATE-001 — SMTP 변경 시 JavaMailSenderImpl 런타임 재구성
// @MX:SPEC: SPEC-CMS-EMAIL-TEMPLATE-001#REQ-ET-041
@Service
@Slf4j
public class SmtpConfigServiceImpl implements SmtpConfigService {

    private final SmtpConfigMapper smtpConfigMapper;
    private final EmailEncryptionService emailEncryptionService;
    private final JavaMailSender mailSender;

    public SmtpConfigServiceImpl(SmtpConfigMapper smtpConfigMapper,
                                 EmailEncryptionService emailEncryptionService,
                                 JavaMailSender mailSender) {
        this.smtpConfigMapper = smtpConfigMapper;
        this.emailEncryptionService = emailEncryptionService;
        this.mailSender = mailSender;
    }

    @Override
    @Transactional(readOnly = true)
    public SmtpConfigResponse getActive() {
        return smtpConfigMapper.findActive()
                .map(SmtpConfigResponse::from)
                .orElse(null);
    }

    @Override
    @Transactional
    public SmtpConfigResponse update(SmtpConfigRequest request, Long actorUserId) {
        Optional<SmtpConfig> existing = smtpConfigMapper.findActive();

        // 비밀번호: 입력이 비어있으면 기존 암호화 값 유지 (마스킹 응답 이후 미변경)
        String passwordEnc = existing.map(SmtpConfig::getPasswordEnc).orElse(null);
        if (request.password() != null && !request.password().isBlank()) {
            passwordEnc = encrypt(request.password());
        }

        SmtpConfig config;
        if (existing.isPresent()) {
            config = existing.get();
            config.setHost(request.host());
            config.setPort(request.port());
            config.setUsername(request.username());
            config.setPasswordEnc(passwordEnc);
            config.setFromAddress(request.fromAddress());
            config.setFromName(request.fromName());
            config.setEncryption(request.encryptionOrDefault());
            config.setIsActive(true);
            config.setUpdatedBy(actorUserId);
            smtpConfigMapper.update(config);
        } else {
            config = SmtpConfig.builder()
                    .host(request.host())
                    .port(request.port())
                    .username(request.username())
                    .passwordEnc(passwordEnc)
                    .fromAddress(request.fromAddress())
                    .fromName(request.fromName())
                    .encryption(request.encryptionOrDefault())
                    .isActive(true)
                    .updatedBy(actorUserId)
                    .build();
            smtpConfigMapper.insert(config);
        }

        // 재시작 없이 적용 (REQ-ET-041)
        reconfigureMailSender(config, request.password());
        return SmtpConfigResponse.from(config);
    }

    /** JavaMailSenderImpl을 런타임 재구성한다. 평문 비밀번호는 변경 요청에만 존재. */
    private void reconfigureMailSender(SmtpConfig config, String plainPassword) {
        if (!(mailSender instanceof JavaMailSenderImpl impl)) {
            log.warn("JavaMailSender가 JavaMailSenderImpl이 아니어서 동적 재구성을 건너뜁니다: {}",
                    mailSender.getClass().getName());
            return;
        }
        impl.setHost(config.getHost());
        impl.setPort(config.getPort());
        if (config.getUsername() != null) {
            impl.setUsername(config.getUsername());
        }
        if (plainPassword != null && !plainPassword.isBlank()) {
            impl.setPassword(plainPassword);
        }
        var props = impl.getJavaMailProperties();
        boolean tls = !"NONE".equalsIgnoreCase(config.getEncryption());
        props.put("mail.smtp.starttls.enable", String.valueOf("STARTTLS".equalsIgnoreCase(config.getEncryption())));
        props.put("mail.smtp.ssl.enable", String.valueOf("SSL".equalsIgnoreCase(config.getEncryption())));
        props.put("mail.smtp.auth", String.valueOf(config.getUsername() != null));
        log.info("SMTP 설정 동적 재구성 완료: host={}, port={}, tls={}", config.getHost(), config.getPort(), tls);
    }

    private String encrypt(String plain) {
        var enc = emailEncryptionService.encrypt(plain);
        return java.util.Base64.getEncoder().encodeToString(enc.ciphertext())
                + ":" + java.util.Base64.getEncoder().encodeToString(enc.iv())
                + ":" + java.util.Base64.getEncoder().encodeToString(enc.tag())
                + ":" + enc.keyVersion();
    }
}
