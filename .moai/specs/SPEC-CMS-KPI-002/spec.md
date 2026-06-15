---
id: SPEC-CMS-KPI-002
version: 0.1.0
status: Implemented
created_at: 2026-06-15
updated: 2026-06-15
author: manager-spec
priority: high
parent: SPEC-CMS-KPI-001
labels: [kpi, dashboard, aggregation, access-log, dau-mau, session, error-rate]
related:
  - SPEC-CMS-KPI-001 (플랫폼 KPI 통합 관리 — 집계/조회/내보내기 인프라 재사용 대상, Completed v1.0.2)
  - SPEC-CMS-008 (시각화 대시보드 + KPI 위젯 — kpi_value/MV/vue-echarts 원천 인프라)
  - SPEC-CMS-005 (access_log 시스템 로그 스키마 — 신규 4종 KPI 단일 데이터 원천)
  - SPEC-CMS-002 (RBAC — ADMIN 권한 검증)
issue_number: null
---

# SPEC-CMS-KPI-002 운영 활동 지표 KPI 확장

## HISTORY

- v0.1 / 2026-06-15 / manager-spec / 신규 작성. 부모 SPEC-CMS-KPI-001(Completed) 이 구축한 KPI 집계·조회·내보내기·위젯 인프라 위에, `access_log` 단일 원천을 사용하는 **운영 활동 지표 4종**(DAU/MAU, 콘텐츠 조회 수, 평균 세션 지속 시간, API 오류 응답 비율)을 추가한다. 신규 테이블/컬럼/구조 DDL 및 신규 API 엔드포인트를 도입하지 않으며, `kpi_definition` 시드 INSERT 단일 마이그레이션(V53)과 집계 로직·프런트 위젯 확장만 수행한다.

---

## 1. 개요

본 SPEC 은 부모 SPEC-CMS-KPI-001(Completed) 이 완성한 KPI 인프라 위에 **운영 활동 지표(operational activity metrics) 4종 격차** 를 채우는 자식 SPEC 이다.

부모 SPEC 이 다룬 3종(기능별 이용률, 파일 다운로드 수, 정책 매칭 전환율)은 "콘텐츠·정책 성과" 관점이었다. 본 SPEC 은 "사용자 활동·시스템 건강성" 관점의 4종을 추가한다.

핵심 가치:

- 운영자가 **활성 사용자(DAU/MAU)** 추이로 플랫폼 도달·유지 성과를 측정
- **콘텐츠 유형별 조회 수**(공지/게시물/발간자료)로 콘텐츠 영향력 비교
- **평균 세션 지속 시간**으로 사용자 몰입도(engagement)를 정량화
- **API 오류 응답 비율**로 시스템 안정성을 상시 모니터링

본 SPEC 은 새로운 KPI 데이터 모델·API 엔드포인트를 도입하지 않으며, 기존 `kpi_value`(V17) 테이블·`KpiAggregationServiceImpl` 집계 격리 패턴·`GET /api/v1/admin/kpi/values` 동적 조회 API·KPI 대시보드 위젯을 **확장**하여 4종을 통합한다.

---

## 2. 목표 및 범위

### 2.1 비즈니스 목표

- 사용자 활동 지표(DAU/MAU)와 시스템 건강성 지표(오류율)를 단일 KPI 화면에서 통합 조회
- 콘텐츠 유형별 조회 수로 콘텐츠 운영 의사결정 근거 제공
- 세션 지속 시간으로 UX 개선 효과를 시계열로 추적

### 2.2 범위 경계

- **포함**: 신규 KPI 4종의 일별 집계 로직(`access_log` 원천), `kpi_definition` 시드(V53), 프런트 위젯 4종 확장
- **제외**: 신규 KPI 데이터 모델, 신규 API 엔드포인트, 실시간 스트리밍 집계, ML 예측, access_log 스키마 변경 (제9절 참조)

---

## 3. 배경 및 컨텍스트

### 3.1 부모 SPEC-CMS-KPI-001 이 이미 제공하는 것 (재사용)

