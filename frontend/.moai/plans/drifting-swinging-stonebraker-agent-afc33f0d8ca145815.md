# SPEC-CMS-AI-001 구현 계획 (Implementation Plan)

> 분석 전용. 코드 작성 없음. manager-tdd 에이전트가 본 계획을 직접 사용함.
> 작성 기준: 기존 코드베이스 패턴 실측 분석 (build.gradle.kts, AsyncConfig, CacheConfig, GovernanceJobSupport, AuditLog AOP, SearchController, admin frontend).

---

## 0. 핵심 사전 확인 사항 (Critical Findings — 사용자 확인 필요)

기존 코드베이스 분석 결과, SPEC 작업 지시문과 실제 프로젝트 사이에 **3건의 중대한 불일치**가 발견되었습니다. manager-tdd 착수 전 결정이 필요합니다.

| # | 항목 | SPEC 지시문 | 실제 프로젝트 | 권장 결정 |
|---|------|------------|--------------|----------|
| C1 | 빌드 도구 | "pom.xml" (Maven) | **Gradle Kotlin DSL** (`backend/build.gradle.kts`) | Gradle 사용. 의존성은 `build.gradle.kts` 에 추가 |
| C2 | 영속성 계층 | "entities + repositories" (JPA 뉘앙스) | **MyBatis** (`@Mapper` 인터페이스 + XML). JPA 미사용 | MyBatis Mapper + XML 패턴 사용 (기존 `AuditLogMapper` 동일) |
| C3 | Circuit Breaker | "Resilience4j (resilience4j-spring-boot3)" | **미존재**. `spring-retry` 만 존재 | 신규 의존성 추가 (아래 §5 R1 참조) |
| C4 | Java 버전 | "Java 21 가정 (가이드 문서)" | **Java 17 toolchain** (`build.gradle.kts:17`) | Java 17 문법만 사용 (record/sealed OK, virtual thread/structured concurrency 금지) |

추가 확인:
- **PDF 라이브러리 미존재**: Apache POI(엑셀)만 있음. iText/JasperReports 둘 다 없음 → 신규 추가 필요 (§5 R2)
- **프론트엔드는 pnpm 모노레포**: `frontend/admin/`, `frontend/public/`, `frontend/shared/` 워크스페이스. AI 모니터링 대시보드는 **`frontend/admin/src/`** (TypeScript + Element Plus + vue-echarts + Vitest). SPEC가 명시한 `frontend/src/views/ai/` 경로는 존재하지 않음 → `frontend/admin/src/views/ai/` 로 교정

---

## 1. 의존성 감사 (Dependency Audit)

대상 파일: `backend/build.gradle.kts`

| 의존성 | 현재 상태 | 조치 |
|--------|----------|------|
| Caffeine cache | **존재** (`spring-boot-starter-cache` + `caffeine:3.1.8`, line 55-56) | 추가 불필요. 기존 `CacheConfig.java` 의 `SimpleCacheManager` 에 캐시 1개 추가 |
| Spring AOP | **존재** (`spring-boot-starter-aop`, line 52) | 추가 불필요. `@AiAuditLog` AOP 구현에 사용 |
| Spring Retry | **존재** (`spring-retry`, line 62) | Circuit Breaker 대체 후보. 단, 본격 CB 기능(half-open, 슬라이딩 윈도우)은 미흡 |
| springdoc-openapi | **존재** (`springdoc-openapi-starter-webmvc-ui:2.8.17`, line 81) | 추가 불필요. OpenAPI 계약 파일은 정적 yaml 로 별도 작성 |
| MyBatis | **존재** (`mybatis-spring-boot-starter:3.0.4`, line 65) | 추가 불필요. Mapper + XML 패턴 |
| Testcontainers / Awaitility / ArchUnit | **존재** (line 108-117) | 추가 불필요. IT + @Async 검증 테스트에 사용 |
| Apache POI | **존재** (`poi:5.2.5`, line 140) | 엑셀 전용. PDF에는 사용 불가 |
| **Resilience4j** | **미존재** | **신규 추가 필요** → `io.github.resilience4j:resilience4j-spring-boot3:2.2.0` + `resilience4j-circuitbreaker` (Spring Boot 3.5 호환 검증 필요, §5 R1) |
| **PDF 생성 라이브러리** | **미존재** | **신규 추가 필요** → OpenPDF 권장 (§5 R2) |
| Micrometer Prometheus | **존재** (line 59) | model-health 메트릭 노출에 재사용 가능 |

