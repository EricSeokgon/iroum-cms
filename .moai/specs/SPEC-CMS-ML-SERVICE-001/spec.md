---
id: SPEC-CMS-ML-SERVICE-001
version: 0.2.0
status: Completed
created: 2026-05-19
updated: 2026-05-20
author: manager-spec (MoAI)
priority: P1 (옵션 트랙)
issue_number: 0
---

# SPEC-CMS-ML-SERVICE-001: Python FastAPI ML 추론 서비스 — Spring Boot ML 계약 실서비스 구현 v0.1

본 SPEC은 SPEC-CMS-AI-001/002/003에서 확립·검증된 **Spring Boot(게이트웨이) ↔ Python ML(추론 전용 마이크로서비스)** 분리 구조에서, 그동안 `MockMlServiceClient`(테스트 더블)로만 검증되어 온 ML 추론 계약을 **실제 Python FastAPI 서비스로 구현**하는 명세이다.

핵심 설계 원칙(형제 AI SPEC 계승):
① Spring Boot(Java 17)는 API Gateway + 비즈니스 로직 + 하이브리드 머지 책임, Python FastAPI 서비스는 **순수 추론 전용 마이크로서비스**(내부망 전용, 외부 비노출)로 책임 분리한다.
② 두 서비스 간 계약은 기존 OpenAPI 3.1 문서 `docs/ai-ml-service-openapi.yaml`을 **단일 진실 공급원(Single Source of Truth)** 으로 삼는다. 본 SPEC은 새 계약을 만들지 않으며 기존 계약을 구현한다.
③ Spring Boot 측 코드(`MlServiceClient`/`MlServiceClientImpl`/`MockMlServiceClient`/Resilience4j `ml-service` CircuitBreaker)는 **읽기 전용·무수정**이며, 본 SPEC은 Python 측 서비스만 신규 작성한다.
④ PII는 ML 요청에 절대 포함하지 않는다(기업명·대표자명·사업자등록번호 등). 허용 프로필 필드는 `ksic_code/employee_count/growth_stage/region_code/annual_revenue` 한정이다.

---

## 1. 개요

| 항목 | 내용 |
|------|------|
| SPEC ID | SPEC-CMS-ML-SERVICE-001 |
| 제목 | Python FastAPI ML 추론 서비스 — 7개 엔드포인트 실서비스 구현 |
| 작성일 | 2026-05-19 |
| 작성자 | manager-spec (MoAI) |
| 상태 | Tested |
| 버전 | v0.1 |
| 우선순위 | P1 (옵션 트랙) |
| 분류 | Detail SPEC (parent: SPEC-CMS-001) |
| 의존 SPEC | SPEC-CMS-AI-001 (ML 인프라·`MlServiceClient` 계약·OpenAPI 문서·CircuitBreaker), SPEC-CMS-AI-002 (`policy-match` 계약), SPEC-CMS-AI-003 (`embed`/`rag` 계약·384차원) |
| 형제 SPEC | SPEC-CMS-AI-001/002/003 (Spring Boot 측 모두 구현 완료) |
| 참조 SPEC | SPEC-CMS-002 (인증/권한·내부망 정책), SPEC-CMS-005 (감사로그·관측성), SPEC-CMS-SECURITY-PII-* (PII 보호 원칙) |
| 추적 prefix | REQ-MLS-* (기능 요구사항), AC-MLS-* (수용 기준) |
| DB 마이그레이션 | 없음 (Python 서비스는 무상태 추론 전용 — Spring 스키마 무변경, 최신 마이그레이션 V33 유지) |
| 빌드 산출물 | `ml-service/` 디렉터리 (신규), `deploy/docker-compose.prod.yml` 통합 |

---

## 2. 참조 문서

