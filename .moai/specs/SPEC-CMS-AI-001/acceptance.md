# SPEC-CMS-AI-001 Acceptance Criteria

> 본 문서는 spec.md의 모든 sub-REQ에 대응하는 Given/When/Then 인수 조건을 정의한다. 자동화 검증은 JUnit 5 + Testcontainers(PostgreSQL 16) + Spring Boot Test로 수행하며, **Python ML 서비스는 `MockMlServiceClient`로 모킹**하여 실제 ML 모델 없이 Spring Boot 게이트웨이 레이어를 독립 검증한다. 실제 ML 모델 정확도는 별도 ML ops 인수 절차이며 본 SPEC 범위가 아니다. SPEC-CMS-009가 구축한 batch_execution_log/retention_policy/배치 공통 패턴은 입력으로 사용하므로 해당 항목의 재검증은 SPEC-CMS-009 acceptance.md를 따른다.

---

## A. 성장단계 예측 (REQ-AI-001 ~ 004)

### REQ-AI-001 — 성장단계 5단계 예측

**Given** `MockMlServiceClient`가 `{stage:"GROWTH", entryProbabilities:{PREPARATION:0.05, GROWTH:0.62, STAGNATION:0.18, MATURITY:0.10, DECLINE:0.05}, confidence:0.81}` 을 반환하도록 설정된 상태에서
**When** 인증된 사용자가 `POST /api/v1/ai/growth-stage` 로 `{features:{revenue:5000000000, capital:1000000000, operatingProfit:300000000, debtRatio:0.45}}` 를 전송하면
**Then** 200 OK + `stage="GROWTH"` + entryProbabilities 5개 키(PREPARATION/GROWTH/STAGNATION/MATURITY/DECLINE)가 모두 0.0~1.0 범위이고 합계가 1.0±0.01 이다.

**And** 응답 latency 가 3초 이내이다 (p95 검증은 §F-1).

### REQ-AI-002 — 결과 캐싱

**Given** 동일 companyId + 동일 input 해시로 1회 성장단계 예측이 수행되어 캐시에 적재된 상태에서
**When** 캐시 TTL(1시간) 내에 동일 요청을 재전송하면
**Then** 200 OK + 응답에 `cached=true` 가 포함되고 `MockMlServiceClient` 호출 카운트가 증가하지 않는다 (재호출 없음).

**And When** 입력 features 중 한 값이라도 변경되어 input 해시가 달라지면
**Then** `cached=false` 이고 ML 클라이언트가 재호출된다.

### REQ-AI-003 — 예측 로그 비동기 적재

**Given** 성장단계 예측 요청이 정상 처리된 직후
**When** 비동기 로그 적재가 완료되면 (테스트는 Awaitility 로 대기)
**Then** `ai_prediction_log` 에 (prediction_type='GROWTH_STAGE', model_name, model_version, input_features, output_result, confidence, latency_ms, status='SUCCESS') 1건이 적재된다.

**And When** 로그 적재 스레드풀에서 INSERT 가 실패하도록 강제하면
**Then** 예측 API 응답은 여전히 200 OK 이며 (로그 실패가 응답을 차단하지 않음), ERROR 로그가 기록된다.

### REQ-AI-004 — ML 서비스 장애 폴백

**Given** `MockMlServiceClient`가 3초 타임아웃(또는 5xx)을 시뮬레이션하도록 설정된 상태에서
**When** 사용자가 `POST /api/v1/ai/growth-stage` 를 호출하면
**Then** 503 이 아닌 200 OK + `status='FALLBACK'` + (휴리스틱 결과 또는 `unavailable=true`) 가 반환된다.

**And** `ai_prediction_log` 에 status='TIMEOUT' (또는 'ML_ERROR') 행이 적재된다.

**And When** 연속 실패가 회로차단기 임계를 초과하면
**Then** 후속 요청은 ML 호출 없이 즉시 FALLBACK 응답을 반환한다.

---

## B. 창업기업 가상 시뮬레이션 (REQ-SIM-001 ~ 005)

### REQ-SIM-001 — 비회원 시뮬레이션 요청

**Given** 인증 토큰이 없는 비회원 요청에서
**When** `POST /api/v1/ai/simulation` 로 `{ksicCode:"62010", capitalAmount:50000000, foundingYear:2024, revenueAmount:120000000}` 를 전송하면
**Then** 201 Created + 응답에 `sessionId`(UUID) 가 포함되고 `ai_simulation_session` 에 행이 생성된다.

