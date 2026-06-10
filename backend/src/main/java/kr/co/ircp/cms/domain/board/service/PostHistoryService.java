package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PostHistoryDetail;
import kr.co.ircp.cms.domain.board.dto.PostHistoryItem;

/**
 * 게시글 버전 히스토리 read 전용 서비스 인터페이스.
 *
 * <p>SPEC-CMS-POST-HISTORY-001 — 적재(write)된 bbs_post_history 스냅샷을 조회만 한다.
 * 복원/롤백 기능은 본 SPEC 범위 외(read-only 뷰어).
 */
public interface PostHistoryService {

    /**
     * 게시글 버전 히스토리 페이징 목록 조회 (version DESC, 본문 제외).
     * REQ-PH-001/002/003 — 이력이 없으면 빈 목록을 반환한다(오류 아님).
     *
     * @param postId 게시글 ID
     * @param page   0-based 페이지 번호
     * @param size   페이지 크기
     */
    PageResponse<PostHistoryItem> getHistory(Long postId, int page, int size);

    /**
     * 특정 버전 단건 본문 조회 (title + content_html).
     * REQ-PH-004/005 — (postId, version) 스냅샷이 없으면 예외(404).
     *
     * @param postId  게시글 ID
     * @param version 버전 번호
     */
    PostHistoryDetail getVersion(Long postId, int version);
}
