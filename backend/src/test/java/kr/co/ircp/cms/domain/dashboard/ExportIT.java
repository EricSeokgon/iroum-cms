package kr.co.ircp.cms.domain.dashboard;

import kr.co.ircp.cms.domain.dashboard.controller.ExportController;
import kr.co.ircp.cms.domain.dashboard.dto.ExportRequest;
import kr.co.ircp.cms.domain.dashboard.dto.ExportResponse;
import kr.co.ircp.cms.domain.dashboard.entity.ExportHistory;
import kr.co.ircp.cms.domain.dashboard.exception.ExportAccessDeniedException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportExpiredException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportNotFoundException;
import kr.co.ircp.cms.domain.dashboard.repository.ExportHistoryMapper;
import kr.co.ircp.cms.domain.dashboard.service.ExportService;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * SPEC-CMS-008 §F 내보내기 도메인 통합 테스트.
 *
 * <p>본 IT는 REQ-VIZ-006-D 인수 기준 14건 중 비동기 처리, signed URL, 권한,
 * 만료, CSV BOM 등 9개 핵심 시나리오를 검증한다.
 *
 * <p>설계 결정:
 * <ul>
 *   <li>Service-level 직접 호출 — {@code @AuthenticationPrincipal Long userId}
 *       ArgumentResolver가 운영에 정의되지 않아 controller 경로는 principal 추출이
 *       null이 되므로, 서비스 계층 통합 테스트가 가장 신뢰성 있는 검증 경로.</li>
 *   <li>CSV BOM 검증은 ExportController.download 메서드를
 *       MockHttpServletResponse로 직접 호출하여 1차 출시 stub 동작을 확인.</li>
 *   <li>실제 export 파일 작성(SXSSFWorkbook/CSV)은 1차 출시 범위가 아니며
 *       (ExportServiceImpl 주석 v0.4+ 도입 계획), 본 IT는 진입 계약과 권한·만료를
 *       검증한다.</li>
 *   <li>users(id) FK 존재 — V2 시드 사용자 ID 사용 (V2 사용자 시드 부재 시
 *       테스트가 SQLException으로 SKIP 처리되도록 FK 정합 ID로 fallback).</li>
 * </ul>
 *
 * <p>AC 매핑:
 * <ul>
 *   <li>F-1 비동기 export — {@link AsyncSyncSplitTests#asyncExport_returnsProcessingStatus}</li>
 *   <li>F-2 동기 export — {@link AsyncSyncSplitTests#syncExport_returnsCompletedWithUrl}</li>
 *   <li>F-5 진행률 갱신 — {@link AsyncSyncSplitTests#progressUpdate_isReflectedInStatus}</li>
 *   <li>F-6 CSV BOM — {@link CsvBomTests#csvDownload_writesUtf8Bom}</li>
 *   <li>F-10 본인 다운로드 — {@link DownloadPermissionTests#owner_canVerifyDownload}</li>
 *   <li>F-11 타인 다운로드 거부 — {@link DownloadPermissionTests#nonOwner_isDeniedWith403Mapping}</li>
 *   <li>F-12 SUPER_ADMIN 다운로드 — {@link DownloadPermissionTests#superAdmin_canVerifyDownloadOfAnyExport}</li>
 *   <li>F-13 만료 다운로드 — {@link ExpiryAndHistoryTests#expiredExport_throwsGone}</li>
 *   <li>F-14 이력 조회 — {@link ExpiryAndHistoryTests#historyList_isScopedToRequestor}</li>
 * </ul>
 *
 * <p>관련 SPEC: SPEC-CMS-008 REQ-VIZ-006-D-1~5
 */
// @MX:NOTE: [AUTO] ExportIT — 내보내기 도메인 9 AC GREEN 회귀 IT
// @MX:SPEC: SPEC-CMS-008 REQ-VIZ-006
@DisplayName("SPEC-CMS-008 §F 내보내기 통합 테스트")
class ExportIT extends AbstractIntegrationTest {

    @Autowired
    ExportService exportService;

    @Autowired
    ExportController exportController;

    @Autowired
    ExportHistoryMapper historyMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${iroum.export.signing-key:please-change-in-prod}")
    String signingKey;

    /** 테스트 시나리오마다 분리된 requestor user id. V2 사용자 시드의 ROOT 계정 id=1. */
    private static final Long REQUESTOR_A = 1L;
    /** 시스템 시드에 존재하는 두 번째 사용자 id (SUPER_ADMIN 등). 없으면 동일 ID 재사용 가능. */
    private Long requestorBId;

    /**
     * 운영 V2 시드는 admin 계정(id=1)만 보장하므로, B 사용자가 필요한 시나리오에서는
     * 동적으로 사용자를 INSERT한다.
     *
     * <p>users 테이블 NOT NULL 컬럼 (V2 + V24~V26 후):
     * username, password_hash, name, status(default ACTIVE), uuid(default gen_random_uuid()),
     * email_key_version(default 1), password_changed_at(default NOW()),
     * created_at/updated_at(default NOW()). email 평문 컬럼은 V26 에서 DROP.
     */
    @BeforeEach
    void prepareSecondUser() {
        // requestor_id FK 제약 — users 테이블에 id 가 존재해야 한다.
        // admin 사용자(username='admin', id=1)는 V4 시드로 항상 존재.
        Long existing = jdbc.query(
                "SELECT id FROM users WHERE username = 'export-it-user-b' LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null);
        if (existing != null && existing > 0) {
            requestorBId = existing;
            return;
        }
        // V26 적용 상태: email 평문 컬럼 DROP. email_hmac (nullable, UNIQUE WHERE NOT NULL) 만 사용 가능.
        jdbc.update("""
                INSERT INTO users (username, password_hash, name, status)
                VALUES ('export-it-user-b',
                        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
                        '내보내기 IT 보조 사용자',
                        'ACTIVE')
                """);
        requestorBId = jdbc.queryForObject(
                "SELECT id FROM users WHERE username = 'export-it-user-b' LIMIT 1",
                Long.class);
    }

    // =================================================================================
    // §F-1, F-2, F-5 — 비동기/동기 분기 + 진행률
    // =================================================================================
    @Nested
    @DisplayName("§F-1/F-2/F-5 비동기·동기 분기 + 진행률 갱신")
    class AsyncSyncSplitTests {

        /**
         * F-1: 예상 행수 50,000 → 비동기 export → 202 시그널과 등가인 status=PROCESSING.
         *
         * <p>{@link ExportService#createExport}는 row_count_estimate > 10,000일 때
         * PROCESSING 상태로 export_history 행을 적재하고 응답 status를 PROCESSING으로 반환한다.
         * 컨트롤러는 이 status를 보고 202 Accepted를 매핑한다(IT는 service 응답으로 검증).
         */
        @Test
        @DisplayName("F-1: 50,000행 scope → PROCESSING status + DB 행 적재")
        void asyncExport_returnsProcessingStatus() {
            ExportRequest req = new ExportRequest(
                    "EXCEL",
                    "{\"dashboard_id\":5,\"row_count_estimate\":50000}",
                    null);

            ExportResponse resp = exportService.createExport(REQUESTOR_A, req);

            assertThat(resp.status()).isEqualTo("PROCESSING");
            assertThat(resp.progressPct()).isEqualTo(0);
            assertThat(resp.id()).isNotNull();

            // DB 확인
            ExportHistory persisted = historyMapper.findById(resp.id()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo("PROCESSING");
            assertThat(persisted.getRequestorId()).isEqualTo(REQUESTOR_A);
            assertThat(persisted.getExportType()).isEqualTo("EXCEL");
        }

        /**
         * F-2: 예상 행수 500 → 동기 export → COMPLETED + signedDownloadUrl 반환.
         *
         * <p>10,000행 이하면 즉시 완료 처리되고 signed URL이 응답에 포함된다.
         */
        @Test
        @DisplayName("F-2: 500행 scope → COMPLETED + signedDownloadUrl 발급")
        void syncExport_returnsCompletedWithUrl() {
            ExportRequest req = new ExportRequest(
                    "CSV",
                    "{\"dashboard_id\":5,\"row_count_estimate\":500}",
                    false);

            ExportResponse resp = exportService.createExport(REQUESTOR_A, req);

            assertThat(resp.status()).isEqualTo("COMPLETED");
            assertThat(resp.progressPct()).isEqualTo(100);
            assertThat(resp.rowCount()).isEqualTo(500);
            assertThat(resp.signedDownloadUrl()).isNotBlank();
            assertThat(resp.signedDownloadUrl()).contains("/api/v1/dashboard/export/" + resp.id() + "/download");
            assertThat(resp.signedDownloadUrl()).contains("sig=");
            assertThat(resp.signedDownloadUrl()).contains("exp=");
            assertThat(resp.expiresAt()).isNotNull();
            // expires_at 은 requested_at + 24h ~ NOW + 24h 사이
            assertThat(resp.expiresAt()).isAfter(Instant.now().plusSeconds(23 * 3600));
        }

        /**
         * F-5: 비동기 export 진행률 — mapper.updateProgress 후 getStatus 가 갱신 값을 노출.
         *
         * <p>실제 SXSSFWorkbook 작성은 v0.4+ 범위라 본 IT는 비동기 enqueue + progress
         * 갱신 경로의 통합 정합성만 검증한다. Awaitility로 @Async enqueue 안정화 대기.
         */
        @Test
        @DisplayName("F-5: progressPct 0 → 50 → 100 갱신이 status 응답에 반영")
        void progressUpdate_isReflectedInStatus() {
            ExportResponse created = exportService.createExport(
                    REQUESTOR_A,
                    new ExportRequest("EXCEL", "{\"row_count_estimate\":80000}", null));
            assertThat(created.status()).isEqualTo("PROCESSING");

            // @Async 큐 적재가 완료될 때까지 짧게 대기 (실패 시 직접 mapper 갱신과 동일 검증)
            await().atMost(5, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS)
                    .untilAsserted(() ->
                            assertThat(historyMapper.findById(created.id())).isPresent());

            historyMapper.updateProgress(created.id(), 50);
            ExportResponse mid = exportService.getStatus(created.id(), REQUESTOR_A);
            assertThat(mid.progressPct()).isEqualTo(50);
            assertThat(mid.status()).isEqualTo("PROCESSING");
            assertThat(mid.signedDownloadUrl()).isNull(); // 아직 미완료

            historyMapper.updateProgress(created.id(), 100);
            ExportResponse late = exportService.getStatus(created.id(), REQUESTOR_A);
            assertThat(late.progressPct()).isEqualTo(100);
            // status 자체는 별도 update 필요 — 본 IT 범위는 progress 갱신 검증.
        }
    }

    // =================================================================================
    // §F-6 — CSV BOM
    // =================================================================================
    @Nested
    @DisplayName("§F-6 CSV BOM + UTF-8 응답 헤더")
    class CsvBomTests {

        /**
         * F-6: 동기 CSV export 다운로드 시 응답 첫 3 byte 가 UTF-8 BOM (EF BB BF)
         * 이고 Content-Type 헤더가 text/csv; charset=UTF-8 이며 Content-Disposition
         * 가 attachment 다.
         *
         * <p>본 IT는 1차 출시 stub 동작을 검증한다. 실제 row 작성은 v0.4+ 도입.
         */
        @Test
        @DisplayName("F-6: CSV 다운로드 응답이 UTF-8 BOM 3바이트와 attachment 헤더 보유")
        void csvDownload_writesUtf8Bom() throws IOException {
            ExportResponse created = exportService.createExport(
                    REQUESTOR_A,
                    new ExportRequest("CSV", "{\"row_count_estimate\":300}", false));
            assertThat(created.status()).isEqualTo("COMPLETED");

            // signedDownloadUrl 에서 sig 파라미터 추출
            String url = created.signedDownloadUrl();
            String sig = url.substring(url.indexOf("sig=") + 4);
            int amp = sig.indexOf('&');
            if (amp > 0) sig = sig.substring(0, amp);
            long exp = Long.parseLong(url.substring(url.indexOf("exp=") + 4, url.indexOf("&sig=")));

            MockHttpServletResponse response = new MockHttpServletResponse();
            exportController.download(created.id(), REQUESTOR_A, sig, exp, response);

            byte[] body = response.getContentAsByteArray();
            assertThat(body).hasSizeGreaterThanOrEqualTo(3);
            assertThat(body[0]).isEqualTo((byte) 0xEF);
            assertThat(body[1]).isEqualTo((byte) 0xBB);
            assertThat(body[2]).isEqualTo((byte) 0xBF);

            assertThat(response.getContentType()).contains("text/csv");
            assertThat(response.getContentType()).contains("UTF-8");
            String disposition = response.getHeader(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(disposition).isNotNull();
            assertThat(disposition).contains("attachment");
            assertThat(disposition).contains("export-" + created.id() + ".csv");
        }
    }

    // =================================================================================
    // §F-10/F-11/F-12 — 다운로드 권한 매트릭스
    // =================================================================================
    @Nested
    @DisplayName("§F-10/F-11/F-12 다운로드 권한 매트릭스")
    class DownloadPermissionTests {

        /**
         * F-10: 본인이 본인 export 다운로드 시 verifyDownload 가 ExportHistory 반환.
         */
        @Test
        @DisplayName("F-10: 본인 다운로드 요청 → verifyDownload 성공 + 엔티티 반환")
        void owner_canVerifyDownload() {
            ExportResponse created = exportService.createExport(
                    REQUESTOR_A,
                    new ExportRequest("CSV", "{\"row_count_estimate\":100}", false));
            String sig = exportService.signFor(created.id(), created.expiresAt());

            ExportHistory verified = exportService.verifyDownload(
                    created.id(), REQUESTOR_A, false, sig);

            assertThat(verified.getId()).isEqualTo(created.id());
            assertThat(verified.getRequestorId()).isEqualTo(REQUESTOR_A);
            assertThat(verified.getStatus()).isEqualTo("COMPLETED");
        }

        /**
         * F-11: 타인이 본인 아닌 export 다운로드 시도 → ExportAccessDeniedException
         * → 전역 핸들러가 HTTP 403 EXPORT_ACCESS_DENIED 매핑.
         */
        @Test
        @DisplayName("F-11: 타인 다운로드 시도 → ExportAccessDeniedException (HTTP 403 매핑)")
        void nonOwner_isDeniedWith403Mapping() {
            ExportResponse created = exportService.createExport(
                    REQUESTOR_A,
                    new ExportRequest("CSV", "{\"row_count_estimate\":100}", false));
            String sig = exportService.signFor(created.id(), created.expiresAt());

            assertThatThrownBy(() -> exportService.verifyDownload(
                    created.id(), requestorBId, false, sig))
                    .isInstanceOf(ExportAccessDeniedException.class)
                    .hasMessageContaining(String.valueOf(created.id()));
        }

        /**
         * F-12: SUPER_ADMIN(isSuperAdmin=true) 은 본인이 아닌 export 도 다운로드 가능.
         */
        @Test
        @DisplayName("F-12: SUPER_ADMIN 다운로드 → 본인 아니어도 verifyDownload 성공")
        void superAdmin_canVerifyDownloadOfAnyExport() {
            ExportResponse created = exportService.createExport(
                    REQUESTOR_A,
                    new ExportRequest("CSV", "{\"row_count_estimate\":100}", false));
            String sig = exportService.signFor(created.id(), created.expiresAt());

            ExportHistory verified = exportService.verifyDownload(
                    created.id(), requestorBId, true, sig);

            assertThat(verified.getId()).isEqualTo(created.id());
        }

        /**
         * 본인이지만 서명이 잘못된 경우도 거부됨을 추가 회귀 검증 (signed URL HMAC 정합성).
         */
        @Test
        @DisplayName("F-10b: 본인이라도 sig 위조 → ExportAccessDeniedException")
        void owner_forgedSignature_isDenied() {
            ExportResponse created = exportService.createExport(
                    REQUESTOR_A,
                    new ExportRequest("CSV", "{\"row_count_estimate\":100}", false));

            assertThatThrownBy(() -> exportService.verifyDownload(
                    created.id(), REQUESTOR_A, false, "deadbeef-forged-signature"))
                    .isInstanceOf(ExportAccessDeniedException.class);
        }
    }

    // =================================================================================
    // §F-13/F-14 — 만료 + 이력 조회
    // =================================================================================
    @Nested
    @DisplayName("§F-13/F-14 만료 다운로드 + 이력 조회 스코프")
    class ExpiryAndHistoryTests {

        /**
         * F-13: expires_at 이 과거인 export 다운로드 시도 → ExportExpiredException
         * → 전역 핸들러가 HTTP 410 Gone + EXPORT_EXPIRED 매핑.
         */
        @Test
        @DisplayName("F-13: expires_at 과거 export → ExportExpiredException (HTTP 410 매핑)")
        void expiredExport_throwsGone() {
            // 직접 INSERT 로 만료된 export 행 적재 (24h 이전)
            ExportHistory expired = ExportHistory.builder()
                    .requestorId(REQUESTOR_A)
                    .exportType("CSV")
                    .scope("{\"row_count_estimate\":100}")
                    .status("COMPLETED")
                    .progressPct(100)
                    .rowCount(100)
                    .build();
            historyMapper.insert(expired);

            // expires_at 을 직접 과거로 갱신 (mapper.update 가 expires_at 컬럼 미포함이므로 JdbcTemplate 사용)
            jdbc.update("UPDATE export_history SET expires_at = NOW() - INTERVAL '1 hour' WHERE id = ?",
                    expired.getId());

            Instant pastExpiry = jdbc.queryForObject(
                    "SELECT expires_at FROM export_history WHERE id = ?",
                    Instant.class, expired.getId());
            String sig = exportService.signFor(expired.getId(), pastExpiry);

            assertThatThrownBy(() -> exportService.verifyDownload(
                    expired.getId(), REQUESTOR_A, false, sig))
                    .isInstanceOf(ExportExpiredException.class)
                    .hasMessageContaining(String.valueOf(expired.getId()));
        }

        /**
         * F-14: 본인의 export 이력 조회 시 타인 export 가 결과에 포함되지 않는다.
         */
        @Test
        @DisplayName("F-14: listHistory(A) 응답에 B 의 export 미포함")
        void historyList_isScopedToRequestor() {
            ExportResponse a1 = exportService.createExport(
                    REQUESTOR_A,
                    new ExportRequest("CSV", "{\"row_count_estimate\":100}", false));
            ExportResponse a2 = exportService.createExport(
                    REQUESTOR_A,
                    new ExportRequest("CSV", "{\"row_count_estimate\":200}", false));
            ExportResponse b1 = exportService.createExport(
                    requestorBId,
                    new ExportRequest("CSV", "{\"row_count_estimate\":50}", false));

            List<ExportResponse> aHistory = exportService.listHistory(REQUESTOR_A, null);
            List<Long> aIds = aHistory.stream().map(ExportResponse::id).toList();

            assertThat(aIds).contains(a1.id(), a2.id());
            assertThat(aIds).doesNotContain(b1.id());

            // status 필터 — COMPLETED 만 조회
            List<ExportResponse> aCompleted = exportService.listHistory(REQUESTOR_A, "COMPLETED");
            assertThat(aCompleted).allSatisfy(r ->
                    assertThat(r.status()).isEqualTo("COMPLETED"));
            assertThat(aCompleted.stream().map(ExportResponse::id).toList())
                    .contains(a1.id(), a2.id())
                    .doesNotContain(b1.id());
        }

        /**
         * 미존재 export id 조회 시 ExportNotFoundException → HTTP 404 매핑 (sanity check).
         */
        @Test
        @DisplayName("F-13b: 미존재 export id → ExportNotFoundException (HTTP 404 매핑)")
        void unknownExportId_throwsNotFound() {
            Long unknownId = 99_999_999L;
            // 사전 부재 확인
            Optional<ExportHistory> empty = historyMapper.findById(unknownId);
            assertThat(empty).isEmpty();

            assertThatThrownBy(() -> exportService.getStatus(unknownId, REQUESTOR_A))
                    .isInstanceOf(ExportNotFoundException.class);
        }
    }
}
