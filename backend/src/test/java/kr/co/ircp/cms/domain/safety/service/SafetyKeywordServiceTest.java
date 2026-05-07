package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.KeywordRequest;
import kr.co.ircp.cms.domain.safety.dto.KeywordSummary;
import kr.co.ircp.cms.domain.safety.entity.SafetyKeyword;
import kr.co.ircp.cms.domain.safety.entity.SafetyKeywordSynonym;
import kr.co.ircp.cms.domain.safety.exception.SafetyKeywordNotFoundException;
import kr.co.ircp.cms.domain.safety.repository.SafetyKeywordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SafetyKeywordService GREEN 단계 테스트.
 * REQ-SAFETY-002 — 안전 키워드 사전 CRUD + 동의어 관리.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SafetyKeywordService GREEN 테스트 (REQ-SAFETY-002)")
class SafetyKeywordServiceTest {

    @Mock
    private SafetyKeywordMapper keywordMapper;

    private SafetyKeywordService service;

    @BeforeEach
    void setUp() {
        service = new SafetyKeywordServiceImpl(keywordMapper);
    }

    // ─── 공통 스텁 빌더 ─────────────────────────────────────────────────────

    private SafetyKeyword stubKeyword(long id, String category, String code, String term) {
        return SafetyKeyword.builder()
                .id(id)
                .category(category)
                .code(code)
                .term(term)
                .description("설명 " + id)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();
    }

    private SafetyKeywordSynonym stubSynonym(long id, long keywordId, String text) {
        return SafetyKeywordSynonym.builder()
                .id(id)
                .keywordId(keywordId)
                .synonym(text)
                .build();
    }

    // ──────────────────────────────────────────────
    // listKeywords
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("키워드 목록 조회 — 카테고리 전달 시 mapper.findByCategory 호출 후 동의어 포함 매핑")
    void listKeywords_withCategory_returnsSummaryListWithSynonyms() {
        // arrange
        SafetyKeyword k1 = stubKeyword(1L, "INDUSTRY", "F4521", "건설업");
        SafetyKeyword k2 = stubKeyword(2L, "INDUSTRY", "C2511", "제조업");
        when(keywordMapper.findByCategory("INDUSTRY")).thenReturn(List.of(k1, k2));
        when(keywordMapper.findSynonymsByKeywordId(1L))
                .thenReturn(List.of(stubSynonym(11L, 1L, "토목"), stubSynonym(12L, 1L, "건축")));
        when(keywordMapper.findSynonymsByKeywordId(2L)).thenReturn(List.of());

        // act
        List<KeywordSummary> result = service.listKeywords("INDUSTRY");

        // assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).category()).isEqualTo("INDUSTRY");
        assertThat(result.get(0).code()).isEqualTo("F4521");
        assertThat(result.get(0).term()).isEqualTo("건설업");
        assertThat(result.get(0).status()).isEqualTo("ACTIVE");
        assertThat(result.get(0).synonyms()).containsExactly("토목", "건축");
        assertThat(result.get(1).synonyms()).isEmpty();
        verify(keywordMapper).findByCategory("INDUSTRY");
    }

    @Test
    @DisplayName("키워드 목록 조회 — null 카테고리는 그대로 mapper에 전달")
    void listKeywords_nullCategory_passesNullToMapper() {
        // arrange
        when(keywordMapper.findByCategory(null)).thenReturn(List.of());

        // act
        List<KeywordSummary> result = service.listKeywords(null);

        // assert
        assertThat(result).isEmpty();
        verify(keywordMapper).findByCategory(null);
    }

    @Test
    @DisplayName("키워드 목록 조회 — 빈 결과 반환")
    void listKeywords_emptyResult_returnsEmptyList() {
        // arrange
        when(keywordMapper.findByCategory("HAZARD")).thenReturn(List.of());

        // act
        List<KeywordSummary> result = service.listKeywords("HAZARD");

        // assert
        assertThat(result).isEmpty();
        verify(keywordMapper, never()).findSynonymsByKeywordId(any());
    }

