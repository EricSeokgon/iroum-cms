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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
                .status("ACTIVE")
                .build();
    }

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
        org.assertj.core.api.Assertions.assertThat(result.groupCode()).isEqualTo("GRP01");
    }

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
}
