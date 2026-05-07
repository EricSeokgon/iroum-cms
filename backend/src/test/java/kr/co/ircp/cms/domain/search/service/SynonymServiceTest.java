package kr.co.ircp.cms.domain.search.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.search.dto.SynonymCreateRequest;
import kr.co.ircp.cms.domain.search.dto.SynonymUpdateRequest;
import kr.co.ircp.cms.domain.search.entity.SearchSynonym;
import kr.co.ircp.cms.domain.search.exception.DuplicateSynonymException;
import kr.co.ircp.cms.domain.search.exception.SynonymNotFoundException;
import kr.co.ircp.cms.domain.search.exception.SynonymSelfException;
import kr.co.ircp.cms.domain.search.repository.SearchSynonymMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SynonymService GREEN 테스트.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-009: 동의어 CRUD + expandQuery (OR 확장 + 20 토큰 절단).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SynonymService GREEN 테스트 (REQ-SEARCH-009)")
class SynonymServiceTest {

    @Mock
    private SearchSynonymMapper synonymMapper;

    private SynonymService service;

    @BeforeEach
    void setUp() {
        service = new SynonymServiceImpl(synonymMapper);
    }

    private SearchSynonym makeSynonym(Long id, String term, String synonym, String locale) {
        return SearchSynonym.builder()
                .id(id)
                .term(term)
                .synonym(synonym)
                .locale(locale)
                .status("ACTIVE")
                .build();
    }

    // ─── A. CRUD ────────────────────────────────────────────────────────

    @Test
    @DisplayName("listSynonyms — 페이징 응답 반환")
    void listSynonyms_returnsPageResponse() {
        List<SearchSynonym> rows = List.of(
                makeSynonym(1L, "수도", "서울", "ko"),
                makeSynonym(2L, "교통", "교통편", "ko")
        );
        when(synonymMapper.findAllActive("ko", 0, 20)).thenReturn(rows);
        when(synonymMapper.countAllActive("ko")).thenReturn(2L);

        PageResponse<SearchSynonym> resp = service.listSynonyms("ko", 0, 20);

        assertThat(resp.content()).hasSize(2);
        assertThat(resp.totalElements()).isEqualTo(2L);
    }

    @Test
    @DisplayName("createSynonym — 신규 등록 성공")
    void createSynonym_success() {
        SynonymCreateRequest req = new SynonymCreateRequest("수도", "서울", "ko", "테스트");
        when(synonymMapper.existsByTermAndSynonym("수도", "서울", "ko")).thenReturn(false);

        SearchSynonym created = service.createSynonym(req, 100L);

        assertThat(created.getTerm()).isEqualTo("수도");
        assertThat(created.getSynonym()).isEqualTo("서울");
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        verify(synonymMapper).insert(any(SearchSynonym.class));
    }

    @Test
    @DisplayName("createSynonym — term==synonym 자기참조 시 SynonymSelfException")
    void createSynonym_termEqualsSynonym_throwsSynonymSelfException() {
        SynonymCreateRequest req = new SynonymCreateRequest("수도", "수도", "ko", null);

        assertThatThrownBy(() -> service.createSynonym(req, 100L))
                .isInstanceOf(SynonymSelfException.class);
    }

    @Test
    @DisplayName("createSynonym — UNIQUE 중복 시 DuplicateSynonymException")
    void createSynonym_duplicate_throwsDuplicateSynonymException() {
        SynonymCreateRequest req = new SynonymCreateRequest("수도", "서울", "ko", null);
        when(synonymMapper.existsByTermAndSynonym("수도", "서울", "ko")).thenReturn(true);

        assertThatThrownBy(() -> service.createSynonym(req, 100L))
                .isInstanceOf(DuplicateSynonymException.class);
    }

    @Test
    @DisplayName("updateSynonym — 본문 갱신 후 최신 row 반환")
    void updateSynonym_success() {
        SearchSynonym existing = makeSynonym(10L, "수도", "서울", "ko");
        SearchSynonym updated = makeSynonym(10L, "수도", "서울특별시", "ko");
        when(synonymMapper.findById(10L)).thenReturn(Optional.of(existing), Optional.of(updated));

        SynonymUpdateRequest req = new SynonymUpdateRequest("서울특별시", null);
        SearchSynonym result = service.updateSynonym(10L, req, 200L);

        assertThat(result.getSynonym()).isEqualTo("서울특별시");
        verify(synonymMapper).update(eq(10L), eq("서울특별시"), eq(null), eq(200L));
    }

    @Test
    @DisplayName("updateSynonym — 미존재 id 시 SynonymNotFoundException")
    void updateSynonym_nonExistent_throwsSynonymNotFoundException() {
        when(synonymMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSynonym(999L, new SynonymUpdateRequest("x", null), 1L))
                .isInstanceOf(SynonymNotFoundException.class);
    }

    @Test
    @DisplayName("deleteSynonym — soft delete (status=PAUSED)")
    void deleteSynonym_softDeletes() {
        when(synonymMapper.findById(10L)).thenReturn(Optional.of(makeSynonym(10L, "수도", "서울", "ko")));

        service.deleteSynonym(10L, 200L);

        verify(synonymMapper).softDelete(10L, 200L);
    }

    @Test
    @DisplayName("deleteSynonym — 미존재 id 시 SynonymNotFoundException")
    void deleteSynonym_nonExistent_throwsSynonymNotFoundException() {
        when(synonymMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteSynonym(999L, 1L))
                .isInstanceOf(SynonymNotFoundException.class);
    }

    // ─── B. expandQuery ─────────────────────────────────────────────────

    @Test
    @DisplayName("expandQuery — 단일 토큰 + 동의어 OR 확장")
    void expandQuery_singleToken_returnsOriginalPlusSynonyms() {
        when(synonymMapper.findActiveByTerm("수도", "ko")).thenReturn(
                List.of(makeSynonym(1L, "수도", "서울", "ko"))
        );

        String expanded = service.expandQuery("수도", "ko");

        assertThat(expanded).contains("수도");
        assertThat(expanded).contains("서울");
        assertThat(expanded).contains("OR");
    }

    @Test
    @DisplayName("expandQuery — 토큰 20개 한도 절단 (RISK-S-05)")
    void expandQuery_at20TokenLimit_truncates() {
        // 'A' 한 토큰에 동의어 25개 등록 → 결과는 원본 1개 + 동의어 19개로 제한 (총 20개)
        // SynonymService.expandQuery는 입력 query를 그대로 토큰 분리 — 정규화는 SearchService가 책임
        List<SearchSynonym> manySynonyms = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            manySynonyms.add(makeSynonym((long) i, "A", "syn" + i, "ko"));
        }
        when(synonymMapper.findActiveByTerm("A", "ko")).thenReturn(manySynonyms);

        String expanded = service.expandQuery("A", "ko");

        // OR로 분리된 토큰 수 검증
        long orCount = Arrays.stream(expanded.split(" OR ")).count();
        assertThat(orCount).isLessThanOrEqualTo(20);
        assertThat(expanded.split(" OR ")).hasSize(20);
    }
}
