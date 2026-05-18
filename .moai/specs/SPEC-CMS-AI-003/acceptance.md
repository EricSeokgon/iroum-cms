# SPEC-CMS-AI-003 수용 기준 (acceptance.md)

> 모든 시나리오는 `MockMlServiceClient` 기반으로 실제 ML 모델 부재 시에도 검증 가능해야 한다(SER-RAG-05).
> Given/When/Then 형식. 추적 prefix: AC-RAG-*.

## AC-RAG-001 — 정상 RAG 질의 → 답변 + 출처 반환

- **추적**: REQ-RAG-001~007, REQ-RAG-005
- **Given**: `policy_program`에 `embed_vector`가 채워진 정책 3건 이상이 존재하고, `MockMlServiceClient`가 `/ml/v1/embed`(384차원 벡터)와 `/ml/v1/rag`(답변+quality_score)에 결정적 응답을 반환하도록 설정됨
- **When**: 비회원이 `POST /api/v1/ai/rag/query`로 자연어 질문("청년 창업 지원 정책 알려줘")을 제출
- **Then**:
  - HTTP 200
  - 응답에 `answer`(비어있지 않은 문자열), `sources`(정책 ID·title·relevance 포함 1건 이상), `degraded=false`, `cached=false`, `queryRef` 포함
  - `sources`의 정책 수가 K 상한(기본 5, 최대 10) 이하 (REQ-RAG-007)
  - `ai_rag_query_log`에 1행 적재 (`question_hash` 해시값, `degraded=false`)

## AC-RAG-002 — CircuitBreaker OPEN → FTS 폴백 + degraded=true

- **추적**: REQ-RAG-008, REQ-RAG-010
- **Given**: Resilience4j `ml-service` CircuitBreaker가 OPEN 상태(또는 ML 호출이 강제 타임아웃)이고, SPEC-CMS-006 FTS `SearchService`가 정책 검색 결과를 반환할 수 있음
- **When**: 비회원이 `POST /api/v1/ai/rag/query` 질의 제출
- **Then**:
  - HTTP **200** (503 아님 — REQ-RAG-010)
  - `degraded=true`
  - `sources`는 FTS 검색 결과로 채워짐, `answer`는 생성형 답변 없이 FTS 결과 안내 메시지
  - `ai_rag_query_log`에 `degraded=true`로 적재
  - `ragQueryCache`에 해당 응답이 저장되지 않음 (REQ-RAG-012)

## AC-RAG-003 — 동일 질문 캐시 히트

- **추적**: REQ-RAG-011
- **Given**: 동일 질문에 대한 정상(`degraded=false`) 응답이 `ragQueryCache`(TTL 15분)에 유효하게 존재
- **When**: 동일 질문 텍스트(정규화 후 동일 SHA-256 해시)로 `POST /api/v1/ai/rag/query` 재요청
- **Then**:
  - HTTP 200, `cached=true`
  - `MlServiceClient.embed`·`rag`가 **호출되지 않음** (mock 호출 카운트 0 증가)
  - 응답 본문이 최초 응답과 동일(answer·sources)
  - PER-RAG-01 충족 (p95 < 100ms 측정 가능 형태)

## AC-RAG-004 — 피드백 HELPFUL/UNHELPFUL 적재

- **추적**: REQ-RAG-013, SER-RAG-03
- **Given**: AC-RAG-001로 생성된 `queryRef`에 대응하는 `ai_rag_query_log` 행이 존재(`feedback=NULL`)
- **When**: 비회원이 `POST /api/v1/ai/rag/feedback`에 `{ queryRef, feedback: "HELPFUL" }` 제출
- **Then**:
  - HTTP 200
  - 해당 `ai_rag_query_log` 행의 `feedback="HELPFUL"`, `feedback_at`이 NOT NULL로 갱신
  - 동일 `queryRef`로 `UNHELPFUL` 재제출 시 새 행 생성 없이 마지막 값(`UNHELPFUL`)으로 갱신 (SER-RAG-03 멱등)
  - 잘못된 feedback 값(예: `MAYBE`) 제출 시 HTTP 400 (SER-RAG-02)

## AC-RAG-005 — PII 미전송 검증

- **추적**: REQ-RAG-017, REQ-RAG-018
- **Given**: 로그인 회원이 RAG 질의를 수행하고, `MlServiceClient` 호출 페이로드를 캡처하는 테스트 스파이가 설정됨
- **When**: 회원이 `POST /api/v1/ai/rag/query` 질의 제출
- **Then**:
  - `/ml/v1/embed` 요청 페이로드에 `text`(질문)만 포함, `company_id`·회원ID·세션 평문 미포함
  - `/ml/v1/rag` 요청 페이로드에 `question`·`contexts`(정책 문서)만 포함, 사용자 식별정보 미포함
  - `ai_rag_query_log.session_ref`·`question_hash`가 평문이 아닌 SHA-256 해시(길이 1~80) — `IpHashUtil` 산식 일치
  - DB·로그 어디에도 질문 평문이 저장되지 않음

