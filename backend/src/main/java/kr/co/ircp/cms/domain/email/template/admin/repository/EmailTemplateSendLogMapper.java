package kr.co.ircp.cms.domain.email.template.admin.repository;

import kr.co.ircp.cms.domain.email.template.admin.dto.SendLogSearchCriteria;
import kr.co.ircp.cms.domain.email.template.admin.entity.EmailTemplateSendLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 이메일 발송 로그 매퍼 (email_template_send_log).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-050/051.
 */
// @MX:SPEC: SPEC-CMS-EMAIL-TEMPLATE-001
@Mapper
public interface EmailTemplateSendLogMapper {

    void insert(EmailTemplateSendLog log);

    List<EmailTemplateSendLog> findAll(@Param("criteria") SendLogSearchCriteria criteria);

    long countAll(@Param("criteria") SendLogSearchCriteria criteria);
}
