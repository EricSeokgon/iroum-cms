package kr.co.ircp.cms.domain.email.template.admin.service;

import kr.co.ircp.cms.domain.email.template.admin.dto.SmtpConfigRequest;
import kr.co.ircp.cms.domain.email.template.admin.dto.SmtpConfigResponse;

/**
 * SMTP 동적 설정 서비스.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-040/041/042.
 */
public interface SmtpConfigService {

    /** 활성 SMTP 설정 조회(비밀번호 마스킹). 미설정 시 null content 응답. */
    SmtpConfigResponse getActive();

    /** SMTP 설정 변경 — 저장 후 JavaMailSender를 재구성한다(재시작 없이 적용). */
    SmtpConfigResponse update(SmtpConfigRequest request, Long actorUserId);
}