- **계약(단일 진실 공급원)**: `docs/ai-ml-service-openapi.yaml` (OpenAPI 3.1, version 1.0.0) — 7개 엔드포인트 요청/응답 스키마의 권위 정의
- **Spring Boot 호출부(읽기 전용)**:
  - `backend/src/main/java/kr/co/ircp/cms/infra/ml/MlServiceClient.java` (인터페이스)
  - `backend/src/main/java/kr/co/ircp/cms/infra/ml/MlServiceClientImpl.java` (RestTemplate + Resilience4j, `@Profile("!test")`)
  - `backend/src/test/java/kr/co/ircp/cms/infra/ml/MockMlServiceClient.java` (테스트 더블 — 결정적 응답 레퍼런스)
- **DTO(읽기 전용·계약 형상)**: `backend/src/main/java/kr/co/ircp/cms/infra/ml/dto/*.java` (16개 record)
- **Spring 설정(읽기 전용)**: `backend/src/main/resources/application.yml` §`ml.service` (base-url, 엔드포인트별 timeout), §`resilience4j.circuitbreaker.instances.ml-service`
- **배포**: `deploy/docker-compose.prod.yml` (`ML_SERVICE_URL` 환경변수 주입 지점), `deploy/Dockerfile.backend`
- **외부 기술 참조**: FastAPI, Uvicorn, `sentence-transformers`(`paraphrase-multilingual-MiniLM-L12-v2`, 384차원 한국어 호환), scikit-learn, NumPy

> **계약 형상 주의(HARD)**: 본 SPEC의 권위 계약은 `docs/ai-ml-service-openapi.yaml` 이다. 해당 문서에서 `growth-stage`/`risk-score`/`simulation`/`health` 응답은 camelCase(`entryProbabilities`, `defaultProbability`, `riskGrade`, `topFactors`, `modelVersion`, `loadedModels`, `projection`)이고, `policy-match`/`embed`/`rag` 요청·응답은 snake_case(`company_profile`, `candidate_policy_ids`, `top_k`, `query_text`, `semantic_score`, `model_name`, `model_version`, `matched_terms`, `rationale`, `quality_score`)이다. 또한 OpenAPI 의 `GrowthStageResponse.stage` enum 은 `[SEED, STARTUP, GROWTH, EXPANSION, MATURITY]`, `RiskScoreResponse.riskGrade` enum 은 `[GREEN, YELLOW, ORANGE, RED]` 로 정의되어 있다. Python 서비스는 **OpenAPI 문서의 JSON 필드명·enum을 정확히 따른다**. Spring `MlServiceClientImpl` 는 Jackson 역직렬화를 사용하므로 JSON 필드명이 OpenAPI 와 일치하는 것이 검증의 기준이다.

---

## 3. 범위 및 비범위

### 3.1 1차 포함 범위 (P1, 옵션 트랙)

- **FastAPI 애플리케이션 골격 (REQ-MLS-001~003)**: Python 3.11+, FastAPI + Uvicorn, OpenAPI 3.1 계약 정합 라우터 7개, 내부망 전용 바인딩
- **성장단계 예측 (REQ-MLS-010~012)**: `POST /ml/v1/growth-stage` — 업종/자본/설립연도/매출 입력 → 단계 + 확률맵 + confidence + modelVersion
- **위험점수 예측 (REQ-MLS-013~015)**: `POST /ml/v1/risk-score` — 부도확률 + 위험등급 + 상위 기여요인 ≤3
- **성장 시뮬레이션 (REQ-MLS-016~017)**: `POST /ml/v1/simulation` — 연도별 단계 전이 투영 ≥2개 포인트
- **시맨틱 정책 매칭 (REQ-MLS-018~020)**: `POST /ml/v1/policy-match` — 코사인 유사도 기반 후보별 시맨틱 점수(0~1) + 매칭 근거
- **문장 임베딩 (REQ-MLS-021~023)**: `POST /ml/v1/embed` — `sentence-transformers` 384차원 float 벡터
- **RAG 생성형 답변 (REQ-MLS-024~026)**: `POST /ml/v1/rag` — 컨텍스트 기반 규칙형 답변 + 환각 가드(빈 컨텍스트 시 안내문)
- **헬스체크 (REQ-MLS-027~028)**: `GET /ml/v1/health` — 상태 + 적재 모델 목록
- **PII 차단·관측성 (REQ-MLS-030~034)**: 요청 검증 화이트리스트, PII 미수용·미로깅, 구조화 로그(PII 제외)
- **컨테이너화·배포 (REQ-MLS-040~043)**: `ml-service/` Docker 이미지, `docker-compose.prod.yml` 통합, 내부 네트워크 한정