| 기능 | 위치 | 본 SPEC 활용 |
|---|---|---|
| KPI 값 테이블 | `kpi_value` (V17): `kpi_id` FK, JSONB `dimension`, `value_numeric`, `calculated_at` | 신규 4종 집계 결과 UPSERT 대상 |
| 멱등 UPSERT 키 | `uk_kpi_value UNIQUE (kpi_id, dimension)` | `ON CONFLICT (kpi_id, dimension) DO UPDATE` |
| 시계열 아카이브 | `kpi_value_history` + `archiveExisting` 쿼리 | 갱신 전 값 보존 (access_log 3개월 TTL 대비) |
| 집계 배치 스케줄 | `KpiAggregationJob @Scheduled "0 0 4 * * *"` | 변경 없이 재사용 (service 확장만) |
| KPI 단위 실패 격리 | `KpiAggregationServiceImpl.aggregateAll` try-catch 패턴 | 신규 4종을 동일 패턴으로 추가 |
| 집계 MV | `kpi_aggregation_mv` + `uk_kpi_aggregation_mv UNIQUE (kpi_id, dimension)` | 신규 dimension 자동 수용 (MV 변경 불필요) |
| 동적 조회 API | `GET /api/v1/admin/kpi/values` (kpiCode/날짜/dimension/granularity 필터) | 신규 KPI 코드로 그대로 조회 (엔드포인트 추가 0개) |
| 트렌드 API | `GET .../values` + granularity | 신규 KPI 트렌드 자동 지원 |
| 엑셀 내보내기 | `KpiQueryMapper.searchForExport` (code 무관) | 신규 KPI 자동 내보내기 |
| KPI 위젯 | `KpiSummaryCards.vue`(METRIC_CARD), `KpiTrendChart.vue`(vue-echarts LINE_CHART), `KpiFilterPanel.vue` | 신규 4종 카드/차트 추가 |
| Pinia 스토어 | `kpiStore.ts` (KPI_CODES 별 computed getter 패턴) | 신규 4종 getter 추가 |

→ 본 SPEC 은 위 목록을 **변경하거나 대체하지 않는다. 모두 additive 확장이다.**

### 3.2 access_log 단일 데이터 원천 (V14__system_schema.sql)

신규 KPI 4종은 모두 `access_log` 테이블 하나만 원천으로 사용한다. 실제 스키마(research.md §1 참조):

| 컬럼 | 타입 | 본 SPEC 사용 |
|---|---|---|
| `user_id` | BIGINT (nullable) | DAU/MAU `COUNT(DISTINCT user_id)` (NULL 제외) |
| `session_id` | VARCHAR(128) (nullable) | 세션 지속 시간 그룹핑 키 (NULL 제외) |
| `page_url` | TEXT NOT NULL | 콘텐츠 조회 수 URL 패턴 분류 |
| `status_code` | SMALLINT NOT NULL | API 오류율 `status_code >= 500` |
| `created_at` | TIMESTAMPTZ NOT NULL (파티션 키) | 파티션 프루닝 범위 조건 + 세션 시각 |

[HARD] 정정 사항: 오류 응답 컬럼명은 **`status_code`** (작업 지시의 `response_status` 아님). 기존 인덱스 `idx_access_log_status (status_code, created_at DESC)` 가 오류율 집계를 가속한다.

### 3.3 본 SPEC 이 채우는 격차

| 격차 | 현재 상태 | 본 SPEC 해결 방식 |
|---|---|---|
| 활성 사용자 지표 | KPI 부재 | DAU(일별)/MAU(월별) `COUNT(DISTINCT user_id)` 집계 |
| 콘텐츠 유형별 조회 | KPI 부재 (부모는 기능별 이용률만) | `page_url` 패턴을 notice/post/publication 으로 분류 집계 |
| 사용자 몰입도 | KPI 부재 | session_id 별 (MAX-MIN) created_at → AVG 세션 지속(초) |
| 시스템 안정성 | KPI 부재 | `status_code >= 500` 비율 집계 |

---

## 4. 데이터 모델

### 4.1 재사용 테이블 (변경 없음)

`kpi_definition` / `kpi_value` / `kpi_value_history` / `kpi_aggregation_mv` — 모두 V17/V45 기존 스키마 그대로 사용. **신규 테이블·컬럼·인덱스·MV 변경 없음.**

### 4.2 신규 KPI 정의 (kpi_definition 시드)

