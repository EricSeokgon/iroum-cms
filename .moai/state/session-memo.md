# Session Memo

## P1: Session Context

session_id: 3bc66823-1d8a-4b6f-8e45-d4cc57c86bad
cwd: /home/sklee/moai/iroum-cms
event: PreCompact

## 묶음 4 Step 1 완료 (2026-04-29)

SPEC-CMS-005 Bundle D 백엔드 핵심 6 도메인 + 인프라 완성.

### 완료 커밋 (이번 세션)
- 21e694c: Bundle C Step 1 RED (V13 + 4 도메인 골격 + 31 RED)
- ea1c8c8: Bundle C Step 2 GREEN (Site/Menu/Template/Page 32 PASS)
- f2f7f55: Bundle C 잔여 (popup/banner/i18n/seo/sitemap, Caffeine 4종, 누적 61)
- 10d0be8: Bundle C Frontend (9 view + 5 component, 28 GREEN)
- 3e7bbbe: SPEC-CMS-005 Step 1 (V14 + 6 도메인 + 31 GREEN)

### 묶음 4 Step 1 산출물
- V14: access_log(파티션) + stats_daily/monthly + code_group/code + system_setting + maintenance
- 6 도메인: accesslog, stats(+batch), code, codegroup, setting, maintenance
- 인프라: Spring Boot Actuator, Micrometer Prometheus, AsyncConfig+Scheduling+Retry, FilterRegistration
- 캐시: codes, codeGroups, dashboard 추가
- 테스트: 31 신규 GREEN

### 다음 세션 첫 작업 (검증 필수)

호스트 셸에는 Java 8만 설치되어 있어 직접 빌드 검증 불가. 다음 세션 첫 작업:
```bash
cd backend && ./gradlew test --tests 'kr.co.ircp.cms.domain.system.*'
./gradlew test --tests 'kr.co.ircp.cms.domain.content.*'  # 회귀 검증
./gradlew test --tests 'kr.co.ircp.cms.domain.board.*'     # 회귀 검증
```

### 묶음 4 잔여
- Step 2: SPEC-CMS-005 Frontend (대시보드 차트 + 코드 관리 + 설정 + 점검모드 UI)
- Step 3: audit 보강 (PARTITION 전환은 별도) + Logback JSON 운영 프로파일
- Step 4: Docker 배포 (Multi-stage Dockerfile + docker-compose.yml + .env.example)

### 묶음 5/6
- 묶음 5: SPEC-CMS-006(안전경영) → 007(정책매칭) → 008(시각화) — P0
- 묶음 6: SPEC-CMS-009(거버넌스) → 010(검색) — P1

## 누적 상태

- 38 commits
- ~810 files
- ~70,000 LOC
- Backend GREEN: 521+ (Bundle C 61 + Bundle D 31 외 기존)
- Frontend GREEN: 136+
- 총 657+ GREEN tests

## 잠재적 이슈 (다음 세션 검토)

1. **MaintenanceFilter 순서**: FilterRegistrationConfig에서 order=Integer.MAX_VALUE-10. Spring Security 기본 order(-100)와의 상호작용 운영 환경 검증 필요.
2. **DashboardServiceTest**: @Cacheable AOP 프록시 없는 단위 테스트라 캐시 동작 미검증. @SpringBootTest 통합 테스트 별도 필요.
3. **DailyStatsBatchJob 재시도**: BACKOFF_MS=1시간이라 실패 경로 테스트는 타임아웃. 성공 경로만 커버됨.
4. **호스트 Java 8**: 다음 세션에서 ./gradlew 실행 시 Java 17 toolchain 자동 다운로드 필요.
