package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.PreviewRequest;
import kr.co.ircp.cms.domain.safety.dto.PreviewResponse;
import kr.co.ircp.cms.domain.safety.dto.TemplateRequest;
import kr.co.ircp.cms.domain.safety.dto.TemplateResponse;
import kr.co.ircp.cms.domain.safety.entity.SafetyGuidelineTemplate;
import kr.co.ircp.cms.domain.safety.exception.SafetyTemplateNotFoundException;
import kr.co.ircp.cms.domain.safety.repository.SafetyChecklistItemMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyGuidelineTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** REQ-SAFETY-005 — 템플릿 CRUD + 버전 관리 + 미리보기 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SafetyTemplateService — REQ-SAFETY-005")
class SafetyTemplateServiceTest {

    @Mock private SafetyGuidelineTemplateMapper templateMapper;
    @Mock private SafetyChecklistItemMapper itemMapper;

    private SafetyTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SafetyTemplateServiceImpl(templateMapper, itemMapper);
    }

    private SafetyGuidelineTemplate sample(long id, String code, String version) {
        return SafetyGuidelineTemplate.builder()
                .id(id).code(code).name("템플릿").description("설명")
                .applicableIndustryCodes(List.of("F4521"))
                .applicableGrades(List.of("D"))
                .structure("{}").status("PUBLISHED").version(version)
                .build();
    }

    private TemplateRequest sampleRequest(String code) {
        return new TemplateRequest(
                code, "신규 템플릿", "설명",
                List.of("F4521"), List.of("C", "D"),
                "{\"sections\":[]}",
                "NONE"
        );
    }

    // ──────────────────────────────────────────────
    // REQ-SAFETY-005-D-1: 템플릿 CRUD
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("템플릿 목록 조회 — DB 결과를 응답으로 매핑")
    void listTemplates_returnsResponseList() {
        when(templateMapper.findAll()).thenReturn(List.of(sample(1L, "T001", "v1.0")));

        List<TemplateResponse> result = service.listTemplates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("T001");
    }

    @Test
    @DisplayName("템플릿 단건 조회 — 미존재 시 SafetyTemplateNotFoundException")
    void getTemplate_missing_throws() {
        when(templateMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTemplate(99L))
                .isInstanceOf(SafetyTemplateNotFoundException.class);
    }

    @Test
    @DisplayName("템플릿 신규 생성 — version=v1.0, status=DRAFT")
    void createTemplate_newDefaults_v10AndDraft() {
        TemplateResponse created = service.createTemplate(sampleRequest("T002"), 100L);

        assertThat(created.version()).isEqualTo("v1.0");
        assertThat(created.status()).isEqualTo("DRAFT");
        verify(templateMapper).insert(any());
    }

    // ──────────────────────────────────────────────
    // REQ-SAFETY-005-D-2: 신규 버전 발행 (semver bump)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("신규 버전 발행 — v1.0 → v1.1, 기존 PUBLISHED는 ARCHIVED")
    void releaseNewVersion_bumpsMinorAndArchivesPrevious() {
        SafetyGuidelineTemplate existing = sample(1L, "T001", "v1.0");
        when(templateMapper.findById(1L)).thenReturn(Optional.of(existing));

        TemplateResponse newVersion = service.releaseNewVersion(1L, sampleRequest("T001"), 100L);

        assertThat(newVersion.version()).isEqualTo("v1.1");
        assertThat(newVersion.status()).isEqualTo("PUBLISHED");
        verify(templateMapper).archivePublishedByCode("T001");
        // code UNIQUE 제약으로 신규 INSERT 불가 — 기존 레코드를 버전업하여 update
        verify(templateMapper, times(1)).update(any());
    }

    @Test
    @DisplayName("bumpMinor 헬퍼 — v2.3 → v2.4")
    void bumpMinor_handlesArbitraryVersion() {
        assertThat(SafetyTemplateServiceImpl.bumpMinor("v2.3")).isEqualTo("v2.4");
        assertThat(SafetyTemplateServiceImpl.bumpMinor("invalid")).isEqualTo("v1.1");
        assertThat(SafetyTemplateServiceImpl.bumpMinor(null)).isEqualTo("v1.1");
    }

    @Test
    @DisplayName("논리 삭제 — archiveById 호출")
    void archive_existing_callsArchive() {
        when(templateMapper.findById(1L)).thenReturn(Optional.of(sample(1L, "T001", "v1.0")));

        service.archiveTemplate(1L);

        verify(templateMapper).archiveById(1L);
    }

    // ──────────────────────────────────────────────
    // REQ-SAFETY-005-D-4: 미리보기 (저장 없음)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("미리보기 — HTML 반환, insert 호출 없음")
    void preview_renderHtmlNoSave() {
        when(templateMapper.findById(1L)).thenReturn(Optional.of(sample(1L, "T001", "v1.0")));
        PreviewRequest req = new PreviewRequest("D", "F4521", "샘플기업");

        PreviewResponse response = service.previewTemplate(1L, req);

        assertThat(response.contentHtml()).contains("<html>").contains("미리보기");
        verify(templateMapper, times(0)).insert(any());
    }

    @Test
    @DisplayName("미리보기 — 미존재 템플릿 SafetyTemplateNotFoundException")
    void preview_missingTemplate_throws() {
        when(templateMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.previewTemplate(99L, null))
                .isInstanceOf(SafetyTemplateNotFoundException.class);
    }

    // ──────────────────────────────────────────────
    // 체크리스트 항목
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("체크리스트 항목 추가 — 템플릿 존재 시 insert 호출")
    void addChecklistItem_validTemplate_inserts() {
        when(templateMapper.findById(1L)).thenReturn(Optional.of(sample(1L, "T001", "v1.0")));

        var request = new kr.co.ircp.cms.domain.safety.dto.ChecklistItemRequest(
                "PPE", "안전모 착용 확인", "CRITICAL", 0
        );
        var result = service.addChecklistItem(1L, request);

        assertThat(result.itemText()).isEqualTo("안전모 착용 확인");
        assertThat(result.severity()).isEqualTo("CRITICAL");
        verify(itemMapper).insert(any());
    }
}
