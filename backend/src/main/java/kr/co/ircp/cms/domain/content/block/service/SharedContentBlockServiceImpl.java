package kr.co.ircp.cms.domain.content.block.service;

import kr.co.ircp.cms.domain.audit.service.AuditLogService;
import kr.co.ircp.cms.domain.content.block.dto.SharedContentBlockRequest;
import kr.co.ircp.cms.domain.content.block.dto.SharedContentBlockResponse;
import kr.co.ircp.cms.domain.content.block.entity.SharedContentBlock;
import kr.co.ircp.cms.domain.content.block.exception.ContentBlockEmbedProviderInvalidException;
import kr.co.ircp.cms.domain.content.block.exception.ContentBlockNotFoundException;
import kr.co.ircp.cms.domain.content.block.exception.ContentBlockSlugDuplicateException;
import kr.co.ircp.cms.domain.content.block.mapper.SharedContentBlockMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 공유 콘텐츠 블록 서비스 구현.
 *
 * <p>SPEC-CMS-CONTENT-BLOCK-001 — block_type 별 Jsoup 살균 정책:
 * <ul>
 *   <li>RICH_TEXT: {@link Safelist#relaxed()} (일반 HTML 허용)</li>
 *   <li>MARKDOWN : {@link Safelist#none()} (text-only, 모든 태그 제거)</li>
 *   <li>HTML     : 살균 없음 (SUPER_ADMIN 전용 — 컨트롤러에서 역할 검사)</li>
 *   <li>EMBED    : HTML 살균 없음 + URL 제공자 allowlist 검증</li>
 * </ul>
 */
// @MX:ANCHOR: [AUTO] SharedContentBlockServiceImpl create/update/delete — 감사 로그 연동 진입점
// @MX:REASON: ContentBlockController + ContentBlockIT 다수가 호출 (fan_in >= 3); 살균·감사 계약 변경 시 전파
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SharedContentBlockServiceImpl implements SharedContentBlockService {

    /** REQ-CB-014 — EMBED 허용 제공자 도메인. */
    private static final Set<String> EMBED_ALLOWLIST = Set.of("youtube.com", "vimeo.com", "map.kakao.com");

    private static final String ENTITY_TYPE = "shared_content_block";

    private final SharedContentBlockMapper blockMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public SharedContentBlockResponse create(SharedContentBlockRequest req, Long actorId) {
        // REQ-CB-002/011 — slug 유일성 검사
        if (blockMapper.existsBySlug(req.slug())) {
            throw new ContentBlockSlugDuplicateException(req.slug());
        }
        // REQ-CB-014/015 — EMBED 제공자 검증
        if ("EMBED".equals(req.blockType())) {
            validateEmbedProvider(req.contentRaw());
        }

        SharedContentBlock block = SharedContentBlock.builder()
                .name(req.name())
                .slug(req.slug())
                .blockType(req.blockType())
                .contentHtml(sanitizeHtml(req.blockType(), req.contentHtml()))
                .contentRaw(sanitizeRaw(req.blockType(), req.contentRaw()))
                .description(req.description())
                .status(req.status() != null ? req.status() : "ACTIVE")
                .createdBy(actorId)
                .build();

        blockMapper.insert(block);
        recordAudit("CREATE", block.getId(), actorId);
        return SharedContentBlockResponse.from(blockMapper.findById(block.getId())
                .orElseThrow(() -> new ContentBlockNotFoundException(block.getId())));
    }

    @Override
    public List<SharedContentBlockResponse> findAll(String status, String blockType) {
        return blockMapper.findAll(status, blockType).stream()
                .map(SharedContentBlockResponse::from)
                .toList();
    }

    @Override
    public SharedContentBlockResponse findById(Long id) {
        return SharedContentBlockResponse.from(getOrThrow(id));
    }

    @Override
    @Transactional
    public SharedContentBlockResponse update(Long id, SharedContentBlockRequest req, Long actorId) {
        SharedContentBlock existing = getOrThrow(id);
        // REQ-CB-011 — slug 변경 시 자신 제외 중복 검사
        if (!existing.getSlug().equals(req.slug())
                && blockMapper.existsBySlugAndIdNot(req.slug(), id)) {
            throw new ContentBlockSlugDuplicateException(req.slug());
        }
        if ("EMBED".equals(req.blockType())) {
            validateEmbedProvider(req.contentRaw());
        }

        existing.setName(req.name());
        existing.setSlug(req.slug());
        existing.setBlockType(req.blockType());
        existing.setContentHtml(sanitizeHtml(req.blockType(), req.contentHtml()));
        existing.setContentRaw(sanitizeRaw(req.blockType(), req.contentRaw()));
        existing.setDescription(req.description());
        if (req.status() != null) {
            existing.setStatus(req.status());
        }

        blockMapper.update(existing);
        recordAudit("UPDATE", id, actorId);
        return SharedContentBlockResponse.from(getOrThrow(id));
    }

    @Override
    @Transactional
    public SharedContentBlockResponse updateStatus(Long id, String status, Long actorId) {
        getOrThrow(id);
        blockMapper.updateStatus(id, status, Instant.now());
        recordAudit("UPDATE", id, actorId);
        return SharedContentBlockResponse.from(getOrThrow(id));
    }

    @Override
    @Transactional
    public void delete(Long id, Long actorId) {
        getOrThrow(id);
        blockMapper.deleteById(id);
        recordAudit("DELETE", id, actorId);
    }

    @Override
    public String preview(Long id) {
        SharedContentBlock block = getOrThrow(id);
        // RICH_TEXT/MARKDOWN 은 저장 시 이미 살균되어 있으나, 미리보기에서도 일관 살균 적용.
        if ("MARKDOWN".equals(block.getBlockType())) {
            return Jsoup.clean(block.getContentRaw() == null ? "" : block.getContentRaw(), Safelist.none());
        }
        String html = block.getContentHtml() != null ? block.getContentHtml() : "";
        if ("HTML".equals(block.getBlockType())) {
            return html; // SUPER_ADMIN 전용 원본
        }
        return Jsoup.clean(html, Safelist.relaxed());
    }

    // ─── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    private SharedContentBlock getOrThrow(Long id) {
        return blockMapper.findById(id)
                .orElseThrow(() -> new ContentBlockNotFoundException(id));
    }

    /** RICH_TEXT → relaxed 살균, 그 외 타입은 content_html 그대로(HTML 원본/EMBED 미사용). */
    private String sanitizeHtml(String blockType, String contentHtml) {
        if (contentHtml == null) {
            return null;
        }
        if ("RICH_TEXT".equals(blockType)) {
            return Jsoup.clean(contentHtml, Safelist.relaxed());
        }
        return contentHtml;
    }

    /** MARKDOWN → text-only 살균(none), 그 외 타입은 content_raw 그대로. */
    private String sanitizeRaw(String blockType, String contentRaw) {
        if (contentRaw == null) {
            return null;
        }
        if ("MARKDOWN".equals(blockType)) {
            return Jsoup.clean(contentRaw, Safelist.none());
        }
        return contentRaw;
    }

    /** REQ-CB-014/015 — EMBED URL host 가 허용 도메인(또는 서브도메인)인지 검증. */
    private void validateEmbedProvider(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                throw new ContentBlockEmbedProviderInvalidException();
            }
            boolean valid = EMBED_ALLOWLIST.stream().anyMatch(allowed ->
                    host.equals(allowed) || host.endsWith("." + allowed));
            if (!valid) {
                throw new ContentBlockEmbedProviderInvalidException();
            }
        } catch (IllegalArgumentException e) {
            throw new ContentBlockEmbedProviderInvalidException();
        }
    }

    private void recordAudit(String action, Long blockId, Long actorId) {
        auditLogService.record(new AuditLogService.AuditLogRecord(
                Instant.now(), actorId, null,
                action, ENTITY_TYPE, String.valueOf(blockId),
                null, null, null, null, null,
                "INFO", "SUCCESS", null, null));
    }
}