### 신규 추가 의존성 (최종)

```
// Step 1 — build.gradle.kts dependencies 블록
implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")   // ML 서비스 Circuit Breaker (R1 결정 시)
implementation("com.github.librepdf:openpdf:1.3.39")                        // 시뮬레이션 PDF export (R2 결정 시)
```

---

## 2. 완전 파일 목록 (Complete File List)

패키지 루트: `kr.co.ircp.cms.domain.ai` (신규 도메인). 기존 도메인 패턴(`domain/{name}/{controller,service,dto,entity,repository,exception}`) 준수.
MyBatis XML 루트: `backend/src/main/resources/mybatis/mapper/ai/`

### Step 1 — 기반 (Foundation): DB · 계약 · ML 클라이언트 · 엔티티 · 매퍼

| # | 경로 | 유형 | 설명 |
|---|------|------|------|
| 1 | `backend/src/main/resources/db/migration/V28__ai_prediction_log.sql` | new | `ai_prediction_log` 테이블 + CHECK 제약 + 인덱스(prediction_type, predicted_at, status) |
| 2 | `backend/src/main/resources/db/migration/V29__ai_simulation_session.sql` | new | `ai_simulation_session` 테이블 + `gen_random_uuid()` PK + `expires_at` 인덱스 |
| 3 | `backend/src/main/resources/db/migration/V30__ai_model_metric.sql` | new | `ai_model_metric` 테이블 + `UNIQUE(model_name,prediction_type,aggregate_period,period_start)` |
| 4 | `backend/src/main/resources/db/migration/V31__ai_retrain_queue.sql` | new | `ai_retrain_queue` 테이블 + CHECK 제약 + status 인덱스 |
| 5 | `docs/ai-ml-service-openapi.yaml` | new | OpenAPI 3.1 — ML FastAPI 4개 엔드포인트(growth-stage/risk-score/simulation/health) 계약 |
| 6 | `.../domain/ai/client/MlServiceClient.java` | new | 인터페이스: `predictGrowthStage`, `predictRiskScore`, `simulate`, `health` |
| 7 | `.../domain/ai/client/MlServiceClientImpl.java` | new | RestTemplate(또는 RestClient) 구현 + 타임아웃(3000/500/3000/1000ms) + `@CircuitBreaker` |
| 8 | `.../domain/ai/client/MockMlServiceClient.java` | new | `@Profile("test")` 또는 `@ConditionalOnProperty` 결정 가능한 결정론적 mock |
| 9 | `.../domain/ai/client/dto/GrowthStageMlResponse.java` | new | record: stage, entryProbabilities(Map), confidence, modelVersion |
| 10 | `.../domain/ai/client/dto/RiskScoreMlResponse.java` | new | record: defaultProbability, riskGrade, topFactors(List), modelVersion |
| 11 | `.../domain/ai/client/dto/SimulationMlResponse.java` | new | record: projection(List×3), modelVersion |
| 12 | `.../domain/ai/client/dto/MlHealthResponse.java` | new | record: status, loadedModels(List) |
| 13 | `.../domain/ai/client/dto/MlPredictRequest.java` | new | record: ksicCode, capitalAmount, foundingYear, revenueAmount (PII 없음 보장) |
| 14 | `.../domain/ai/entity/AiPredictionLog.java` | new | `@Alias` + Lombok `@Data/@Builder`. JSONB→String 매핑 (기존 AuditLog 패턴) |
| 15 | `.../domain/ai/entity/AiSimulationSession.java` | new | UUID id(String), JSONB→String, expires_at(Instant) |
| 16 | `.../domain/ai/entity/AiModelMetric.java` | new | 메트릭 집계 엔티티 |
| 17 | `.../domain/ai/entity/AiRetrainQueue.java` | new | 재학습 큐 엔티티 |
| 18 | `.../domain/ai/repository/AiPredictionLogMapper.java` | new | `@Mapper`: insert, updateActualValue, selectForMetricAggregation |
| 19 | `.../domain/ai/repository/AiSimulationSessionMapper.java` | new | `@Mapper`: insert, selectById, updatePdfStatus, deleteById, deleteExpired |
| 20 | `.../domain/ai/repository/AiModelMetricMapper.java` | new | `@Mapper`: upsert, selectByFilter, selectById, selectDriftAlerts |
| 21 | `.../domain/ai/repository/AiRetrainQueueMapper.java` | new | `@Mapper`: insert, selectAll, updateStatus |
| 22 | `backend/src/main/resources/mybatis/mapper/ai/AiPredictionLogMapper.xml` | new | SQL: insert(JSONB cast), aggregation 집계 쿼리 |
| 23 | `backend/src/main/resources/mybatis/mapper/ai/AiSimulationSessionMapper.xml` | new | SQL: UUID/JSONB/expires_at |
| 24 | `backend/src/main/resources/mybatis/mapper/ai/AiModelMetricMapper.xml` | new | SQL: ON CONFLICT UPSERT, drift 필터 |
| 25 | `backend/src/main/resources/mybatis/mapper/ai/AiRetrainQueueMapper.xml` | new | SQL: status CHECK 일치 |

