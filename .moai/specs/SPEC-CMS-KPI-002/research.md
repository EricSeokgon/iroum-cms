# SPEC-CMS-KPI-002 연구 노트 (코드베이스 분석)

본 문서는 SPEC 작성 전 실제 코드베이스를 조사한 결과다. SPEC 의 모든 기술 가정은 아래 사실에 근거한다.

## 1. access_log 실제 스키마 (V14__system_schema.sql, line 8-38)

```sql
CREATE TABLE access_log (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY,
    site_id          BIGINT       NOT NULL DEFAULT 1,
    user_id          BIGINT,                          -- nullable (비로그인 가능)
    session_id       VARCHAR(128),                    -- nullable
    ip_hash          CHAR(64)     NOT NULL,           -- SHA-256, PII 익명화
    user_agent       TEXT,
    referrer         TEXT,
    page_url         TEXT         NOT NULL,
    status_code      SMALLINT     NOT NULL,           -- ★ HTTP 상태코드 (response_status 아님)
    response_time_ms INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 파티션 키
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
```

핵심 정정 (작업 지시 대비):
- 오류 응답 컬럼명은 **`status_code`** (SMALLINT) 이며 작업 지시의 `response_status` 가 아니다.
- 세션 식별 컬럼은 **`session_id`** (VARCHAR(128), nullable) 으로 존재한다.
- `response_time_ms` 컬럼이 존재한다 (세션 지속시간과는 무관, 단건 응답시간).
- 기존 인덱스: `idx_access_log_status (status_code, created_at DESC)` → 오류율 집계 가속.
- 기존 인덱스: `idx_access_log_page_url (page_url, created_at DESC)` → 콘텐츠 조회 분류 가속.
- 월별 RANGE 파티션. V45 가 2026-06/2026-07 파티션을 선행 생성함.
- access_log 3개월 TTL 삭제 정책 존재(부모 SPEC) → 집계 후 kpi_value_history 아카이브 필수.

## 2. kpi_value / kpi_definition 실제 스키마 (V17__dashboard_schema.sql, line 8-43)

```sql
CREATE TABLE kpi_definition (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    calculation_query TEXT NOT NULL,          -- ★ NOT NULL — 시드 시 산식 설명 필수
    refresh_interval_min INTEGER NOT NULL DEFAULT 60,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- CHECK IN ('ACTIVE','INACTIVE')
    ...
);

CREATE TABLE kpi_value (
    id BIGSERIAL PRIMARY KEY,
    kpi_id BIGINT NOT NULL REFERENCES kpi_definition(id) ON DELETE CASCADE,
    dimension JSONB NOT NULL DEFAULT '{}'::jsonb,
    value_numeric NUMERIC(20,4) NULL,
    value_text TEXT NULL,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_kpi_value UNIQUE (kpi_id, dimension)   -- ★ 멱등 UPSERT 키
);
```

핵심 정정 (작업 지시 대비):
- `kpi_value` 에는 **`period`/`calc_date`/`code` 컬럼이 없다.** 작업 지시의 `dimension={"period":...}` 표현은 실제 스키마와 다르다.
- 일/월 구분은 **dimension JSONB 에 인코딩**한다. 부모 SPEC 구현 규약: 일별 = `{"date":"YYYY-MM-DD"}`.
- KPI 종류는 `kpi_definition.code` 로 구분하고, `kpi_value.kpi_id` FK 로 연결한다.
- 시간 컬럼은 `calculated_at` (MV 에서 `aggregated_at` 별칭으로 노출).
- 멱등 UPSERT 는 `ON CONFLICT (kpi_id, dimension) DO UPDATE` 로 동작 (UNIQUE 제약 `uk_kpi_value`).
- `value_numeric` 은 NUMERIC(20,4) — 비율은 0~1 또는 0~100, 카운트는 정수, 지속시간(초)은 정수.

## 3. KPI 집계 MV (V45__kpi_aggregation_mv.sql)

- `kpi_aggregation_mv` = `kpi_value × kpi_definition` JOIN, 컬럼: kpi_id, kpi_code, kpi_name, dimension, value_numeric, value_text, aggregated_at.
- `CREATE UNIQUE INDEX uk_kpi_aggregation_mv ON kpi_aggregation_mv (kpi_id, dimension)` → CONCURRENTLY REFRESH 전제.
- `idx_kpi_aggregation_mv_code (kpi_code)` 보조 인덱스.
- 기존 3종 KPI 시드는 `INSERT ... ON CONFLICT (code) DO NOTHING` 패턴 사용.
- MV 는 신규 KPI dimension 형태(예: `{"date","contentType"}`)도 그대로 수용한다(JSONB 컬럼). **MV 정의/인덱스 변경 불필요.**

## 4. 집계 배치 확장 지점

### KpiAggregationJob.java
- `@Scheduled(cron = "0 0 4 * * *")` runDaily() → `service.aggregateAll(어제)` 호출 후 `mapper.refreshAggregationMv()` (트랜잭션 밖).
- 신규 KPI 추가 시 Job 변경 불필요 — service.aggregateAll 내부만 확장.

