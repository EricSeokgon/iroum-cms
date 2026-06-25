package kr.co.ircp.cms.domain.content.page.service;

import kr.co.ircp.cms.domain.audit.service.AuditLogService;
import kr.co.ircp.cms.domain.content.page.dto.PageCreateRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageHistoryResponse;
import kr.co.ircp.cms.domain.content.page.dto.PagePublishRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageResponse;
import kr.co.ircp.cms.domain.content.page.dto.PageScheduleRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageUpdateRequest;
import kr.co.ircp.cms.domain.content.page.entity.Page;
import kr.co.ircp.cms.domain.content.page.entity.PageHistory;
import kr.co.ircp.cms.domain.content.page.exception.PageSlugInvalidException;
import kr.co.ircp.cms.domain.content.page.exception.PageStatusTransitionException;
import kr.co.ircp.cms.domain.content.page.mapper.ContentBlockMapper;
import kr.co.ircp.cms.domain.content.page.mapper.PageHistoryMapper;
import kr.co.ircp.cms.domain.content.page.mapper.PageMapper;
import kr.co.ircp.cms.domain.content.seo.service.SeoRedirectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PageService RED 단계 테스트.
 * REQ-CONTENT-005-D: 페이지 CRUD + 발행/예약/철회 + 이력
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PageService RED 테스트 (REQ-CONTENT-005-D)")
class PageServiceTest {

    @Mock private PageMapper pageMapper;
    @Mock private ContentBlockMapper contentBlockMapper;
    @Mock private PageHistoryMapper pageHistoryMapper;
    @Mock private SeoRedirectService seoRedirectService;
    @Mock private PageHistoryRetentionService pageHistoryRetentionService;
    @Mock private AuditLogService auditLogService;

    private PageService pageService;