## AC-RAG-006 — 관리자 메트릭 조회

- **추적**: REQ-RAG-015, REQ-RAG-016, REQ-RAG-019
- **Given**: `ai_rag_query_log`에 HELPFUL/UNHELPFUL 피드백·캐시 히트·degraded 혼합 데이터가 적재됨
- **When**: ROLE=ADMIN 사용자가 `GET /api/v1/admin/ai/rag/metrics?period=DAY&from=...&to=...` 호출
- **Then**:
  - HTTP 200
  - 응답에 만족도 비율(HELPFUL/전체 피드백), 캐시 히트율, 평균 응답시간, degraded 비율, 시계열 배열 포함
  - SPEC-CMS-005 audit_log에 접근 기록 1건 생성 (REQ-RAG-019)
  - 동일 API를 비ADMIN(USER/비회원)이 호출 시 HTTP **403**, 본문 미제공 (REQ-RAG-016)

## AC-RAG-007 — 임베딩 실패 → FTS 폴백

- **추적**: REQ-RAG-009, REQ-RAG-010
- **Given**: `MockMlServiceClient.embed`가 예외/오류를 반환하도록 설정(임베딩 단계만 실패), `/ml/v1/rag`·FTS는 정상
- **When**: 비회원이 `POST /api/v1/ai/rag/query` 질의 제출
- **Then**:
  - HTTP 200 (503 아님)
  - pgvector 검색을 건너뛰고 FTS 단독 검색 결과로 응답
  - `degraded=true`
  - `rag` 생성 호출은 수행되지 않거나 FTS 컨텍스트 기반으로만 동작(파이프라인이 임베딩 실패에서 graceful degrade)
  - `ai_rag_query_log`에 `degraded=true` 적재, `ragQueryCache` 미저장

## AC-RAG-008 — 빈 검색 결과 처리

- **추적**: SER-RAG-04, REQ-RAG-005
- **Given**: pgvector·FTS 모두 질문에 매칭되는 정책을 0건 반환하도록 설정(`embed_vector IS NULL` 정책만 존재하거나 무관 질문)
- **When**: 비회원이 `POST /api/v1/ai/rag/query` 질의 제출
- **Then**:
  - HTTP 200
  - `sources`는 빈 배열
  - `answer`는 "관련 정책을 찾지 못함" 안내 메시지(LLM 환각 답변 미생성)
  - `ai_rag_query_log`에 `retrieved_policy_ids`가 빈 배열로 적재
  - 500/에러 미발생

## AC-RAG-009 — 입력 검증 (경계)

- **추적**: SER-RAG-02
- **Given**: RAG 질의 엔드포인트가 활성화됨
- **When/Then**:
  - 빈 질문(`""` 또는 공백만) 제출 → HTTP 400
  - 1000자 초과 질문 제출 → HTTP 400
  - 정상 길이 질문 → HTTP 200 (AC-RAG-001 경로)

## AC-RAG-010 — 프론트엔드 RAG 화면 (시민/관리자)

- **추적**: REQ-RAG-020, REQ-RAG-021
- **Given**: 시민 SPA `PolicyRagView.vue`, 관리자 SPA `RagMetrics.vue`가 배포됨
- **When**: 시민이 질문 입력 후 제출 / 관리자가 RAG 메트릭 화면 진입
- **Then**:
  - `PolicyRagView.vue`: 질문 입력창, 답변 영역, 출처 정책 목록(클릭 시 정책 상세 이동), HELPFUL/UNHELPFUL 피드백 버튼이 i18n ko/en으로 렌더링
  - `RagMetrics.vue`: 만족도 비율·캐시 히트율·평균 응답시간·degraded 비율 시계열 차트가 AI-002 `PolicyMatchMetrics.vue` 패턴으로 렌더링
  - `degraded=true` 응답 시 시민 화면에 "간소 검색 결과" 안내 표시

## Definition of Done

- [ ] AC-RAG-001 ~ AC-RAG-010 전부 통과 (MockMlServiceClient 기반 자동화)
- [ ] V33 단일 마이그레이션 적용·롤백 검증
- [ ] EARS REQ-RAG-001 ~ REQ-RAG-021 전부 1개 이상 AC로 추적됨
- [ ] PII 미전송(AC-RAG-005)·인가 경계(AC-RAG-006) 보안 기준 통과
- [ ] `## 3.3 Exclusions` 7개 항목 위반 없음
- [ ] TRUST 5: 테스트 커버리지 ≥85%, 컨벤셔널 커밋, audit_log 추적
- [ ] LSP run 게이트: 0 error / 0 type error / 0 lint error
