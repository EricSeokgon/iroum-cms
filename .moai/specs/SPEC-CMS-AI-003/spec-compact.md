# SPEC-CMS-AI-003 (Compact) — RAG 질의응답

> 압축 요약본: 요구사항(EARS)·수용 기준(GWT)·수정 파일·제외 항목만 추출. 전체 명세는 spec.md 참조.

## 기능 요구사항 (REQ-RAG-* / EARS)

### RAG 자연어 질의
- **REQ-RAG-001** (Event-driven): 사용자가 `POST /api/v1/ai/rag/query`로 질문을 제출하면, 시스템은 질문을 정규화한 후 RAG 파이프라인을 개시해야 한다.
- **REQ-RAG-002** (Event-driven): 정규화된 질문이 준비되면, 시스템은 `POST /ml/v1/embed`로 384차원 임베딩 벡터를 획득해야 한다.
- **REQ-RAG-003** (Event-driven): 질문 임베딩이 획득되면, 시스템은 `policy_program.embed_vector`에 대해 pgvector cosine similarity 검색을 수행해야 한다.
- **REQ-RAG-004** (Event-driven): 상위 K개 정책 컨텍스트가 선정되면, 시스템은 `POST /ml/v1/rag`로 생성형 답변을 획득해야 한다.
- **REQ-RAG-005** (Ubiquitous): 시스템은 RAG 응답에 답변 본문, 출처 정책 목록(ID·제목·관련도), `degraded` 플래그를 포함해야 한다.

### 하이브리드 검색 재랭킹
- **REQ-RAG-006** (Ubiquitous): 시스템은 pgvector cosine 점수와 PostgreSQL FTS(tsvector) 점수를 설정 가능한 가중치로 결합해 재랭킹해야 한다.
- **REQ-RAG-007** (Ubiquitous): 시스템은 LLM 컨텍스트 정책 수를 설정값 K(기본 5, 상한 10)로 제한해야 한다.

### 폴백
- **REQ-RAG-008** (Conditional): `ml-service` CircuitBreaker가 OPEN이거나 ML 호출이 타임아웃되면, 시스템은 FTS 단독 결과로 응답하고 `degraded=true`로 표기해야 한다.
- **REQ-RAG-009** (Conditional): 질문 임베딩 단계가 실패하면, 시스템은 pgvector 검색을 건너뛰고 FTS 단독 결과로 응답하며 `degraded=true`로 표기해야 한다.
- **REQ-RAG-010** (Unwanted): ML 장애로 폴백이 발생한 경우, 시스템은 503을 반환해서는 안 되며 FTS 결과를 200으로 반환해야 한다.

### 캐싱
- **REQ-RAG-011** (State-driven): 동일 질문 해시(SHA-256)의 유효 캐시 항목이 `ragQueryCache`(TTL 기본 15분)에 존재하면, 시스템은 ML 미호출로 캐시 응답을 즉시 반환해야 한다.
- **REQ-RAG-012** (Unwanted): 응답이 `degraded=true`이면, 시스템은 해당 응답을 `ragQueryCache`에 저장해서는 안 된다.

### 피드백 루프
- **REQ-RAG-013** (Event-driven): 사용자가 `POST /api/v1/ai/rag/feedback`로 만족도(HELPFUL/UNHELPFUL)를 제출하면, 시스템은 대응 `ai_rag_query_log` 행의 `feedback`·`feedback_at`을 갱신해야 한다.
- **REQ-RAG-014** (Event-driven): RAG 질의가 완료되면, 시스템은 `@Async("aiLogExecutor")`로 `ai_rag_query_log`를 적재하되 적재 실패가 사용자 응답을 차단해서는 안 된다.

### 품질 모니터링
- **REQ-RAG-015** (Event-driven): 관리자가 `GET /api/v1/admin/ai/rag/metrics`를 호출하면, 시스템은 만족도 비율·캐시 히트율·평균 응답시간·degraded 비율·시계열을 기간 필터와 함께 반환해야 한다.
- **REQ-RAG-016** (Unwanted): 요청자가 ROLE=ADMIN이 아니면, 시스템은 RAG 메트릭 응답 본문을 제공해서는 안 된다(403).

### 보안
- **REQ-RAG-017** (Unwanted): ML 서비스로 전송 시, 시스템은 `company_id`·사용자 식별정보·세션 평문을 페이로드에 포함해서는 안 되며 질문 텍스트·검색 컨텍스트만 전송해야 한다.
- **REQ-RAG-018** (Ubiquitous): 시스템은 `session_ref`·`question_hash`에 SHA-256 해시(`IpHashUtil` 재사용)만 저장해야 한다.
- **REQ-RAG-019** (Event-driven): 관리자 RAG 메트릭 API가 호출되면, 시스템은 SPEC-CMS-005 audit_log AOP로 기록을 남겨야 한다.

