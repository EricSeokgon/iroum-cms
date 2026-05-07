package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.KeywordRequest;
import kr.co.ircp.cms.domain.safety.dto.KeywordSummary;
import kr.co.ircp.cms.domain.safety.entity.SafetyKeyword;
import kr.co.ircp.cms.domain.safety.entity.SafetyKeywordSynonym;
import kr.co.ircp.cms.domain.safety.exception.SafetyKeywordNotFoundException;
import kr.co.ircp.cms.domain.safety.repository.SafetyKeywordMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-SAFETY-002: 안전 키워드 사전 서비스 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SafetyKeywordServiceImpl — REQ-SAFETY-002")
class SafetyKeywordServiceImplTest {

    @Mock
    private SafetyKeywordMapper keywordMapper;

    @InjectMocks
    private SafetyKeywordServiceImpl service;

    private SafetyKeyword keyword(long id, String category, String code, String term) {
        return SafetyKeyword.builder()
                .id(id).category(category).code(code).term(term)
                .description("desc").status("ACTIVE")
                .build();
    }

    private SafetyKeywordSynonym synonym(long id, String value) {
        return SafetyKeywordSynonym.builder()
                .id(id).keywordId(10L).synonym(value).build();
    }

    // ──────────────────────────────────────────────
    // listKeywords
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("키워드 목록 조회 — 카테고리 필터 + 동의어 포함 응답")
    void listKeywords_returnsSummariesWithSynonyms() {
        SafetyKeyword k = keyword(10L, "INDUSTRY", "F4521", "건설업");
        when(keywordMapper.findByCategory("INDUSTRY")).thenReturn(List.of(k));
        when(keywordMapper.findSynonymsByKeywordId(10L))
                .thenReturn(List.of(synonym(1L, "건축업"), synonym(2L, "토목")));

        List<KeywordSummary> result = service.listKeywords("INDUSTRY");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
        assertThat(result.get(0).category()).isEqualTo("INDUSTRY");
        assertThat(result.get(0).synonyms()).containsExactly("건축업", "토목");
    }

    @Test
    @DisplayName("키워드 목록 조회 — 빈 결과")
    void listKeywords_empty_returnsEmpty() {
        when(keywordMapper.findByCategory("HAZARD")).thenReturn(List.of());

        List<KeywordSummary> result = service.listKeywords("HAZARD");

        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    // createKeyword
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("키워드 생성 — INSERT + 동의어 INSERT")
    void createKeyword_insertsKeywordAndSynonyms() {
        KeywordRequest req = new KeywordRequest(
                "INDUSTRY", "F4521", "건설업", "설명",
                List.of("건축업", "토목")
        );
        when(keywordMapper.findSynonymsByKeywordId(any())).thenReturn(List.of(
                synonym(1L, "건축업"), synonym(2L, "토목")
        ));

        KeywordSummary result = service.createKeyword(req);

        ArgumentCaptor<SafetyKeyword> kwCaptor = ArgumentCaptor.forClass(SafetyKeyword.class);
        verify(keywordMapper, times(1)).insert(kwCaptor.capture());
        SafetyKeyword inserted = kwCaptor.getValue();
        assertThat(inserted.getCategory()).isEqualTo("INDUSTRY");
        assertThat(inserted.getCode()).isEqualTo("F4521");
        assertThat(inserted.getTerm()).isEqualTo("건설업");
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");

        verify(keywordMapper, times(2)).insertSynonym(any());
        assertThat(result.synonyms()).containsExactly("건축업", "토목");
    }

    @Test
    @DisplayName("키워드 생성 — synonyms null 안전 처리")
    void createKeyword_nullSynonyms_skipsInsert() {
        KeywordRequest req = new KeywordRequest(
                "INDUSTRY", "F4521", "건설업", "설명", null
        );

        service.createKeyword(req);

        verify(keywordMapper, times(1)).insert(any());
        verify(keywordMapper, never()).insertSynonym(any());
    }

    @Test
    @DisplayName("키워드 생성 — 빈/공백 동의어 skip")
    void createKeyword_blankSynonyms_skipped() {
        KeywordRequest req = new KeywordRequest(
                "INDUSTRY", "F4521", "건설업", "설명",
                java.util.Arrays.asList("건축업", null, "  ", "토목")
        );

        service.createKeyword(req);

        verify(keywordMapper, times(1)).insert(any());
        verify(keywordMapper, times(2)).insertSynonym(any());
    }

    // ──────────────────────────────────────────────
    // updateKeyword
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("키워드 수정 — UPDATE + 동의어 갱신")
    void updateKeyword_updatesAndReplacesSynonyms() {
        SafetyKeyword existing = keyword(10L, "INDUSTRY", "F4521", "건설업");
        KeywordRequest req = new KeywordRequest(
                "PROCESS", "F4521", "건축업", "수정된 설명",
                List.of("새동의어")
        );
        when(keywordMapper.findById(10L)).thenReturn(Optional.of(existing));

        service.updateKeyword(10L, req);

        verify(keywordMapper, times(1)).update(existing);
        verify(keywordMapper, times(1)).deleteSynonymsByKeywordId(10L);
        verify(keywordMapper, times(1)).insertSynonym(any());
        assertThat(existing.getCategory()).isEqualTo("PROCESS");
        assertThat(existing.getTerm()).isEqualTo("건축업");
        assertThat(existing.getDescription()).isEqualTo("수정된 설명");
    }

    @Test
    @DisplayName("키워드 수정 미존재 시 SafetyKeywordNotFoundException")
    void updateKeyword_missing_throws() {
        when(keywordMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateKeyword(99L, new KeywordRequest(
                "INDUSTRY", "F4521", "건설업", "desc", List.of()
        ))).isInstanceOf(SafetyKeywordNotFoundException.class);

        verify(keywordMapper, never()).update(any());
    }

    @Test
    @DisplayName("키워드 수정 — synonyms null 시 동의어 갱신 skip")
    void updateKeyword_nullSynonyms_skipsSynonymUpdates() {
        SafetyKeyword existing = keyword(10L, "INDUSTRY", "F4521", "건설업");
        KeywordRequest req = new KeywordRequest(
                "INDUSTRY", "F4521", "건설업", "수정된 설명", null
        );
        when(keywordMapper.findById(10L)).thenReturn(Optional.of(existing));

        service.updateKeyword(10L, req);

        verify(keywordMapper, times(1)).update(existing);
        verify(keywordMapper, never()).deleteSynonymsByKeywordId(any());
        verify(keywordMapper, never()).insertSynonym(any());
    }

    // ──────────────────────────────────────────────
    // deactivateKeyword
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("키워드 비활성화 — INACTIVE 상태로 전환")
    void deactivateKeyword_marksInactive() {
        SafetyKeyword existing = keyword(10L, "INDUSTRY", "F4521", "건설업");
        when(keywordMapper.findById(10L)).thenReturn(Optional.of(existing));

        service.deactivateKeyword(10L);

        verify(keywordMapper, times(1)).deactivateById(10L);
    }

    @Test
    @DisplayName("키워드 비활성화 미존재 시 SafetyKeywordNotFoundException")
    void deactivateKeyword_missing_throws() {
        when(keywordMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivateKeyword(99L))
                .isInstanceOf(SafetyKeywordNotFoundException.class);

        verify(keywordMapper, never()).deactivateById(any());
    }
}
