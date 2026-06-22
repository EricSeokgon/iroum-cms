package kr.co.ircp.cms.domain.content.page.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
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
import java.util.HashMap;
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
    // SPEC-CMS-CONTENT-REVISION-001 M3: 저장 후 이력 보존 정책(best-effort) 적용.
    private final kr.co.ircp.cms.common.service.RevisionRetentionService retentionService;
    private final ObjectMapper objectMapper;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PageServiceImpl.class);

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

        // 수정 전 스냅샷을 page_history에 기록
        String snapshotJson;
        try {
            Map<String, String> snapData = new HashMap<>();
            snapData.put("title", existing.getTitle());
            snapData.put("slug", existing.getSlug());
            snapshotJson = objectMapper.writeValueAsString(snapData);
        } catch (JsonProcessingException e) {
            snapshotJson = "{}";
        }
        PageHistory history = PageHistory.builder()
                .pageId(id)
                .version(existing.getCurrentVersion())
                .snapshot(snapshotJson)
                .editedBy(updatedBy)
                .editedAt(Instant.now())
                // REQ-PHIST-003: changeSummary 미제공 시 변경 필드명 목록 자동 생성
                .changeSummary(
                        (request.changeSummary() == null || request.changeSummary().isBlank())
                                ? PageChangeSummaryGenerator.generate(
                                        existing.getTitle(),
                                        request.title() != null ? request.title() : existing.getTitle(),
                                        existing.getSlug(),
                                        request.slug() != null ? request.slug() : existing.getSlug())
                                : request.changeSummary())
                .build();
        pageHistoryMapper.insert(history);

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

        // SPEC-CMS-CONTENT-REVISION-001 REQ-REV-005: 낙관적 잠금.
        // 더블 증가 버그 수정 — current_version 증가는 SQL(current_version + 1)에만 위임하고
        // Java 측 +1 은 제거한다. WHERE current_version = expectedVersion 충돌 검출을 위해
        // 엔티티에 클라이언트가 보낸 expectedVersion 을 주입한다.
        int expectedVersion = request.expectedVersion();
        existing.setCurrentVersion(expectedVersion);

        int updatedRows = pageMapper.updateWithVersion(existing);
        if (updatedRows == 0) {
            // 버전 불일치(다른 사용자 선수정) → 서버 현재 버전을 실어 409 로 응답
            long currentVersion = pageMapper.findById(id)
                    .map(Page::getCurrentVersion)
                    .map(Integer::longValue)
                    .orElse((long) expectedVersion);
            throw new kr.co.ircp.cms.common.exception.RevisionConflictException(currentVersion);
        }

        // SPEC-CMS-CONTENT-REVISION-001 M3 (REQ-REV-006): 저장 성공 후 이력 보존 정책 적용.
        // best-effort — 보존 정리 실패가 저장 결과를 되돌리지 않도록 호출자에서도 방어한다.
        try {
            retentionService.prunePageHistory(id);
        } catch (Exception e) {
            log.warn("페이지 이력 보존 정리 호출 실패 (best-effort, 무시). pageId={}", id, e);
        }

        // 응답에는 갱신 후 버전(expectedVersion + 1)을 반영
        existing.setCurrentVersion(expectedVersion + 1);
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
     * REQ-CONTENT-005-D-7 / REQ-PHIST-002: snapshot JSON 파싱으로 title/slug 복원, status='DRAFT' 강제
     *
     * // @MX:NOTE: [AUTO] REQ-PHIST-002 — snapshot JSON 파싱으로 title/slug 복원. ObjectMapper 실패 시 status=DRAFT만 적용 (graceful degradation)
     * // @MX:SPEC: SPEC-CMS-PAGE-HISTORY-001
     */
    @Override
    @Transactional
    @AuditLog(action = "UPDATE", entityType = "Page", captureReturn = true)
    public PageResponse rollbackPage(Long id, int version, Long rolledBackBy) {
        Page page = pageMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다. id=" + id));
        PageHistory historySnapshot = pageHistoryMapper.findByPageIdAndVersion(id, version)
                .orElseThrow(() -> new PageHistoryVersionNotFoundException(version));

        // snapshot JSON 을 파싱해 title/slug 를 실제 복원한다. 파싱 실패 시 status=DRAFT 복원만 진행.
        try {
            Map<String, Object> snap = objectMapper.readValue(
                    historySnapshot.getSnapshot(), new TypeReference<Map<String, Object>>() {});
            if (snap.get("title") != null) {
                page.setTitle((String) snap.get("title"));
            }
            if (snap.get("slug") != null) {
                page.setSlug((String) snap.get("slug"));
            }
        } catch (JsonProcessingException e) {
            // snapshot 파싱 실패 시 DRAFT 복원만 진행 (graceful degradation)
        }
        page.setStatus("DRAFT");
        page.setCurrentVersion(page.getCurrentVersion() + 1);
        page.setUpdatedBy(rolledBackBy);
        pageMapper.update(page);

        // 롤백 이력 기록
        PageHistory rollbackHistory = PageHistory.builder()
                .pageId(id)
                .version(page.getCurrentVersion())
                .snapshot(historySnapshot.getSnapshot())
                .editedBy(rolledBackBy)
                .editedAt(Instant.now())
                .changeSummary("ROLLBACK_FROM_v" + version)
                .build();
        pageHistoryMapper.insert(rollbackHistory);

        return PageResponse.from(page);
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
