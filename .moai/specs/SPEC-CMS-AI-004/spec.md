---
id: SPEC-CMS-AI-004
version: 0.1.0
status: completed
created: 2026-06-15
updated: 2026-06-15
author: manager-spec (MoAI)
priority: P2 (옵션 트랙)
issue_number: 0
---

# SPEC-CMS-AI-004: AI 스마트 태그 추천 기능 — 게시글·Q&A 실시간 태그 추천 v0.1

본 SPEC은 SPEC-CMS-001(Umbrella) AI 트랙의 확장 명세이다. **옵션 트랙 P2**로, 별도 사용자 승인 시점에 착수한다.

핵심 설계 원칙은 형제 SPEC SPEC-CMS-AI-001 / AI-002 / AI-003(모두 구현 완료)에서 검증·확립된 패턴을 그대로 계승한다:

① Spring Boot(Java)는 **API Gateway + 비즈니스 로직 + 캐시·로깅 오케스트레이션**, Python FastAPI ML 서비스는 **태그 추천 추론 전용 마이크로서비스**(내부망 전용, 외부 비노출)로 책임을 분리한다.
② 두 서비스 간 계약을 OpenAPI 3.1(`docs/ai-ml-service-openapi.yaml`)로 명시적으로 정의한다.
③ ML 응답을 모킹할 수 있는 `MlServiceClient` 인터페이스를 **재사용·확장**하여 ML 모델 부재 시에도 Spring Boot 레이어를 독립적으로 검증한다.
④ ML 장애 시 태그 추천 기능은 **조용히 비활성화**(graceful degradation)되며, 사용자에게 오류를 노출하지 않는다. 글쓰기 본연의 흐름은 절대 방해받지 않는다.

본 SPEC의 정체성은 **글쓰기 보조(write-time assistance)**이다. 작성자가 본문을 입력하는 동안 디바운스된 비동기 추천 요청으로 태그 후보를 제시하고, 작성자가 클릭 또는 자유 입력으로 태그를 선택한다. 추천은 강제되지 않으며(자동 적용 안 함), 게시글 저장 본연의 동작을 차단하지 않는다.

---

## 1. 개요

| 항목 | 내용 |
|------|------|
| SPEC ID | SPEC-CMS-AI-004 |
| 제목 | AI 스마트 태그 추천 기능 — 게시글·Q&A 실시간 태그 추천 |
| 작성일 | 2026-06-15 |
| 작성자 | manager-spec (MoAI) |
| 상태 | completed |
| 버전 | v0.1 |
| 우선순위 | P2 (옵션 트랙) |
| 분류 | Detail SPEC (parent: SPEC-CMS-001) |
| 의존 SPEC | SPEC-CMS-AI-001 (AI/ML 인프라 — `MlServiceClient`·`MlServiceClientImpl`·`MockMlServiceClient`·`AiPredictionLogService`·CircuitBreaker `ml-service`·Caffeine 캐시·OpenAPI 계약·`AsyncConfig.aiLogExecutor`, 구현 완료) |
| 형제 SPEC | SPEC-CMS-AI-002 (`ai_policy_recommendation_log` 비동기 적재·세션 SHA-256 해시·피드백 루프 패턴, 구현 완료), SPEC-CMS-AI-003 (`MlServiceClient` 신규 메서드 확장·Caffeine 캐시·그레이스풀 폴백 패턴, 구현 완료) |
| 참조 SPEC | SPEC-CMS-002 (인증/권한 — 관리자 ROLE=ADMIN, 비회원 공개 API 화이트리스트), SPEC-CMS-005 (감사로그 인프라 — `@AuditLog` AOP), SPEC-CMS (게시판 도메인 — `bbs_post`·`qna` 테이블 V10) |
| 추적 prefix | REQ-AI-TAG-* (기능 요구사항), AC-AI-TAG-* (수용 기준) |
| DB 마이그레이션 | V54 (단일 마이그레이션) |
| 코드 주석 언어 | 한국어 (ko) — `.moai/config/sections/language.yaml` `code_comments: ko` |
| 개발 방법론 | TDD (RED-GREEN-REFACTOR) — `quality.yaml` `development_mode: tdd` |

---

## 2. 참조 문서

