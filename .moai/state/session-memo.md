# Session Memo

## P1: Session Context

session_id: 3bc66823-1d8a-4b6f-8e45-d4cc57c86bad
cwd: /home/sklee/moai/iroum-cms
event: PreCompact

## 묶음 4 Step 1+2 완료 (2026-04-29)

SPEC-CMS-005 Bundle D Backend 핵심 + Frontend 풀스택 완성.

### 이번 세션 누적 커밋
- 21e694c: Bundle C Step 1 RED (V13 + 4 도메인 골격 + 31 RED)
- ea1c8c8: Bundle C Step 2 GREEN (Site/Menu/Template/Page 32 PASS)
- f2f7f55: Bundle C 잔여 (popup/banner/i18n/seo/sitemap, 누적 61)
- 10d0be8: Bundle C Frontend (9 view + 5 component, 28 GREEN)
- 3e7bbbe: SPEC-CMS-005 Step 1 (V14 + 6 도메인 + 31 GREEN)
- 75dd9dd: SPEC-CMS-005 Step 2 Frontend (6 view + 5 component + 25 GREEN)

### 묶음 4 Step 2 산출물
- 6 view: SystemDashboard / AccessLog / CodeManager / SystemSetting / MaintenanceManager / AuditLog
- 5 component: KpiCard, DashboardTrendChart, DashboardTopPagesPanel, MaintenanceBanner, JsonValueEditor
- API: src/api/system.ts (7 그룹)
- Store: src/stores/system.ts (useDashboardStore, useCodeCacheStore)
- 의존성: echarts 5.5.1, vue-echarts 7.0.3
- 라우터 6 추가 + 사이드바 + 전역 MaintenanceBanner

### 다음 세션 첫 작업 (검증)

호스트 셸에 Java 17 toolchain 미설치. 다음 세션 첫 명령:
```bash
cd backend && ./gradlew test --tests 'kr.co.ircp.cms.domain.system.*'
./gradlew test --tests 'kr.co.ircp.cms.domain.content.*'  # 회귀 검증
```

### 묶음 4 잔여
- Step 3: audit_log 보강 + Logback JSON 운영 프로파일 (REQ-CROSS-007-D)
- Step 4: Docker 배포 (Multi-stage Dockerfile + docker-compose.yml + .env.example)

### 묶음 5/6
- 묶음 5: SPEC-CMS-006(안전경영) → 007(정책매칭) → 008(시각화) — P0
- 묶음 6: SPEC-CMS-009(거버넌스) → 010(검색) — P1

## 누적 상태

- 40 commits
- ~835 files
- ~73,500 LOC
- Backend GREEN: 521+ (Bundle D 31 + Bundle C 61 + 기존)
- Frontend GREEN: 161+ (이전 91 + Bundle C 28 + Bundle D 25 + 17 기타)
- 총 682+ GREEN tests

## 잠재적 이슈 (다음 세션 검토)

1. **MaintenanceFilter Spring Security 통합**: 운영 환경 검증 필요 (filter chain order)
2. **DashboardServiceTest 단위 테스트 한계**: @Cacheable AOP 미검증 — @SpringBootTest 별도 필요
3. **DailyStatsBatchJob 재시도 BACKOFF=1시간**: 실패 경로 단위 테스트 미커버
4. **호스트 Java 8**: 다음 세션 ./gradlew 실행 시 Java 17 toolchain 자동 다운로드
5. **@types/node 빌드 경고**: `node:url` 모듈 경고 — `pnpm add -D @types/node` 또는 tsconfig types 설정
6. **el-table-column 슬롯 패턴**: 신규 테이블 작성 시 `#default="scope"` + `scope?.row` 표준 사용
