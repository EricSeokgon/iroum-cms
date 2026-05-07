package kr.co.ircp.cms.domain.system.code.service;

import kr.co.ircp.cms.domain.system.code.dto.BulkCodesResponse;
import kr.co.ircp.cms.domain.system.code.dto.CodeRequest;
import kr.co.ircp.cms.domain.system.code.dto.CodeResponse;
import kr.co.ircp.cms.domain.system.code.entity.Code;
import kr.co.ircp.cms.domain.system.code.exception.CodeDuplicateException;
import kr.co.ircp.cms.domain.system.code.mapper.CodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CodeService GREEN 테스트.
 * REQ-SYSTEM-004-D: 공통코드 CRUD + UNIQUE 중복 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CodeService GREEN 테스트 (REQ-SYSTEM-004-D)")
class CodeServiceTest {

    @Mock private CodeMapper codeMapper;

    private CodeServiceImpl codeService;

    @BeforeEach
    void setUp() {
        codeService = new CodeServiceImpl(codeMapper);
    }

    private Code sampleCode(Long id, String groupCode, String code) {
        return Code.builder()
                .id(id)
                .groupCode(groupCode)
                .code(code)
                .name("테스트 코드")
                .sortOrder(0)
                .status("ACTIVE")
                .build();
    }

    // ──────────────────────────────────────────────
    // create()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("create() — 중복 없으면 mapper.insert 호출")
    void create_inserts_when_no_duplicate() {
        // given
        CodeRequest req = new CodeRequest("GRP01", "CD001", "코드명", "설명", 0, null);
        when(codeMapper.existsByGroupCodeAndCode("GRP01", "CD001")).thenReturn(false);
        doNothing().when(codeMapper).insert(any());
        when(codeMapper.findActiveByGroupCode("GRP01"))
                .thenReturn(List.of(sampleCode(1L, "GRP01", "CD001")));

        // when
        CodeResponse result = codeService.create(req);

        // then
        verify(codeMapper).insert(any());
        assertThat(result.code()).isEqualTo("CD001");
    }

    @Test
    @DisplayName("create() — 중복 코드이면 CodeDuplicateException")
    void create_throws_when_duplicate() {
        // given
        CodeRequest req = new CodeRequest("GRP01", "CD001", "코드명", null, null, null);
        when(codeMapper.existsByGroupCodeAndCode("GRP01", "CD001")).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> codeService.create(req))
                .isInstanceOf(CodeDuplicateException.class);
        verify(codeMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create() — sortOrder null 시 0 기본값으로 INSERT")
    void create_nullSortOrder_defaultsToZero() {
        // given
        CodeRequest req = new CodeRequest("GRP01", "CD002", "코드명", "설명", null, "{\"k\":\"v\"}");
        when(codeMapper.existsByGroupCodeAndCode("GRP01", "CD002")).thenReturn(false);
        when(codeMapper.findActiveByGroupCode("GRP01"))
                .thenReturn(List.of(sampleCode(1L, "GRP01", "CD002")));

        // when
        codeService.create(req);

        // then — INSERT된 Code 캡처
        ArgumentCaptor<Code> captor = ArgumentCaptor.forClass(Code.class);
        verify(codeMapper).insert(captor.capture());
        Code inserted = captor.getValue();
        assertThat(inserted.getSortOrder()).isZero();
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getExtraData()).isEqualTo("{\"k\":\"v\"}");
    }