### Step 2 — 비즈니스 로직: REST · 비동기 로그 · 캐시 · CB · 배치 · PDF

| # | 경로 | 유형 | 설명 |
|---|------|------|------|
| 26 | `.../domain/ai/controller/GrowthStageController.java` | new | `GET /api/v1/ai/growth-stage` (1 endpoint) |
| 27 | `.../domain/ai/service/GrowthStageService.java` | new | ML 호출 → `@Cacheable("aiGrowthStage")` → async log → fallback |
| 28 | `.../domain/ai/controller/SimulationController.java` | new | `POST start`, `GET {id}`, `GET {id}/pdf` (3 endpoints) |
| 29 | `.../domain/ai/service/SimulationService.java` | new | UUID 세션, IP SHA-256 해시, 24h 만료, projection 생성 |
| 30 | `.../domain/ai/controller/RiskScoreController.java` | new | `GET /risk-score`, `GET /risk-score/explain/{predictionId}` (2 endpoints) |
| 31 | `.../domain/ai/service/RiskScoreService.java` | new | risk score → grade threshold(설정값) → `@AiAuditLog` AOP |
| 32 | `.../domain/ai/controller/AiAdminController.java` | new | 관리자 10개 endpoint, 클래스 `@PreAuthorize("hasRole('ADMIN')")` |
| 33 | `.../domain/ai/service/AiAdminService.java` | new | metrics/drift/retrain-queue/model-health/simulation-stats 조회·변경 |
| 34 | `.../domain/ai/service/AiPredictionLogService.java` | new | `@Async("aiLogExecutor")` 예측 로그 비동기 적재 |
| 35 | `.../domain/ai/batch/AiModelMetricJob.java` | new | `@Scheduled(cron 02:15)` + `GovernanceJobSupport.run()` 패턴 |
| 36 | `.../domain/ai/service/AiMetricAggregationService.java` | new | prediction_log → model_metric 집계, drift 판정(accuracy<0.70 \|\| nRMSE>0.20) |
| 37 | `.../domain/ai/service/PdfGeneratorService.java` | new | OpenPDF 기반 시뮬레이션 결과 PDF 생성 (비동기 상태 전이) |
| 38 | `.../domain/ai/service/AiRateLimiterService.java` | new | ip-hash 당 30 req/h (Caffeine 기반 카운터, 설정 가능) |
| 39 | `.../domain/ai/annotation/AiAuditLog.java` | new | 위험예측 감사용 AOP 어노테이션 (기존 `@AuditLog` 패턴 복제) |
| 40 | `.../domain/ai/aspect/AiAuditLogAspect.java` | new | risk-score 호출 audit_log 적재 (SPEC-CMS-005 AuditLogService 재사용) |
| 41 | `.../domain/ai/config/AiResilienceConfig.java` | new | Resilience4j CircuitBreaker 빈/설정 (ml-service 인스턴스) |
| 42 | `.../domain/ai/config/AiMlClientConfig.java` | new | RestTemplate/RestClient 빈 + 타임아웃 + 내부망 baseUrl |
| 43 | `.../domain/ai/properties/AiProperties.java` | new | `@ConfigurationProperties("ai")`: risk.thresholds, rate-limit, ml.base-url, cache TTL |
| 44 | `.../domain/ai/dto/*.java` (약 12개) | new | 요청/응답 DTO records (GrowthStageResponse, SimulationStartRequest, RiskScoreResponse, RiskExplainResponse, MetricView, DriftAlertView, RetrainQueueView, RetrainCreateRequest, RetrainStatusUpdateRequest, ModelHealthView, SimulationStatsView, SimulationSessionView) |
| 45 | `.../domain/ai/exception/*.java` (약 3개) | new | MlServiceUnavailableException, SimulationExpiredException, AiRateLimitExceededException |
| 46 | `backend/src/main/java/kr/co/ircp/cms/config/CacheConfig.java` | **modify** | `aiGrowthStage` 캐시 추가 (TTL 1h, maxSize 적정), `setCaches` List 확장 |
| 47 | `backend/src/main/java/kr/co/ircp/cms/config/AsyncConfig.java` | **modify** | `aiLogExecutor` 빈 추가 (core=2, max=4, queue=500, prefix `ai-log-`, CallerRunsPolicy 또는 DiscardPolicy) |
| 48 | `backend/src/main/java/kr/co/ircp/cms/config/SecurityConfig.java` | **modify** | `/api/v1/ai/**` PUBLIC, `/api/v1/admin/ai/**` ROLE=ADMIN 경로 규칙 추가 (기존 패턴 확인 필요) |
| 49 | `backend/build.gradle.kts` | **modify** | Resilience4j + OpenPDF 의존성 추가 |
| 50 | `backend/src/main/resources/application.yml` | **modify** | `ai:` 설정 블록 (ml.base-url, risk.thresholds, rate-limit, scheduler cron, resilience4j) |
| 51 | `backend/src/main/resources/application-prod.yml` | **modify** | 운영 ml.base-url(내부망), CB 임계값 override |