### 프론트엔드
- **REQ-RAG-020** (Ubiquitous): 시민 SPA `PolicyRagView.vue`는 질문 입력·답변·출처 목록·피드백 버튼을 i18n(ko/en)으로 제공해야 한다.
- **REQ-RAG-021** (Ubiquitous): 관리자 SPA `RagMetrics.vue`는 만족도·캐시 히트율·응답시간·degraded 시계열 차트를 제공해야 한다.

## 수용 기준 (Given/When/Then 요약)

- **AC-RAG-001**: Given 임베딩된 정책 + Mock 정상 / When `POST /rag/query` / Then 200, answer+sources+degraded=false, K≤상한, 로그 적재
- **AC-RAG-002**: Given CircuitBreaker OPEN / When 질의 / Then 200(503 아님), degraded=true, FTS 결과, 캐시 미저장
- **AC-RAG-003**: Given 캐시 유효 / When 동일 질문 / Then 200 cached=true, ML 미호출, 동일 본문
- **AC-RAG-004**: Given queryRef 존재 / When `POST /rag/feedback` HELPFUL / Then feedback·feedback_at 갱신, 재제출 멱등, 잘못된 값 400
- **AC-RAG-005**: Given 페이로드 스파이 / When 회원 질의 / Then ML 페이로드에 질문만, 식별자 미포함, 해시만 저장
- **AC-RAG-006**: Given 혼합 로그 / When ADMIN 메트릭 조회 / Then 200 지표+시계열, audit_log 1건, 비ADMIN 403
- **AC-RAG-007**: Given embed 실패 / When 질의 / Then 200, FTS 폴백, degraded=true, 캐시 미저장
- **AC-RAG-008**: Given 검색 0건 / When 질의 / Then 200, 빈 sources, 안내 메시지(환각 미생성), 에러 미발생
- **AC-RAG-009**: 빈/1000자 초과 질문 → 400, 정상 → 200
- **AC-RAG-010**: 시민/관리자 화면 i18n ko/en 렌더링, degraded 안내 표시

## 수정/생성 파일 목록

### 신규 (backend)
- `domain/ai/rag/controller/RagQueryController.java`, `RagAdminController.java`
- `domain/ai/rag/service/RagQueryService(+Impl).java`, `RagMetricsService(+Impl).java`
- `domain/ai/rag/repository/RagQueryLogRepository.java`, `PolicyEmbeddingRepository.java` (+ Mapper XML)
- `domain/ai/rag/dto/*`, `domain/ai/rag/entity/AiRagQueryLog.java`
- `infra/ml/dto/EmbedRequest/EmbedResponse/RagRequest/RagResponse/RagContextItem.java`
- `db/migration/V33__ai_rag_query_log_and_policy_embedding.sql` (단일)
- `frontend/public/src/views/ai/PolicyRagView.vue` (+ i18n), `frontend/admin/src/views/ai/RagMetrics.vue`
- test: `RagQueryServiceTest`, `RagQueryControllerIT`, `RagAdminControllerIT`

### 수정 (확장만, 재작성 금지)
- `infra/ml/MlServiceClient.java` (+`embed`/`rag`), `MlServiceClientImpl.java`, `MockMlServiceClient.java`
- `config/CacheConfig.java` (+`ragQueryCache`)
- `docs/ai-ml-service-openapi.yaml` (+`/ml/v1/embed`·`/ml/v1/rag`)
- SPEC-CMS-002 비회원 공개 화이트리스트 (`/api/v1/ai/rag/query`·`/feedback`)
- public/admin SPA router (라우트 추가)

## Exclusions (What NOT to Build)

1. 새 ML 모델 학습/임베딩/LLM 서빙 코드 작성 금지 — `MlServiceClient` 계약 + `MockMlServiceClient`만.
2. AI-001 인프라(`MlServiceClientImpl`/`AiPredictionLogService`/`IpHashUtil`/`CacheConfig`/`AsyncConfig`/Resilience4j `ml-service`) 재작성 금지 — 확장만.
3. SPEC-CMS-006 `SearchService` FTS 알고리즘 수정 금지 — 읽기 전용 호출.
4. 신규 인증/세션 메커니즘 구축 금지 — SPEC-CMS-002 재사용.
5. 별도 벡터 DB(Milvus 등) 인프라 도입 금지 — PostgreSQL `pgvector`만.
6. 다중 마이그레이션 금지 — V33 단일.
7. `company_id`·사용자 식별정보 ML 전송 금지 — 질문 텍스트만.
