package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.common.dto.RevisionDiffResponse;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PostHistoryDetail;
import kr.co.ircp.cms.domain.board.dto.PostHistoryItem;

import java.util.List;

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

    /**
     * 두 version 간 title·content_html 라인 diff 비교.
     *
     * <p>SPEC-CMS-CONTENT-REVISION-001 M2 (REQ-REV-003, AC-003-1/2) — 필드별로
     * {@link RevisionDiffResponse}를 만들어 리스트(title, content 순)로 반환한다.
     * (postId, fromVersion) 또는 (postId, toVersion) 스냅샷이 없으면
     * {@link kr.co.ircp.cms.domain.board.exception.PostHistoryVersionNotFoundException}(404).
     *
     * @param postId      게시글 ID
     * @param fromVersion 비교 기준 이전 version
     * @param toVersion   비교 기준 이후 version
     */
    List<RevisionDiffResponse> diff(Long postId, int fromVersion, int toVersion);

    /**
     * 특정 버전의 본문(title + content_html)으로 게시물을 롤백한다.
     *
     * <p>SPEC-CMS-CONTENT-REVISION-001 M3 — 롤백 자체가 하나의 새 리비전이다.
     * 롤백 직전 상태를 이력에 보존한 뒤, 대상 버전의 스냅샷으로 게시물을 복원하고
     * 게시물 version 을 1 증가시킨다. 낙관적 잠금을 적용한다.
     *
     * <ul>
     *   <li>{@code (postId, rollbackToVersion)} 스냅샷이 없으면
     *       {@link kr.co.ircp.cms.domain.board.exception.PostHistoryVersionNotFoundException}(404).</li>
     *   <li>{@code expectedVersion} 이 게시물 현재 version 과 다르면
     *       {@link kr.co.ircp.cms.common.exception.RevisionConflictException}(409).</li>
     * </ul>
     *
     * @param postId           게시물 ID
     * @param rollbackToVersion 복원할 이력 버전
     * @param expectedVersion  클라이언트가 알고 있는 게시물 현재 version
     * @return 롤백 후 게시물 상태(version = 직전 version + 1, title/contentHtml = 복원 값)
     */
    PostHistoryDetail rollback(Long postId, int rollbackToVersion, int expectedVersion);
}