### 3.2 2차/후속 범위 (본 SPEC 비포함, 업그레이드 경로만 명시)

- 실제 학습 데이터 기반 ML 모델 학습 파이프라인(MVP는 규칙 증강 scikit-learn 추론으로 대체)
- LLM 기반 RAG 생성(MVP는 규칙형 템플릿 답변 — 추후 LLM 어댑터로 교체 가능하도록 인터페이스 분리)
- 모델 버전 A/B 테스트·온라인 재학습·피처스토어
- GPU 추론·모델 서빙 프레임워크(Triton/TorchServe) 도입

### 3.3 Exclusions (What NOT to Build)

- **[제외] Spring Boot 측 코드 신규 작성/수정 금지**: `MlServiceClient`/`MlServiceClientImpl`/`MockMlServiceClient`/DTO/`application.yml`/Resilience4j 설정은 읽기 전용. 본 SPEC은 Python 서비스만 신설한다. (이유: 형제 AI SPEC 으로 이미 구현·검증 완료. 변경 시 회귀 위험)
- **[제외] OpenAPI 계약 변경 금지**: `docs/ai-ml-service-openapi.yaml` 의 경로·필드명·enum·타입을 변경하지 않는다. Python 서비스가 계약에 맞춘다. (이유: 계약은 단일 진실 공급원이며 Spring 측 Jackson 역직렬화가 이에 의존)
- **[제외] DB 스키마/Flyway 마이그레이션 금지**: Python 서비스는 무상태(stateless) 추론 전용. PostgreSQL/pgvector 접근 없음. 임베딩·검색 오케스트레이션은 Spring Boot 책임이며 본 SPEC 범위 밖. 최신 마이그레이션 V33 그대로 유지한다.
- **[제외] ML 모델 학습·재학습·데이터셋 구축 금지**: MVP는 규칙 증강(rule-augmented) 결정적 추론. 학습 파이프라인은 후속 SPEC.
- **[제외] LLM(외부 생성형 API) 연동 금지**: RAG MVP는 규칙형 템플릿 답변. 외부 LLM 호출·키 관리·과금 경로를 만들지 않는다.
- **[제외] ML 서비스 외부 노출·인증서버·API 키 게이트웨이 금지**: 내부 Docker 네트워크 전용. 공개 인터넷/리버스 프록시 노출 라우트를 만들지 않는다. (이유: 추론 서비스는 신뢰 경계 내부 호출만 허용)
- **[제외] Vue/프론트엔드 변경 금지**: 모니터링 대시보드는 SPEC-CMS-AI-001 범위. 본 SPEC은 백엔드-ML 경로만 다룬다.

---

## 4. 용어 정의

| 용어 | 정의 |
|------|------|
| ML 서비스 | 본 SPEC이 신설하는 Python FastAPI 추론 마이크로서비스 (`ml-service/`) |
| 게이트웨이 | Spring Boot 백엔드 — ML 서비스의 유일한 호출자 |
| 계약 | `docs/ai-ml-service-openapi.yaml` — 두 서비스 간 OpenAPI 3.1 계약 |
| PII | 개인/사업체 식별정보 (기업명, 대표자명, 사업자등록번호, 연락처, 주소 평문 등) |
| 허용 프로필 필드 | `ksic_code, employee_count, growth_stage, region_code, annual_revenue` 5개 한정 |
| 규칙 증강 추론 | 학습 데이터 없이 결정적 규칙 + 통계 휴리스틱으로 산출하는 MVP 추론 |
| 환각 가드 | RAG 컨텍스트가 비었을 때 추측 답변 대신 "관련 정책을 찾지 못했습니다" 류 안내 반환 |
| 내부망 전용 | Docker 사설 네트워크에서만 도달 가능, 외부 포트 미공개 |

