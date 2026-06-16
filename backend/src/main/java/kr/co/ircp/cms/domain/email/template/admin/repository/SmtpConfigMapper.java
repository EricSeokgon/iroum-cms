package kr.co.ircp.cms.domain.email.template.admin.repository;

import kr.co.ircp.cms.domain.email.template.admin.entity.SmtpConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

/**
 * SMTP 설정 매퍼 (smtp_config) — 단일 활성 행.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-040/041/042.
 */
// @MX:SPEC: SPEC-CMS-EMAIL-TEMPLATE-001
@Mapper
public interface SmtpConfigMapper {

    Optional<SmtpConfig> findActive();

    void insert(SmtpConfig config);

    int update(SmtpConfig config);
}