### KpiAggregationServiceImpl.java (확장 대상)
- `@Transactional aggregateAll(LocalDate)` 내부에서 KPI 별 **독립 try-catch 격리** 패턴 (AC-003).
- 각 KPI: `requireKpiId(code)` → `archiveExisting(kpiId, dimJson)` → `upsertXxx(...)` → `processed++`.
- 실패 시 `failed++`, `errors.add(...)`, 나머지 KPI 계속.
- 종료 시 `batch_execution_log` 에 SUCCESS/FAILURE 기록.
- 일별 dimension 헬퍼: `dayDimension(date)` = `{"date":"YYYY-MM-DD"}`.
- **신규 KPI 4종은 이 메서드에 try-catch 블록 추가로 확장.**

### KpiAggregationMapper.xml (확장 대상)
- 기존 UPSERT 패턴: `INSERT INTO kpi_value (...) SELECT 집계식 FROM access_log WHERE created_at >= :targetDate::date AND created_at < :targetDate::date + INTERVAL '1 day' ON CONFLICT (kpi_id, dimension) DO UPDATE`.
- 파티션 프루닝: `created_at` 범위 조건 필수 (REQ 계승).
- `archiveExisting` / `findKpiIdByCode` / `refreshAggregationMv` 재사용.
- **신규 4종 UPSERT 쿼리를 동일 패턴으로 추가.**

## 5. 조회 API — 신규 엔드포인트 불필요

### AdminKpiController.java + KpiQueryMapper.xml
- `GET /api/v1/admin/kpi/values` 는 `kpiCode`, `fromDate`, `toDate`, `dimensionJson`, `granularity`, `page`, `size` 파라미터로 **임의 KPI code 를 동적 조회**한다.
- KpiQueryMapper `filterWhere`: `kd.code = :kpiCode`, `kv.calculated_at` 범위, `kv.dimension @> :dimensionJson::jsonb`, `jsonb_exists(dimension, :granularityKey)`.
- 클래스 레벨 `@PreAuthorize("hasRole('ADMIN')")` + `@AuditLog`.
- **결론: 신규 KPI 4종은 기존 `/values` 엔드포인트로 그대로 조회 가능. 컨트롤러/매퍼 변경 불필요(필터 확장만, 신규 엔드포인트 0개).**
- export 경로(`KpiQueryMapper.searchForExport`)도 code 무관하게 동작 → 신규 KPI 자동 내보내기 지원.

## 6. 프런트엔드 확장 지점

- `frontend/admin/src/api/kpi.ts`: `KPI_CODES` 상수 (UPPERCASE). 신규 4종 코드 추가 필요.
- `frontend/admin/src/stores/kpiStore.ts`: `KPI_CODES` 별 computed getter 패턴 (`featureUsageItems` 등). 신규 4종 getter 추가.
- `frontend/admin/src/views/dashboard/KpiDashboardView.vue`: `KpiSummaryCards`, `KpiTrendChart`, `KpiConversionFunnel` 위젯 조합. 신규 카드/차트 추가.
- 컴포넌트: `KpiSummaryCards.vue`(METRIC_CARD), `KpiTrendChart.vue`(vue-echarts LINE_CHART), `KpiFilterPanel.vue` 재사용 가능.

## 7. 마이그레이션 번호

- 현재 tip: **V52** (`V52__super_admin_permissions_sync.sql`). V11 결번. 총 51개.
- 다음 번호: **V53** (작업 지시와 일치).
- V53 은 `kpi_definition` 신규 5코드 INSERT (DAU, MAU, CONTENT_VIEW, AVG_SESSION_DURATION, API_ERROR_RATE) 만 수행. 신규 테이블/컬럼/MV 변경 없음.

## 8. 테스트 패턴

- IT 베이스: `AbstractIntegrationTest` (실 PostgreSQL 16 + Flyway 전체 적용).
- 기존 `KpiAggregationServiceImplIT`: access_log/audit_log 시드 → `aggregateAll(TARGET)` → kpi_value/history/batch_execution_log 검증.
- TARGET 일자는 파티션 존재 범위(2026-06/2026-07) 내여야 함. 신규 일자 집계 시 파티션 사전 확인 필요.
- 프런트: `kpiStore.spec.ts` (vitest) 패턴.

## 9. 위험 식별

- access_log 의 `user_id`/`session_id` 가 nullable → DAU/MAU/세션 집계 시 NULL 제외 필요 (비로그인/세션없음 행).
- 세션 지속시간: access_log 에 명시적 세션 종료 이벤트 없음 → 30분 idle gap 으로 세션 경계 정의 필요(윈도우 함수).
- API_ERROR_RATE: 분모가 0(해당 일자 access_log 없음)이면 NULLIF 로 0 처리.
- CONTENT_VIEW: dimension 에 contentType 추가 → `(kpi_id, dimension)` UNIQUE 가 (date, contentType) 조합별 1행 보장.
- audit_log 부모 SPEC 의 자식: 신규 KPI 는 access_log 만 사용(파일 다운로드는 부모 KPI-001 이 audit_log 사용).
