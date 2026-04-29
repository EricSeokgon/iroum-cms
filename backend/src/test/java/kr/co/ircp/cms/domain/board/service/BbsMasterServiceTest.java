package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.BbsMasterCreateRequest;
import kr.co.ircp.cms.domain.board.dto.BbsMasterDetail;
import kr.co.ircp.cms.domain.board.dto.BbsMasterSummary;
import kr.co.ircp.cms.domain.board.dto.BbsMasterUpdateRequest;
import kr.co.ircp.cms.domain.board.entity.BbsMaster;
import kr.co.ircp.cms.domain.board.entity.BbsType;
import kr.co.ircp.cms.domain.board.exception.BbsMasterNotFoundException;
import kr.co.ircp.cms.domain.board.exception.DuplicateBbsCodeException;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BbsMasterService GREEN 단계 테스트.
 * REQ-BOARD-001: 게시판 마스터 CRUD
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BbsMasterService GREEN 테스트 (REQ-BOARD-001)")
class BbsMasterServiceTest {

    @Mock
    private BbsMasterMapper bbsMasterMapper;

    private BbsMasterService bbsMasterService;

    @BeforeEach
    void setUp() {
        bbsMasterService = new BbsMasterServiceImpl(bbsMasterMapper);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-001-Q-1: 게시판 목록 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시판 목록 조회 — 활성 게시판 반환")
    void listBoards_returnsActiveBoardList() {
        BbsMaster board = BbsMaster.builder()
                .id(1L).code("NOTICE").name("공지사항")
                .type("NOTICE").status("ACTIVE")
                .useComment(true).useAttachment(true)
                .build();
        when(bbsMasterMapper.findAll()).thenReturn(List.of(board));

        List<BbsMasterSummary> result = bbsMasterService.listBoards();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("NOTICE");
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-001-Q-2: 게시판 단건 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시판 단건 조회 — 존재하는 ID 성공")
    void getBoard_existingId_returnsDetail() {
        BbsMaster board = BbsMaster.builder()
                .id(1L).code("NOTICE").name("공지사항")
                .type("NOTICE").status("ACTIVE")
                .build();
        when(bbsMasterMapper.findById(1L)).thenReturn(Optional.of(board));

        BbsMasterDetail result = bbsMasterService.getBoard(1L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.code()).isEqualTo("NOTICE");
    }

    @Test
    @DisplayName("게시판 단건 조회 — 존재하지 않는 ID는 BbsMasterNotFoundException")
    void getBoard_nonExistentId_throwsNotFoundException() {
        when(bbsMasterMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bbsMasterService.getBoard(999L))
                .isInstanceOf(BbsMasterNotFoundException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-001-C: 게시판 생성
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시판 생성 — 새 코드 성공")
    void createBoard_newCode_success() {
        BbsMasterCreateRequest request = new BbsMasterCreateRequest(
                "NOTICE", "공지사항", "공지사항 게시판", BbsType.NOTICE,
                true, true, 5, 10240L, false, false, 20, null, null
        );
        when(bbsMasterMapper.existsByCode("NOTICE")).thenReturn(false);

        BbsMasterDetail result = bbsMasterService.createBoard(request);

        assertThat(result).isNotNull();
        assertThat(result.code()).isEqualTo("NOTICE");
        verify(bbsMasterMapper).insert(any());
    }

    @Test
    @DisplayName("게시판 생성 — 중복 코드 시 DuplicateBbsCodeException")
    void createBoard_duplicateCode_throwsDuplicateBbsCodeException() {
        BbsMasterCreateRequest request = new BbsMasterCreateRequest(
                "DUPLICATE", "중복", null, BbsType.NORMAL,
                false, false, 0, 0L, false, false, 20, null, null
        );
        when(bbsMasterMapper.existsByCode("DUPLICATE")).thenReturn(true);

        assertThatThrownBy(() -> bbsMasterService.createBoard(request))
                .isInstanceOf(DuplicateBbsCodeException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-001-U: 게시판 수정
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시판 수정 — 존재하는 ID 성공")
    void updateBoard_existingId_returnsUpdated() {
        BbsMaster existing = BbsMaster.builder()
                .id(1L).code("NOTICE").name("공지사항")
                .type("NOTICE").status("ACTIVE")
                .build();
        when(bbsMasterMapper.findById(1L)).thenReturn(Optional.of(existing));

        BbsMasterUpdateRequest updateReq = new BbsMasterUpdateRequest(
                "수정된 공지사항", null, true, true,
                5, 10240L, false, false, 20, null, null, "ACTIVE"
        );
        BbsMasterDetail result = bbsMasterService.updateBoard(1L, updateReq);

        assertThat(result.name()).isEqualTo("수정된 공지사항");
        verify(bbsMasterMapper).update(any());
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-001-D: 게시판 삭제
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시판 삭제 — 소프트 삭제 성공")
    void deleteBoard_existingId_softDelete() {
        BbsMaster existing = BbsMaster.builder()
                .id(1L).code("NOTICE").name("공지사항")
                .type("NOTICE").status("ACTIVE")
                .build();
        when(bbsMasterMapper.findById(1L)).thenReturn(Optional.of(existing));

        bbsMasterService.deleteBoard(1L);

        verify(bbsMasterMapper).deleteById(1L);
    }
}
