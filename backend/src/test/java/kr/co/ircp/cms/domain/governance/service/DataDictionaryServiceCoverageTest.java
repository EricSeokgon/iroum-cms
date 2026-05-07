package kr.co.ircp.cms.domain.governance.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.governance.entity.DataDictionary;
import kr.co.ircp.cms.domain.governance.entity.DataDictionaryHistory;
import kr.co.ircp.cms.domain.governance.repository.DataDictionaryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPEC-CMS-009 REQ-GOV-001~005: DataDictionaryService 커버리지 보강 테스트.
 *
 * <p>findById, findAll, findByTable, findFiltered, findHistory, create, softDelete,
 * exportDictionary(csv/xlsx), compareWithSchema 등 추가 메서드를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataDictionaryService — 커버리지 보강")
class DataDictionaryServiceCoverageTest {

    @Mock
    private DataDictionaryMapper mapper;

    @InjectMocks
    private DataDictionaryService service;

    private DataDictionary sampleDictionary() {
        return DataDictionary.builder()
                .id(10L)
                .tableName("users")
                .columnName("email")
                .logicalNameKo("이메일")
                .logicalNameEn("Email")
                .dataDomain("MASTER")
                .dataType("VARCHAR(100)")
                .description("사용자 이메일")
                .isPii(true)
                .isRequired(true)
                .status("ACTIVE")
                .build();
    }

    // ──────────────────────────────────────────────
    // 단순 조회 메서드
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("findById — 매퍼 위임")
    void findById_delegatesToMapper() {
        DataDictionary d = sampleDictionary();
        when(mapper.findById(10L)).thenReturn(Optional.of(d));

        Optional<DataDictionary> result = service.findById(10L);

        assertThat(result).contains(d);
    }

    @Test
    @DisplayName("findByTableAndColumn — 매퍼 위임")
    void findByTableAndColumn_delegatesToMapper() {
        DataDictionary d = sampleDictionary();
        when(mapper.findByTableAndColumn("users", "email")).thenReturn(Optional.of(d));

        Optional<DataDictionary> result = service.findByTableAndColumn("users", "email");

        assertThat(result).contains(d);
    }