| KPI code | 지표명(name) | 산식 | 집계 주기 | dimension 형태 |
|---|---|---|---|---|
| `DAU` | 일 활성 사용자 | `COUNT(DISTINCT user_id)` (일별) | 일별 | `{"date":"YYYY-MM-DD"}` |
| `MAU` | 월 활성 사용자 | `COUNT(DISTINCT user_id)` (월별) | 월별 | `{"month":"YYYY-MM"}` |
| `CONTENT_VIEW` | 콘텐츠 조회 수 | `COUNT(*)` GROUP BY 콘텐츠 유형 (일별) | 일별 | `{"date":"YYYY-MM-DD","contentType":"notice\|post\|publication"}` |
| `AVG_SESSION_DURATION` | 평균 세션 지속 시간(초) | 세션별 (MAX-MIN) created_at → AVG (일별) | 일별 | `{"date":"YYYY-MM-DD"}` |
| `API_ERROR_RATE` | API 오류 응답 비율(%) | `COUNT(status_code>=500) / COUNT(*) * 100` (일별) | 일별 | `{"date":"YYYY-MM-DD"}` |

비고:
- KPI 종류 5개 코드, 지표 4개 묶음. DAU/MAU 는 동일 "활성 사용자" 지표의 일별/월별 분리 코드.
- 일별 dimension 키는 부모 SPEC 규약(`{"date":"YYYY-MM-DD"}`)을 계승. 월별은 `{"month":"YYYY-MM"}`.
- `CONTENT_VIEW` 는 dimension 에 `contentType` 추가 → `(kpi_id, dimension)` UNIQUE 가 (date, contentType) 조합별 1행 보장.

### 4.3 콘텐츠 유형 URL 패턴 분류

| contentType | page_url 패턴(정규식) | 비고 |
|---|---|---|
| `notice` | `~ '/notices?/'` 또는 `/board/notice` | 공지사항 |
| `post` | `~ '/posts?/'` 또는 `/board/(free\|qna)/` | 일반 게시물 |
| `publication` | `~ '/publications?/'` 또는 `/pub/` | 발간자료 |

[HARD] 위 정규식은 RUN 단계에서 실제 `access_log.page_url` 샘플로 보정한다. 어느 패턴에도 매칭되지 않는 URL 은 집계 대상에서 제외한다(3개 유형만 분류).

### 4.4 신규 마이그레이션 — V53 (시드 전용)

- 파일: `V53__kpi_definition_activity_seed.sql` (현재 tip V52 다음 번호; RUN 시 `ls V*.sql | sort -V | tail` 로 재확인)
- 내용: `kpi_definition` 에 신규 5코드 INSERT (`ON CONFLICT (code) DO NOTHING`).
  - `calculation_query` 는 NOT NULL 이므로 산식 설명 문자열 적재 (부모 V45 시드 패턴 계승).
  - `status='ACTIVE'`, `refresh_interval_min`: 일별=1440, 월별(MAU)=43200.
- [HARD] **신규 테이블/컬럼/구조 DDL 금지.** INSERT 문만 허용.
- 백필: 불필요 (집계 배치가 첫 실행 시 kpi_value 를 채움).

---

## 5. 요구사항 (EARS)

### REQ-KPI2-001 DAU/MAU 집계

- **REQ-KPI2-001-1 (DAU 집계 — Ubiquitous)**
  시스템은 일별로 `access_log` 에서 `COUNT(DISTINCT user_id)`(user_id IS NOT NULL)를 집계하여 `DAU` KPI 값을 `kpi_value` 에 `dimension={"date":"YYYY-MM-DD"}` 로 UPSERT 해야 한다.
- **REQ-KPI2-001-2 (MAU 집계 — Ubiquitous)**
  시스템은 월별로 해당 월 `access_log` 의 `COUNT(DISTINCT user_id)` 를 집계하여 `MAU` KPI 값을 `dimension={"month":"YYYY-MM"}` 로 UPSERT 해야 한다.
- **REQ-KPI2-001-3 (파티션 프루닝 — Ubiquitous)**
  시스템은 DAU/MAU 집계 시 항상 `created_at` 범위 조건(일별: 해당일 0시~익일 0시, 월별: 해당월 1일~익월 1일)을 포함하여 월별 RANGE 파티션 제거가 동작하도록 해야 한다.
- **REQ-KPI2-001-4 (비로그인 제외 — Unwanted)**
  IF `access_log.user_id` 가 NULL(비로그인 접속)이면, THEN 시스템은 해당 행을 활성 사용자 집계에서 제외해야 한다.