- **상위 SPEC**: SPEC-CMS-001 — AI 확장 트랙 정의, 데이터 분류, 품질 게이트
- **선행 SPEC (인프라 재사용)**: SPEC-CMS-AI-001
  - `infra/ml/MlServiceClient.java` 인터페이스 — `tagRecommendation` 메서드 확장 (기존 `predictGrowthStage`/`predictRiskScore`/`predictSimulation`/`policyMatch`/`embed`/`rag`/`health` 보존)
  - `infra/ml/MlServiceClientImpl.java` (RestTemplate + Resilience4j CircuitBreaker `ml-service`, 3초 타임아웃) — `tagRecommendation` 구현 추가
  - `infra/ml/MockMlServiceClient.java` (테스트 모킹 어댑터) — `tagRecommendation` 모킹 구현 추가
  - `domain/ai/service/AiPredictionLogService.java` (`@Async("aiLogExecutor")` 비동기 로그) — 태그 로깅 메서드 추가
  - `config/AsyncConfig.java` (`aiLogExecutor` 스레드 풀 core=2/max=4)
  - `config/CacheConfig.java` (Caffeine 캐시 빈) — `tagRecommendationCache` 추가
  - `common/util/IpHashUtil.java` (SHA-256 해시)
  - `docs/ai-ml-service-openapi.yaml` (Spring Boot ↔ Python FastAPI 계약)
  - `infra/ml/MlServiceException.java` (ML 장애·타임아웃·CircuitBreaker OPEN 시 throw → 호출부 폴백 위임)
- **선행 SPEC (패턴 재사용)**: SPEC-CMS-AI-002 — `ai_policy_recommendation_log`(V32) 비동기 적재 스키마·세션 SHA-256 해시·추천/피드백 이중 행 패턴
- **선행 SPEC (캐시 패턴)**: SPEC-CMS-AI-003 — Caffeine 캐시 키·TTL·그레이스풀 폴백 패턴
- **참조 SPEC**:
  - SPEC-CMS-002 (인증/권한 — 관리자 글쓰기 ROLE=ADMIN, Q&A 시민 작성 공개 API 화이트리스트)
  - SPEC-CMS-005 (감사로그 — `@AuditLog` AOP)
  - 게시판 도메인 — `bbs_post`(V10), `qna`(V10) 테이블

---

## 3. 배경 및 현황 분석

### 3.1 현재 태그 구현 상태 (Brownfield 사실 확인)

| 대상 | 태그 컬럼 존재 여부 | 비고 |
|------|------------------|------|
| `bbs_post` (게시글, V10) | **없음** | `tags` 컬럼 미존재 — 신규 컬럼 추가 필요 |
| `qna` (Q&A, V10) | **없음** | `tags` 컬럼 미존재 — 신규 컬럼 추가 필요 |
| `media_asset` (V12) | 있음 (`tags TEXT[]`) | 미디어 자산에만 존재 — 본 SPEC 참조 패턴 |

- 게시글·Q&A에는 **태그 개념 자체가 없다**. 본 SPEC은 ① 태그 저장 컬럼 신설 + ② AI 추천 두 가지를 함께 도입한다.
- `media_asset.tags TEXT[]` 패턴을 그대로 차용하여 `bbs_post`·`qna`에 `tags TEXT[] DEFAULT '{}'`를 additive 추가한다.

### 3.2 프론트엔드 현황

- 관리자 게시글 작성: `frontend/admin/` `PostFormView.vue` — 태그 입력 필드 **없음**.
- 시민 Q&A 작성: `frontend/public/` `QnaCreateView.vue` — 태그 입력 필드 **없음**.
- 스택: Vue 3 + Pinia + TypeScript + Element Plus.

### 3.3 기존 ML 통합 진입점

- `MlServiceClient`는 fan_in ≥ 3 외부 통합 경계(@MX:ANCHOR)이다. 신규 메서드 추가는 인터페이스 확장이며 기존 시그니처를 변경하지 않는다.
- 기존 AI 컨트롤러 경로 규약: 관리자 `/api/v1/admin/ai/**`, 일반/인증 `/api/v1/ai/**`.

---

## 4. 핵심 설계 결정