### Step 3 — 프론트엔드: Vue 3 모니터링 대시보드 (admin 워크스페이스, TypeScript)

| # | 경로 | 유형 | 설명 |
|---|------|------|------|
| 52 | `frontend/admin/src/views/ai/ModelDashboard.vue` | new | 모델 메트릭(rmse/mae/accuracy/latency p50·p95·p99) + vue-echarts 차트 |
| 53 | `frontend/admin/src/views/ai/DriftAlerts.vue` | new | drift 감지 알림 목록 (drift_detected=true) |
| 54 | `frontend/admin/src/views/ai/RetrainQueue.vue` | new | 재학습 큐 조회 + 수동 등록 + 상태 변경(PUT) |
| 55 | `frontend/admin/src/api/ai.ts` | new | axios 기반 11개 admin API 함수 (기존 `api/governance.ts` 패턴) |
| 56 | `frontend/admin/src/router/index.ts` | **modify** | `/ai/dashboard`, `/ai/drift`, `/ai/retrain` 라우트 + ADMIN 가드 추가 |
| 57 | `frontend/admin/src/locales/*` (ko/en) | **modify** | AI 메뉴/화면 i18n 키 추가 (기존 i18n 패턴 확인 필요) |

---

## 3. TDD 테스트 파일 계획 (RED-GREEN-REFACTOR, 목표 커버리지 85%)

테스트 규약 (build.gradle.kts 실측):
- **단위 테스트** `*Test.java`: JUnit5 + Mockito, 기본 `./gradlew test` 실행, `@Tag("integration")` 미부여
- **통합 테스트** `*IT.java`: `@Tag("integration")`, Testcontainers PostgreSQL, `./gradlew integrationTest` 실행, Docker 미가용 시 `Assumptions.assumeTrue` SKIP
- **비동기 검증**: Awaitility 사용 (기존 `PiiAuditEnhanceIT` 패턴)

### Step 1 테스트