**And** `projection_result` 에 향후 3년치 `[{year, stage, entryProbabilities{}}]` 배열(길이 3)이 저장된다.

**And** 인증 헤더 없이도 401/403 없이 정상 처리된다 (PUBLIC 화이트리스트).

### REQ-SIM-002 — 입력 검증

**Given** 비회원 요청에서
**When** `ksicCode="6201"` (4자리, 정규식 불일치) 으로 시뮬레이션을 요청하면
**Then** 400 Bad Request + 에러 코드 `AI_SIMULATION_INVALID_INPUT` 가 반환되고 세션이 생성되지 않는다.

**And When** `capitalAmount=0` 또는 음수이면
**Then** 400 Bad Request + `AI_SIMULATION_INVALID_INPUT` 가 반환된다.

**And When** `foundingYear=1800` (범위 밖) 이면
**Then** 400 Bad Request 가 반환된다.

### REQ-SIM-003 — 세션 TTL 24시간

**Given** 시뮬레이션 세션이 생성되어 expires_at = created_at + 24h 로 설정된 상태에서
**When** `GET /api/v1/ai/simulation/{sessionId}` 를 expires_at 이전에 호출하면
**Then** 200 OK + projection_result 가 반환된다.

**And When** expires_at 이 경과한 세션(테스트는 expires_at 을 과거로 강제 설정)을 조회하면
**Then** 404 Not Found + 에러 코드 `AI_SIMULATION_EXPIRED` 가 반환된다.

**And** 만료 세션의 물리 삭제는 SPEC-CMS-009 retention_policy(`target_table='ai_simulation_session'`)로 처리됨이 시드 데이터로 확인된다 (배치 동작 자체는 SPEC-CMS-009 acceptance 검증).

### REQ-SIM-004 — 서버사이드 PDF 리포트

**Given** projection_result 가 존재하는 유효 세션에서
**When** `POST /api/v1/ai/simulation/{sessionId}/report` 를 호출하면
**Then** 202 Accepted (또는 200) + `pdf_status` 가 NONE→GENERATING→READY 로 전이된다.

**And When** READY 상태에서 `GET /api/v1/ai/simulation/{sessionId}/report` 를 호출하면
**Then** 200 OK + `Content-Type=application/pdf` + 비어있지 않은 PDF 바이트가 다운로드된다.

**And When** 만료된 세션 또는 projection_result 가 없는 세션에 PDF 생성을 요청하면
**Then** 404 (또는 409) 가 반환되고 PDF 가 생성되지 않는다.

### REQ-SIM-005 — 비회원 남용 방지

**Given** 동일 client IP 에서 1시간 내 시뮬레이션 생성이 임계값(기본 30회) 미만인 상태에서
**When** 31번째 시뮬레이션 생성을 요청하면
**Then** 429 Too Many Requests + 에러 코드 `AI_SIMULATION_RATE_LIMITED` 가 반환된다.

**And** `ai_simulation_session.client_ip_hash` 에 SHA-256 해시만 저장되고 IP 평문은 어디에도 저장되지 않는다 (테이블·로그 검사).

---

## C. 경영위험 예측 (REQ-AI-005 ~ 007)

### REQ-AI-005 — 경영위험 스코어링

**Given** `MockMlServiceClient`가 `{defaultProbability:0.43, riskGrade:"YELLOW", topFactors:[{name:"부채비율", contribution:0.41},{name:"영업현금흐름", contribution:0.33},{name:"매출성장률", contribution:0.26}]}` 을 반환하도록 설정된 상태에서
**When** 인증된 사용자가 `POST /api/v1/ai/risk-score` 로 재무 데이터를 전송하면
**Then** 200 OK + `defaultProbability`(0.0~1.0) + `riskGrade` + `topFactors`(정확히 3개, 각 name+contribution) 가 반환된다.

### REQ-AI-006 — 위험 등급 경계값

**Given** 위험 등급 임계값이 기본값(GREEN<0.25, YELLOW<0.50, ORANGE<0.75, RED≥0.75) 으로 설정된 상태에서
**When** ML 이 defaultProbability=0.24 를 반환하면 **Then** riskGrade='GREEN' 이다.
**When** defaultProbability=0.25 → **Then** 'YELLOW', 0.50 → **Then** 'ORANGE', 0.75 → **Then** 'RED' 로 결정론적으로 매핑된다 (경계값 포함 관계 검증).

