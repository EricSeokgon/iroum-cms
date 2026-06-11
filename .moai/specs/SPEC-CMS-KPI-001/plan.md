---
id: SPEC-CMS-KPI-001
type: plan
version: 0.1.0
created: 2026-06-11
updated: 2026-06-11
author: manager-spec
---

# SPEC-CMS-KPI-001 구현 계획

본 계획은 SPEC-CMS-008 대시보드 인프라(`kpi_value`, `chart_dataset_cache`, `ExportServiceImpl`, vue-echarts)를 재사용하여 KPI 집계·조회·내보내기·시각화 격차를 채운다. 신규 KPI 저장 테이블은 도입하지 않으며 사전집계 MV(V45)만 추가한다.

## 기술 접근 (Technical Approach)

- 패키지: `kr.co.ircp.cms.domain.dashboard.kpi` (entity/dto(record)/service(+Impl)/mapper(@Mapper+XML)/controller/job)
- 집계: `GovernanceStatsMapper.xml` 의 GROUP BY + `ON CONFLICT DO UPDATE` UPSERT 패턴 계승
- 조회: 기존 `KpiValueMapper.xml` 의 JSONB containment(`@>`) 패턴 확장, 동적 WHERE 절
- 내보내기: `ExportServiceImpl` 의 비동기/서명URL/TTL 골격 위에 SXSSFWorkbook 실구현 완성
- 캐시: `chart_dataset_cache` 재활용, 고빈도 KPI 는 1시간 TTL 옵션
- 프런트: vue-echarts(LineChart 모듈형) + grid-layout-plus 기존 패턴 재사용
- 테스트: `kr.co.ircp.cms.integration.AbstractIntegrationTest` 상속 IT, 명명 `AdminKpiControllerIT`

## 마일스톤 (우선순위 기반, 시간 추정 없음)

### Phase 1: 백엔드 KPI 집계 서비스 (Priority High)

목표: REQ-KPI-001 — 일별 배치로 access_log/audit_log/policy_match_stats_monthly 를 kpi_value 에 집계.

생성 파일:
- `backend/.../domain/dashboard/kpi/job/KpiAggregationJob.java` (@Scheduled "0 0 4 * * *")
- `backend/.../domain/dashboard/kpi/service/KpiAggregationService.java` + `KpiAggregationServiceImpl.java`
- `backend/.../domain/dashboard/kpi/mapper/KpiAggregationMapper.java`
- `backend/src/main/resources/mapper/dashboard/kpi/KpiAggregationMapper.xml`
- `backend/src/main/resources/db/migration/V45__kpi_aggregation_mv.sql` (MV + UNIQUE 인덱스 + 부분 인덱스 + kpi_definition 시드)

변경 파일:
- 없음 (기존 V17 테이블·매퍼 무수정)

테스트 요구:
- `KpiAggregationServiceImplIT`: 시드 access_log → 집계 → kpi_value 검증 (AC-001)
- 파일 다운로드 수 집계 검증 (AC-017)
- 갱신 전 값 kpi_value_history 아카이브 검증 (AC-002)
- 단일 KPI 실패 격리 검증 (AC-003)

### Phase 2: KPI 조회 API (Priority High)

목표: REQ-KPI-002 — 멀티조건 필터링 조회.

생성 파일:
- `backend/.../domain/dashboard/kpi/controller/AdminKpiController.java` (@PreAuthorize ADMIN, @AuditLog)
- `backend/.../domain/dashboard/kpi/service/KpiQueryService.java` + `KpiQueryServiceImpl.java`
- `backend/.../domain/dashboard/kpi/mapper/KpiQueryMapper.java` + `KpiQueryMapper.xml`
- `backend/.../domain/dashboard/kpi/dto/KpiQueryRequest.java` (record)
- `backend/.../domain/dashboard/kpi/dto/KpiValueResponse.java` (record, dataState 포함)

