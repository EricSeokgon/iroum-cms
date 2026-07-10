package kr.co.ircp.cms.integration.notification;

import kr.co.ircp.cms.domain.notification.template.admin.entity.NotificationTemplate;
import kr.co.ircp.cms.domain.notification.template.admin.repository.NotificationTemplateMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NotificationTemplateMapper MyBatis 통합 테스트.
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — V60 확장 컬럼(subject/body_html/variables JSONB/language/is_active) +
 * (code, language) 복합 UNIQUE 검증.
 */
@Transactional
class NotificationTemplateMapperIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationTemplateMapper mapper;

    private NotificationTemplate build(String code, String language) {
        return NotificationTemplate.builder()
                .code(code)
                .name("정책 공개 알림")
                .channel("EMAIL")
                .subject("[알림] ${policyName}")
                .bodyHtml("<p>${policyName}</p>")
                .variables("[\"policyName\"]")
                .language(language)
                .isActive(true)
                .build();
    }

    @Test
    void insertAndFindById_roundTrip() {
        NotificationTemplate t = build("NOTI_OPEN", "ko");
        mapper.insert(t);

        assertThat(t.getId()).isNotNull();
        Optional<NotificationTemplate> found = mapper.findById(t.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("NOTI_OPEN");
        assertThat(found.get().getSubject()).isEqualTo("[알림] ${policyName}");
        assertThat(found.get().getVariables()).contains("policyName");
        assertThat(found.get().getIsActive()).isTrue();
    }

    @Test
    void existsByCodeAndLanguage_detectsDuplicate() {
        mapper.insert(build("NOTI_DUP", "ko"));

        assertThat(mapper.existsByCodeAndLanguage("NOTI_DUP", "ko", null)).isTrue();
        // 다른 언어는 복합 UNIQUE로 별개
        assertThat(mapper.existsByCodeAndLanguage("NOTI_DUP", "en", null)).isFalse();
    }

    @Test
    void findAll_and_countAll_withActiveFilter() {
        mapper.insert(build("NOTI_A", "ko"));
        NotificationTemplate inactive = build("NOTI_B", "ko");
        inactive.setIsActive(false);
        mapper.insert(inactive);

        List<NotificationTemplate> active = mapper.findAll(true, 0, 50);
        assertThat(active).extracting(NotificationTemplate::getCode).contains("NOTI_A");
        assertThat(active).extracting(NotificationTemplate::getCode).doesNotContain("NOTI_B");
        assertThat(mapper.countAll(true)).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void update_changesFields() {
        NotificationTemplate t = build("NOTI_UPD", "ko");
        mapper.insert(t);

        t.setName("수정된 이름");
        t.setIsActive(false);
        mapper.update(t);

        NotificationTemplate found = mapper.findById(t.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("수정된 이름");
        assertThat(found.getIsActive()).isFalse();
    }

    @Test
    void delete_removesRow() {
        NotificationTemplate t = build("NOTI_DEL", "ko");
        mapper.insert(t);

        mapper.delete(t.getId());

        assertThat(mapper.findById(t.getId())).isEmpty();
    }
}