**And When** 시스템 설정 `ai.risk.thresholds` 를 변경하면
**Then** 동일 확률에 대해 변경된 경계값으로 등급이 재계산된다.

### REQ-AI-007 — 추론 응답 SLA

**Given** `MockMlServiceClient`가 600ms 지연 응답하도록 설정된 상태에서
**When** `POST /api/v1/ai/risk-score` 를 호출하면
**Then** `ai_prediction_log.latency_ms ≈ 600` 으로 기록되고, 500ms 초과이므로 SPEC-CMS-005 운영자 알림 큐에 WARN 이 push 된다.

**And When** ML 이 정상(≤500ms) 응답하면
**Then** WARN 알림이 push 되지 않는다.

---

## D. 알고리즘 품질 모니터링 (REQ-MON-001 ~ 005)

### REQ-MON-001 — 예측 로그 비동기 적재 (일반화)

**Given** 성장단계·경영위험·시뮬레이션 예측이 각 1회 수행된 직후
**When** 비동기 적재가 완료되면
**Then** `ai_prediction_log` 에 prediction_type 별(GROWTH_STAGE/RISK_SCORE/SIMULATION) 행이 각각 1건씩, (model_name, model_version, input_features, output_result, confidence, latency_ms, status) 가 채워진 채로 적재된다.

### REQ-MON-002 — 정답 라벨 사후 주입

**Given** ai_prediction_log id=100 에 actual_value=NULL 인 행이 존재하고 운영자가 ADMIN 으로 인증된 상태에서
**When** `PUT /api/v1/admin/ai/predictions/100/label` 로 `{actualValue:{stage:"GROWTH"}}` 를 전송하면
**Then** 200 OK + ai_prediction_log id=100 의 actual_value 와 labeled_at 이 갱신된다.

**And When** USER 권한으로 동일 요청을 시도하면
**Then** 403 Forbidden 이 반환된다.

### REQ-MON-003 — 지표 집계 배치

**Given** 전일자 ai_prediction_log 에 model_name='gs-1.2.0', prediction_type='GROWTH_STAGE' 인 분류형 예측 100건이 actual_value 와 함께 적재되어 있고 (정답 75건)
**When** 02:15 에 `AiModelMetricJob` 이 실행되면
**Then** `ai_model_metric` 에 (model_name='gs-1.2.0', aggregate_period='DAILY', accuracy=0.75, latency_p50/p95/p99, sample_count=100) 행이 UPSERT 된다.

**And** 회귀형(시뮬레이션) 모델에 대해서는 rmse/mae 가 산출된다.

**And** `batch_execution_log`(SPEC-CMS-009, job_group='STATS', job_name='AiModelMetricJob', status='SUCCESS') 에 실행 이력이 적재된다.

**And When** actual_value 가 하나도 없는 model 이면
**Then** accuracy/rmse/mae 는 NULL 로 기록되고 sample_count=0 이다.

### REQ-MON-004 — 드리프트 감지 알림

**Given** `AiModelMetricJob` 집계 결과 model_name='rs-0.9.1' 의 accuracy=0.62 (임계 0.70 미만) 인 상태에서
**When** 드리프트 판정 단계가 실행되면
**Then** `ai_model_metric.drift_detected=TRUE` 로 갱신되고 SPEC-CMS-005 운영자 알림 큐에 CRITICAL push 된다.

**And** `ai_retrain_queue` 에 (model_name='rs-0.9.1', trigger_reason='DRIFT_ACCURACY', status='QUEUED', trigger_detail={accuracy:0.62, threshold:0.70}) 1건이 등록된다.

**And When** 동일 model_name 에 이미 status IN ('QUEUED','ACKNOWLEDGED') 인 항목이 존재하면
**Then** 추가 등록되지 않는다 (중복 등록 금지, 큐 카운트 불변).

**And When** 정규화 RMSE > 0.20 인 회귀 모델이면
**Then** trigger_reason='DRIFT_ERROR' 로 동일 로직이 적용된다.

### REQ-MON-005 — 재학습 큐 등록 API