| 결정 항목 | 선택 | 근거 |
|----------|------|------|
| ML 통합 | `MlServiceClient` 인터페이스 확장 (신규 메서드 `tagRecommendation`) | AI-003 `embed`/`rag` 확장 선례 동일. 신규 클라이언트 생성 금지 |
| UX 모드 | 실시간 디바운스 추천 (버튼 트리거·자동 적용 아님) | 사용자 확정 요구사항. 글쓰기 흐름 비차단 |
| 폴백 | ML 장애 시 추천 조용히 비활성화 (HTTP 200 + 빈 배열) | 글쓰기 본연 동작 보호. 503·오류 노출 금지 |
| 태그 저장 | `bbs_post`·`qna`에 `tags TEXT[] DEFAULT '{}'` additive 컬럼 | `media_asset` 선례. 기존 행은 빈 배열 |
| 태그 어휘(vocabulary) | ML 동적 반환 (마스터 테이블 비도입) | 큐레이션은 향후 확장. 초기 단순화(YAGNI) |
| 추천 로그 | `ai_tag_recommendation_log` 단일 테이블 (추천·채택/거부 이중 행) | AI-002 `ai_policy_recommendation_log` 패턴 동일. 향후 파인튜닝 입력 |
| 캐시 | Caffeine `tagRecommendationCache` (content 해시 → 추천, TTL 30분) | AI-003 캐시 패턴. 동일 본문 반복 입력 시 ML 호출 절감 |
| 비동기 로깅 | `AiPredictionLogService` `@Async("aiLogExecutor")` | AI-001/002 선례. 추천 응답 지연 0 |
| 마이그레이션 | V54 단일 파일 | 형제 SPEC 단일 마이그레이션 규약 |

---

## 5. EARS 요구사항

### 5.1 ML 클라이언트 확장 (인프라)

**REQ-AI-TAG-001 (Ubiquitous)**
THE SYSTEM SHALL `MlServiceClient` 인터페이스에 `TagRecommendationResponse tagRecommendation(TagRecommendationRequest request)` 메서드를 추가하되, 기존 메서드 시그니처(`predictGrowthStage`/`predictRiskScore`/`predictSimulation`/`policyMatch`/`embed`/`rag`/`health`)를 변경하지 않는다.
- 수용 기준: 인터페이스 컴파일 성공, 기존 호출부·테스트 무수정 통과.

**REQ-AI-TAG-002 (Event-Driven)**
WHEN `MlServiceClientImpl.tagRecommendation`이 호출되면, THE SYSTEM SHALL 내부 ML 서비스의 `POST /ml/v1/tag-recommend` 엔드포인트를 RestTemplate으로 호출하고 Resilience4j CircuitBreaker `ml-service`(공유)를 적용하며 3초 타임아웃을 설정한다.
- 수용 기준: 요청 바디 `content`/`existing_tags`/`top_k` snake_case 직렬화, 응답 `recommended_tags`/`scores`/`model_version` 역직렬화.

**REQ-AI-TAG-003 (Unwanted Behavior)**
IF ML 서비스 호출이 실패하거나(connection refused/5xx) 타임아웃되거나 CircuitBreaker가 OPEN 상태이면, THEN THE SYSTEM SHALL `MlServiceException`을 던져 호출부의 그레이스풀 폴백으로 위임한다.
- 수용 기준: 실패 시 호출부가 빈 추천 결과(HTTP 200)를 반환, 예외가 사용자에게 전파되지 않음.

**REQ-AI-TAG-004 (Ubiquitous)**
THE SYSTEM SHALL `MockMlServiceClient`에 `tagRecommendation`의 결정론적 모킹 구현을 추가하여 ML 모델 부재 시에도 Spring Boot 레이어를 독립 검증할 수 있도록 한다.
- 수용 기준: 모킹 활성 프로파일에서 본문 입력 시 고정된 추천 태그 반환, 통합 테스트 통과.

**REQ-AI-TAG-005 (Ubiquitous)**
THE SYSTEM SHALL `docs/ai-ml-service-openapi.yaml`에 `POST /ml/v1/tag-recommend` 경로(요청·응답 스키마 포함)를 추가하여 Spring Boot ↔ Python FastAPI 계약 단일 진실을 유지한다.
- 수용 기준: OpenAPI 스키마에 `TagRecommendRequest`/`TagRecommendResponse` 정의, 필드명·타입이 DTO와 일치.