- **REQ-KPI2-001-5 (빈 일자 0 처리 — Unwanted)**
  IF 대상 일자/월에 `access_log` 행이 없으면, THEN 시스템은 값을 누락하지 않고 `value_numeric=0` 으로 1행 UPSERT 해야 한다.

### REQ-KPI2-002 콘텐츠 조회 수 집계

- **REQ-KPI2-002-1 (유형별 집계 — Ubiquitous)**
  시스템은 일별로 `access_log.page_url` 을 notice/post/publication 유형으로 분류하여 유형별 `COUNT(*)` 를 `CONTENT_VIEW` KPI 로 `dimension={"date","contentType"}` 별로 UPSERT 해야 한다.
- **REQ-KPI2-002-2 (미분류 제외 — Unwanted)**
  IF `page_url` 이 3개 콘텐츠 유형 패턴 중 어느 것에도 매칭되지 않으면, THEN 시스템은 해당 행을 콘텐츠 조회 집계에서 제외해야 한다.
- **REQ-KPI2-002-3 (유형 조합 멱등 — Ubiquitous)**
  시스템은 (date, contentType) 조합별로 정확히 1행을 유지하며, 재집계 시 `ON CONFLICT (kpi_id, dimension) DO UPDATE` 로 멱등하게 갱신해야 한다.
- **REQ-KPI2-002-4 (유형별 0 처리 — Unwanted)**
  IF 특정 유형의 조회가 해당 일자에 0건이면, THEN 시스템은 해당 유형 행을 `value_numeric=0` 으로 기록하거나(존재 보장) 미기록할 수 있으며, 조회 응답 시 빈 유형은 0 으로 간주해야 한다.

### REQ-KPI2-003 평균 세션 지속 시간 집계

- **REQ-KPI2-003-1 (세션 지속 산출 — Ubiquitous)**
  시스템은 일별로 `access_log` 를 `session_id` 로 그룹핑하여 세션별 지속시간(`EXTRACT(EPOCH FROM (MAX(created_at) - MIN(created_at)))` 초)을 산출하고, 전체 세션 평균을 `AVG_SESSION_DURATION` KPI 로 `dimension={"date":"YYYY-MM-DD"}` 에 UPSERT 해야 한다.
- **REQ-KPI2-003-2 (단일 요청 세션 — Unwanted)**
  IF 한 세션의 요청이 1건뿐이면(MAX=MIN), THEN 시스템은 해당 세션 지속시간을 0초로 계산해야 한다.
- **REQ-KPI2-003-3 (idle gap 세션 경계 — State-driven)**
  WHILE 동일 `session_id` 내 인접 요청 간격이 30분(1800초)을 초과하는 idle gap 이 존재하면, 시스템은 해당 gap 을 세션 경계로 간주하여 분리된 하위 세션 단위로 지속시간을 산출해야 한다.
- **REQ-KPI2-003-4 (세션 없음 제외 — Unwanted)**
  IF `access_log.session_id` 가 NULL 이면, THEN 시스템은 해당 행을 세션 지속 집계에서 제외해야 한다.
- **REQ-KPI2-003-5 (세션 부재 0 처리 — Unwanted)**
  IF 대상 일자에 유효 세션이 없으면, THEN 시스템은 평균을 0초로 UPSERT 해야 한다.

### REQ-KPI2-004 API 오류 응답 비율 집계

- **REQ-KPI2-004-1 (오류율 집계 — Ubiquitous)**
  시스템은 일별로 `access_log` 에서 `COUNT(CASE WHEN status_code >= 500 THEN 1 END)::numeric / NULLIF(COUNT(*),0) * 100` 을 계산하여 `API_ERROR_RATE` KPI 를 `dimension={"date":"YYYY-MM-DD"}` 에 UPSERT 해야 한다.
- **REQ-KPI2-004-2 (오류 기준 — Ubiquitous)**
  시스템은 HTTP `status_code >= 500` 을 서버 오류로 정의하고 비율 분자로 집계해야 한다(4xx 는 클라이언트 오류로 분자에서 제외).
- **REQ-KPI2-004-3 (분모 0 처리 — Unwanted)**
  IF 대상 일자에 `access_log` 행이 없으면(분모 0), THEN 시스템은 NULLIF 로 0% 를 UPSERT 해야 하며 division-by-zero 오류를 발생시키지 않아야 한다.