**Given** 운영자가 ADMIN 으로 인증된 상태에서
**When** `POST /api/v1/admin/ai/retrain` 로 `{modelName:"gs-1.2.0"}` 를 전송하면
**Then** 201 Created + ai_retrain_queue 에 (trigger_reason='MANUAL', status='QUEUED', requested_by=운영자ID) 행이 추가된다.

**And When** `PUT /api/v1/admin/ai/retrain/{id}` 로 status='IN_PROGRESS' → 'DONE' 으로 전이하면
**Then** 200 OK + status 와 updated_at 이 갱신된다.

**And When** `GET /api/v1/admin/ai/retrain` 를 호출하면
**Then** 큐 목록이 status·기간 필터·페이징과 함께 반환된다.

**And When** 비ADMIN 권한으로 위 API 를 호출하면
**Then** 403 Forbidden 이 반환된다.

---

## E. Spring Boot ↔ Python ML 인터페이스 계약 (§6.4)

### E-1 OpenAPI 계약 존재 및 DTO 일치

**Given** `docs/ai-ml-service-openapi.yaml` 가 저장소에 존재하는 상태에서
**When** 계약 기반 DTO(GrowthStageMlResponse, RiskScoreMlResponse, SimulationMlResponse) 의 직렬화/역직렬화 테스트를 실행하면
**Then** OpenAPI components/schemas 의 필드와 1:1 일치하며 누락 필드 없이 매핑된다.

### E-2 PII 비전송 강제

**Given** 기업 입력에 대표자명·식별정보(PII) 가 포함된 상태에서
**When** ML 요청 DTO 매핑 레이어를 통과하면
**Then** ML 요청 payload 에는 재무 지표·업종코드·연도만 포함되고 PII 필드는 어떤 경로로도 포함되지 않는다 (payload 직렬화 검증).

### E-3 Mock 어댑터 독립 검증

**Given** Python ML 서비스가 기동되지 않은 테스트 환경에서
**When** `MockMlServiceClient` 활성 프로파일로 전체 acceptance(§A~§D) 통합 테스트를 실행하면
**Then** 모든 시나리오가 실제 ML 호출 없이 통과한다 (Spring Boot 게이트웨이 레이어 독립성 입증).

---

## F. 비기능 요구사항 검증

### F-1 성능 (PER-003)

**When** 성장단계 예측 API 를 부하 테스트하면 **Then** p95 < 3000ms.
**When** 시뮬레이션 생성 API 를 부하 테스트하면 **Then** p95 < 3000ms.
**When** 경영위험 ML 추론 round-trip 을 측정하면 **Then** p95 < 500ms (Mock 정상 응답 기준).
**When** 캐시 적중 성장단계 응답을 측정하면 **Then** < 100ms.
**When** `AiModelMetricJob` 일별 배치를 실행하면 **Then** finished_at − started_at < 600,000ms (10분, SPEC-CMS-001 §17.1).

### F-2 가용성 (SER-003)

**When** ML 서비스 장애를 주입하면 **Then** 모든 ai 예측 API 가 503 없이 FALLBACK 200 응답을 유지한다.
**When** 비동기 로그 적재를 실패시키면 **Then** 예측 API 응답이 차단되지 않는다.

### F-3 보안

**When** 공개 API(`/api/v1/ai/simulation/**`)를 인증 없이 호출 **Then** 정상 처리(화이트리스트). 그 외 ai API 는 인증 미보유 시 401.
**When** 관리자 모니터링 API 를 호출 **Then** SPEC-CMS-005 audit_log 에 action 이 자동 적재된다 (AOP 연동).
**When** ai 테이블·로그를 점검 **Then** IP 평문·PII 평문이 저장되지 않는다.

### F-4 데이터 분류 자기 등록

**When** 마이그레이션 후 SPEC-CMS-009 data_dictionary 를 조회 **Then** ai_prediction_log/ai_simulation_session/ai_model_metric/ai_retrain_queue 4개 테이블의 컬럼이 한글명·도메인 분류와 함께 등록되어 있다.

---

## G. Quality Gates

### G-1 공통 (SPEC-CMS-001 §17.4)

- **QG-COMMON-1**: 결함 발생률 시험 운영 기간 동안 5% 미만 (QUR-004)
- **QG-COMMON-2**: P0 결함 지속시간 1시간 이내 (QUR-004)