### 5.2 REST API

**REQ-AI-TAG-006 (Event-Driven)**
WHEN 인증된 관리자가 게시글 작성 중 `POST /api/v1/ai/tag-recommend`를 호출하면, THE SYSTEM SHALL 본문(`content`)·기존 선택 태그(`existingTags`)를 받아 최대 5개의 추천 태그와 신뢰도 점수를 반환한다.
- 수용 기준: 응답 `recommendedTags`(camelCase) 배열 길이 ≤ 5, `scores` 맵 포함, HTTP 200.

**REQ-AI-TAG-007 (State-Driven)**
WHILE Q&A 시민 작성 화면에서 호출될 때, THE SYSTEM SHALL 동일한 `POST /api/v1/ai/tag-recommend`를 비회원(공개 API 화이트리스트) 접근으로 허용하되, 요청에 PII를 포함하지 않는다(본문 텍스트만 전송).
- 수용 기준: 비인증 요청 200 응답, 작성자 식별정보 미전송, SecurityConfig 화이트리스트 등록.

**REQ-AI-TAG-008 (Unwanted Behavior)**
IF 추천 요청의 본문 길이가 20자 미만이면, THEN THE SYSTEM SHALL ML 서비스를 호출하지 않고 빈 추천 배열(HTTP 200)을 반환한다.
- 수용 기준: 19자 본문 요청 시 ML 미호출 + 빈 배열, 20자 본문 요청 시 ML 호출.

**REQ-AI-TAG-009 (Unwanted Behavior)**
IF ML 서비스가 사용 불가하면, THEN THE SYSTEM SHALL 빈 추천 배열을 HTTP 200으로 반환하고 어떤 오류도 사용자에게 노출하지 않는다(graceful degradation).
- 수용 기준: ML down 상황 통합 테스트에서 200 + 빈 배열, 로그에만 경고 기록.

### 5.3 캐시 및 로깅

**REQ-AI-TAG-010 (Event-Driven)**
WHEN 동일 본문 내용에 대한 추천 요청이 30분 이내 재발생하면, THE SYSTEM SHALL Caffeine 캐시(`tagRecommendationCache`, 키=본문 SHA-256 해시, TTL 30분)에서 결과를 반환하고 ML 서비스를 재호출하지 않는다.
- 수용 기준: 동일 본문 2회 요청 시 ML 호출 1회만 발생(모킹 호출 카운트 검증).

**REQ-AI-TAG-011 (Event-Driven)**
WHEN 태그 추천이 사용자에게 제시되면, THE SYSTEM SHALL `AiPredictionLogService`를 통해 `ai_tag_recommendation_log`에 추천 이벤트(본문 해시·추천 태그·점수·콘텐츠 유형)를 `@Async("aiLogExecutor")`로 비동기 적재한다.
- 수용 기준: 추천 응답 지연에 로깅이 영향 없음, 적재 행 `event_type='SUGGESTED'`.

**REQ-AI-TAG-012 (Event-Driven)**
WHEN 사용자가 추천 태그를 채택하거나 거부하면, THE SYSTEM SHALL 해당 상호작용(`ACCEPTED`/`REJECTED`)을 `ai_tag_recommendation_log`에 비동기 적재하여 향후 모델 파인튜닝 입력으로 보존한다.
- 수용 기준: 채택/거부 행 `event_type` 정확, 대상 `tag_value` 기록.

**REQ-AI-TAG-013 (Unwanted Behavior)**
THE SYSTEM SHALL `ai_tag_recommendation_log`에 작성자 평문 식별자·PII를 저장하지 않으며, 세션·회원 식별은 SHA-256 해시로만 보존한다.
- 수용 기준: 스키마에 평문 식별 컬럼 부재, `session_ref` 해시 길이 검증 제약.

### 5.4 태그 저장 (스키마)

**REQ-AI-TAG-014 (Ubiquitous)**
THE SYSTEM SHALL `bbs_post`·`qna` 테이블에 `tags TEXT[] DEFAULT '{}'` 컬럼을 additive 추가하며, 기존 행은 빈 배열을 기본값으로 갖는다.
- 수용 기준: 마이그레이션 V54 적용 후 기존 행 `tags = '{}'`, 기존 INSERT/SELECT 동작 무영향.

