package kr.co.ircp.cms.domain.system.code.service;

import kr.co.ircp.cms.domain.system.code.dto.CodeGroupRequest;
import kr.co.ircp.cms.domain.system.code.dto.CodeGroupResponse;
import kr.co.ircp.cms.domain.system.code.entity.CodeGroup;
import kr.co.ircp.cms.domain.system.code.exception.CodeGroupInUseException;
import kr.co.ircp.cms.domain.system.code.mapper.CodeGroupMapper;
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
 * CodeGroupService GREEN 테스트.
 * REQ-SYSTEM-004-D: 코드 그룹 CRUD + RESTRICT 삭제 제한
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CodeGroupService GREEN 테스트 (REQ-SYSTEM-004-D)")
class CodeGroupServiceTest {

    @Mock private CodeGroupMapper codeGroupMapper;

    private CodeGroupServiceImpl codeGroupService;

    @BeforeEach
    void setUp() {
        codeGroupService = new CodeGroupServiceImpl(codeGroupMapper);
    }

    private CodeGroup sampleGroup(Long id, String groupCode) {
        return CodeGroup.builder()
                .id(id)
                .groupCode(groupCode)
                .name("테스트 그룹")
                .description("설명")
                .status("ACTIVE")
                .build();
    }

    // ──────────────────────────────────────────────
    // create()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("create() — mapper.insert 후 findByGroupCode 결과 반환")
    void create_inserts_and_returns() {
        // given
        CodeGroupRequest req = new CodeGroupRequest("GRP01", "그룹명", "설명");
        doNothing().when(codeGroupMapper).insert(any());
        when(codeGroupMapper.findByGroupCode("GRP01"))
                .thenReturn(Optional.of(sampleGroup(1L, "GRP01")));

        // when
        CodeGroupResponse result = codeGroupService.create(req);

        // then
        verify(codeGroupMapper).insert(any());
        assertThat(result.groupCode()).isEqualTo("GRP01");
    }

    @Test
    @DisplayName("create() — INSERT 후 재조회 시 데이터 미존재이면 NoSuchElementException")
    void create_postLookup_missing_throws() {
        // given
        CodeGroupRequest req = new CodeGroupRequest("GRP01", "그룹명", null);
        doNothing().when(codeGroupMapper).insert(any());
        when(codeGroupMapper.findByGroupCode("GRP01")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> codeGroupService.create(req))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("create() — status는 ACTIVE 자동 설정")
    void create_setsActiveStatus() {
        // given
        CodeGroupRequest req = new CodeGroupRequest("GRP01", "그룹명", "설명");
        doNothing().when(codeGroupMapper).insert(any());
        when(codeGroupMapper.findByGroupCode("GRP01"))
                .thenReturn(Optional.of(sampleGroup(1L, "GRP01")));

        // when
        codeGroupService.create(req);

        // then — INSERT된 그룹의 status가 ACTIVE인지
        ArgumentCaptor<CodeGroup> captor = ArgumentCaptor.forClass(CodeGroup.class);
        verify(codeGroupMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    // ──────────────────────────────────────────────
    // getById()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getById() — 존재하면 CodeGroupResponse 반환")
    void getById_returns_response() {
        // given
        when(codeGroupMapper.findById(1L)).thenReturn(Optional.of(sampleGroup(1L, "GRP01")));

        // when
        CodeGroupResponse result = codeGroupService.getById(1L);

        // then
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.groupCode()).isEqualTo("GRP01");
    }

    @Test
    @DisplayName("getById() — 존재하지 않으면 NoSuchElementException")
    void getById_throws_when_not_found() {
        // given
        when(codeGroupMapper.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> codeGroupService.getById(99L))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ──────────────────────────────────────────────
    // listAll()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("listAll() — 전체 그룹 목록 반환")
    void listAll_returnsAll() {
        // given
        when(codeGroupMapper.findAll()).thenReturn(List.of(
                sampleGroup(1L, "GRP01"),
                sampleGroup(2L, "GRP02")
        ));

        // when
        List<CodeGroupResponse> result = codeGroupService.listAll();

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(CodeGroupResponse::groupCode)
                .containsExactly("GRP01", "GRP02");
    }

    @Test
    @DisplayName("listAll() — 결과 없으면 빈 리스트")
    void listAll_empty() {
        // given
        when(codeGroupMapper.findAll()).thenReturn(List.of());

        // when
        List<CodeGroupResponse> result = codeGroupService.listAll();

        // then
        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    // update()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("update() — 존재하지 않으면 NoSuchElementException")
    void update_throws_when_not_found() {
        // given
        when(codeGroupMapper.findById(99L)).thenReturn(Optional.empty());
        CodeGroupRequest req = new CodeGroupRequest("GRP01", "이름", null);

        // when / then
        assertThatThrownBy(() -> codeGroupService.update(99L, req))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("update() — groupCode는 변경되지 않고 기존 값 유지")
    void update_keepsOriginalGroupCode() {
        // given
        CodeGroup existing = sampleGroup(1L, "ORIGINAL");
        when(codeGroupMapper.findById(1L)).thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(existing));
        CodeGroupRequest req = new CodeGroupRequest("CHANGED", "새이름", "새설명");

        // when
        codeGroupService.update(1L, req);

        // then — update에 전달된 객체의 groupCode가 ORIGINAL인지
        ArgumentCaptor<CodeGroup> captor = ArgumentCaptor.forClass(CodeGroup.class);
        verify(codeGroupMapper).update(captor.capture());
        assertThat(captor.getValue().getGroupCode()).isEqualTo("ORIGINAL");
        assertThat(captor.getValue().getName()).isEqualTo("새이름");
    }

    // ──────────────────────────────────────────────
    // delete()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("delete() — 그룹에 코드가 있으면 CodeGroupInUseException")
    void delete_throws_when_codes_exist() {
        // given
        when(codeGroupMapper.findById(1L))
                .thenReturn(Optional.of(sampleGroup(1L, "GRP01")));
        when(codeGroupMapper.hasCodesInGroup("GRP01")).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> codeGroupService.delete(1L))
                .isInstanceOf(CodeGroupInUseException.class);
        verify(codeGroupMapper, never()).delete(any());
    }

    @Test
    @DisplayName("delete() — 코드 없으면 mapper.delete 호출")
    void delete_calls_mapper_when_no_codes() {
        // given
        when(codeGroupMapper.findById(1L))
                .thenReturn(Optional.of(sampleGroup(1L, "GRP01")));
        when(codeGroupMapper.hasCodesInGroup("GRP01")).thenReturn(false);

        // when
        codeGroupService.delete(1L);

        // then
        verify(codeGroupMapper).delete(1L);
    }

    @Test
    @DisplayName("delete() — 존재하지 않으면 NoSuchElementException")
    void delete_throws_when_not_found() {
        // given
        when(codeGroupMapper.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> codeGroupService.delete(99L))
                .isInstanceOf(NoSuchElementException.class);
    }
}
