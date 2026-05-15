package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PublicationCategoryDto;
import kr.co.ircp.cms.domain.board.dto.PublicationCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PublicationDetail;
import kr.co.ircp.cms.domain.board.dto.PublicationSummary;
import kr.co.ircp.cms.domain.board.dto.PublicationUpdateRequest;
import kr.co.ircp.cms.domain.board.dto.ZipDownloadRequest;
import kr.co.ircp.cms.domain.board.dto.ZipDownloadResponse;
import kr.co.ircp.cms.domain.board.entity.BbsMaster;
import kr.co.ircp.cms.domain.board.entity.BbsPost;
import kr.co.ircp.cms.domain.board.entity.PublicationCategory;
import kr.co.ircp.cms.domain.board.entity.PublicationMeta;
import kr.co.ircp.cms.domain.board.entity.PublicationZipArchive;
import kr.co.ircp.cms.domain.board.exception.BbsMasterNotFoundException;
import kr.co.ircp.cms.domain.board.exception.PublicationNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import kr.co.ircp.cms.domain.board.repository.PublicationCategoryMapper;
import kr.co.ircp.cms.domain.board.repository.PublicationMetaMapper;
import kr.co.ircp.cms.domain.board.repository.PublicationZipArchiveMapper;
import kr.co.ircp.cms.domain.board.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 발간자료(Publication) 서비스 구현체.
 * REQ-BOARD-012: 발간자료 카테고리·메타·다운로드 통계·ZIP 아카이브
 *
 * // @MX:ANCHOR: [AUTO] PublicationServiceImpl — 발간자료 도메인 핵심 진입점
 * // @MX:REASON: PublicationController 외 ZIP 다운로드·카테고리 트리 등 fan_in >= 3
 * // @MX:SPEC: REQ-BOARD-012
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicationServiceImpl implements PublicationService {

    /** ZIP 동기 모드 임계값 (50MB). 초과 시 ASYNC 모드. */
    private static final long SYNC_SIZE_THRESHOLD_BYTES = 50L * 1024 * 1024;

    /** ZIP 단일 첨부파일 추정 크기 (10MB) — stub heuristic. */
    private static final long STUB_PER_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    /** 발간자료 게시판 마스터 코드 (V19 시드 참조). */
    private static final String PUBLICATION_BBS_CODE = "PUBLICATION";

    private final PublicationMetaMapper publicationMetaMapper;
    private final PublicationCategoryMapper publicationCategoryMapper;
    private final PublicationZipArchiveMapper publicationZipArchiveMapper;
    private final BbsPostMapper bbsPostMapper;
    private final BbsMasterMapper bbsMasterMapper;
    private final HtmlSanitizer htmlSanitizer;

    @Override
    public PageResponse<PublicationSummary> listPublications(
            Integer year, Integer month, String documentType,
            Long categoryId, String keyword, int page, int size) {
        int offset = page * size;
        List<PublicationMeta> rows = publicationMetaMapper.findWithFilters(
                year, month, documentType, categoryId, keyword, offset, size);
        long total = publicationMetaMapper.countWithFilters(
                year, month, documentType, categoryId, keyword);
        List<PublicationSummary> content = rows.stream().map(this::toSummary).toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional
    public PublicationDetail getPublication(Long id) {
        PublicationMeta meta = publicationMetaMapper.findById(id)
                .orElseThrow(() -> new PublicationNotFoundException(id));
        publicationMetaMapper.incrementViewCount(id);
        return toDetail(meta);
    }

    @Override
    @Transactional
    public PublicationDetail createPublication(PublicationCreateRequest req, Long authorId) {
        // 1) PUBLICATION 게시판 마스터 조회 (V19 시드)
        BbsMaster master = bbsMasterMapper.findByCode(PUBLICATION_BBS_CODE)
                .orElseThrow(() -> new BbsMasterNotFoundException(0L));

        // 2) bbs_post INSERT (id 자동 생성)
        // SPEC-CMS-SECURITY-XSS — RICH_TEXT 콘텐츠 Jsoup sanitize 적용
        String rawHtml = req.contentHtml() != null ? req.contentHtml() : "";
        String contentHtml = htmlSanitizer.sanitize(rawHtml);
        String contentText = req.contentText() != null ? req.contentText() : stripHtml(contentHtml);
        BbsPost post = BbsPost.builder()
                .bbsId(master.getId())
                .title(req.title())
                .contentHtml(contentHtml)
                .contentText(contentText)
                .authorId(authorId)
                .notice(false)
                .secret(false)
                .status("PUBLISHED")
                .build();
        bbsPostMapper.insert(post);

        // 3) bbs_post_publication_meta INSERT
        publicationMetaMapper.insert(post.getId(), req);

        // 4) 생성된 메타 재조회 후 반환
        PublicationMeta saved = publicationMetaMapper.findById(post.getId())
                .orElseThrow(() -> new PublicationNotFoundException(post.getId()));
        return toDetail(saved);
    }

    @Override
    @Transactional
    public PublicationDetail updatePublication(Long id, PublicationUpdateRequest req) {
        PublicationMeta existing = publicationMetaMapper.findById(id)
                .orElseThrow(() -> new PublicationNotFoundException(id));

        // bbs_post 필드 업데이트 (title/contentHtml/contentText)
        boolean postChanged = req.title() != null || req.contentHtml() != null || req.contentText() != null;
        if (postChanged) {
            // SPEC-CMS-SECURITY-XSS — 변경 시 RICH_TEXT 콘텐츠 Jsoup sanitize 적용
            String sanitizedHtml = req.contentHtml() != null
                    ? htmlSanitizer.sanitize(req.contentHtml())
                    : existing.getContentHtml();
            BbsPost post = BbsPost.builder()
                    .id(existing.getPostId())
                    .bbsId(null)
                    .title(req.title() != null ? req.title() : existing.getTitle())
                    .contentHtml(sanitizedHtml)
                    .contentText(req.contentText() != null
                            ? req.contentText()
                            : (req.contentHtml() != null ? stripHtml(sanitizedHtml) : existing.getContentText()))
                    .notice(false)
                    .secret(false)
                    .status(existing.getStatus())
                    .build();
            bbsPostMapper.update(post);
        }

        // bbs_post_publication_meta 부분 업데이트
        publicationMetaMapper.update(id, req);

        PublicationMeta refreshed = publicationMetaMapper.findById(id)
                .orElseThrow(() -> new PublicationNotFoundException(id));
        return toDetail(refreshed);
    }

    @Override
    @Transactional
    public void deletePublication(Long id) {
        publicationMetaMapper.findById(id)
                .orElseThrow(() -> new PublicationNotFoundException(id));
        // bbs_post 소프트 삭제 (status=DELETED, deleted_at=NOW)
        int affected = bbsPostMapper.deleteById(id);
        if (affected == 0) {
            throw new PublicationNotFoundException(id);
        }
    }

    @Override
    public List<PublicationCategoryDto> getCategories() {
        List<PublicationCategory> all = publicationCategoryMapper.findAllActive();
        // parent_id 기준 그룹핑하여 트리 구성
        Map<Long, List<PublicationCategory>> byParent = new LinkedHashMap<>();
        for (PublicationCategory c : all) {
            Long key = c.getParentId();
            byParent.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }
        // 루트(parent_id IS NULL) → 자식 트리 빌드
        List<PublicationCategory> roots = byParent.getOrDefault(null, List.of());
        List<PublicationCategoryDto> result = new ArrayList<>(roots.size());
        for (PublicationCategory root : roots) {
            result.add(buildTree(root, byParent));
        }
        return result;
    }

    @Override
    @Transactional
    public ZipDownloadResponse requestZipDownload(Long postId, ZipDownloadRequest req, Long requestedBy) {
        publicationMetaMapper.findById(postId)
                .orElseThrow(() -> new PublicationNotFoundException(postId));

        // @MX:NOTE: [AUTO] 실제 파일 크기 계산은 첨부파일 테이블 조회로 대체 필요 (현재 stub: 파일당 10MB 추정)
        long estimatedSize = (long) req.assetUuids().size() * STUB_PER_FILE_SIZE_BYTES;
        String mode = estimatedSize <= SYNC_SIZE_THRESHOLD_BYTES ? "SYNC" : "ASYNC";

        UUID downloadId = UUID.randomUUID();
        // ZIP 파일 경로는 운영 환경에서 별도 저장소(S3/디스크) 결정. stub: 임시 경로 기록
        String zipFilePath = "/tmp/publication-zip/" + downloadId + ".zip";

        PublicationZipArchive archive = PublicationZipArchive.builder()
                .downloadId(downloadId)
                .requestedBy(requestedBy)
                .postId(postId)
                .assetUuids(req.assetUuids())
                .zipFilePath(zipFilePath)
                .sizeBytes(estimatedSize)
                .mode(mode)
                .build();
        publicationZipArchiveMapper.insert(archive);

        String message = "SYNC".equals(mode)
                ? "동기 다운로드 가능 (≤ 50MB)"
                : "비동기 처리 중입니다. 완료 후 다운로드 링크가 제공됩니다.";
        return new ZipDownloadResponse(downloadId, mode, message, estimatedSize);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────

    private PublicationCategoryDto buildTree(
            PublicationCategory node,
            Map<Long, List<PublicationCategory>> byParent) {
        List<PublicationCategory> kids = byParent.getOrDefault(node.getId(), List.of());
        List<PublicationCategoryDto> children = new ArrayList<>(kids.size());
        for (PublicationCategory k : kids) {
            children.add(buildTree(k, byParent));
        }
        return new PublicationCategoryDto(
                node.getId(),
                node.getCode(),
                node.getName(),
                node.getParentId(),
                node.getDepth(),
                node.getSortOrder(),
                node.getStatus(),
                children
        );
    }

    private PublicationSummary toSummary(PublicationMeta m) {
        return new PublicationSummary(
                m.getPostId(),
                m.getTitle(),
                (int) m.getPublicationYear(),
                m.getPublicationMonth() != null ? m.getPublicationMonth().intValue() : null,
                m.getDocumentType(),
                m.getCategoryName(),
                m.getFileCount(),
                m.getIsbn(),
                m.getPublisher(),
                m.getViewCount(),
                m.getPublishedAt()
        );
    }

    private PublicationDetail toDetail(PublicationMeta m) {
        return new PublicationDetail(
                m.getPostId(),
                m.getTitle(),
                m.getContentHtml(),
                (int) m.getPublicationYear(),
                m.getPublicationMonth() != null ? m.getPublicationMonth().intValue() : null,
                m.getDocumentType(),
                m.getPublicationCategoryId(),
                m.getCategoryName(),
                m.getFileCount(),
                m.getIsbn(),
                m.getPublisher(),
                m.getViewCount(),
                m.getPublishedAt(),
                m.getCreatedAt(),
                m.getUpdatedAt()
        );
    }

    /** HTML 태그 제거 (간이). */
    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").trim();
    }
}