---

## 5. 요구사항 (EARS)

### 5.1 애플리케이션 골격 (REQ-MLS-001~003)

- **REQ-MLS-001 (Ubiquitous)**: THE ML 서비스 SHALL Python 3.11 이상 + FastAPI + Uvicorn 으로 구동되며 `docs/ai-ml-service-openapi.yaml` 계약의 7개 경로(`/ml/v1/growth-stage`, `/ml/v1/risk-score`, `/ml/v1/simulation`, `/ml/v1/policy-match`, `/ml/v1/embed`, `/ml/v1/rag`, `/ml/v1/health`)를 노출한다.
- **REQ-MLS-002 (Ubiquitous)**: THE ML 서비스 SHALL 모든 응답 JSON 필드명을 `docs/ai-ml-service-openapi.yaml` 스키마와 정확히 일치시킨다(예측 계열 camelCase, policy-match/embed/rag 계열 snake_case).
- **REQ-MLS-003 (Event-Driven)**: WHEN 요청 본문이 계약 스키마(필수 필드·타입)를 위반하면 THE ML 서비스 SHALL HTTP 422 와 구조화된 검증 오류를 반환하고 PII 가능 필드 값을 오류 메시지·로그에 포함하지 않는다.

### 5.2 성장단계 예측 (REQ-MLS-010~012)

- **REQ-MLS-010 (Event-Driven)**: WHEN `POST /ml/v1/growth-stage` 가 `ksicCode`/`capitalAmount`/`foundingYear`(+선택 `revenueAmount`)로 호출되면 THE ML 서비스 SHALL `stage`(문자열), `entryProbabilities`(맵), `confidence`(double), `modelVersion`(문자열)을 200 으로 반환한다.
- **REQ-MLS-011 (State-Driven)**: WHILE 성장단계를 산출하는 동안 THE ML 서비스 SHALL `stage` 값을 계약 enum `{SEED, STARTUP, GROWTH, EXPANSION, MATURITY}` 중 하나로 한정하고, MVP 규칙 증강 모델은 최소 `{STARTUP, GROWTH, EXPANSION}` 를 산출 가능해야 한다.
- **REQ-MLS-012 (State-Driven)**: WHILE 확률맵을 구성하는 동안 THE ML 서비스 SHALL `entryProbabilities` 의 모든 값을 0.0~1.0 범위로 두고 합이 1.0 ±0.01 이 되도록 정규화하며 `confidence` 를 0.0~1.0 으로 산출한다.

### 5.3 위험점수 예측 (REQ-MLS-013~015)

- **REQ-MLS-013 (Event-Driven)**: WHEN `POST /ml/v1/risk-score` 가 호출되면 THE ML 서비스 SHALL `defaultProbability`(0.0~1.0), `riskGrade`, `topFactors`(≤3, 각 `name`/`contribution`), `modelVersion` 을 200 으로 반환한다.
- **REQ-MLS-014 (State-Driven)**: WHILE 위험등급을 산출하는 동안 THE ML 서비스 SHALL `defaultProbability < 0.3` → `GREEN`, `0.3 ≤ defaultProbability ≤ 0.7` → `YELLOW`, `defaultProbability > 0.7` → `RED` 로 매핑한다(계약 enum `{GREEN, YELLOW, ORANGE, RED}` 호환, MVP 는 GREEN/YELLOW/RED 사용).
- **REQ-MLS-015 (State-Driven)**: WHILE 기여요인을 구성하는 동안 THE ML 서비스 SHALL 각 `contribution` 을 0.0~1.0 범위로 산출하고 기여도 내림차순 최대 3개만 포함한다.

### 5.4 성장 시뮬레이션 (REQ-MLS-016~017)