    @Test
    @DisplayName("findAll — 전체 조회")
    void findAll_returnsAll() {
        when(mapper.findAll()).thenReturn(List.of(sampleDictionary()));

        List<DataDictionary> result = service.findAll();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("findByTable — 테이블별 조회")
    void findByTable_returnsByTable() {
        when(mapper.findByTable("users")).thenReturn(List.of(sampleDictionary()));

        List<DataDictionary> result = service.findByTable("users");

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("findHistory — 매퍼 위임")
    void findHistory_delegatesToMapper() {
        DataDictionaryHistory h = DataDictionaryHistory.builder().id(1L).dictionaryId(10L).build();
        when(mapper.findHistory(10L)).thenReturn(List.of(h));

        List<DataDictionaryHistory> result = service.findHistory(10L);

        assertThat(result).hasSize(1);
    }

    // ──────────────────────────────────────────────
    // findFiltered — 페이징
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("findFiltered — 페이징 + 카운트")
    void findFiltered_returnsPagedResponse() {
        when(mapper.findFiltered(any())).thenReturn(List.of(sampleDictionary()));
        when(mapper.countFiltered(any())).thenReturn(1);

        PageResponse<DataDictionary> page = service.findFiltered("users", "MASTER", "ACTIVE", 0, 10);

        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.content()).hasSize(1);
    }

    @Test
    @DisplayName("findFiltered — null 인자 안전 처리")
    void findFiltered_nullArgs_safe() {
        when(mapper.findFiltered(any())).thenReturn(List.of());
        when(mapper.countFiltered(any())).thenReturn(0);

        PageResponse<DataDictionary> page = service.findFiltered(null, null, null, 0, 10);

        assertThat(page.totalElements()).isEqualTo(0L);
    }

    // ──────────────────────────────────────────────
    // CRUD: create / softDelete
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("create — 매퍼 INSERT 위임")
    void create_delegatesToMapper() {
        DataDictionary d = sampleDictionary();

        DataDictionary result = service.create(d);

        verify(mapper, times(1)).insert(d);
        assertThat(result).isSameAs(d);
    }

    @Test
    @DisplayName("update — 비존재 시 IllegalArgumentException")
    void update_missingId_throws() {
        DataDictionary updated = sampleDictionary();
        when(mapper.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(updated, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data_dictionary not found");
    }

    @Test
    @DisplayName("softDelete — 1건 삭제")
    void softDelete_returnsTrueWhenAffected() {
        when(mapper.softDelete(10L)).thenReturn(1);

        boolean result = service.softDelete(10L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("softDelete — 0건 삭제 시 false")
    void softDelete_returnsFalseWhenNoneAffected() {
        when(mapper.softDelete(10L)).thenReturn(0);

        boolean result = service.softDelete(10L);

        assertThat(result).isFalse();
    }

    // ──────────────────────────────────────────────
    // exportDictionary — REQ-GOV-005
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("exportDictionary CSV — 헤더 + 행 직렬화")
    void exportDictionary_csv_returnsCsvBytes() {
        when(mapper.findAllForExport()).thenReturn(List.of(sampleDictionary()));

        byte[] data = service.exportDictionary("csv");

        String csv = new String(data, StandardCharsets.UTF_8);
        assertThat(csv).contains("테이블명,컬럼명");
        assertThat(csv).contains("users").contains("email").contains("이메일");
    }

    @Test
    @DisplayName("exportDictionary CSV — 콤마/따옴표 escape")
    void exportDictionary_csv_escapesSpecialChars() {
        DataDictionary tricky = DataDictionary.builder()
                .id(1L).tableName("a,b").columnName("c\"d").logicalNameKo("줄\n바꿈")
                .dataDomain("MASTER").dataType("VARCHAR")
                .isPii(false).status("ACTIVE")
                .build();
        when(mapper.findAllForExport()).thenReturn(List.of(tricky));

        byte[] data = service.exportDictionary("csv");

        String csv = new String(data, StandardCharsets.UTF_8);
        assertThat(csv).contains("\"a,b\"");
        assertThat(csv).contains("\"c\"\"d\"");
        assertThat(csv).contains("\"줄\n바꿈\"");
    }

    @Test
    @DisplayName("exportDictionary CSV — null 필드 안전 처리")
    void exportDictionary_csv_handlesNulls() {
        DataDictionary nulls = DataDictionary.builder()
                .id(1L).tableName(null).columnName(null).logicalNameKo(null)
                .dataDomain(null).dataType(null).description(null)
                .isPii(null).status("ACTIVE")
                .build();
        when(mapper.findAllForExport()).thenReturn(List.of(nulls));

        byte[] data = service.exportDictionary("csv");

        String csv = new String(data, StandardCharsets.UTF_8);
        assertThat(csv).contains("N");  // isPii false fallback when null
    }

    @Test
    @DisplayName("exportDictionary XLSX — SXSSFWorkbook 바이트 반환")
    void exportDictionary_xlsx_returnsBinary() {
        when(mapper.findAllForExport()).thenReturn(List.of(sampleDictionary()));

        byte[] data = service.exportDictionary("xlsx");

        assertThat(data).isNotEmpty();
        // PK ZIP magic header (XLSX is a ZIP archive)
        assertThat(data[0]).isEqualTo((byte) 0x50);
        assertThat(data[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    @DisplayName("exportDictionary XLSX — null 필드 안전 처리")
    void exportDictionary_xlsx_handlesNulls() {
        DataDictionary nulls = DataDictionary.builder()
                .id(1L).tableName(null).columnName(null).logicalNameKo(null)
                .dataDomain(null).dataType(null).description(null)
                .isPii(null).status("ACTIVE")
                .build();
        when(mapper.findAllForExport()).thenReturn(List.of(nulls));

        byte[] data = service.exportDictionary("xlsx");

        assertThat(data).isNotEmpty();
    }

    // ──────────────────────────────────────────────
    // compareWithSchema
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("compareWithSchema — 등록/실제 스키마 카운트 + 누락 컬럼")
    void compareWithSchema_returnsCounts() {
        DataDictionary registered = DataDictionary.builder()
                .id(10L).tableName("users").columnName("email")
                .build();
        DataDictionaryMapper.SchemaColumn registeredCol = new DataDictionaryMapper.SchemaColumn();
        registeredCol.setTableName("users");
        registeredCol.setColumnName("email");
        DataDictionaryMapper.SchemaColumn missingCol = new DataDictionaryMapper.SchemaColumn();
        missingCol.setTableName("users");
        missingCol.setColumnName("status");

        when(mapper.findAll()).thenReturn(List.of(registered));
        when(mapper.findActualSchemaColumns()).thenReturn(List.of(registeredCol, missingCol));

        Map<String, Object> result = service.compareWithSchema();

        assertThat(result).containsEntry("registeredCount", 1);
        assertThat(result).containsEntry("actualCount", 2);
        assertThat(result).containsEntry("missingInDictionary", 1);
        @SuppressWarnings("unchecked")
        List<String> samples = (List<String>) result.get("missingSamples");
        assertThat(samples).contains("users.status");
        assertThat(samples).doesNotContain("users.email");
    }

    @Test
    @DisplayName("compareWithSchema — 모든 컬럼 등록 시 missing=0")
    void compareWithSchema_allRegistered_zeroMissing() {
        DataDictionary registered = DataDictionary.builder()
                .id(10L).tableName("users").columnName("email")
                .build();
        DataDictionaryMapper.SchemaColumn col = new DataDictionaryMapper.SchemaColumn();
        col.setTableName("users");
        col.setColumnName("email");

        when(mapper.findAll()).thenReturn(List.of(registered));
        when(mapper.findActualSchemaColumns()).thenReturn(List.of(col));

        Map<String, Object> result = service.compareWithSchema();

        assertThat(result).containsEntry("missingInDictionary", 0);
        @SuppressWarnings("unchecked")
        List<String> samples = (List<String>) result.get("missingSamples");
        assertThat(samples).isEmpty();
    }

    // ──────────────────────────────────────────────
    // update — 변경 이력 적재 추가 케이스 (필드별)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("update — description, status 등 다양한 필드 변경 시 history 적재")
    void update_recordsHistory_forVariousFields() {
        DataDictionary before = DataDictionary.builder()
                .id(10L).tableName("users").columnName("email")
                .logicalNameKo("이메일").logicalNameEn("Email")
                .dataDomain("MASTER").dataType("VARCHAR(100)")
                .description("기존").status("ACTIVE")
                .build();
        DataDictionary updated = DataDictionary.builder()
                .id(10L).tableName("users").columnName("email")
                .logicalNameKo("이메일").logicalNameEn("EmailAddr")  // 변경
                .dataDomain("MASTER").dataType("VARCHAR(100)")
                .description("수정")                                   // 변경
                .status("DEPRECATED")                                  // 변경
                .build();
        when(mapper.findById(10L)).thenReturn(Optional.of(before));

        service.update(updated, 7L);

        ArgumentCaptor<DataDictionaryHistory> captor = ArgumentCaptor.forClass(DataDictionaryHistory.class);
        verify(mapper, times(3)).insertHistory(captor.capture());
        assertThat(captor.getAllValues()).extracting(DataDictionaryHistory::getFieldChanged)
                .containsExactlyInAnyOrder("logical_name_en", "description", "status");
        verify(mapper).update(updated);
    }
}
