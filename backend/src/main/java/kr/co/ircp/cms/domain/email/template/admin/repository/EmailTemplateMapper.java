package kr.co.ircp.cms.domain.email.template.admin.repository;

import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplateSearchCriteria;
import kr.co.ircp.cms.domain.email.template.admin.entity.EmailTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 이메일 템플릿 매퍼 (email_template).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-001~005.
 */
// @MX:SPEC: SPEC-CMS-EMAIL-TEMPLATE-001
@Mapper
public interface EmailTemplateMapper {

    Optional<EmailTemplate> findById(@Param("id") Long id);

    Optional<EmailTemplate> findActiveByCodeAndLanguage(@Param("code") String code,
                                                        @Param("language") String language);

    List<EmailTemplate> findAll(@Param("criteria") EmailTemplateSearchCriteria criteria);

    long countAll(@Param("criteria") EmailTemplateSearchCriteria criteria);

    boolean existsByCodeAndLanguage(@Param("code") String code,
                                    @Param("language") String language,
                                    @Param("excludeId") Long excludeId);

    void insert(EmailTemplate template);

    int update(EmailTemplate template);

    int deleteById(@Param("id") Long id);
}