- **REQ-MLS-016 (Event-Driven)**: WHEN `POST /ml/v1/simulation` 이 호출되면 THE ML 서비스 SHALL `projection`(배열, 각 `year`/`stage`/`entryProbabilities`)과 `modelVersion` 을 200 으로 반환한다.
- **REQ-MLS-017 (State-Driven)**: WHILE 투영을 생성하는 동안 THE ML 서비스 SHALL 최소 2개 이상의 시점 포인트를 산출하고, `foundingYear` 가 제공되면 이를 기준 연도로 사용한다.

### 5.5 시맨틱 정책 매칭 (REQ-MLS-018~020)

- **REQ-MLS-018 (Event-Driven)**: WHEN `POST /ml/v1/policy-match` 가 `company_profile`/`candidate_policy_ids`/`top_k`(+선택 `query_text`)로 호출되면 THE ML 서비스 SHALL `matches`(각 `policy_id`/`semantic_score`/`explanation{matched_terms, rationale}`), `model_name`, `model_version` 을 200 으로 반환한다.
- **REQ-MLS-019 (State-Driven)**: WHILE 시맨틱 점수를 산출하는 동안 THE ML 서비스 SHALL 각 후보 정책의 `semantic_score` 를 코사인 유사도 기반 0.0~1.0 범위로 산출한다.
- **REQ-MLS-020 (Unwanted Behavior)**: IF `company_profile` 에 허용 필드(`ksic_code/employee_count/growth_stage/region_code/annual_revenue`) 외의 키가 포함되면 THEN THE ML 서비스 SHALL 해당 키를 추론·로깅에서 무시(drop)하고 처리를 계속하되 PII 가능 값을 저장·반향하지 않는다.

### 5.6 문장 임베딩 (REQ-MLS-021~023)

- **REQ-MLS-021 (Event-Driven)**: WHEN `POST /ml/v1/embed` 가 `text` 로 호출되면 THE ML 서비스 SHALL `vector`(float 배열)를 200 으로 반환한다.
- **REQ-MLS-022 (Ubiquitous)**: THE ML 서비스 SHALL 임베딩 `vector` 길이를 정확히 384 로 보장한다(`paraphrase-multilingual-MiniLM-L12-v2` 또는 동등 384차원 한국어 호환 모델).
- **REQ-MLS-023 (State-Driven)**: WHILE 임베딩을 산출하는 동안 THE ML 서비스 SHALL 동일 입력 텍스트에 대해 결정적(deterministic)·재현 가능한 동일 벡터를 반환한다.

### 5.7 RAG 생성형 답변 (REQ-MLS-024~026)

- **REQ-MLS-024 (Event-Driven)**: WHEN `POST /ml/v1/rag` 가 `question` 과 비어있지 않은 `contexts` 로 호출되면 THE ML 서비스 SHALL 컨텍스트에 근거한 비어있지 않은 `answer`, `sources`(각 `id`/`relevance` 0~1), nullable `quality_score`(0~100)를 200 으로 반환한다.
- **REQ-MLS-025 (Unwanted Behavior)**: IF `contexts` 가 비어 있으면 THEN THE ML 서비스 SHALL 추측·창작 답변을 생성하지 않고 환각 가드 안내문("관련 정책을 찾지 못했습니다." 류)과 빈 `sources` 를 반환한다.
- **REQ-MLS-026 (State-Driven)**: WHILE `sources` 를 구성하는 동안 THE ML 서비스 SHALL 각 `relevance` 를 0.0~1.0 범위로 산출하고 입력 `contexts` 의 `id` 만 참조한다(외부 출처 생성 금지).

### 5.8 헬스체크 (REQ-MLS-027~028)