| 경로 | 종류 | 검증 대상 |
|------|------|----------|
| `.../domain/ai/client/MockMlServiceClientTest.java` | 단위 | mock 결정론적 응답 |
| `.../integration/ai/AiPredictionLogMapperIT.java` | 통합(Testcontainers) | V28 마이그레이션 + JSONB insert/select |
| `.../integration/ai/AiSimulationSessionMapperIT.java` | 통합 | V29 UUID PK + expires_at + deleteExpired |
| `.../integration/ai/AiModelMetricMapperIT.java` | 통합 | V30 UPSERT(ON CONFLICT) + UNIQUE 제약 |
| `.../integration/ai/AiRetrainQueueMapperIT.java` | 통합 | V31 CHECK 제약 + status 전이 |
| `.../integration/ai/AiMigrationIT.java` | 통합 | V28-V31 Flyway clean migration |

### Step 2 테스트

| 경로 | 종류 | 검증 대상 |
|------|------|----------|
| `.../domain/ai/service/GrowthStageServiceTest.java` | 단위 | MockMlServiceClient 주입, 캐시 히트, fallback 경로 |
| `.../domain/ai/service/RiskScoreServiceTest.java` | 단위 | grade threshold 경계값(GREEN/YELLOW/ORANGE/RED) |
| `.../domain/ai/service/SimulationServiceTest.java` | 단위 | UUID 생성, IP SHA-256(평문 미저장 검증), 24h 만료 |
| `.../domain/ai/service/AiMetricAggregationServiceTest.java` | 단위 | drift 판정식(accuracy<0.70 \|\| nRMSE>0.20) 경계 |
| `.../domain/ai/service/PdfGeneratorServiceTest.java` | 단위 | PDF 바이트 생성, 상태 전이(NONE→GENERATING→READY/FAILED) |
| `.../domain/ai/service/AiRateLimiterServiceTest.java` | 단위 | 30 req/h 초과 시 차단, 시간 윈도우 리셋 |
| `.../domain/ai/batch/AiModelMetricJobTest.java` | 단위 | `GovernanceJobSupport` wrapper 호출 검증 (기존 `*JobTest` 패턴) |
| `.../domain/ai/aspect/AiAuditLogAspectTest.java` | 단위 | risk-score 호출 시 AuditLogService 호출 |
| `.../domain/ai/controller/GrowthStageControllerTest.java` | 단위(MockMvc) | 파라미터 검증, 200/4xx |
| `.../domain/ai/controller/SimulationControllerTest.java` | 단위(MockMvc) | start/get/pdf 흐름, 만료 404 |
| `.../domain/ai/controller/RiskScoreControllerTest.java` | 단위(MockMvc) | explain 경로 |
| `.../domain/ai/controller/AiAdminControllerTest.java` | 단위(MockMvc + spring-security-test) | ROLE=ADMIN 강제(403 비관리자) |
| `.../integration/ai/AiPredictionLogAsyncIT.java` | 통합(Awaitility) | `@Async` 예측 로그 실제 적재 |
| `.../integration/ai/AiModelMetricJobIT.java` | 통합 | 배치 → metric UPSERT + drift → retrain_queue |
| `.../integration/ai/AiCircuitBreakerIT.java` | 통합 | ML 장애 시 CB open + fallback |
| `.../integration/ai/AiAdminApiIT.java` | 통합 | 16개 endpoint 중 admin 경로 end-to-end + audit_log 적재 |

### Step 3 테스트 (Vitest + @vue/test-utils)

| 경로 | 검증 대상 |
|------|----------|
| `frontend/admin/src/views/ai/__tests__/ModelDashboard.spec.ts` | 메트릭 렌더, API mock |
| `frontend/admin/src/views/ai/__tests__/DriftAlerts.spec.ts` | drift 목록 렌더 |
| `frontend/admin/src/views/ai/__tests__/RetrainQueue.spec.ts` | 등록/상태변경 인터랙션 |
| `frontend/admin/src/api/__tests__/ai.spec.ts` | axios 함수 시그니처/경로 |

---

## 4. 의존성 그래프 (SPEC-CMS-AI-001 내부)

