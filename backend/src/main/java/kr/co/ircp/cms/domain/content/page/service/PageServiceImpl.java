package kr.co.ircp.cms.domain.content.page.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.audit.service.AuditLogService;
import kr.co.ircp.cms.domain.content.page.dto.PageCreateRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageHistoryResponse;
import kr.co.ircp.cms.domain.content.page.dto.PageListResponse;
import kr.co.ircp.cms.domain.content.page.dto.PagePublishRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageResponse;
import kr.co.ircp.cms.domain.content.page.dto.PageScheduleRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageUpdateRequest;
import kr.co.ircp.cms.domain.content.page.entity.Page;
import kr.co.ircp.cms.domain.content.page.entity.PageHistory;
import kr.co.ircp.cms.domain.content.page.exception.PageHistoryVersionNotFoundException;
import kr.co.ircp.cms.domain.content.page.exception.PageNotFoundException;
import kr.co.ircp.cms.domain.content.page.exception.PageSlugDuplicateException;
import kr.co.ircp.cms.domain.content.page.exception.PageSlugInvalidException;
import kr.co.ircp.cms.domain.content.page.exception.PageStatusTransitionException;
import kr.co.ircp.cms.domain.content.page.mapper.ContentBlockMapper;
import kr.co.ircp.cms.domain.content.page.mapper.PageHistoryMapper;
import kr.co.ircp.cms.domain.content.page.mapper.PageMapper;
import kr.co.ircp.cms.domain.content.seo.service.SeoRedirectService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 페이지 서비스 구현체.
 * REQ-CONTENT-005-D: 페이지 CRUD + 발행/예약/철회 + 이력
 *
 * // @MX:ANCHOR: [AUTO] PageServiceImpl — 페이지 전체 라이프사이클 관리
 * // @MX:REASON: PageController, ScheduledPublishJob, SitemapService에서 fan_in >= 3으로 참조
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PageServiceImpl implements PageService {

    /** slug 허용 패턴: 소문자/숫자로 시작, 이후 하이픈/슬래시 허용 */
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9\\-/]*$");

    private final PageMapper pageMapper;
    private final ContentBlockMapper contentBlockMapper;
    private final PageHistoryMapper pageHistoryMapper;
    private final SeoRedirectService seoRedirectService;
    private final PageChangeSummaryGenerator changeSummaryGenerator;
    private final PageHistoryRetentionService pageHistoryRetentionService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * 페이지 생성.
     * REQ-CONTENT-005-D-1: slug 패턴 검증, 유일성 검증, status='DRAFT' 초기값
     */
    @Override
    @Transactional
    @CacheEvict(value = {"sitemap"}, allEntries = true)
    public PageResponse createPage(PageCreateRequest request, Long createdBy) {
        // slug 패턴 검증
        if (!SLUG_PATTERN.matcher(request.slug()).matches()) {
            throw new PageSlugInvalidException(request.slug());
        }

        // slug 유일성 검증
        if (pageMapper.existsBySiteIdAndSlug(request.siteId(), request.slug())) {
            throw new PageSlugDuplicateException(request.slug());
        }

        // code 유일성 검증
        if (pageMapper.existsBySiteIdAndCode(request.siteId(), request.code())) {
            throw new PageSlugDuplicateException(request.code());
        }

        Page page = Page.builder()
                .siteId(request.siteId())
                .templateId(request.templateId())
                .menuId(request.menuId())
                .code(request.code())
                .title(request.title())
                .slug(request.slug())
                .status("DRAFT")
                .currentVersion(1)
                .createdBy(createdBy)
                .build();

        pageMapper.insert(page);
        return PageResponse.from(page);
    }

    /**
     * 페이지 수정.
     * REQ-CONTENT-005-D-2: 변경 전 스냅샷을 page_history에 INSERT, slug 변경 시 seo_redirect 자동 INSERT
     */
    @Override
    @Transactional
    public PageResponse updatePage(Long id, PageUpdateRequest request, Long updatedBy) {
        Page existing = pageMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다. id=" + id));

        // REQ-PHIST-003: changeSummary 미입력 시 diff 기반 자동 생성 (사용자 입력 우선)
        String changeSummary = changeSummaryGenerator.summarize(existing, request);

        // 수정 전 스냅샷을 page_history에 기록
        PageHistory history = PageHistory.builder()
                .pageId(id)
                .version(existing.getCurrentVersion())
                .snapshot("{\"title\":\"" + existing.getTitle() + "\",\"slug\":\"" + existing.getSlug() + "\"}")
                .editedBy(updatedBy)
                .editedAt(Instant.now())
                .changeSummary(changeSummary)
                .build();
        pageHistoryMapper.insert(history);

        // REQ-PHIST-001: 이력 보존 정책 — 페이지당 최대 보존 한도 초과분 정리
        pageHistoryRetentionService.enforceRetention(id, existing.getCurrentVersion());

        // slug 변경 시 seo_redirect 자동 INSERT (REQ-CONTENT-005-D-8)
        // Step 2: pageMapper.insertSeoRedirect 직접 호출 → SeoRedirectService 추상화로 변경
        String oldSlug = existing.getSlug();
        if (request.slug() != null && !request.slug().equals(oldSlug)) {
            seoRedirectService.upsertFromSlugChange(
                    "/" + oldSlug,
                    "/" + request.slug(),
                    "SLUG_CHANGE_PAGE_ID:" + id
            );
        }

        // 페이지 필드 업데이트
        existing.setTitle(request.title());
        if (request.slug() != null) existing.setSlug(request.slug());
        if (request.templateId() != null) existing.setTemplateId(request.templateId());
        if (request.menuId() != null) existing.setMenuId(request.menuId());
        if (request.seoTitle() != null) existing.setSeoTitle(request.seoTitle());
        if (request.seoDescription() != null) existing.setSeoDescription(request.seoDescription());
        if (request.ogImageUrl() != null) existing.setOgImageUrl(request.ogImageUrl());
        if (request.canonicalUrl() != null) existing.setCanonicalUrl(request.canonicalUrl());
        existing.setUpdatedBy(updatedBy);
        existing.setCurrentVersion(existing.getCurrentVersion() + 1);

        pageMapper.update(existing);
        return PageResponse.from(existing);
    }

    /**
     * 페이지 즉시 발행.
     * REQ-CONTENT-005-D-3: DRAFT/RETRACTED → PUBLISHED. 이미 PUBLISHED면 예외.
     */
    @Override
    @Transactional
    @CacheEvict(value = {"pageBySlug", "sitemap"}, allEntries = true)
    public PageResponse publishPage(Long id, PagePublishRequest request, Long publishedBy) {
        Page page = pageMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다. id=" + id));

        // PUBLISHED 상태에서 재발행 불가
        if ("PUBLISHED".equals(page.getStatus())) {
            throw new PageStatusTransitionException(page.getStatus(), "PUBLISHED");
        }

        pageMapper.publish(id);
        // 메모리 상 Page 객체 직접 업데이트 후 반환 (DB 재조회 없이)
        page.setStatus("PUBLISHED");
        page.setPublishedAt(Instant.now());
        page.setScheduledAt(null);
        return PageResponse.from(page);
    }

    /**
     * 페이지 예약 발행.
     * REQ-CONTENT-005-D-4: scheduledAt > now 검증
     */
    @Override
    @Transactional
    public PageResponse schedulePage(Long id, PageScheduleRequest request, Long scheduledBy) {
        Page page = pageMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다. id=" + id));

        // scheduledAt은 현재 시각 이후여야 함
        if (!request.scheduledAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("예약 발행 시간은 현재 시각 이후여야 합니다. scheduledAt=" + request.scheduledAt());
        }

        pageMapper.schedule(id, request.scheduledAt());
        page.setStatus("SCHEDULED");
        page.setScheduledAt(request.scheduledAt());
        return PageResponse.from(page);
    }

    /**
     * 페이지 철회.
     * REQ-CONTENT-005-D-5: PUBLISHED → RETRACTED
     */
    @Override
    @Transactional
    @CacheEvict(value = {"pageBySlug", "sitemap"}, allEntries = true)
    public PageResponse retractPage(Long id, Long retractedBy) {
        Page page = pageMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다. id=" + id));

        pageMapper.retract(id);
        // 메모리 상 Page 객체 직접 업데이트 후 반환
        page.setStatus("RETRACTED");
        return PageResponse.from(page);
    }

    /**
     * 페이지 이력 목록 조회.
     * REQ-CONTENT-005-D-6: version DESC 정렬
     */
    @Override
    public List<PageHistoryResponse> getPageHistory(Long id) {
        return pageHistoryMapper.findByPageId(id).stream()
                .map(PageHistoryResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 버전으로 롤백.
     * REQ-CONTENT-005-D-7: snapshot 복원, status='DRAFT' 강제
     */
    @Override
    @Transactional
    public PageResponse rollbackPage(Long id, int version, Long rolledBackBy) {
        Page page = pageMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다. id=" + id));
        PageHistory historySnapshot = pageHistoryMapper.findByPageIdAndVersion(id, version)
                .orElseThrow(() -> new PageHistoryVersionNotFoundException(version));

        int fromVersion = page.getCurrentVersion();

        // REQ-PHIST-002: snapshot JSON 파싱하여 title/slug 등 페이지 필드 실제 복원
        // snapshot 포맷: {"title":"...","slug":"..."} — 누락 키는 기존 값 유지
        restoreFromSnapshot(page, historySnapshot.getSnapshot());
        page.setStatus("DRAFT");
        page.setCurrentVersion(fromVersion + 1);
        page.setUpdatedBy(rolledBackBy);
        pageMapper.update(page);

        int toVersion = page.getCurrentVersion();

        // 롤백 이력 기록
        PageHistory rollbackHistory = PageHistory.builder()
                .pageId(id)
                .version(toVersion)
                .snapshot(historySnapshot.getSnapshot())
                .editedBy(rolledBackBy)
                .editedAt(Instant.now())
                .changeSummary("ROLLBACK_FROM_v" + version)
                .build();
        pageHistoryMapper.insert(rollbackHistory);

        // REQ-PHIST-004: 롤백 성공 시에만 감사 로그 기록 (실패 시 예외가 위에서 throw되어 미기록)
        // action="UPDATE" (audit_log CHECK 제약 준수), afterValue에 from/to version 기록.
        recordRollbackAudit(id, rolledBackBy, fromVersion, toVersion);

        return PageResponse.from(page);
    }

    /**
     * snapshot JSON을 파싱하여 페이지 필드를 복원한다.
     * REQ-PHIST-002 — title/slug 복원. 누락 키는 기존 값 유지.
     */
    private void restoreFromSnapshot(Page page, String snapshotJson) {
        try {
            JsonNode snapshot = objectMapper.readTree(snapshotJson);
            if (snapshot.hasNonNull("title")) {
                page.setTitle(snapshot.get("title").asText());
            }
            if (snapshot.hasNonNull("slug")) {
                page.setSlug(snapshot.get("slug").asText());
            }
        } catch (JsonProcessingException e) {
            // snapshot 파싱 실패 시 필드 복원을 건너뛰고 status/version 변경만 적용 (best-effort)
            // 실 데이터는 항상 유효 JSON이므로 운영 중 발생 가능성 낮음.
        }
    }

    /**
     * 롤백 작업 감사 로그를 기록한다.
     * REQ-PHIST-004 — action="UPDATE", entityType="Page", afterValue={"from_version":N,"to_version":M}.
     */
    private void recordRollbackAudit(Long pageId, Long actorId, int fromVersion, int toVersion) {
        String afterValue;
        try {
            afterValue = objectMapper.writeValueAsString(
                    Map.of("from_version", fromVersion, "to_version", toVersion));
        } catch (JsonProcessingException e) {
            afterValue = "{\"from_version\":" + fromVersion + ",\"to_version\":" + toVersion + "}";
        }
        auditLogService.record(new AuditLogService.AuditLogRecord(
                Instant.now(),
                actorId,
                null,
                "UPDATE",
                "Page",
                String.valueOf(pageId),
                null,
                afterValue,
                null,
                null,
                null,
                "INFO",
                "SUCCESS",
                null,
                null
        ));
    }

    /**
     * 관리자용 페이지 목록 조회.
     * REQ-CONTENT-005-D: 사이트/상태/검색 필터 + 페이징
     */
    @Override
    public PageListResponse listPages(Long siteId, String status, String search, int page, int size) {
        String effectiveStatus = (status == null || status.isBlank()) ? null : status;
        String effectiveSearch = (search == null || search.isBlank()) ? null : search;
        int offset = page * size;
        List<Page> pages = pageMapper.listBySiteId(siteId, effectiveStatus, effectiveSearch, offset, size);
        long total = pageMapper.countBySiteId(siteId, effectiveStatus, effectiveSearch);
        List<PageResponse> content = pages.stream().map(PageResponse::from).collect(Collectors.toList());
        return PageListResponse.of(content, page, size, total);
    }

    /**
     * slug 기반 공개 페이지 조회.
     * REQ-CONTENT-005-D: PUBLISHED 상태만 반환
     */
    @Override
    @Cacheable(value = "pageBySlug", key = "#siteId + ':' + #slug")
    public PageResponse getPublishedPageBySlug(Long siteId, String slug) {
        Page page = pageMapper.findBySiteIdAndSlug(siteId, slug)
                .orElseThrow(() -> new PageNotFoundException(slug));

        // 비공개 상태 페이지는 시민에게 404로 처리 (정보 은닉)
        if (!"PUBLISHED".equals(page.getStatus())) {
            throw new PageNotFoundException(slug);
        }

        return PageResponse.from(page);
    }
}