- **REQ-MLS-027 (Event-Driven)**: WHEN `GET /ml/v1/health` 가 호출되면 THE ML 서비스 SHALL `status`(`UP`/`DOWN`)와 `loadedModels`(적재 모델명 배열)를 200 으로 반환한다.
- **REQ-MLS-028 (State-Driven)**: WHILE 정상 동작 중인 동안 THE ML 서비스 SHALL `status="UP"` 과 함께 임베딩·성장단계·위험·시뮬레이션·정책매칭 모델을 식별하는 모델명을 `loadedModels` 에 비어있지 않게 나열한다.

### 5.9 성능·복원력 (REQ-MLS-029)

- **REQ-MLS-029 (State-Driven)**: WHILE 정상 부하 동안 THE ML 서비스 SHALL 각 엔드포인트를 Spring `application.yml` 의 `ml.service.timeout` 한계(risk-score 500ms, embed 1500ms, growth-stage/simulation/policy-match 3000ms, rag 5000ms, health 1000ms) 이내에 응답하여 Resilience4j `ml-service` 회로 차단(slowCall 5s, 50% 실패율 → OPEN 30s)을 유발하지 않는다.

### 5.10 보안·PII (REQ-MLS-030~034)

- **REQ-MLS-030 (Unwanted Behavior)**: IF 요청에 기업명·대표자명·사업자등록번호·연락처·주소 평문 등 PII 로 식별되는 필드가 포함되면 THEN THE ML 서비스 SHALL 해당 값을 추론에 사용하지 않고 응답·로그·예외 메시지에 절대 반향(echo)하지 않는다.
- **REQ-MLS-031 (Ubiquitous)**: THE ML 서비스 SHALL 어떤 로그 레벨에서도 요청 본문 원문(특히 `text`/`question`/`query_text`/`company_profile` 값)을 평문으로 기록하지 않는다.
- **REQ-MLS-032 (Ubiquitous)**: THE ML 서비스 SHALL 컨테이너 내부에서만 수신 포트를 바인딩하며 `deploy/docker-compose.prod.yml` 에서 호스트/공개 포트로 매핑되지 않는다(내부 네트워크 전용).
- **REQ-MLS-033 (State-Driven)**: WHILE 운영 프로파일로 구동되는 동안 THE ML 서비스 SHALL DEBUG/TRACE 페이로드 로깅을 비활성화한다(SPEC-CMS-SECURITY-MEDIUM-16 원칙 준용).
- **REQ-MLS-034 (Unwanted Behavior)**: IF ML 서비스가 PostgreSQL·외부 인터넷·LLM API 등 비계약 외부 자원에 접근을 시도하면 THEN 이는 설계 위반이며 THE ML 서비스 SHALL 그러한 호출 경로를 포함하지 않는다.

### 5.11 컨테이너화·배포 (REQ-MLS-040~043)

- **REQ-MLS-040 (Ubiquitous)**: THE ML 서비스 SHALL `ml-service/` 디렉터리에 독립 Docker 이미지(`Dockerfile`)로 패키징되며 Python 의존성을 고정 버전으로 명시(`requirements.txt` 또는 동등)한다.
- **REQ-MLS-041 (State-Driven)**: WHILE `docker-compose.prod.yml` 로 배포되는 동안 THE ML 서비스 SHALL Spring Boot 가 `ML_SERVICE_URL` 로 사설 네트워크 호스트명을 통해 도달 가능하도록 동일 compose 네트워크에 참여한다.
- **REQ-MLS-042 (Event-Driven)**: WHEN 컨테이너 헬스 프로브가 실행되면 THE ML 서비스 SHALL `/ml/v1/health` 를 통해 기동 완료(모델 적재 후)를 보고한다.
- **REQ-MLS-043 (Unwanted Behavior)**: IF 모델 적재가 완료되지 않은 상태에서 추론 요청이 도착하면 THEN THE ML 서비스 SHALL 추측 응답 대신 503(서비스 미준비)을 반환하여 게이트웨이 폴백을 유도한다.

---

## 6. 인수 조건 요약

상세 Given-When-Then 시나리오는 `acceptance.md` 를 따른다. 핵심 게이트:

1. 7개 엔드포인트가 OpenAPI 계약대로 응답하고 Spring `MlServiceClientImpl` Jackson 역직렬화가 성공한다 (AC-MLS-001~007)
2. `/ml/v1/embed` 가 정확히 384차원 float 벡터를 반환한다 (AC-MLS-021)
3. PII 필드가 수용·로깅·반향되지 않는다 (AC-MLS-030~031)
4. `/ml/v1/health` 가 적재 모델명을 비어있지 않게 나열한다 (AC-MLS-027)
5. 통합 테스트: Spring Boot ↔ 실제 FastAPI 서비스 연결(Docker Compose 또는 TestContainers)이 성공한다 (AC-MLS-050)
6. 성장단계가 `{STARTUP, GROWTH, EXPANSION}` 중 하나를 반환한다 (AC-MLS-010)
7. 위험점수 등급이 임계값(GREEN<0.3 / YELLOW 0.3~0.7 / RED>0.7) 규칙을 따른다 (AC-MLS-014)
8. 시뮬레이션이 2개 이상의 투영 포인트를 반환한다 (AC-MLS-016)
9. 정책 매칭이 후보별 `semantic_score` 0~1 을 반환한다 (AC-MLS-018)
10. RAG 가 컨텍스트 존재 시 비어있지 않은 답변, 빈 컨텍스트 시 환각 가드 안내를 반환한다 (AC-MLS-024~025)

---

## 7. 기술 접근 방법

### 7.1 아키텍처 개요

```
Spring Boot (게이트웨이, :8080)
  └─ MlServiceClientImpl (RestTemplate + Resilience4j ml-service CB)
        │  HTTP (사설 compose 네트워크)  ML_SERVICE_URL
        ▼
Python FastAPI ML 서비스 (:8000, 내부망 전용 — 본 SPEC 신설)
  ├─ router/  ml_v1.py        7개 경로, OpenAPI 계약 정합 Pydantic 모델
  ├─ service/ growth.py       규칙 증강 성장단계 추론
  ├─ service/ risk.py         규칙 증강 위험점수 + 등급 임계 매핑
  ├─ service/ simulation.py   연도별 단계 전이 투영
  ├─ service/ embedding.py    sentence-transformers 384차원 (단일 로드·캐시)
  ├─ service/ policy_match.py 코사인 유사도 시맨틱 점수
  ├─ service/ rag.py          규칙형 템플릿 답변 + 환각 가드
  └─ core/    pii_guard.py / logging.py / model_registry.py
```

### 7.2 핵심 결정

- **계약 정합 우선**: Pydantic 모델 필드명을 OpenAPI 문서와 1:1 정렬. 예측 계열은 camelCase alias(`entryProbabilities` 등), policy-match/embed/rag 계열은 snake_case. FastAPI `response_model` 로 직렬화 형상을 계약에 고정한다.
- **임베딩 모델 단일 로드**: `paraphrase-multilingual-MiniLM-L12-v2` 를 프로세스 기동 시 1회 로드(`model_registry`)하고 워밍업 후 `health` 가 UP 으로 전환. embed 1500ms 타임아웃 충족을 위해 모델은 미리 적재한다.
- **규칙 증강 결정적 추론(MVP)**: 학습 데이터 없이 업종/자본/연차/매출 휴리스틱 + scikit-learn 보조로 단계·위험·시뮬레이션 산출. 동일 입력 → 동일 출력(테스트 재현성). 추론 인터페이스는 추후 학습 모델 교체가 가능하도록 service 계층으로 분리.
- **RAG 인터페이스 분리**: MVP 는 규칙형 템플릿(`contexts` 제목·관련도 결합). `AnswerGenerator` 추상 인터페이스를 두어 후속 LLM 어댑터 교체 경로 확보(본 SPEC 에서는 규칙형 1개 구현만).
- **PII 가드 미들웨어**: 요청 진입 시 허용 필드 화이트리스트 적용, 비허용 키 drop, 로깅 필터는 본문 값을 마스킹/제외. 422/503 오류 응답에 입력 값 미포함.
- **무상태**: DB·캐시·외부 네트워크 의존 없음. 수평 확장 가능(동일 입력 결정적).