테스트 요구:
- `AdminKpiControllerIT`: 멀티조건 일치 (AC-004), 빈 결과 200 (AC-005), PREPARING (AC-006), 기간 그래뉼래리티 (AC-018), LIMIT 상한 (AC-019), 캐시 히트 (AC-013), HMAC 검증 (AC-020), SQL 인젝션 거부 (AC-021), PII 비노출 (AC-022), 비ADMIN 403 (AC-014)

### Phase 3: 엑셀 내보내기 실구현 (Priority High)

목표: REQ-KPI-003 — ExportServiceImpl SXSSFWorkbook stub 완성.

생성 파일:
- `backend/.../domain/dashboard/kpi/controller/KpiExportController.java`
- `backend/.../domain/dashboard/kpi/service/KpiExportService.java` + `KpiExportServiceImpl.java`

변경 파일:
- `backend/.../domain/dashboard/service/ExportServiceImpl.java` (SXSSFWorkbook stub → 실구현; KpiExportService 가 위임)

테스트 요구:
- `KpiExportServiceImplIT`: 동기 스트리밍 <10k행 (AC-007), 비동기 ≥10k행 서명URL (AC-008), 다중시트 분할 (AC-009), max_export_rows 거부 (AC-010), audit_log EXPORT (AC-015)

### Phase 4: 프런트엔드 KPI 대시보드 위젯 (Priority Medium)

목표: REQ-KPI-006 — 요약/트렌드/전환율 위젯 + 필터 패널.

생성 파일:
- `frontend/admin/src/views/dashboard/KpiDashboardView.vue`
- `frontend/admin/src/components/dashboard/KpiSummaryCards.vue`
- `frontend/admin/src/components/dashboard/KpiTrendChart.vue`
- `frontend/admin/src/components/dashboard/KpiConversionFunnel.vue`
- `frontend/admin/src/components/dashboard/KpiFilterPanel.vue`
- `frontend/admin/src/stores/kpiStore.ts`
- `frontend/admin/src/api/kpi.ts`

테스트 요구:
- 필터 변경 시 전 위젯 갱신 (AC-016), PREPARING 안내 표시 (REQ-KPI-006-5), 내보내기 진행률 폴링

### Phase 5: 성능 검증 (Priority Medium)

목표: REQ-KPI-004 — 인덱스/파티션/MV/캐시 검증.

작업:
- `EXPLAIN ANALYZE` 로 access_log 집계 파티션 제거 확인 (AC-011)
- MV CONCURRENTLY REFRESH 검증 (AC-012)
- 복합/GIN 인덱스 스캔 확인
- 부하 시나리오: 대용량 로그 집계 응답 시간 측정

테스트 요구:
- `KpiPerformanceIT`: 파티션 제거 쿼리 플랜 검증, MV refresh 검증

## 테스트 전략

- 모든 IT 는 `AbstractIntegrationTest` 상속 (Testcontainers PostgreSQL)
- 명명 규약: `Admin{Domain}ControllerIT`, `{Service}ImplIT`
- 시드 데이터: access_log 파티션에 직접 INSERT 후 집계 검증
- SPEC-CMS-007 미구현 시나리오: policy_match_stats_monthly 0 값 → PREPARING 검증
- 커버리지 목표: 85%+ (TRUST 5 Tested)

## 구현 순서 의존성

```
Phase 1 (집계) → Phase 2 (조회) → Phase 3 (내보내기)
                      ↓
                 Phase 4 (프런트) → Phase 5 (성능 검증)
```

Phase 1 의 kpi_value 집계 결과가 있어야 Phase 2 조회가 의미를 가지므로 1→2 순차. Phase 3 은 Phase 2 의 조회 서비스를 재사용. Phase 4 는 Phase 2/3 API 완료 후. Phase 5 는 전체 완료 후 최종 검증.

## 위험 완화 (구현 시)

- ExportServiceImpl stub 실구현 시 기존 비동기/서명/TTL 로직 보존, SXSSFWorkbook 생성부만 교체
- MV REFRESH CONCURRENTLY 는 UNIQUE 인덱스 선행 필수 → V45 에서 보장
- 전환율 KPI 는 항상 dataState 메타 동반하여 0 값 오해 방지
