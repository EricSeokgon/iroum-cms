package kr.co.ircp.cms.domain.email.template.admin;

import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplateSearchCriteria;
import kr.co.ircp.cms.domain.email.template.admin.entity.EmailTemplate;
import kr.co.ircp.cms.domain.email.template.admin.entity.SmtpConfig;
import kr.co.ircp.cms.domain.email.template.admin.repository.EmailTemplateMapper;
import kr.co.ircp.cms.domain.email.template.admin.repository.SmtpConfigMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이메일 템플릿 마이그레이션·매퍼 통합 테스트 (V59/V60).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 — 실제 PostgreSQL에서 테이블 생성·JSONB 왕복·CRUD를 검증한다.
 */
@DisplayName("EmailTemplate 마이그레이션·매퍼 IT (SPEC-CMS-EMAIL-TEMPLATE-001)")
class EmailTemplateMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private EmailTemplateMapper templateMapper;

    @Autowired
    private SmtpConfigMapper smtpConfigMapper;

    @Test
    @DisplayName("템플릿 INSERT 후 JSONB variables가 왕복 보존된다")
    void insertAndFindRoundTripsJsonbVariables() {
        EmailTemplate template = EmailTemplate.builder()
                .code("IT_OTP_" + System.nanoTime())
                .name("IT OTP")
                .templateType("OTP")
                .language("ko")
                .subject("[(${code})] 인증")
                .bodyHtml("<p>[(${code})]</p>")
                .bodyText("코드 [(${code})]")
                .variables(List.of(Map.of("name", "code", "required", true)))
                .isActive(true)
                .createdBy(1L)
                .updatedBy(1L)
                .build();

        templateMapper.insert(template);
        assertThat(template.getId()).isNotNull();

        Optional<EmailTemplate> found = templateMapper.findById(template.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getVariables()).hasSize(1);
        assertThat(found.get().getVariables().get(0)).containsEntry("name", "code");
    }

    @Test
    @DisplayName("findActiveByCodeAndLanguage는 비활성 템플릿을 제외한다")
    void findActiveExcludesInactive() {
        String code = "IT_INACTIVE_" + System.nanoTime();
        EmailTemplate inactive = EmailTemplate.builder()
                .code(code).name("비활성").templateType("CUSTOM").language("ko")
                .subject("s").bodyHtml("<p>b</p>").isActive(false).build();
        templateMapper.insert(inactive);

        assertThat(templateMapper.findActiveByCodeAndLanguage(code, "ko")).isEmpty();
    }

    @Test
    @DisplayName("existsByCodeAndLanguage가 유니크 제약을 반영한다")
    void existsByCodeAndLanguage() {
        String code = "IT_DUP_" + System.nanoTime();
        EmailTemplate t = EmailTemplate.builder()
                .code(code).name("n").templateType("CUSTOM").language("ko")
                .subject("s").bodyHtml("<p>b</p>").isActive(true).build();
        templateMapper.insert(t);

        assertThat(templateMapper.existsByCodeAndLanguage(code, "ko", null)).isTrue();
        assertThat(templateMapper.existsByCodeAndLanguage(code, "ko", t.getId())).isFalse();
        assertThat(templateMapper.existsByCodeAndLanguage(code, "en", null)).isFalse();
    }

    @Test
    @DisplayName("findAll/countAll이 type 필터를 적용한다")
    void findAllAppliesTypeFilter() {
        templateMapper.insert(EmailTemplate.builder()
                .code("IT_F_" + System.nanoTime()).name("n").templateType("QNA_ANSWER")
                .language("ko").subject("s").bodyHtml("<p>b</p>").isActive(true).build());

        var criteria = new EmailTemplateSearchCriteria("QNA_ANSWER", null, null, null, 0, 50);
        List<EmailTemplate> rows = templateMapper.findAll(criteria);
        assertThat(rows).allMatch(r -> "QNA_ANSWER".equals(r.getTemplateType()));
        assertThat(templateMapper.countAll(criteria)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("SMTP 설정 INSERT 후 findActive로 조회된다")
    void smtpConfigInsertAndFindActive() {
        smtpConfigMapper.insert(SmtpConfig.builder()
                .host("smtp.it.test").port(587).username("u").passwordEnc("enc")
                .fromAddress("from@it.test").encryption("STARTTLS").isActive(true)
                .updatedBy(1L).build());

        Optional<SmtpConfig> active = smtpConfigMapper.findActive();
        assertThat(active).isPresent();
        assertThat(active.get().getHost()).isNotBlank();
    }
}
