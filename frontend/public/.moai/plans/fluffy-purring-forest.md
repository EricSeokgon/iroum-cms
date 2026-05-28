# Plan: Public SPA `policy-match.spec.ts` E2E 3건 수정

## Context

Public SPA E2E 회귀 테스트에서 `frontend/public/tests/e2e/policy-match.spec.ts`의 3개 테스트가 실패하고 있다.

**근본 원인 (production code 변경에 따른 테스트 misalignment, production 결함 아님):**
- `PolicyMatchView.vue`가 구 엔드포인트 `policyApi.match()` (POST `/policies/match`) 대신 신규 AI 매칭 엔드포인트 `policyApi.aiMatch()` (POST `/ai/policy-match`)를 호출하도록 변경됨
- 신규 엔드포인트의 요청 body 스키마가 `AiPolicyMatchRequest` 구조로 변경됨:
  - `{ companyProfile: { ksic_code, annual_revenue, employee_count, region_code }, topK: 10 }`
  - `capitalAmount`는 `toCompanyProfile` 매퍼에서 더 이상 전송되지 않음
- 백엔드는 `/ai/policy-match`에 대해 미인증 시 **403**을 반환하고, axios 응답 인터셉터(`client.ts`)는 **403을 무조건 `/error/403`로 리다이렉트**한다 (401 분기와 달리 `requiresAuth` 메타 확인 없음)

**현재 실패:**
1. `policy-match.spec.ts:30` (S5 POST 발생 검증) — `waitForRequest` predicate가 구 URL `/policies/match`만 매칭 → TimeoutError
2. `policy-match.spec.ts:61` (S5 결과 영역 표시) — 실제 호출 `/ai/policy-match`에 mock 미등록 → 백엔드 403 → `/error/403` 리다이렉트
3. `policy-match.spec.ts:81` (E3 401 응답 redirect 없음) — `page.route` 패턴 `**/api/**/policies/match`가 신규 호출 `/ai/policy-match`를 가로채지 못함 → 실제 백엔드 403 → `/error/403` 리다이렉트

**의도된 결과:** 테스트가 production 행동(`policyApi.aiMatch()`)에 맞춰 정렬되어 `policy-match.spec.ts` 4건 모두 GREEN. production 코드는 절대 수정하지 않는다.

## 변경 대상 파일 (1개)

- `frontend/public/tests/e2e/policy-match.spec.ts`

## 변경 패턴

세 테스트 모두 동일한 두 가지 패턴을 적용한다:

1. **URL 매처 변경**: `'/policies/match'` → `'/ai/policy-match'`
   - `page.waitForRequest` predicate
   - `page.route` glob 패턴 (`**/api/**/policies/match` → `**/ai/policy-match`)
2. **Request body 검증 변경**: 평탄 객체 → `AiPolicyMatchRequest` 중첩 구조
   - 매퍼: `industry → companyProfile.ksic_code`, `revenueAmount → companyProfile.annual_revenue`, `employeeCount → companyProfile.employee_count`, `region → companyProfile.region_code`, `+ topK: 10`
   - `capitalAmount` 검증 제거 (매퍼에 존재하지 않음)

추가로 두 번째 테스트(`S5: 제출 후 결과 영역이 표시된다`)는 `**/ai/policy-match`에 200 빈 결과 mock을 등록해야 한다. 그렇지 않으면 실제 백엔드 403 응답이 `/error/403` 리다이렉트를 유발해 검증 단언(URL 유지)이 실패한다.

### 세부 변경

**Test 1 — `S5: 폼 제출 시 POST ... 요청이 발생한다` (line ~30)**
- `page.waitForRequest` predicate URL: `/policies/match` → `/ai/policy-match`
- body 단언: `{ industry, capitalAmount, revenueAmount, employeeCount, region }` (평탄)
  → `{ companyProfile: { ksic_code: '제조업', annual_revenue: 500000000, employee_count: 10, region_code: '서울' }, topK: 10 }` (`capitalAmount` 단언 삭제)

**Test 2 — `S5: 제출 후 결과 영역이 표시된다` (line ~61)**
- `page.route('**/ai/policy-match', ...)` 추가: status 200, body `{ matches: [], totalCount: 0 }` 등 빈 결과 (혹은 `policyApi.aiMatch()` 응답 타입에 맞는 최소 구조)
- 기존 단언 로직 (results 표시 OR URL 유지)은 그대로 둠

**Test 3 — `E3: requiresAuth=false 라우트 → 401 응답에서도 /login 리다이렉트 없음` (line ~81)**
- `page.route` 패턴: `**/api/**/policies/match` → `**/ai/policy-match`
- 401 fulfill 응답은 그대로 유지 (테스트 의도는 401 + `requiresAuth=false` 조합에서 `/login` 리다이렉트 없음을 검증)

## 재사용할 기존 코드 / 참조

- `frontend/public/src/api/policyApi.ts` (line 173-175): `aiMatch()` 함수 시그니처 및 `AiPolicyMatchRequest` 타입 정의
- `frontend/public/src/api/client.ts` (line 60-81): 401/403 인터셉터 분기 동작 (수정 안 함, 행동 이해 목적)
- `frontend/public/src/views/policies/PolicyMatchView.vue`: `toCompanyProfile` 매퍼 (검증 단언의 출처)
- `frontend/public/src/router/index.ts` (line 45): `/policies/match` 라우트가 `requiresAuth` 메타 없음 (E3 테스트 전제)

## 검증

1. `cd frontend/public && npx playwright test tests/e2e/policy-match.spec.ts --reporter=list`
   → 4 passed 기대
2. 추가로 변경된 endpoint가 다른 spec과 충돌하지 않는지 확인:
   - `cd frontend/public && npx playwright test --reporter=list`
   - Public E2E 전체 GREEN 확인
3. Admin E2E는 본 변경의 영향권 밖이지만, 회귀 확인용으로 함께 실행:
   - `cd frontend/admin && npx playwright test --reporter=list`
4. 전체 GREEN 확인 후 사용자에게 commit / push 승인 요청
   - 커밋 메시지(한글, Conventional Commits): `test(public-e2e): policy-match.spec.ts를 aiMatch 엔드포인트로 정렬`
   - **사용자 명시적 승인 전까지 commit / push 금지** (CLAUDE.md: "NEVER commit changes unless the user explicitly asks you to")

## Out of Scope

- production 코드 수정 (PolicyMatchView, policyApi, client.ts, router 등)
- 401/403 인터셉터 정책 재설계
- a11y.spec.ts (이미 PASS)
- 다른 SPEC, 다른 spec 파일
- `.moai/specs/` 문서 갱신 (테스트 정렬만 수행)