```
Step 1 (병렬 가능 그룹 A): V28~V31 마이그레이션 (상호 독립)
        │
        ├─> 엔티티 14~17 (마이그레이션 컬럼과 1:1)
        │       └─> 매퍼 인터페이스 18~21 ──> 매퍼 XML 22~25
        │               └─> 매퍼 IT (Testcontainers, 마이그레이션 필수 선행)
        │
        └─> OpenAPI yaml(5) ──> MlServiceClient 인터페이스(6)
                                    ├─> MlServiceClientImpl(7)  [CB 의존: §5 R1 결정 선행]
                                    └─> MockMlServiceClient(8)  [Step 2 서비스 단위테스트 필수]

Step 2: AiProperties(43) ──> 모든 서비스/설정
        AiResilienceConfig(41) + AiMlClientConfig(42) ──> MlServiceClientImpl
        AsyncConfig 수정(47: aiLogExecutor) ──> AiPredictionLogService(34)
        엔티티+매퍼(Step1) + MockMlServiceClient(Step1) ──> GrowthStage/RiskScore/Simulation Service
        CacheConfig 수정(46) ──> GrowthStageService @Cacheable
        AiAuditLog AOP(39,40) ──> RiskScoreService
        서비스 ──> 컨트롤러(26,28,30,32)
        AiMetricAggregationService(36) ──> AiModelMetricJob(35)
        SecurityConfig 수정(48) ──> AiAdminController 권한 IT

Step 3: 백엔드 admin API(Step2) 안정화 후 ──> Vue 뷰 + ai.ts + 라우터
```

핵심 순서 제약:
1. **마이그레이션 → 매퍼 IT**: V28~V31 없이는 Testcontainers IT 불가
2. **MockMlServiceClient → 모든 서비스 단위 테스트**: 외부 ML 의존 차단
3. **§5 R1/R2 결정 → Step 1 완료**: CB·PDF 라이브러리 미확정 시 MlServiceClientImpl·PdfGeneratorService 착수 불가
4. **AiProperties → 전체**: risk threshold/rate-limit/ml-url 설정 주입점
5. **Step 2 백엔드 안정 → Step 3**: 프론트는 admin API 계약 확정 후

---

## 5. 위험 항목 (Risk Items)

### R1 — Circuit Breaker 라이브러리 결정 [사용자 확인 필요]
- Resilience4j 미존재. 옵션:
  - **(권장) Resilience4j 신규 추가**: `resilience4j-spring-boot3:2.2.0`. Spring Boot 3.5.9 호환성 Context7/공식문서 검증 필요. `@CircuitBreaker` 선언적 사용으로 SPEC 요구(half-open, 슬라이딩 윈도우) 충족
  - **기존 spring-retry + 수동 fallback**: 신규 의존성 없음. 단 진정한 CB 상태머신 부재 → SPEC "circuit breaker" 요구 부분 충족만
- 권장: Resilience4j 추가 (SPEC 명시 요구). 단 버전-부트 호환성 사전 검증 작업 1건 추가

### R2 — PDF 라이브러리 결정 [사용자 확인 필요]
- iText / JasperReports 모두 미존재. POI는 엑셀 전용
- 옵션:
  - **(권장) OpenPDF (`com.github.librepdf:openpdf:1.3.39`)**: LGPL/MPL, iText 2.x fork, 상용 라이선스 비용 없음
  - iText 7 (`com.itextpdf:itext7-core`): AGPL — 상용 시 라이선스 비용 발생 위험
  - JasperReports: 템플릿 기반, 의존성 무거움(과설계 우려)
- 권장: OpenPDF (라이선스 안전 + 경량)

### R3 — 기존 코드 충돌 지점
- `CacheConfig.java` (modify): `SimpleCacheManager.setCaches(List.of(...))` 에 `aiGrowthStage` 추가 시 기존 7개 캐시 List 재구성 필요. 누락 시 기존 캐시(menuTree 등) 전부 소실 위험 → **List 전체 보존 + 1개 append** 명시
- `AsyncConfig.java` (modify): `@Bean(name="aiLogExecutor")` 추가. 기존 3개 executor(audit/accessLog/searchLog) 영향 없음. RejectedExecutionHandler 정책 결정 필요(예측 로그 유실 허용→DiscardPolicy 권장, SPEC는 queueCapacity=500 명시)
- `SecurityConfig.java` (modify): 경로 권한 규칙 추가. 기존 `authorizeHttpRequests` 순서 의존성 — `/api/v1/admin/ai/**` 를 일반 `/api/v1/**` 보다 먼저 선언해야 ADMIN 강제 유효. 기존 패턴 정밀 확인 필요(본 분석에서 SecurityConfig 본문 미독)
- `application.yml` (modify): `ai:` 키 신규. 기존 키 충돌 없음