### G-2 본 SPEC 고유

- **QG-AI-1**: §A~§E 모든 sub-REQ 가 `MockMlServiceClient` 기반 통합 테스트로 커버되고 GREEN (실제 ML 모델 불요)
- **QG-AI-2**: 코드 커버리지 ≥ 85% (TRUST 5 Tested), Spring Boot 게이트웨이/서비스/매퍼 레이어 기준
- **QG-AI-3**: ML 요청 payload PII 비포함이 자동 테스트로 강제 (E-2)
- **QG-AI-4**: OpenAPI 계약 ↔ DTO 일치 contract test GREEN (E-1)
- **QG-AI-5**: 드리프트 알림 중복 등록 금지 회귀 테스트 GREEN (REQ-MON-004)
- **QG-AI-6**: 4개 신규 테이블이 SPEC-CMS-009 data_dictionary 에 자기 등록 (F-4)

### G-3 검증 시나리오 (Test Scenarios)

| 시나리오 | 검증 대상 | 방법 |
|---|---|---|
| TS-AI-01 | 성장단계 예측 정상 + 캐시 적중 | JUnit 5 + MockMlServiceClient |
| TS-AI-02 | ML 타임아웃 → FALLBACK + 회로차단기 | JUnit 5 (지연/오류 주입) |
| TS-AI-03 | 비회원 시뮬레이션 생성·조회·TTL 만료 | Testcontainers + Awaitility |
| TS-AI-04 | 시뮬레이션 입력 검증 4종 (ksic/capital/year/rate-limit) | Parameterized Test |
| TS-AI-05 | PDF 생성 상태 전이 + 다운로드 | MockMvc + 바이트 검증 |
| TS-AI-06 | 경영위험 등급 경계값 4단계 | Parameterized Test |
| TS-AI-07 | 추론 SLA 초과 시 WARN 알림 | JUnit 5 (지연 주입) |
| TS-AI-08 | AiModelMetricJob 집계 + 드리프트 + 큐 중복 방지 | Testcontainers IT |
| TS-AI-09 | 라벨 주입 → 지표 산출 | Testcontainers IT |
| TS-AI-10 | 재학습 큐 ADMIN CRUD + 권한 403 | MockMvc + 권한 토큰 |
| TS-AI-11 | OpenAPI 계약 ↔ DTO 일치 + PII 비전송 | Contract/Serialization Test |
| TS-AI-12 | 비동기 로그 실패가 응답 비차단 | JUnit 5 (INSERT 실패 주입) |

---

## H. Definition of Done

- [ ] spec.md §5 모든 sub-REQ(REQ-AI-001~007, REQ-SIM-001~005, REQ-MON-001~005) 구현 완료
- [ ] §A~§F acceptance 시나리오가 `MockMlServiceClient` 기반으로 GREEN (실제 Python ML 모델 없이 검증 가능)
- [ ] 4개 신규 테이블 마이그레이션 + SPEC-CMS-009 retention_policy 시드 적용
- [ ] `docs/ai-ml-service-openapi.yaml` OpenAPI 3.1 계약 작성 + DTO contract test GREEN
- [ ] `MlServiceClient` 인터페이스 + `HttpMlServiceClient`(타임아웃/회로차단기) + `MockMlServiceClient` 구현
- [ ] 16개 REST 엔드포인트 (공개 4 + 인증 2 + 관리자 6 + 시뮬레이션 보고 포함)
- [ ] `AiModelMetricJob` 배치 + SPEC-CMS-009 batch_execution_log 연동 + 드리프트 중복 방지
- [ ] 비동기 로그 적재 (전용 스레드풀 + 폴백 큐, 응답 비차단)
- [ ] 서버사이드 PDF 리포트 (iText 또는 Jasper Reports)
- [ ] Vue 3 관리자 모니터링 UI 3개 뷰 (모니터링/예측로그/재학습큐)
- [ ] TRUST 5 Quality Gates G-1/G-2 충족, 커버리지 ≥ 85%
- [ ] PII 비전송 + IP 해시 저장 보안 게이트 자동 검증 GREEN
- [ ] 4개 테이블 SPEC-CMS-009 data_dictionary 자기 등록
- [ ] spec.md 변경 이력 + 구현 메모 갱신, 상태 Draft → Implemented → Tested 전이