    @Test
    @DisplayName("create() — INSERT 후 재조회 결과에서 코드 미발견 시 NoSuchElementException")
    void create_postLookup_emptyStream_throws() {
        // given — INSERT 했지만 조회 시 다른 코드만 반환되는 비정상 상황
        CodeRequest req = new CodeRequest("GRP01", "CD003", "코드명", null, 5, null);
        when(codeMapper.existsByGroupCodeAndCode("GRP01", "CD003")).thenReturn(false);
        when(codeMapper.findActiveByGroupCode("GRP01"))
                .thenReturn(List.of(sampleCode(1L, "GRP01", "OTHER")));

        // when / then
        assertThatThrownBy(() -> codeService.create(req))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ──────────────────────────────────────────────
    // getById()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getById() — 존재하지 않으면 NoSuchElementException")
    void getById_throws_when_not_found() {
        // given
        when(codeMapper.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> codeService.getById(99L))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("getById() — 존재하면 CodeResponse 반환")
    void getById_returns_response_when_found() {
        // given
        when(codeMapper.findById(1L)).thenReturn(Optional.of(sampleCode(1L, "GRP01", "CD001")));

        // when
        CodeResponse result = codeService.getById(1L);

        // then
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.code()).isEqualTo("CD001");
    }

    // ──────────────────────────────────────────────
    // listByGroup()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("listByGroup() — groupCode에 해당하는 ACTIVE 코드 목록 반환")
    void listByGroup_returns_active_codes() {
        // given
        when(codeMapper.findActiveByGroupCode("GRP01"))
                .thenReturn(List.of(
                        sampleCode(1L, "GRP01", "A"),
                        sampleCode(2L, "GRP01", "B")));

        // when
        List<CodeResponse> result = codeService.listByGroup("GRP01");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).code()).isEqualTo("A");
    }

    @Test
    @DisplayName("listByGroup() — 결과 없으면 빈 리스트")
    void listByGroup_emptyResult() {
        // given
        when(codeMapper.findActiveByGroupCode("UNKNOWN")).thenReturn(List.of());

        // when
        List<CodeResponse> result = codeService.listByGroup("UNKNOWN");

        // then
        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    // bulkByGroups()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("bulkByGroups() — 여러 그룹의 코드를 groupCode별 Map으로 그룹화")
    void bulkByGroups_groupsByGroupCode() {
        // given
        List<String> groups = List.of("GRP01", "GRP02");
        when(codeMapper.findActiveByGroupCodes(groups))
                .thenReturn(List.of(
                        sampleCode(1L, "GRP01", "A"),
                        sampleCode(2L, "GRP01", "B"),
                        sampleCode(3L, "GRP02", "X")
                ));

        // when
        BulkCodesResponse result = codeService.bulkByGroups(groups);

        // then
        assertThat(result.codes()).containsKeys("GRP01", "GRP02");
        assertThat(result.codes().get("GRP01")).hasSize(2);
        assertThat(result.codes().get("GRP02")).hasSize(1);
    }

    @Test
    @DisplayName("bulkByGroups() — 결과 없으면 빈 Map")
    void bulkByGroups_empty() {
        // given
        when(codeMapper.findActiveByGroupCodes(any())).thenReturn(List.of());

        // when
        BulkCodesResponse result = codeService.bulkByGroups(List.of("X"));

        // then
        assertThat(result.codes()).isEmpty();
    }

    // ──────────────────────────────────────────────
    // update()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("update() — 존재하지 않으면 NoSuchElementException")
    void update_throws_when_not_found() {
        // given
        when(codeMapper.findById(99L)).thenReturn(Optional.empty());
        CodeRequest req = new CodeRequest("GRP01", "CD001", "이름", null, null, null);

        // when / then
        assertThatThrownBy(() -> codeService.update(99L, req))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("update() — code 변경 시 자기 자신 제외 중복 체크 (중복이면 예외)")
    void update_codeChanged_duplicateExcludeId_throws() {
        // given
        Code existing = sampleCode(1L, "GRP01", "OLD");
        when(codeMapper.findById(1L)).thenReturn(Optional.of(existing));
        when(codeMapper.existsByGroupCodeAndCodeExcludeId("GRP01", "NEW", 1L))
                .thenReturn(true);

        CodeRequest req = new CodeRequest("GRP01", "NEW", "이름", null, null, null);

        // when / then
        assertThatThrownBy(() -> codeService.update(1L, req))
                .isInstanceOf(CodeDuplicateException.class);
        verify(codeMapper, never()).update(any());
    }

    @Test
    @DisplayName("update() — code 동일 시 중복 체크 생략하고 update 호출")
    void update_codeUnchanged_noDuplicateCheck() {
        // given — 동일 code로 업데이트
        Code existing = sampleCode(1L, "GRP01", "CD001");
        when(codeMapper.findById(1L)).thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(existing));
        CodeRequest req = new CodeRequest("GRP01", "CD001", "수정된 이름", "수정 설명", 9, null);

        // when
        codeService.update(1L, req);

        // then — duplicate check 호출 안 됨
        verify(codeMapper, never()).existsByGroupCodeAndCodeExcludeId(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong());
        verify(codeMapper).update(any());
    }

    @Test
    @DisplayName("update() — sortOrder null 시 기존 sortOrder 유지")
    void update_nullSortOrder_keepsExisting() {
        // given
        Code existing = Code.builder()
                .id(1L).groupCode("GRP01").code("CD001")
                .name("기존").sortOrder(7).status("ACTIVE").build();
        when(codeMapper.findById(1L)).thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(existing));
        CodeRequest req = new CodeRequest("GRP01", "CD001", "수정 이름", null, null, null);

        // when
        codeService.update(1L, req);

        // then
        ArgumentCaptor<Code> captor = ArgumentCaptor.forClass(Code.class);
        verify(codeMapper).update(captor.capture());
        assertThat(captor.getValue().getSortOrder()).isEqualTo(7);
    }

    @Test
    @DisplayName("update() — 정상 흐름에서 update 호출 후 재조회 결과 반환")
    void update_happyPath_returnsResponse() {
        // given
        Code existing = sampleCode(1L, "GRP01", "CD001");
        Code updated = Code.builder().id(1L).groupCode("GRP01").code("CD001")
                .name("수정 이름").sortOrder(0).status("ACTIVE").build();
        when(codeMapper.findById(1L))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(updated));
        CodeRequest req = new CodeRequest("GRP01", "CD001", "수정 이름", null, 0, null);

        // when
        CodeResponse result = codeService.update(1L, req);

        // then
        assertThat(result.name()).isEqualTo("수정 이름");
    }

    // ──────────────────────────────────────────────
    // delete()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("delete() — 존재하지 않으면 NoSuchElementException")
    void delete_throws_when_not_found() {
        // given
        when(codeMapper.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> codeService.delete(99L))
                .isInstanceOf(NoSuchElementException.class);
        verify(codeMapper, never()).delete(any());
    }

    @Test
    @DisplayName("delete() — 존재하면 mapper.delete 호출")
    void delete_calls_mapper_delete() {
        // given
        when(codeMapper.findById(1L)).thenReturn(Optional.of(sampleCode(1L, "GRP01", "CD001")));

        // when
        codeService.delete(1L);

        // then
        verify(codeMapper).delete(1L);
    }
}