### R4 — 보안 제약 (HARD, 위반 시 SPEC 실패)
- IP 평문 저장 금지 → SHA-256만 (기존 `SearchController.hashIp()` 패턴 재사용 권장: `MessageDigest SHA-256 + HexFormat`)
- ML 요청에 PII 금지 → `MlPredictRequest` record 필드를 ksicCode/capitalAmount/foundingYear/revenueAmount 로만 제한 (이름/주민번호 필드 부재 보장)
- admin API ROLE=ADMIN + audit_log AOP → `AiAdminController` 클래스레벨 `@PreAuthorize("hasRole('ADMIN')")` + `@AiAuditLog`
- ML 서비스 내부망 only → `application-prod.yml` base-url 내부 호스트, 외부 노출 금지(컨트롤러에 ML 직접 프록시 엔드포인트 미생성)

### R5 — Java 17 제약
- 가이드 문서가 Java 21(virtual thread, structured concurrency) 예시 제공하나 실 toolchain은 **Java 17**. `Executors.newVirtualThreadPerTaskExecutor()`, `StructuredTaskScope` 사용 금지. record/sealed/pattern matching(switch)은 17에서 사용 가능 (단 sealed+switch 패턴은 17 preview 주의 → 표준 if/instanceof 권장)

### R6 — egovframe 미통합 상태
- `build.gradle.kts:87-99` — egovframe 의존성 주석 처리, Spring 표준(spring-jdbc, spring-tx)로 대체 중. SPEC가 "egovframe batch" 명시하나 실제는 `@Scheduled` + `GovernanceJobSupport` 패턴. AiModelMetricJob도 동일 패턴 사용(egovframe batch API 사용 금지)

### R7 — 프론트 경로/스택 불일치
- SPEC: `frontend/src/views/ai/*.vue` (JS), `aiApi.js` → 실제: `frontend/admin/src/views/ai/*.vue` (**TypeScript**), `frontend/admin/src/api/ai.ts`, Element Plus + vue-echarts. 라우터는 `frontend/admin/src/router/index.ts` 단일 파일

---

## 6. 예상 파일 수 (Estimated File Count)

| 구분 | 신규 | 수정 | 합계 |
|------|------|------|------|
| Step 1 (DB·계약·클라이언트·엔티티·매퍼) | 25 | 0 | 25 |
| Step 2 (REST·비동기·캐시·CB·배치·PDF) | 약 33 (DTO 12 + 예외 3 포함) | 6 | 39 |
| Step 3 (프론트) | 4 | 2 | 6 |
| **소스 소계** | **약 62** | **8** | **70** |
| 테스트 — Step 1 | 6 | - | 6 |
| 테스트 — Step 2 | 16 | - | 16 |
| 테스트 — Step 3 (Vitest) | 4 | - | 4 |
| **테스트 소계** | **26** | - | **26** |
| **총계** | **약 88** | **8** | **약 96** |

테스트 분해: 단위(JUnit5/Mockito) 14 + 통합(Testcontainers/Awaitility) 12 + Vitest 4 = 26

---

## 7. manager-tdd 착수 전 선결 결정 (Blocking Decisions)

1. **R1**: Circuit Breaker — Resilience4j 신규 추가 vs spring-retry 활용
2. **R2**: PDF — OpenPDF vs iText7 vs JasperReports
3. **R3**: `aiLogExecutor` 큐 포화 정책 — DiscardPolicy(로그 유실 허용) vs CallerRunsPolicy(지연 허용)
4. SecurityConfig 본문 미독 — manager-tdd가 Step 2 진입 시 `SecurityConfig.java` 정밀 분석 후 경로 규칙 위치 결정 필요

위 4건은 사용자/오케스트레이터 확인 후 manager-tdd 에 전달되어야 함. 나머지는 본 계획대로 RED-GREEN-REFACTOR 진행 가능.
