package kr.co.ircp.cms.domain.system.code.service;

import kr.co.ircp.cms.domain.system.code.dto.CodeRequest;
import kr.co.ircp.cms.domain.system.code.dto.CodeResponse;
import kr.co.ircp.cms.domain.system.code.entity.Code;
import kr.co.ircp.cms.domain.system.code.exception.CodeDuplicateException;
import kr.co.ircp.cms.domain.system.code.mapper.CodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    }

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
}