### REQ-KPI2-005 집계 배치 통합

- **REQ-KPI2-005-1 (배치 스케줄 재사용 — Event-driven)**
  WHEN 매일 04:00 KST `KpiAggregationJob` 이 실행되면, THEN 시스템은 부모 SPEC 의 기존 3종에 더해 신규 4종(DAU, CONTENT_VIEW, AVG_SESSION_DURATION, API_ERROR_RATE)을 전일자 기준으로 집계해야 한다.
- **REQ-KPI2-005-2 (MAU 월별 갱신 — Event-driven)**
  WHEN KPI 배치가 실행되면, THEN 시스템은 진행 중인 당월(또는 전일이 속한 월)의 MAU 를 `COUNT(DISTINCT user_id)` 로 재계산하여 멱등 UPSERT 해야 한다.
- **REQ-KPI2-005-3 (실패 격리 — Unwanted)**
  IF 신규 KPI 중 하나의 집계 쿼리가 실패하면, THEN 시스템은 해당 KPI 만 실패 처리하고 나머지 신규·기존 KPI 집계를 계속 진행하며, 실패를 `batch_execution_log` 에 기록해야 한다(부모 try-catch 격리 패턴 계승).
- **REQ-KPI2-005-4 (아카이브 — Event-driven)**
  WHEN 신규 KPI 집계가 기존 `kpi_value` 행을 갱신하면, THEN 시스템은 갱신 전 값을 `kpi_value_history` 에 `archiveExisting` 으로 아카이브해야 한다.
- **REQ-KPI2-005-5 (MV 갱신 재사용 — Event-driven)**
  WHEN 집계 트랜잭션이 커밋되면, THEN 시스템은 기존 `refreshAggregationMv()`(CONCURRENTLY)를 그대로 호출하여 신규 KPI dimension 을 포함한 MV 를 비차단 갱신해야 한다.

### REQ-KPI2-006 조회 및 보안 (기존 인프라 재사용)

- **REQ-KPI2-006-1 (기존 조회 API 재사용 — Ubiquitous)**
  시스템은 신규 KPI 4종을 기존 `GET /api/v1/admin/kpi/values` 엔드포인트로 `kpiCode` 필터를 통해 조회 가능하게 해야 하며, 신규 조회 엔드포인트를 추가하지 않아야 한다.
- **REQ-KPI2-006-2 (granularity 트렌드 — Event-driven)**
  WHEN 운영자가 신규 KPI 의 시계열 트렌드를 요청하면, THEN 시스템은 기존 `granularity`(daily/monthly) 필터와 `calculated_at` 범위로 시계열을 반환해야 한다.
- **REQ-KPI2-006-3 (ADMIN 권한 — Ubiquitous)**
  시스템은 신규 KPI 조회를 기존 `@PreAuthorize("hasRole('ADMIN')")` + `@AuditLog` 계약 하에서만 허용해야 한다.
- **REQ-KPI2-006-4 (PII 비노출 — Ubiquitous)**
  시스템은 신규 KPI 집계·조회 결과에 원본 `user_id`/`session_id`/`ip_hash` 를 노출하지 않고 집계값(카운트·비율·평균)만 반환해야 한다.

### REQ-KPI2-007 UI 위젯 확장

- **REQ-KPI2-007-1 (요약 카드 — Ubiquitous)**
  프런트엔드는 DAU/MAU·오류율을 기존 `KpiSummaryCards`(METRIC_CARD) 위젯으로 요약 표시하고 전기 대비 증감을 표기해야 한다.
- **REQ-KPI2-007-2 (트렌드 차트 — Event-driven)**
  WHEN 운영자가 활동 지표 트렌드를 조회하면, THEN 프런트엔드는 기존 `KpiTrendChart`(vue-echarts LINE_CHART)로 DAU·세션 지속·오류율 시계열을 렌더링해야 한다.
- **REQ-KPI2-007-3 (콘텐츠 유형 비교 — Ubiquitous)**
  프런트엔드는 콘텐츠 조회 수를 유형별(notice/post/publication) 막대/누적 차트로 비교 표시해야 한다.
- **REQ-KPI2-007-4 (필터 연동 — Event-driven)**
  WHEN 운영자가 기존 `KpiFilterPanel` 에서 기간을 변경하면, THEN 프런트엔드는 신규 KPI 위젯도 함께 갱신해야 한다.
