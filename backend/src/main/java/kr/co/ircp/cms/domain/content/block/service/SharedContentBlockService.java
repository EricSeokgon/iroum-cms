package kr.co.ircp.cms.domain.content.block.service;

import kr.co.ircp.cms.domain.content.block.dto.SharedContentBlockRequest;
import kr.co.ircp.cms.domain.content.block.dto.SharedContentBlockResponse;

import java.util.List;

/**
 * 공유 콘텐츠 블록 서비스.
 *
 * <p>SPEC-CMS-CONTENT-BLOCK-001 — 생성/조회/수정/상태변경/삭제/미리보기.
 * Jsoup 살균, EMBED 제공자 검증, 감사 로그 적재를 담당한다.
 */
public interface SharedContentBlockService {

    SharedContentBlockResponse create(SharedContentBlockRequest req, Long actorId);

    List<SharedContentBlockResponse> findAll(String status, String blockType);

    SharedContentBlockResponse findById(Long id);

    SharedContentBlockResponse update(Long id, SharedContentBlockRequest req, Long actorId);

    SharedContentBlockResponse updateStatus(Long id, String status, Long actorId);

    void delete(Long id, Long actorId);

    /** REQ-CB-010 — 살균된 HTML 미리보기. */
    String preview(Long id);
}
