package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.BbsMasterCreateRequest;
import kr.co.ircp.cms.domain.board.dto.BbsMasterDetail;
import kr.co.ircp.cms.domain.board.dto.BbsMasterSummary;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BbsMasterService RED 단계 테스트.
 * REQ-BOARD-001: 게시판 마스터 CRUD
 *
 * <p>모든 테스트는 Step 2 GREEN 전까지 UnsupportedOperationException으로 실패해야 한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BbsMasterService RED 테스트 (REQ-BOARD-001)")
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
        // RED: Step 2 GREEN에서 구현 필요
        assertThatThrownBy(() -> bbsMasterService.listBoards())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Step 2 GREEN 대기");
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-001-Q-2: 게시판 단건 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시판 단건 조회 — 존재하는 ID 성공")
    void getBoard_existingId_returnsDetail() {
        assertThatThrownBy(() -> bbsMasterService.getBoard(1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("게시판 단건 조회 — 존재하지 않는 ID는 BbsMasterNotFoundException")
    void getBoard_nonExistentId_throwsNotFoundException() {
        // GREEN에서: bbsMasterMapper.findById가 Optional.empty() 반환 시 BbsMasterNotFoundException 발생 검증
        assertThatThrownBy(() -> bbsMasterService.getBoard(999L))
                .isInstanceOf(UnsupportedOperationException.class);
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
        assertThatThrownBy(() -> bbsMasterService.createBoard(request))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("게시판 생성 — 중복 코드 시 DuplicateBbsCodeException")
    void createBoard_duplicateCode_throwsDuplicateBbsCodeException() {
        // GREEN에서: bbsMasterMapper.existsByCode 반환 true 시 예외 검증
        BbsMasterCreateRequest request = new BbsMasterCreateRequest(
                "DUPLICATE", "중복", null, BbsType.NORMAL,
                false, false, 0, 0L, false, false, 20, null, null
        );
        assertThatThrownBy(() -> bbsMasterService.createBoard(request))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-001-U: 게시판 수정
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시판 수정 — 존재하는 ID 성공")
    void updateBoard_existingId_returnsUpdated() {
        assertThatThrownBy(() -> bbsMasterService.updateBoard(1L, null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-001-D: 게시판 삭제
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시판 삭제 — 소프트 삭제 성공")
    void deleteBoard_existingId_softDelete() {
        assertThatThrownBy(() -> bbsMasterService.deleteBoard(1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