- **REQ-KPI2-007-5 (코드 상수 확장 — Ubiquitous)**
  프런트엔드는 `KPI_CODES` 상수와 `kpiStore` getter 에 신규 4종을 추가하되 기존 3종 getter 를 변경하지 않아야 한다.

---

## 6. 시스템 설계

### 6.1 백엔드 (확장 only)

```
[Batch]   KpiAggregationJob (변경 없음 — 기존 04:00 스케줄 재사용)
              │
[Service] KpiAggregationServiceImpl.aggregateAll(targetDate)
              │  ← 신규 4종 try-catch 격리 블록 추가 (기존 3종 유지)
[Mapper]  KpiAggregationMapper.xml
              │  ← upsertDau / upsertMau / upsertContentView / upsertAvgSessionDuration / upsertApiErrorRate 추가
              │     (기존 archiveExisting / findKpiIdByCode / refreshAggregationMv 재사용)
[DB]      access_log(파티션) → kpi_value (UPSERT) → kpi_value_history (archive) → kpi_aggregation_mv (REFRESH)
[Query]   AdminKpiController + KpiQueryMapper (변경 없음 — code 동적 필터로 신규 KPI 자동 조회)
```

- 패키지 루트: `kr.co.ircp.cms.domain.dashboard.kpi` (부모와 동일, 응집 유지)
- 신규 Java 클래스 최소화: 집계 로직은 기존 `KpiAggregationServiceImpl` + `KpiAggregationMapper` 확장으로 충분. 신규 컨트롤러/DTO 불필요.

### 6.2 집계 SQL 스케치 (실제 스키마 기준)

DAU (일별, 파티션 프루닝):
```sql
INSERT INTO kpi_value (kpi_id, dimension, value_numeric, calculated_at)
SELECT #{kpiId}, #{dimensionJson}::jsonb,
       COUNT(DISTINCT user_id)::numeric, NOW()
FROM access_log
WHERE created_at >= #{targetDate}::date
  AND created_at <  #{targetDate}::date + INTERVAL '1 day'
  AND user_id IS NOT NULL
ON CONFLICT (kpi_id, dimension) DO UPDATE SET
  value_numeric = EXCLUDED.value_numeric, calculated_at = NOW();
```

API_ERROR_RATE (일별, NULLIF 분모 보호):
```sql
... ROUND(
  COUNT(*) FILTER (WHERE status_code >= 500)::numeric
  / NULLIF(COUNT(*), 0) * 100, 4) ...
```

AVG_SESSION_DURATION (세션별 지속 후 평균, 30분 idle gap 분리):
```sql
WITH ordered AS (
  SELECT session_id, created_at,
    CASE WHEN created_at - LAG(created_at) OVER (PARTITION BY session_id ORDER BY created_at)
         > INTERVAL '30 minutes' THEN 1 ELSE 0 END AS new_seg
  FROM access_log
  WHERE created_at >= :d::date AND created_at < :d::date + INTERVAL '1 day'
    AND session_id IS NOT NULL
),
segmented AS (
  SELECT session_id, created_at,
    SUM(new_seg) OVER (PARTITION BY session_id ORDER BY created_at) AS seg_no
  FROM ordered
),
durations AS (
  SELECT EXTRACT(EPOCH FROM (MAX(created_at) - MIN(created_at))) AS dur_sec
  FROM segmented GROUP BY session_id, seg_no
)
SELECT COALESCE(AVG(dur_sec), 0) FROM durations;
```

[HARD] 위 SQL 은 설계 스케치다. RUN 단계에서 MyBatis XML 로 작성하며 `&gt;`/`&lt;` 이스케이프와 파티션 범위 조건을 준수한다.

### 6.3 프런트엔드 (확장 only)

```
api/kpi.ts          → KPI_CODES 에 DAU/MAU/CONTENT_VIEW/AVG_SESSION_DURATION/API_ERROR_RATE 추가
stores/kpiStore.ts  → dauItems / mauItems / contentViewItems / sessionDurationItems / errorRateItems getter 추가
views/dashboard/KpiDashboardView.vue → 신규 카드/차트 위젯 배치
components/dashboard/KpiSummaryCards.vue, KpiTrendChart.vue → props 로 신규 KPI 데이터 전달 (컴포넌트 재사용)
```

---