**REQ-AI-TAG-015 (Event-Driven)**
WHEN 게시글 또는 Q&A가 태그와 함께 저장되면, THE SYSTEM SHALL 선택된 태그 배열을 해당 행의 `tags` 컬럼에 영속화하고, 목록·상세 조회 시 읽기 전용 태그 칩으로 노출한다.
- 수용 기준: 저장 후 재조회 시 태그 보존, 목록/상세 응답 DTO에 `tags` 포함.

### 5.5 비기능 요구사항

**REQ-AI-TAG-NFR-001 (성능)**
THE SYSTEM SHALL 추천 API의 디바운스 트리거를 타이핑 정지 후 500ms로 설정하고, ML 호출 타임아웃을 3초로 제한하며, 캐시 적중 응답은 50ms 이내를 목표로 한다.

**REQ-AI-TAG-NFR-002 (폴백 안정성)**
THE SYSTEM SHALL 추천 기능 장애가 게시글·Q&A 저장 경로에 어떠한 영향도 주지 않도록 추천 호출을 저장 트랜잭션과 분리한다.

**REQ-AI-TAG-NFR-003 (보안)**
THE SYSTEM SHALL 비회원 Q&A 추천 요청을 SecurityConfig 공개 화이트리스트에 등록하되, 요청 본문 외 어떠한 사용자 식별정보도 ML 서비스로 전송하지 않는다.

---

## 6. 기술 접근 (요약 — 상세는 plan.md)

### 6.1 백엔드 (Spring Boot + MyBatis + PostgreSQL)

- DTO 신규: `infra/ml/dto/TagRecommendationRequest.java`, `TagRecommendationResponse.java`
- `MlServiceClient` 인터페이스에 `tagRecommendation` 추가 → `MlServiceClientImpl`·`MockMlServiceClient` 구현
- 신규 도메인: `domain/ai/tag/` (controller/service/mapper/dto/entity)
  - `POST /api/v1/ai/tag-recommend` (추천 조회)
  - `POST /api/v1/ai/tag-recommend/feedback` (채택/거부 피드백)
- `CacheConfig`에 `tagRecommendationCache` 빈 추가 (Caffeine, TTL 30min)
- `AiPredictionLogService`에 태그 로그 비동기 메서드 추가
- 마이그레이션 V54: `ai_tag_recommendation_log` 생성 + `bbs_post`/`qna` `tags` 컬럼 추가

### 6.2 프론트엔드 (Vue 3 + Element Plus)

- 관리자 재사용 컴포넌트: `TagRecommendationInput.vue`
- 컴포저블: `useTagRecommendation.ts` (디바운스 500ms·최소 20자·최대 5개)
- `PostFormView.vue` 본문 에디터 하단 태그 섹션 통합
- 시민 `QnaCreateView.vue` 태그 입력 통합
- 게시글 목록·상세 읽기 전용 태그 칩

### 6.3 ML 계약

```
POST /ml/v1/tag-recommend
Request:  { "content": "텍스트 내용", "existing_tags": [], "top_k": 5 }
Response: { "recommended_tags": ["태그1","태그2"], "scores": {"태그1": 0.92}, "model_version": "1.0.0" }
Timeout: 3s, CircuitBreaker: ml-service (공유)
```

---

## 7. 데이터 스키마 변경 (V54 — 단일 마이그레이션)

### 7.1 신규 테이블 `ai_tag_recommendation_log`

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | BIGSERIAL PK | |
| `session_ref` | VARCHAR(80) NOT NULL | 익명 세션 또는 회원ID의 SHA-256 해시 (평문 미저장) |
| `content_type` | VARCHAR(20) NOT NULL | `POST` / `QNA` |
| `content_hash` | VARCHAR(64) NOT NULL | 추천 입력 본문 SHA-256 해시 (캐시 키 겸용) |
| `recommended_tags` | JSONB NULL | 순서 보존 추천 태그 배열 |
| `ml_scores` | JSONB NULL | {"태그1": 0.92, ...} |
| `model_version` | VARCHAR(20) NULL | ML 모델 버전 |
| `event_type` | VARCHAR(20) NOT NULL | `SUGGESTED` / `ACCEPTED` / `REJECTED` |
| `tag_value` | VARCHAR(100) NULL | 채택/거부 대상 태그 (피드백 행만, 추천 행은 NULL) |
| `suggested_at` | TIMESTAMPTZ NOT NULL DEFAULT now() | |
| `interacted_at` | TIMESTAMPTZ NULL | 피드백 행만 |

