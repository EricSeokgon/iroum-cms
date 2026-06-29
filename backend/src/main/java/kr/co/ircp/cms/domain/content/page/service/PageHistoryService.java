package kr.co.ircp.cms.domain.content.page.service;

import kr.co.ircp.cms.common.dto.RevisionDiffResponse;
import kr.co.ircp.cms.domain.content.page.dto.PageHistoryResponse;

import java.util.List;

/**
 * 페이지 이력 서비스 인터페이스.
 * REQ-CONTENT-005-D-6/7: 이력 조회, 롤백
 */
public interface PageHistoryService {

    /** 이력 목록 조회 (version DESC) */
    List<PageHistoryResponse> listHistory(Long pageId);

    /** 특정 버전 이력 조회 */
    PageHistoryResponse getHistory(Long pageId, int version);

    /**
     * 두 version 간 title·slug 라인 diff 비교.
     *
     * <p>SPEC-CMS-CONTENT-REVISION-001 M2 (REQ-REV-003, AC-003-3) — 페이지 스냅샷(JSONB)을
     * 평탄화하여 필드(slug, title)별 {@link RevisionDiffResponse}를 리스트로 반환한다.
     * 게시물과 달리 slug diff를 포함한다. (pageId, version) 부재 시
     * {@link kr.co.ircp.cms.domain.content.page.exception.PageHistoryVersionNotFoundException}(404).
     *
     * @param pageId      페이지 ID
     * @param fromVersion 비교 기준 이전 version
     * @param toVersion   비교 기준 이후 version
     */
    List<RevisionDiffResponse> diff(Long pageId, int fromVersion, int toVersion);
}
