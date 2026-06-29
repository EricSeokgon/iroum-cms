package kr.co.ircp.cms.common.service;

/**
 * 콘텐츠 리비전 이력 보존 정책 서비스.
 *
 * <p>SPEC-CMS-CONTENT-REVISION-001 M3 (REQ-REV-006) — 게시물·페이지 이력이 누적될 때
 * {@code system_setting} 의 {@code content.revision.maxPerEntity}(기본 50) 개수를 초과하면
 * 가장 오래된 이력을 정리한다.
 *
 * <p>저장(write) 경로에서 best-effort 로 호출된다 — 보존 정리 실패는 로깅하되
 * 저장 트랜잭션 결과에 영향을 주지 않는다(호출자에게 예외를 던지지 않음).
 */
// @MX:ANCHOR: [AUTO] RevisionRetentionService — 게시물·페이지 도메인이 공유하는 리비전 보존 정책 경계
// @MX:REASON: PostServiceImpl.updatePost 와 PageServiceImpl.updatePage 의 저장 후 finalizer 로 호출되는
//             공용 API 경계(common.service). best-effort 계약(예외 미전파)을 두 도메인에 일관되게 보장.
// @MX:SPEC: SPEC-CMS-CONTENT-REVISION-001 REQ-REV-006
public interface RevisionRetentionService {

    /**
     * 게시물 이력 보존 정리. 이력 수가 최대치를 넘으면 오래된 항목을 삭제한다.
     * 어떤 이유로든 실패하면 로깅 후 조용히 반환한다(예외 미전파).
     *
     * @param postId 게시물 ID
     */
    void prunePostHistory(Long postId);

    /**
     * 페이지 이력 보존 정리. 이력 수가 최대치를 넘으면 오래된 항목을 삭제한다.
     * 어떤 이유로든 실패하면 로깅 후 조용히 반환한다(예외 미전파).
     *
     * @param pageId 페이지 ID
     */
    void prunePageHistory(Long pageId);
}