- 제약: `chk_atrl_event CHECK (event_type IN ('SUGGESTED','ACCEPTED','REJECTED'))`
- 제약: `chk_atrl_feedback CHECK ((event_type='SUGGESTED' AND tag_value IS NULL) OR (event_type<>'SUGGESTED' AND tag_value IS NOT NULL))`
- 제약: `chk_atrl_content_type CHECK (content_type IN ('POST','QNA'))`
- 인덱스: `(session_ref, suggested_at DESC)`, `(event_type, suggested_at DESC)`, `(content_type, suggested_at)`

### 7.2 additive 컬럼

```sql
ALTER TABLE bbs_post ADD COLUMN tags TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE qna      ADD COLUMN tags TEXT[] NOT NULL DEFAULT '{}';
```

---

## 8. Exclusions (What NOT to Build)

본 SPEC이 **명시적으로 다루지 않는** 범위:

- **태그 어휘 큐레이션 마스터 테이블(`ai_tag_vocabulary`)**: 초기 버전은 ML 동적 반환만 사용. 관리자 큐레이션 UI·허용 태그 화이트리스트는 향후 별도 SPEC. (YAGNI — 연구 노트의 optional 항목 제외)
- **태그 기반 검색·필터링**: 본 SPEC은 태그 저장·추천·표시까지만. 태그로 게시글을 검색하는 기능은 검색 도메인(SPEC-CMS-006) 확장 범위.
- **자동 태그 적용(auto-apply)**: 추천은 항상 사용자 선택을 거친다. ML 추천을 자동 저장하지 않는다.
- **버튼 트리거 추천**: UX 모드는 실시간 디바운스로 확정. 별도 "추천 받기" 버튼 미구현.
- **관리자 태그 채택률 대시보드(stretch goal)**: 연구 노트의 optional metrics view는 본 SPEC 핵심 범위에서 제외. Phase 3 옵션 항목으로만 남기되 수용 기준에 포함하지 않는다.
- **기존 게시글·Q&A 본문에 대한 소급 태그 일괄 추천(backfill)**: 신규/수정 작성 시점에만 추천. 과거 데이터 일괄 처리 없음.
- **ML 모델 학습·파인튜닝 파이프라인**: `ai_tag_recommendation_log`는 향후 학습 데이터 적재만 담당. 학습 자체는 Python ML 서비스 책임이며 본 SPEC 비범위.
- **다국어 태그 정규화**: `bbs_post_i18n`(V41) 다국어 본문에 대한 태그 번역·정규화 미포함.
- **기존 엔드포인트 변경**: 게시글·Q&A 저장 기존 API의 계약·동작은 변경하지 않는다(태그는 선택적 필드로만 추가).

---

## 9. HISTORY

- 2026-06-15 (v0.1.0): 초기 Draft 작성. SPEC-CMS-AI-001/002/003 인프라 재사용 기반 게시글·Q&A 실시간 태그 추천 명세. REQ-AI-TAG-001~015 + NFR 3건 정의. V54 단일 마이그레이션. (manager-spec)

---

## 구현 완료 노트

- **완료일**: 2026-06-16
- **커밋**: 62f777d
- **태스크**: T-001~T-014, T-016 완료 (T-015 stretch 미구현)
- **테스트**: 백엔드 2,072 unit + 11 IT / 어드민 414 / 공공 249 전체 통과
- **주요 결정**:
  - ML 실패 시 빈 배열 반환(그레이스풀 디그레이데이션) — 태그 추천 실패가 본문 저장에 영향 없음
  - `StringArrayTypeHandler` 재사용으로 PostgreSQL `TEXT[]` ↔ `List<String>` 자동 변환
  - 공공 UI는 Tailwind 인라인 방식 (어드민 el-tag 컴포넌트와 별도 구현, 기존 Q&A 뷰 일관성 유지)
  - SecurityConfig permit 순서: `/api/v1/ai/tag-recommend`(공개) → `/api/v1/ai/**`(인증) 순서 필수