### 7.3 배포 통합

- `ml-service/Dockerfile`: python:3.11-slim 기반, 의존성 고정, 모델 사전 다운로드(빌드 타임), 비공개 포트 8000 내부 노출.
- `deploy/docker-compose.prod.yml`: `ml-service` 서비스 추가, `ports` 미공개(expose 만), `ML_SERVICE_URL=http://ml-service:8000` 을 backend 서비스 환경에 주입(기존 `${ML_SERVICE_URL}` 자리 매핑), `healthcheck` 로 `/ml/v1/health` 사용, backend `depends_on` 조건 `service_healthy`.

### 7.4 검증 전략

- **단위(Python)**: 각 service 모듈 결정성·범위·정규화·환각 가드를 pytest 로 검증.
- **계약**: FastAPI 자동 OpenAPI 스키마를 `docs/ai-ml-service-openapi.yaml` 과 대조(필드명·필수·enum·차원).
- **통합(Spring↔ML)**: 실제 FastAPI 컨테이너를 TestContainers(GenericContainer) 또는 Docker Compose 로 기동하고, `MlServiceClientImpl`(`@Profile("!test")` 경로) 또는 동등 RestTemplate 호출로 7개 엔드포인트 라운드트립을 검증. `MockMlServiceClient` 의 결정적 응답 형상을 동등성 기준 레퍼런스로 사용.

---

## 8. 구현 메모

- 본 SPEC의 단일 진실 공급원은 `docs/ai-ml-service-openapi.yaml` 이다. 작업 시작 전 해당 문서와 `MockMlServiceClient` 의 응답 형상을 함께 대조하여 필드명 케이스(camelCase vs snake_case)를 엔드포인트별로 확정한다. 계약과 Mock 이 불일치하면 **OpenAPI 문서가 우선**하며, 불일치는 구현 보고서에 명시한다.
- Spring 측 무수정 원칙: `backend/.../infra/ml/**` 및 `application.yml` 의 `ml.service`/`resilience4j` 블록은 절대 수정하지 않는다. Python 서비스가 계약에 맞춘다.
- 신규 산출물은 모두 `ml-service/` 하위에 둔다. `backend/` 트리에 Python 코드를 두지 않는다.
- DB 마이그레이션 추가 금지(최신 V33 유지). Python 서비스는 DB 미접근.
- 임베딩 모델 다운로드는 이미지 빌드 타임에 완료하여 런타임 네트워크 의존(외부 모델 허브 호출)을 제거한다 — REQ-MLS-034 외부 접근 금지와 정합.
- `code_comments=ko` 설정에 따라 Python 코드 주석·@MX 태그 설명은 한국어로 작성한다.
- 관련 메모리: [[project-iroum-ai-spec-pattern]], [[project-iroum-stack]].

---

## HISTORY

- 2026-05-19 v0.1 (Draft → Approved): SPEC 신규 작성. `docs/ai-ml-service-openapi.yaml` 계약·`MlServiceClientImpl`·`MockMlServiceClient`·`application.yml` ml.service/resilience4j 설정을 권위 소스로 분석. 7개 엔드포인트 EARS 요구사항(REQ-MLS-001~043), 인수조건 요약, 기술접근, 구현메모 확정. 어노테이션 완료로 상태 Approved 전환. (작성자: manager-spec / MoAI)
- 2026-05-20 v0.1 (Approved → Tested): 보안 감사 완료 (SPEC-CMS-SECURITY-PII-FOLLOWUP-002). PII 화이트리스트(ksic_code/employee_count/growth_stage/region_code/annual_revenue) 준수 확인, IP 해시 저장·session_ref 해시 처리·관리자 API ROLE=ADMIN 제한 등 보안 요구사항 검증. 상태 Tested 전환. (작성자: MoAI)
