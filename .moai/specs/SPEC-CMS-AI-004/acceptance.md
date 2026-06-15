# SPEC-CMS-AI-004 수용 기준 (acceptance.md)

연관 SPEC: `spec.md` / `plan.md` (SPEC-CMS-AI-004)
형식: Given-When-Then. 모든 시나리오는 자동 테스트로 검증 가능해야 한다.

---

## 1. ML 클라이언트 확장 (REQ-AI-TAG-001~005)

### AC-AI-TAG-001 — 인터페이스 확장 무파손
- Given: 기존 `MlServiceClient` 구현체와 호출부가 존재한다
- When: `tagRecommendation` 메서드를 인터페이스에 추가하고 빌드한다
- Then: 컴파일 성공, 기존 `predictGrowthStage`/`policyMatch`/`embed`/`rag` 호출부·테스트가 무수정 통과한다

### AC-AI-TAG-002 — ML 호출 계약
- Given: ML 서비스가 정상 응답을 반환하도록 모킹된다
- When: `MlServiceClientImpl.tagRecommendation`을 호출한다
- Then: `POST /ml/v1/tag-recommend`로 `content`/`existing_tags`/`top_k`(snake_case)를 전송하고, `recommended_tags`/`scores`/`model_version`를 역직렬화한다. CircuitBreaker `ml-service`·3초 타임아웃이 적용된다

### AC-AI-TAG-003 — 장애 시 예외 위임
- Given: ML 서비스가 connection refused / 5xx / 타임아웃 / CircuitBreaker OPEN 상태이다
- When: `tagRecommendation`을 호출한다
- Then: `MlServiceException`이 발생하고 호출부 폴백으로 위임되며, 예외가 사용자에게 전파되지 않는다

### AC-AI-TAG-004 — 모킹 결정론
- Given: 모킹 프로파일이 활성화된다
- When: 20자 이상 본문으로 `MockMlServiceClient.tagRecommendation`을 호출한다
- Then: 고정된 추천 태그 집합을 반환하여 ML 부재 시에도 Spring Boot 레이어 통합 테스트가 통과한다

### AC-AI-TAG-005 — OpenAPI 계약 일치
- Given: `docs/ai-ml-service-openapi.yaml`을 검사한다
- When: `POST /ml/v1/tag-recommend` 정의를 확인한다
- Then: `TagRecommendRequest`/`TagRecommendResponse` 스키마가 존재하고 필드명·타입이 DTO와 정확히 일치한다

---

## 2. REST API (REQ-AI-TAG-006~009)

### AC-AI-TAG-006 — 관리자 게시글 추천
- Given: 인증된 관리자(ROLE=ADMIN)가 게시글을 작성 중이다
- When: 25자 본문으로 `POST /api/v1/ai/tag-recommend`를 호출한다
- Then: HTTP 200, `recommendedTags`(camelCase) 배열 길이 ≤ 5, `scores` 맵이 포함된다

### AC-AI-TAG-007 — 시민 Q&A 비인증 추천
- Given: 비로그인 시민이 Q&A를 작성 중이다
- When: 본문 텍스트만으로 `POST /api/v1/ai/tag-recommend`를 호출한다
- Then: HTTP 200으로 추천이 반환되고, 요청에 작성자 식별정보(PII)가 포함되지 않으며, SecurityConfig 공개 화이트리스트에 경로가 등록되어 있다

### AC-AI-TAG-008 — 최소 길이 가드
- Given: 작성자가 본문을 입력 중이다
- When: 19자 본문으로 추천을 요청한다 / 20자 본문으로 추천을 요청한다
- Then: 19자는 ML 미호출 + 빈 배열(HTTP 200), 20자는 ML 호출이 발생한다

### AC-AI-TAG-009 — 그레이스풀 폴백
- Given: ML 서비스가 사용 불가하다
- When: 유효한 본문으로 추천을 요청한다
- Then: HTTP 200 + 빈 추천 배열을 반환하고, 사용자에게 어떤 오류도 노출되지 않으며, 서버 로그에만 경고가 기록된다

---

## 3. 캐시·로깅 (REQ-AI-TAG-010~013)

### AC-AI-TAG-010 — 캐시 적중
- Given: 캐시가 비어 있다
- When: 동일 본문으로 추천을 2회(30분 이내) 요청한다
- Then: ML 서비스 호출은 1회만 발생한다(모킹 호출 카운트 = 1), 2번째는 `tagRecommendationCache`에서 반환된다

### AC-AI-TAG-011 — 추천 이벤트 비동기 적재
- Given: 추천이 사용자에게 제시된다
- When: 추천 응답이 반환된다
- Then: `ai_tag_recommendation_log`에 `event_type='SUGGESTED'` 행이 비동기(`aiLogExecutor`)로 적재되고, 추천 응답 지연에 로깅이 영향을 주지 않는다