    // ──────────────────────────────────────────────
    // createKeyword
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("키워드 생성 — status=ACTIVE로 설정되고 mapper.insert 호출")
    void createKeyword_setsStatusActiveAndInserts() {
        // arrange
        ArgumentCaptor<SafetyKeyword> captor = ArgumentCaptor.forClass(SafetyKeyword.class);
        KeywordRequest req = new KeywordRequest("HAZARD", "H001", "고소작업", "추락 위험", null);

        // mocking: insert가 호출된 후 toSummary에서 동의어 조회
        when(keywordMapper.findSynonymsByKeywordId(any())).thenReturn(List.of());

        // act
        KeywordSummary result = service.createKeyword(req);

        // assert
        verify(keywordMapper).insert(captor.capture());
        SafetyKeyword inserted = captor.getValue();
        assertThat(inserted.getCategory()).isEqualTo("HAZARD");
        assertThat(inserted.getCode()).isEqualTo("H001");
        assertThat(inserted.getTerm()).isEqualTo("고소작업");
        assertThat(inserted.getDescription()).isEqualTo("추락 위험");
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.term()).isEqualTo("고소작업");
        assertThat(result.synonyms()).isEmpty();
    }

    @Test
    @DisplayName("키워드 생성 — 동의어 리스트 제공 시 각 동의어를 trim 후 insertSynonym 호출")
    void createKeyword_withSynonyms_insertsEachSynonymTrimmed() {
        // arrange
        KeywordRequest req = new KeywordRequest("PROCESS", "P001", "용접", "용접 작업",
                Arrays.asList("  아크용접  ", "가스용접"));
        when(keywordMapper.findSynonymsByKeywordId(any())).thenReturn(List.of());

        ArgumentCaptor<SafetyKeywordSynonym> synCaptor = ArgumentCaptor.forClass(SafetyKeywordSynonym.class);

        // act
        service.createKeyword(req);

        // assert
        verify(keywordMapper, times(2)).insertSynonym(synCaptor.capture());
        List<SafetyKeywordSynonym> synonyms = synCaptor.getAllValues();
        assertThat(synonyms).extracting(SafetyKeywordSynonym::getSynonym)
                .containsExactly("아크용접", "가스용접");
    }

    @Test
    @DisplayName("키워드 생성 — null/공백 동의어는 insertSynonym 미호출")
    void createKeyword_nullOrBlankSynonym_skipsInsert() {
        // arrange
        KeywordRequest req = new KeywordRequest("PROCESS", "P002", "절단", "절단 작업",
                Arrays.asList(null, "  ", "", "정상값"));
        when(keywordMapper.findSynonymsByKeywordId(any())).thenReturn(List.of());

        ArgumentCaptor<SafetyKeywordSynonym> captor = ArgumentCaptor.forClass(SafetyKeywordSynonym.class);

        // act
        service.createKeyword(req);

        // assert — null/공백은 건너뛰고 "정상값"만 insert
        verify(keywordMapper, times(1)).insertSynonym(captor.capture());
        assertThat(captor.getValue().getSynonym()).isEqualTo("정상값");
    }

    @Test
    @DisplayName("키워드 생성 — synonyms=null이면 insertSynonym 미호출")
    void createKeyword_nullSynonymsList_skipsAllSynonymInserts() {
        // arrange
        KeywordRequest req = new KeywordRequest("EQUIPMENT", "E001", "크레인", "장비", null);
        when(keywordMapper.findSynonymsByKeywordId(any())).thenReturn(List.of());

        // act
        service.createKeyword(req);

        // assert
        verify(keywordMapper, never()).insertSynonym(any());
    }

    // ──────────────────────────────────────────────
    // updateKeyword
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("키워드 수정 — 존재하는 ID는 mapper.update 호출")
    void updateKeyword_existingId_callsUpdate() {
        // arrange
        SafetyKeyword existing = stubKeyword(1L, "INDUSTRY", "F4521", "건설업");
        when(keywordMapper.findById(1L)).thenReturn(Optional.of(existing));
        when(keywordMapper.findSynonymsByKeywordId(1L)).thenReturn(List.of());

        KeywordRequest req = new KeywordRequest("INDUSTRY", "F4521", "건설업(개정)", "수정된 설명", null);

        // act
        KeywordSummary result = service.updateKeyword(1L, req);

        // assert
        ArgumentCaptor<SafetyKeyword> captor = ArgumentCaptor.forClass(SafetyKeyword.class);
        verify(keywordMapper).update(captor.capture());
        SafetyKeyword updated = captor.getValue();
        assertThat(updated.getCategory()).isEqualTo("INDUSTRY");
        assertThat(updated.getTerm()).isEqualTo("건설업(개정)");
        assertThat(updated.getDescription()).isEqualTo("수정된 설명");
        assertThat(result.term()).isEqualTo("건설업(개정)");
    }

    @Test
    @DisplayName("키워드 수정 — synonyms 제공 시 deleteSynonymsByKeywordId 후 새 동의어 insertSynonym 호출")
    void updateKeyword_withSynonyms_deletesOldAndInsertsNew() {
        // arrange
        when(keywordMapper.findById(1L)).thenReturn(Optional.of(stubKeyword(1L, "PROCESS", "P001", "용접")));
        when(keywordMapper.findSynonymsByKeywordId(1L)).thenReturn(List.of());

        KeywordRequest req = new KeywordRequest("PROCESS", "P001", "용접",
                "용접 작업 (개정)", List.of("아크용접", "TIG용접"));

        // act
        service.updateKeyword(1L, req);

        // assert
        verify(keywordMapper).deleteSynonymsByKeywordId(1L);
        verify(keywordMapper, times(2)).insertSynonym(any(SafetyKeywordSynonym.class));
    }

    @Test
    @DisplayName("키워드 수정 — synonyms=null이면 기존 동의어 유지 (delete/insert 미호출)")
    void updateKeyword_nullSynonyms_keepsExisting() {
        // arrange
        when(keywordMapper.findById(1L)).thenReturn(Optional.of(stubKeyword(1L, "PROCESS", "P001", "용접")));
        when(keywordMapper.findSynonymsByKeywordId(1L)).thenReturn(List.of());

        KeywordRequest req = new KeywordRequest("PROCESS", "P001", "용접", "설명", null);

        // act
        service.updateKeyword(1L, req);

        // assert
        verify(keywordMapper, never()).deleteSynonymsByKeywordId(any());
        verify(keywordMapper, never()).insertSynonym(any());
    }

    @Test
    @DisplayName("키워드 수정 — 존재하지 않는 ID는 SafetyKeywordNotFoundException")
    void updateKeyword_nonExistentId_throwsSafetyKeywordNotFoundException() {
        // arrange
        when(keywordMapper.findById(999L)).thenReturn(Optional.empty());
        KeywordRequest req = new KeywordRequest("PROCESS", "P002", "절단", "설명", null);

        // act + assert
        assertThatThrownBy(() -> service.updateKeyword(999L, req))
                .isInstanceOf(SafetyKeywordNotFoundException.class);

        verify(keywordMapper, never()).update(any());
        verify(keywordMapper, never()).deleteSynonymsByKeywordId(any());
    }

    // ──────────────────────────────────────────────
    // deactivateKeyword
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("키워드 비활성화 — 존재하는 ID는 mapper.deactivateById 호출")
    void deactivateKeyword_existingId_callsDeactivateById() {
        // arrange
        when(keywordMapper.findById(1L)).thenReturn(Optional.of(stubKeyword(1L, "HAZARD", "H001", "고소")));

        // act
        service.deactivateKeyword(1L);

        // assert
        verify(keywordMapper).deactivateById(1L);
    }

    @Test
    @DisplayName("키워드 비활성화 — 존재하지 않는 ID는 SafetyKeywordNotFoundException")
    void deactivateKeyword_nonExistentId_throwsSafetyKeywordNotFoundException() {
        // arrange
        when(keywordMapper.findById(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.deactivateKeyword(999L))
                .isInstanceOf(SafetyKeywordNotFoundException.class);

        verify(keywordMapper, never()).deactivateById(any());
    }
}