    @BeforeEach
    void setUp() {
        // PageChangeSummaryGenerator·ObjectMapper는 순수 함수형이라 실제 인스턴스 사용 (mock 불필요)
        pageService = new PageServiceImpl(
                pageMapper, contentBlockMapper, pageHistoryMapper, seoRedirectService,
                new PageChangeSummaryGenerator(), pageHistoryRetentionService,
                auditLogService, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private Page stubPage(long id, String status, String slug) {
        return Page.builder()
                .id(id)
                .siteId(1L)
                .templateId(1L)
                .code("PAGE_" + id)
                .title("테스트 페이지")
                .slug(slug)
                .status(status)
                .currentVersion(1)
                .createdBy(99L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private PageCreateRequest stubCreateRequest(String slug) {
        return new PageCreateRequest(1L, 1L, null, "PAGE_NEW", "신규 페이지", slug);
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-005-D-1: 페이지 생성 — slug 패턴 검증
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("유효하지 않은 slug로 페이지 생성 시 PageSlugInvalidException 발생")
    void shouldRejectInvalidSlug() {
        // Arrange — 대문자 포함 (허용 안 됨)
        PageCreateRequest request = stubCreateRequest("INVALID_SLUG");

        // Act & Assert
        assertThatThrownBy(() -> pageService.createPage(request, 1L))
                .isInstanceOf(PageSlugInvalidException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-005-D-1: 페이지 생성 — slug 유일성 검증
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("중복 slug로 페이지 생성 시 예외 발생")
    void shouldRejectDuplicateSlug() {
        // Arrange
        PageCreateRequest request = stubCreateRequest("about-us");
        when(pageMapper.existsBySiteIdAndSlug(1L, "about-us")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> pageService.createPage(request, 1L))
                .isInstanceOf(RuntimeException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-005-D-1: 페이지 생성 — 성공
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("유효한 요청으로 페이지 생성 시 DRAFT 상태로 반환")
    void shouldCreatePageWithDraftStatus() {
        // Arrange
        PageCreateRequest request = stubCreateRequest("about-us");
        when(pageMapper.existsBySiteIdAndSlug(1L, "about-us")).thenReturn(false);
        when(pageMapper.existsBySiteIdAndCode(1L, "PAGE_NEW")).thenReturn(false);
        doAnswer(inv -> {
            Page page = inv.getArgument(0);
            page.setId(1L);
            return null;
        }).when(pageMapper).insert(any(Page.class));

        // Act
        PageResponse response = pageService.createPage(request, 1L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.slug()).isEqualTo("about-us");
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-005-D-2: 페이지 수정 — 이력 누적
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("페이지 수정 시 page_history에 이력 INSERT")
    void shouldCreateHistoryOnPageUpdate() {
        // Arrange
        Page existing = stubPage(1L, "DRAFT", "about-us");
        when(pageMapper.findById(1L)).thenReturn(Optional.of(existing));
        when(pageMapper.update(any())).thenReturn(1);
        PageUpdateRequest request = new PageUpdateRequest(
                "수정된 제목", "about-us", null, null, null, null, null, null, null, "제목 변경"
        );

        // Act
        PageResponse response = pageService.updatePage(1L, request, 99L);

        // Assert
        assertThat(response).isNotNull();
        verify(pageHistoryMapper).insert(any());
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-005-D-3: 즉시 발행
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("DRAFT 페이지 즉시 발행 — status PUBLISHED로 전환")
    void shouldPublishDraftPage() {
        // Arrange
        Page draftPage = stubPage(1L, "DRAFT", "about-us");
        when(pageMapper.findById(1L)).thenReturn(Optional.of(draftPage));
        when(pageMapper.publish(1L)).thenReturn(1);

        // Act
        PageResponse response = pageService.publishPage(1L, null, 99L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("PUBLISHED");
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-005-D-3: 이미 발행된 페이지 재발행 불가
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("PUBLISHED 상태에서 publish 호출 시 PageStatusTransitionException 발생")
    void shouldRejectPublishingAlreadyPublishedPage() {
        // Arrange
        Page publishedPage = stubPage(1L, "PUBLISHED", "about-us");
        when(pageMapper.findById(1L)).thenReturn(Optional.of(publishedPage));

        // Act & Assert
        assertThatThrownBy(() -> pageService.publishPage(1L, null, 99L))
                .isInstanceOf(PageStatusTransitionException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-005-D-4: 예약 발행 — scheduledAt > now 검증
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("과거 시간으로 예약 발행 요청 시 예외 발생")
    void shouldRejectScheduleWithPastTime() {
        // Arrange
        Page draftPage = stubPage(1L, "DRAFT", "about-us");
        when(pageMapper.findById(1L)).thenReturn(Optional.of(draftPage));
        PageScheduleRequest request = new PageScheduleRequest(
                Instant.now().minusSeconds(3600) // 과거 1시간 전
        );

        // Act & Assert
        assertThatThrownBy(() -> pageService.schedulePage(1L, request, 99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-005-D-5: 철회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("PUBLISHED 페이지 철회 — status RETRACTED로 전환")
    void shouldRetractPublishedPage() {
        // Arrange
        Page publishedPage = stubPage(1L, "PUBLISHED", "about-us");
        when(pageMapper.findById(1L)).thenReturn(Optional.of(publishedPage));
        when(pageMapper.retract(1L)).thenReturn(1);
        when(pageMapper.findById(1L)).thenReturn(Optional.of(
                Page.builder().id(1L).siteId(1L).templateId(1L).code("PAGE_1")
                        .title("테스트 페이지").slug("about-us").status("RETRACTED")
                        .currentVersion(1).createdAt(Instant.now()).updatedAt(Instant.now()).build()
        ));

        // Act
        PageResponse response = pageService.retractPage(1L, 99L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("RETRACTED");
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-005-D-6: 이력 목록 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("페이지 이력 목록 조회 — version DESC 정렬 반환")
    void shouldReturnPageHistoryVersionDesc() {
        // Arrange
        PageHistory h1 = PageHistory.builder().id(1L).pageId(1L).version(1)
                .snapshot("{}").editedBy(99L).editedAt(Instant.now()).build();
        PageHistory h2 = PageHistory.builder().id(2L).pageId(1L).version(2)
                .snapshot("{}").editedBy(99L).editedAt(Instant.now()).build();
        // DB에서 version DESC로 정렬되어 반환
        when(pageHistoryMapper.findByPageId(1L)).thenReturn(List.of(h2, h1));

        // Act
        List<PageHistoryResponse> history = pageService.getPageHistory(1L);

        // Assert
        assertThat(history).hasSize(2);
        assertThat(history.get(0).version()).isGreaterThan(history.get(1).version());
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-005-D-7: 롤백
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("특정 버전으로 롤백 시 status DRAFT로 강제 전환")
    void shouldRollbackPageToDraft() {
        // Arrange
        Page publishedPage = stubPage(1L, "PUBLISHED", "about-us");
        PageHistory histV1 = PageHistory.builder().id(1L).pageId(1L).version(1)
                .snapshot("{\"title\":\"초기 제목\"}").editedBy(99L).editedAt(Instant.now()).build();
        when(pageMapper.findById(1L)).thenReturn(Optional.of(publishedPage));
        when(pageHistoryMapper.findByPageIdAndVersion(1L, 1)).thenReturn(Optional.of(histV1));
        when(pageMapper.update(any())).thenReturn(1);

        // Act
        PageResponse response = pageService.rollbackPage(1L, 1, 99L);

        // Assert
        assertThat(response).isNotNull();
        // 롤백 후 status는 DRAFT로 초기화되어야 함
        assertThat(response.status()).isEqualTo("DRAFT");
    }

    // ──────────────────────────────────────────────
    // SPEC-CMS-PAGE-HISTORY-001 REQ-PHIST-002/004: 롤백 실제 복원 + 감사 로그
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("AC-PHIST-005/006: 롤백 시 snapshot의 title/slug 복원 + status=DRAFT + version+1")
    void rollback_restoresSnapshotFieldsAndIncrementsVersion() {
        // Arrange — currentVersion=3, snapshot은 원본 title/slug 보유
        Page page = Page.builder().id(1L).siteId(1L).templateId(1L).code("PAGE_1")
                .title("현재 제목").slug("current-slug").status("PUBLISHED")
                .currentVersion(3).createdBy(99L)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        PageHistory histV1 = PageHistory.builder().id(1L).pageId(1L).version(1)
                .snapshot("{\"title\":\"원본\",\"slug\":\"orig\"}")
                .editedBy(99L).editedAt(Instant.now()).build();
        when(pageMapper.findById(1L)).thenReturn(Optional.of(page));
        when(pageHistoryMapper.findByPageIdAndVersion(1L, 1)).thenReturn(Optional.of(histV1));
        when(pageMapper.update(any())).thenReturn(1);

        // Act
        PageResponse response = pageService.rollbackPage(1L, 1, 99L);

        // Assert — 실제 필드 복원 (AC-PHIST-005)
        assertThat(response.title()).isEqualTo("원본");
        assertThat(response.slug()).isEqualTo("orig");
        // status=DRAFT, version 증가 (AC-PHIST-006)
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.currentVersion()).isEqualTo(4);
    }

    @Test
    @DisplayName("AC-PHIST-007: 롤백 시 changeSummary에 ROLLBACK_FROM_v1 패턴을 포함한 이력 기록")
    void rollback_recordsHistoryWithRollbackSummary() {
        // Arrange
        Page page = stubPage(1L, "PUBLISHED", "about-us");
        PageHistory histV1 = PageHistory.builder().id(1L).pageId(1L).version(1)
                .snapshot("{\"title\":\"원본\",\"slug\":\"orig\"}")
                .editedBy(99L).editedAt(Instant.now()).build();
        when(pageMapper.findById(1L)).thenReturn(Optional.of(page));
        when(pageHistoryMapper.findByPageIdAndVersion(1L, 1)).thenReturn(Optional.of(histV1));
        when(pageMapper.update(any())).thenReturn(1);

        org.mockito.ArgumentCaptor<PageHistory> captor =
                org.mockito.ArgumentCaptor.forClass(PageHistory.class);

        // Act
        pageService.rollbackPage(1L, 1, 99L);

        // Assert — 롤백 이력 INSERT 시 changeSummary 검증
        verify(pageHistoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getChangeSummary()).contains("ROLLBACK_FROM_v1");
    }

    @Test
    @DisplayName("AC-PHIST-013/014: 롤백 성공 시 audit_log를 action=UPDATE/entityType=Page로 1건 기록")
    void rollback_recordsAuditLogOnSuccess() {
        // Arrange
        Page page = stubPage(1L, "PUBLISHED", "about-us");
        PageHistory histV1 = PageHistory.builder().id(1L).pageId(1L).version(1)
                .snapshot("{\"title\":\"원본\",\"slug\":\"orig\"}")
                .editedBy(99L).editedAt(Instant.now()).build();
        when(pageMapper.findById(1L)).thenReturn(Optional.of(page));
        when(pageHistoryMapper.findByPageIdAndVersion(1L, 1)).thenReturn(Optional.of(histV1));
        when(pageMapper.update(any())).thenReturn(1);

        org.mockito.ArgumentCaptor<AuditLogService.AuditLogRecord> captor =
                org.mockito.ArgumentCaptor.forClass(AuditLogService.AuditLogRecord.class);

        // Act
        pageService.rollbackPage(1L, 1, 99L);

        // Assert — 감사 로그 1건 + action/entityType + afterValue에 from/to version
        verify(auditLogService).record(captor.capture());
        AuditLogService.AuditLogRecord rec = captor.getValue();
        assertThat(rec.action()).isEqualTo("UPDATE");
        assertThat(rec.entityType()).isEqualTo("Page");
        assertThat(rec.entityId()).isEqualTo("1");
        assertThat(rec.afterValue()).contains("from_version").contains("to_version");
    }

    @Test
    @DisplayName("AC-PHIST-015: 롤백 실패(version 미존재) 시 audit_log를 기록하지 않는다")
    void rollback_doesNotRecordAuditOnFailure() {
        // Arrange — version 미존재
        Page page = stubPage(1L, "PUBLISHED", "about-us");
        when(pageMapper.findById(1L)).thenReturn(Optional.of(page));
        when(pageHistoryMapper.findByPageIdAndVersion(1L, 99))
                .thenReturn(Optional.empty());

        // Act & Assert — 예외 발생
        assertThatThrownBy(() -> pageService.rollbackPage(1L, 99, 99L))
                .isInstanceOf(RuntimeException.class);
        // 감사 로그 미기록
        org.mockito.Mockito.verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("AC-PHIST-001 wiring: updatePage 후 보존 정책 enforceRetention 호출")
    void updatePage_invokesRetention() {
        // Arrange
        Page existing = stubPage(1L, "DRAFT", "about-us");
        when(pageMapper.findById(1L)).thenReturn(Optional.of(existing));
        when(pageMapper.update(any())).thenReturn(1);
        PageUpdateRequest request = new PageUpdateRequest(
                "수정된 제목", "about-us", null, null, null, null, null, null, null, "제목 변경");

        // Act
        pageService.updatePage(1L, request, 99L);

        // Assert — 보존 정책 적용 호출 (currentVersion=1 전달)
        verify(pageHistoryRetentionService).enforceRetention(1L, 1);
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-005-D: slug 기반 공개 페이지 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("slug로 PUBLISHED 페이지 조회 성공")
    void shouldGetPublishedPageBySlug() {
        // Arrange
        Page publishedPage = stubPage(1L, "PUBLISHED", "about-us");
        when(pageMapper.findBySiteIdAndSlug(1L, "about-us")).thenReturn(Optional.of(publishedPage));

        // Act
        PageResponse response = pageService.getPublishedPageBySlug(1L, "about-us");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.slug()).isEqualTo("about-us");
        assertThat(response.status()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("slug로 DRAFT 페이지 조회 시 404 예외 발생")
    void shouldRejectDraftPageBySlug() {
        // Arrange — DRAFT 상태 페이지 (시민에게 비공개)
        Page draftPage = stubPage(1L, "DRAFT", "hidden-page");
        when(pageMapper.findBySiteIdAndSlug(1L, "hidden-page")).thenReturn(Optional.of(draftPage));

        // Act & Assert
        assertThatThrownBy(() -> pageService.getPublishedPageBySlug(1L, "hidden-page"))
                .isInstanceOf(RuntimeException.class);
    }
}