### AC-AI-TAG-012 — 채택/거부 피드백 적재
- Given: 추천 태그가 표시된 상태이다
- When: 사용자가 추천 태그를 채택 / 거부한다
- Then: `POST /api/v1/ai/tag-recommend/feedback` 호출로 `event_type='ACCEPTED'` 또는 `'REJECTED'` 행이 `tag_value`와 함께 적재된다

### AC-AI-TAG-013 — PII 미저장
- Given: 추천·피드백 로그가 적재된다
- When: `ai_tag_recommendation_log` 행을 검사한다
- Then: 평문 작성자 식별자·PII 컬럼이 존재하지 않고, `session_ref`는 SHA-256 해시(길이 제약 통과)로만 저장된다

---

## 4. 태그 저장 (REQ-AI-TAG-014~015)

### AC-AI-TAG-014 — additive 컬럼 무파손
- Given: V54 적용 전 `bbs_post`·`qna`에 기존 행이 존재한다
- When: 마이그레이션 V54를 적용한다 (Testcontainers)
- Then: 기존 행의 `tags`가 `'{}'`로 채워지고, 기존 게시글·Q&A INSERT/SELECT 동작이 무영향이다

### AC-AI-TAG-015 — 태그 영속화·표시
- Given: 작성자가 태그를 선택했다
- When: 게시글 또는 Q&A를 저장하고 재조회한다
- Then: 선택된 태그가 `tags` 컬럼에 보존되고, 목록·상세 응답 DTO에 `tags`가 포함되며, 화면에 읽기 전용 칩으로 노출된다

---

## 5. 비기능 (REQ-AI-TAG-NFR-001~003)

### AC-AI-TAG-NFR-001 — 성능
- Given: 프론트 컴포저블이 활성화된다
- When: 작성자가 타이핑한다
- Then: 타이핑 정지 후 500ms에 추천이 트리거되고, ML 호출은 3초 타임아웃이며, 캐시 적중 응답은 50ms 이내(목표)이다

### AC-AI-TAG-NFR-002 — 저장 경로 분리
- Given: 추천 기능이 장애 상태이다
- When: 게시글·Q&A를 저장한다
- Then: 저장 트랜잭션이 정상 완료된다(추천 호출과 저장이 분리되어 추천 장애가 저장에 영향 없음)

### AC-AI-TAG-NFR-003 — 비회원 보안
- Given: 비회원 Q&A 추천 요청이 발생한다
- When: 요청 페이로드를 검사한다
- Then: 본문 텍스트 외 어떠한 사용자 식별정보도 ML 서비스로 전송되지 않는다

---

## 6. 엣지 케이스

| # | 시나리오 | 기대 동작 |
|---|---------|----------|
| E1 | ML 서비스 다운 중 추천 요청 | HTTP 200 + 빈 배열, 오류 미노출 (AC-009) |
| E2 | 본문 19자 (경계) | ML 미호출 + 빈 배열 (AC-008) |
| E3 | 본문 20자 (경계) | ML 호출 발생 (AC-008) |
| E4 | 빈 본문("") 추천 요청 | ML 미호출 + 빈 배열 |
| E5 | 동일 본문 30분 이내 반복 | 캐시 반환, ML 1회만 (AC-010) |
| E6 | 추천 태그와 기존 선택 태그 중복 | 응답에서 `existing_tags` 제외, 중복 미표시 |
| E7 | 기존 태그 5개 이미 선택 + 추천 요청 | 추천은 반환하되 사용자가 자유 선택 (강제 없음) |
| E8 | 같은 태그 중복 클릭(채택 후 재채택) | 태그 배열에 중복 추가 안 됨 (idempotent) |
| E9 | 비회원 Q&A 추천 + ML 다운 | HTTP 200 + 빈 배열, 비인증 흐름 정상 |
| E10 | 매우 긴 본문(수천 자) | 정상 처리, 타임아웃 내 응답 또는 빈 배열 폴백 |
| E11 | V54 적용 후 기존 게시글 저장(태그 미전송) | `tags='{}'` 유지, 저장 성공 (AC-014) |

---

## 7. Definition of Done

- [ ] REQ-AI-TAG-001~015 전 항목의 수용 기준(AC) 자동 테스트 통과
- [ ] NFR-001~003 검증 완료
- [ ] 엣지 케이스 E1~E11 테스트 커버
- [ ] 기존 게시글·Q&A 엔드포인트·테스트 무파손 (회귀 검증)
- [ ] 기존 `MlServiceClient` 호출부 회귀 없음
- [ ] 코드 커버리지 ≥ 85% (TRUST 5 Tested)
- [ ] 코드 주석·@MX 태그 한국어(ko)
- [ ] `MockMlServiceClient`로 ML 부재 환경 독립 검증 통과
- [ ] 마이그레이션 V54 Testcontainers 적용 검증
- [ ] OpenAPI 계약 `POST /ml/v1/tag-recommend` 추가 및 DTO 일치
- [ ] 그레이스풀 폴백: ML 다운 시 사용자 오류 미노출 통합 테스트 통과