## 7. 수용 기준 (Acceptance Criteria)

상세 Given/When/Then 시나리오는 `acceptance.md` 참조. 요약:

- **AC-001**: `aggregateAll` 이 access_log 의 `COUNT(DISTINCT user_id)` 를 DAU 로 `dimension={"date"}` 에 UPSERT 한다.
- **AC-002**: MAU 가 월 범위 `COUNT(DISTINCT user_id)` 로 `dimension={"month"}` 에 멱등 UPSERT 된다.
- **AC-003**: user_id=NULL 행이 활성 사용자 집계에서 제외된다.
- **AC-004**: 빈 일자 집계 시 DAU=0 으로 1행 UPSERT 된다.
- **AC-005**: CONTENT_VIEW 가 page_url 패턴으로 notice/post/publication 유형별 COUNT 를 `dimension={"date","contentType"}` 별로 UPSERT 한다.
- **AC-006**: 3개 유형에 매칭되지 않는 page_url 이 콘텐츠 집계에서 제외된다.
- **AC-007**: (date, contentType) 조합 재집계가 멱등하게 갱신된다(중복 행 미생성).
- **AC-008**: AVG_SESSION_DURATION 이 session_id 별 (MAX-MIN) 평균(초)으로 UPSERT 된다.
- **AC-009**: 단일 요청 세션(MAX=MIN)의 지속시간이 0초로 계산된다.
- **AC-010**: 30분 초과 idle gap 이 세션 경계로 분리되어 하위 세션별 지속시간이 산출된다.
- **AC-011**: session_id=NULL 행이 세션 집계에서 제외된다.
- **AC-012**: API_ERROR_RATE 가 `status_code>=500` 비율(%)로 UPSERT 된다.
- **AC-013**: access_log 행이 없을 때 오류율이 NULLIF 로 0% UPSERT 되고 예외가 발생하지 않는다.
- **AC-014**: 신규 KPI 중 하나의 집계 실패 시 나머지 신규·기존 KPI 집계가 계속 진행되고 batch_execution_log 에 기록된다.
- **AC-015**: 신규 KPI 집계가 갱신 전 값을 kpi_value_history 에 아카이브한다.
- **AC-016**: 집계 후 kpi_aggregation_mv 가 신규 dimension 을 포함하여 CONCURRENTLY REFRESH 된다.
- **AC-017**: 신규 KPI 4종이 기존 `GET /api/v1/admin/kpi/values?kpiCode=...` 로 조회된다(신규 엔드포인트 0개).
- **AC-018**: 비ADMIN 역할의 신규 KPI 조회가 403 으로 거부된다.
- **AC-019**: 집계·조회 결과에 user_id/session_id/ip_hash 가 노출되지 않는다.
- **AC-020**: V53 이 kpi_definition 에 5코드를 ON CONFLICT DO NOTHING 으로 시드하며 신규 테이블/컬럼을 만들지 않는다.

---

## 8. 의존성

| 의존 대상 | 유형 | 비고 |
|---|---|---|
| SPEC-CMS-KPI-001 (KPI 통합) | 강한 의존 (인프라 재사용) | kpi_value, 집계 격리 패턴, /values API, MV, 위젯 |
| SPEC-CMS-008 (대시보드) | 강한 의존 (간접) | kpi_value/MV/vue-echarts 원천 |
| SPEC-CMS-005 (access_log) | 강한 의존 | 신규 4종 단일 데이터 원천 |
| SPEC-CMS-002 (RBAC) | 강한 의존 | ADMIN 권한 검증 |

---

## 9. Exclusions (What NOT to Build)

본 SPEC 이 의도적으로 다루지 않는 항목:

- **신규 KPI 데이터 모델 도입**: V17 의 `kpi_value`/`kpi_value_history` 재사용. 새 KPI 저장 테이블·컬럼 신설 안 함.
- **신규 API 엔드포인트**: 기존 `GET /api/v1/admin/kpi/values` 동적 code 필터로 신규 KPI 조회. 컨트롤러 메서드 추가 안 함.
- **access_log 스키마 변경**: status_code/session_id/user_id 등 컬럼·인덱스·트리거·파티션 정책 변경 금지. 읽기 전용 원천.
- **실시간 스트리밍 집계**: 일별/월별 배치만. WebSocket/Kafka 기반 실시간 KPI 는 비범위.
- **ML 기반 예측·이상 탐지**: DAU 추세 예측·오류율 이상 탐지는 SPEC-CMS-AI 트랙 영역.
- **구조 DDL 마이그레이션**: V53 은 `kpi_definition` INSERT 만 수행. CREATE/ALTER TABLE, MV 변경, 인덱스 추가 금지.
- **세션 정의 고도화**: 30분 idle gap 단일 규칙만. 기기·IP 교차 세션 병합, 봇 트래픽 필터링은 비범위.
- **콘텐츠 유형 동적 확장**: notice/post/publication 3종 고정. 유형 추가는 런타임 설정이 아닌 후속 SPEC.
- **임계값 알림**: 오류율·DAU 급변 실시간 알림은 알림 도메인 SPEC 연계 차후.
- **PDF/외부 BI 내보내기**: 기존 엑셀 내보내기 경로만 자동 지원. PDF/Tableau 커넥터 비범위.

---

## 10. 위험 및 대응

| ID | 위험 | 영향 | 대응 |
|---|---|---|---|
| RISK-K2-01 | 작업 지시의 `response_status` 컬럼명이 실제와 불일치(`status_code`) | 집계 쿼리 컴파일 실패 | 본 SPEC 은 `status_code` 로 명시(§3.2, REQ-KPI2-004) |
| RISK-K2-02 | access_log user_id/session_id nullable → 집계 왜곡 | DAU/세션 과대·과소 | NULL 제외 명시 (REQ-KPI2-001-4, 003-4) |
| RISK-K2-03 | page_url 패턴이 실제 URL 구조와 불일치 | 콘텐츠 유형 오분류 | RUN 시 실제 page_url 샘플로 정규식 보정 (§4.3 HARD) |
| RISK-K2-04 | 세션 경계(idle gap) 정의 모호 | 세션 지속시간 부정확 | 30분 gap LAG 윈도우 함수 규칙 명문화 (REQ-KPI2-003-3, §6.2) |
| RISK-K2-05 | 분모 0(빈 일자) division-by-zero | 배치 실패 | NULLIF 보호 (REQ-KPI2-004-3) |
| RISK-K2-06 | 대상 일자 access_log 파티션 부재 → IT 실패 | 테스트 불안정 | 집계 전 파티션 존재 확인, IT 는 V45 보장 범위(2026-06/07) 사용 |
| RISK-K2-07 | MAU 월 전체 스캔 비용 | 배치 지연 | created_at 월 범위 파티션 프루닝 + status 인덱스 활용 (REQ-KPI2-001-3) |
| RISK-K2-08 | 신규 dimension 형태(month/contentType)가 MV UNIQUE 와 충돌 | REFRESH 실패 | `(kpi_id, dimension)` UNIQUE 가 임의 JSONB 수용 — 검증 IT 포함 (AC-016) |

---

## 11. 검증 체크리스트

- [ ] V53 마이그레이션이 단일 파일이며 V52 다음 번호, INSERT 전용(구조 DDL 0개)
- [ ] 신규 KPI 저장 테이블·컬럼을 만들지 않고 kpi_value(V17) 재사용
- [ ] 신규 조회 API 엔드포인트 0개 (기존 /values 동적 필터 재사용)
- [ ] EARS 패턴 사용 (Ubiquitous/Event-driven/State-driven/Unwanted)
- [ ] 오류율 집계가 `status_code`(SMALLINT) 사용, `response_status` 미사용
- [ ] DAU/MAU/세션이 user_id/session_id NULL 제외
- [ ] 모든 access_log 집계가 created_at 파티션 프루닝 범위 포함
- [ ] 세션 지속이 30분 idle gap 경계 규칙 적용
- [ ] 분모 0 NULLIF 보호 (오류율·세션 평균)
- [ ] 부모 SPEC 의 try-catch 실패 격리 패턴 계승
- [ ] 집계 후 kpi_value_history 아카이브 + MV CONCURRENTLY REFRESH 재사용
- [ ] 모든 신규 KPI 조회가 ADMIN 권한 + @AuditLog
- [ ] PII(user_id/session_id/ip_hash) 결과 비노출
- [ ] Exclusions 절 최소 5개 항목 (현재 10개)
- [ ] 부모 SPEC-CMS-KPI-001 의 어떤 컬럼/API/위젯도 변경하지 않음 (additive only)
