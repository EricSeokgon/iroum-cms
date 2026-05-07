package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PublicationCategoryDto;
import kr.co.ircp.cms.domain.board.dto.PublicationCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PublicationDetail;
import kr.co.ircp.cms.domain.board.dto.PublicationSummary;
import kr.co.ircp.cms.domain.board.dto.PublicationUpdateRequest;
import kr.co.ircp.cms.domain.board.dto.ZipDownloadRequest;
import kr.co.ircp.cms.domain.board.dto.ZipDownloadResponse;

import java.util.List;

/**
 * 발간자료(Publication) 서비스 인터페이스.
 * REQ-BOARD-012: 발간자료 카테고리·메타·다운로드 통계·ZIP 아카이브
 */
public interface PublicationService {

    /** REQ-BOARD-012-R: 발간자료 목록 페이징 조회 (필터: year/month/documentType/categoryId/keyword). */
    PageResponse<PublicationSummary> listPublications(
            Integer year,
            Integer month,
            String documentType,
            Long categoryId,
            String keyword,
            int page,
            int size
    );

    /** REQ-BOARD-012-R: 발간자료 단건 조회 (조회수 증가 포함). */
    PublicationDetail getPublication(Long id);

    /** REQ-BOARD-012-C: 발간자료 신규 등록 (bbs_post + bbs_post_publication_meta 동시 INSERT). */
    PublicationDetail createPublication(PublicationCreateRequest req, Long authorId);

    /** REQ-BOARD-012-U: 발간자료 부분 수정. */
    PublicationDetail updatePublication(Long id, PublicationUpdateRequest req);

    /** REQ-BOARD-012-D: 발간자료 삭제 (bbs_post 소프트 삭제). */
    void deletePublication(Long id);

    /** REQ-BOARD-012-D: 카테고리 트리 조회 (parent→children 그룹핑). */
    List<PublicationCategoryDto> getCategories();

    /** REQ-BOARD-012-D-4: ZIP 다운로드 요청 (≤50MB SYNC, >50MB ASYNC, 7일 보관). */
    ZipDownloadResponse requestZipDownload(Long postId, ZipDownloadRequest req, Long requestedBy);
}
