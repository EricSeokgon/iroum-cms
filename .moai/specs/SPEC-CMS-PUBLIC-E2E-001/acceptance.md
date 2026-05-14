# Acceptance Criteria: SPEC-CMS-PUBLIC-E2E-001

**Version**: 0.1.0
**Status**: Draft
**Created**: 2026-05-14
**Updated**: 2026-05-14
**Format**: Given / When / Then

본 문서는 SPEC-CMS-PUBLIC-E2E-001의 인수 시나리오를 정의한다. 각 시나리오는 해당 REQ-ID에 매핑되며, Playwright spec 파일의 테스트 케이스로 1:1 또는 1:N 변환된다.

---

## 1. 핵심 사용자 여정 시나리오 (Core User Journeys)

### Scenario 1: 홈 영웅 검색 — `/` → `/search`

**REQ 매핑**: REQ-E2E-004, REQ-E2E-002
**Spec 파일**: `tests/e2e/home.spec.ts`

**Given**:
- 사용자가 홈 페이지(`/`)에 접속함
- 페이지가 완전히 로드되어 `data-testid="home-hero"`가 visible 상태임
- localStorage가 비어있음

**When**:
- `data-testid="home-search-input"` 셀렉터의 input에 `"청년 창업"` 입력
- `data-testid="home-search-form"` 폼을 submit (Enter 키 또는 submit 버튼 클릭)

**Then**:
- 페이지 URL이 `/search?q=%EC%B2%AD%EB%85%84+%EC%B0%BD%EC%97%85` 또는 `/search?q=청년+창업` 으로 변경됨
- `data-testid="search-result-list"`가 visible 상태로 렌더링됨
- 검색 결과 영역에 검색어가 표시되거나 (`role="status"` 영역에 `"청년 창업"` 텍스트 포함)

---

### Scenario 2: 공지 카테고리 필터 — `/notices`

**REQ 매핑**: REQ-E2E-005, REQ-E2E-002
**Spec 파일**: `tests/e2e/notices.spec.ts`

**Given**:
- 사용자가 `/notices`에 접속함
- `select#notice-category` 셀렉터의 select가 visible이고 옵션 목록이 로드됨
- 페이지에 `data-testid="notice-list"`가 렌더링됨

**When**:
- `select#notice-category`에서 `"이벤트"` 옵션 선택 (`page.selectOption('select#notice-category', 'EVENT')`)

**Then**:
- 페이지 URL에 `?categoryCode=EVENT` 쿼리 파라미터가 추가됨
- `data-testid="notice-list"`가 갱신됨 (로딩 인디케이터 → 새 목록)
- 표시된 모든 공지 항목이 "이벤트" 카테고리 라벨을 보유 (구조적 셀렉터 기반 검증)

---

### Scenario 3: FAQ 키보드 펼치기 — `/faqs`

**REQ 매핑**: REQ-E2E-007, KWCAG 2.2 AA
**Spec 파일**: `tests/e2e/faq.spec.ts`

**Given**:
- 사용자가 `/faqs`에 접속함
- `data-testid="faq-list"`가 렌더링되고 최소 1개 FAQ 항목 존재
- 첫 번째 FAQ 헤더 버튼(`data-testid="faq-header-0"`)이 `aria-expanded="false"` 상태
- `Tab` 키를 반복해서 눌러 첫 번째 FAQ 헤더 버튼에 포커스함

**When**:
- 사용자가 `Enter` 키를 누름

**Then**:
- 첫 번째 FAQ 헤더의 `aria-expanded` 속성이 `"true"`로 변경됨
- `aria-controls`로 연결된 FAQ 패널(`data-testid="faq-panel-{id}"`)이 visible 상태가 됨
- 다시 `Space` 키를 누르면 `aria-expanded="false"`로 복귀하고 패널이 hidden 됨

---

### Scenario 4: 검색 탭 전환 — `/search`

**REQ 매핑**: REQ-E2E-008, REQ-E2E-002
**Spec 파일**: `tests/e2e/search.spec.ts`

**Given**:
- 사용자가 `/search?q=지원`에 접속함
- `role="tablist"` 컨테이너가 6개 탭(ALL/POST/FAQ/QNA/POLICY/SAFETY) 렌더링
- 기본 활성 탭이 ALL (`aria-selected="true"`)임
- `data-testid="search-result-list"`가 ALL 타입 결과로 렌더링됨

**When**:
- "공지" 라벨 또는 `data-type="POST"` 속성을 가진 탭(`role="tab"`) 클릭

**Then**:
- 페이지 URL이 `/search?q=지원&type=POST` 로 변경됨
- 이전 ALL 탭은 `aria-selected="false"`, 클릭한 POST 탭은 `aria-selected="true"`
- `data-testid="search-result-list"`가 POST 타입 결과로 갱신됨
- (선택) `data-testid="search-empty-tip"`이 표시되거나 결과 카운트가 변경됨

---

### Scenario 5: 정책 매칭 폼 제출 — `/policies/match`

**REQ 매핑**: REQ-E2E-009
**Spec 파일**: `tests/e2e/policy-match.spec.ts`

**Given**:
- 사용자가 `/policies/match`에 접속함
- PolicyMatchForm 컴포넌트가 렌더링되고 5개 필드(`industry`, `capitalAmount`, `revenueAmount`, `employeeCount`, `region`)가 visible

**When**:
- 사용자가 다음 값으로 폼을 채움:
  - 업종(`industry`): "제조업"
  - 자본금(`capitalAmount`): "100000000"
  - 매출(`revenueAmount`): "500000000"
  - 직원수(`employeeCount`): "10"
  - 지역(`region`): "서울"
- 폼 제출 버튼 클릭 (`button[type="submit"]` 또는 `data-testid="policy-match-submit"`)

**Then**:
- `POST /policies/match` 요청이 발생함 (`page.waitForRequest()` 또는 network listener로 단언)
- 요청 body에 5개 필드 값이 포함됨
- 응답 수신 후 `data-testid="policy-match-results"` 영역에 결과 카드(PolicyCard) 렌더링
- 각 카드에 `match-score` 및 `match-reason` 표시

---

## 2. 인증 및 라우팅 시나리오 (Authentication & Routing)

### Scenario 6: 인증 필요 라우트 리다이렉트 — `/qnas/new`

**REQ 매핑**: REQ-E2E-010
**Spec 파일**: `tests/e2e/notices.spec.ts` 또는 `tests/e2e/a11y.spec.ts` (현재는 별도 auth.spec.ts 도입 검토)

**Given**:
- `localStorage`에 `public.accessToken` 키가 존재하지 않음 (`clearAuth(page)` 호출 후)
- `QnaCreateView`의 라우트 메타가 `requiresAuth: true`임

**When**:
- 사용자가 브라우저로 `/qnas/new`에 직접 접근 (`page.goto('/qnas/new')`)

**Then**:
- 페이지 URL이 `/login?redirect=%2Fqnas%2Fnew` 로 변경됨
- `LoginView` 컴포넌트가 렌더링됨 (Phase 0 스텁이라도 URL 매칭만 확인)
- (선택) `redirect` 쿼리 파라미터가 정확히 URL-encoded `/qnas/new`임을 단언

---

### Scenario 7: 404 에러 페이지 — 임의 경로

**REQ 매핑**: REQ-E2E-011
**Spec 파일**: `tests/e2e/error-pages.spec.ts`

**Given**:
- 사용자가 등록되지 않은 경로 `/non-existent-path-xyz`에 직접 접근

**When**:
- 페이지가 로드됨

**Then**:
- Vue Router `:pathMatch(.*)` catch-all 라우트가 매칭되어 `NotFoundView`가 렌더링됨
- 페이지 내에 "페이지를 찾을 수 없습니다" 또는 i18n 키(`error.notFound.title`)에 해당하는 텍스트 표시
- `noLayout: true` 메타에 의해 PublicLayout이 사용되지 않음 (`role="banner"` 또는 헤더 네비게이션 부재 확인)
- 추가 검증: `/error/403` → ForbiddenView, `/error/500` → ServerErrorView, `/maintenance` → MaintenanceView

---

## 3. 접근성 시나리오 (KWCAG 2.2 AA — P0 Mandatory)

### Scenario 8: 스킵 네비게이션 — 모든 페이지

**REQ 매핑**: REQ-E2E-012
**Spec 파일**: `tests/e2e/a11y.spec.ts`

**Given**:
- 사용자가 홈 페이지(`/`)에 접속함
- 페이지가 완전히 로드되어 PublicLayout이 렌더링됨

**When**:
- 사용자가 **첫 `Tab` 키**를 누름

**Then**:
- 스킵 네비게이션 링크(`a[href="#main-content"]` 또는 i18n 키 `common.skipNav`)가 포커스됨
- 스킵 네비게이션 링크가 visible 상태로 전환됨 (`:focus-visible` 가시화)

**When (계속)**:
- 사용자가 포커스된 스킵 네비게이션에서 `Enter` 키를 누름

**Then**:
- 포커스가 `role="main"` 또는 `id="main-content"` 영역으로 이동함 (`page.evaluate('document.activeElement.id')` 단언)
- 페이지 스크롤이 main 영역 상단으로 점프함

**적용 페이지**: `/`, `/notices`, `/faqs`, `/search?q=지원`, `/policies/match` (최소 5개 페이지)

---

### Scenario 9: 폼 에러 ARIA — `/qnas/new`

**REQ 매핑**: REQ-E2E-013
**Spec 파일**: `tests/e2e/a11y.spec.ts`

**Given**:
- `loginAs(page, { token: 'test-token-xyz' })` 호출로 `public.accessToken` 주입
- 사용자가 `/qnas/new`에 접속하고 `QnaCreateView`가 렌더링됨
- `data-testid="qna-create-form"`이 visible

**When**:
- 사용자가 title 입력(`data-testid="qna-title"`)을 **비워두고** content와 함께 제출 시도

**Then**:
- title input 요소가 `aria-invalid="true"`로 전환됨
- title input의 `aria-describedby` 속성이 에러 메시지 요소의 ID를 참조함
- 참조된 에러 메시지 요소가 visible 상태이며 "제목을 입력해 주세요" 등의 메시지를 포함
- (정리) 테스트 종료 시 `clearAuth(page)` 호출 — REQ-E2E-014 준수

---

## 4. 엣지 케이스 (Edge Cases)

### Edge Case 1: 공지 목록 빈 상태

**Given**: `/notices?keyword=zzzzzzz-no-match` 접속 (매칭되는 공지 없음)
**Then**: `data-testid="notice-list"` 대신 `data-testid="empty-state"` 또는 EmptyState 컴포넌트 표시. "검색 결과가 없습니다" 메시지 노출.

### Edge Case 2: 검색 빈 쿼리

**Given**: `/search` 직접 접속 (q 파라미터 없음)
**Then**: 검색 입력 필드에 자동 포커스 (`document.activeElement === searchInput`). 결과 목록은 표시되지 않거나 빈 안내 메시지 표시.

### Edge Case 3: 정책 매칭 401 응답

**Given**: `/policies/match` 라우트는 `requiresAuth: false`. `POST /policies/match`가 401 반환
**Then**: 로그인 리다이렉트가 발생하지 **않음** (requiresAuth=false). 결과 카드 영역에 빈 상태 또는 에러 안내 표시. URL은 `/policies/match` 유지.

### Edge Case 4: 공지 로딩 네트워크 오류

**Given**: 백엔드 응답 지연 또는 503 (테스트에서 `page.route()` 가로채기로 시뮬레이션)
**Then**: `data-testid="home-notices-error"` 또는 ErrorState 컴포넌트 표시. 재시도 버튼(`role="button"` + 적절한 aria-label) 노출 및 클릭 가능.

### Edge Case 5: FAQ 다중 항목 Tab 순서

**Given**: `/faqs`에 3개 이상의 FAQ 항목 존재
**Then**: Tab 키를 연속으로 누르면 헤더 1 → 헤더 2 → 헤더 3 순서로 포커스 이동. 패널 내부 콘텐츠는 펼쳐진 경우에만 포커스 순서에 포함.

### Edge Case 6: 검색 페이지네이션 + 탭 전환 상호작용

**Given**: `/search?q=지원&type=POST&page=2` 접속
**When**: FAQ 탭 클릭
**Then**: URL이 `/search?q=지원&type=FAQ` 로 변경 (page 파라미터 리셋). 결과 목록 1페이지로 복귀.

---

## 5. CI 품질 게이트 (CI Quality Gate)

### Gate 1: E2E 테스트 통과

**조건**: GitHub Actions `frontend-e2e` job이 다음 모두 만족
- Chromium에서 9개 spec 파일 전체 `0 failed`
- `retries: 2` 환경에서도 통과 (flaky 0%)
- Job 실행 시간이 합리적 (Phase 1 기준 baseline 수립 후 회귀 모니터링)

### Gate 2: 아티팩트 업로드

**조건**: `actions/upload-artifact@v4` 사용으로 다음 산출물 업로드 성공
- `frontend/public/playwright-report/` 디렉토리 전체 (HTML 리포트)
- 14일 보관 (`retention-days: 14`)
- `if: always()` 조건으로 실패 시에도 업로드 (디버깅용)

### Gate 3: 의존성 체크

**조건**: `frontend-e2e` job이 `frontend-test` 성공 후에만 실행
- `needs: [frontend-test]` 필수
- 단위 테스트 실패 시 E2E 실행 안 됨 (조기 실패 원칙)

### Gate 4: 백엔드 서비스 가용성

**조건**: Docker 서비스 컨테이너 또는 mock 서버가 `localhost:8080`에서 응답
- 헬스체크 통과 (`options: --health-cmd "curl -f http://localhost:8080/actuator/health"`)
- E2E 실행 전 백엔드 ready 대기

### Gate 5: 셀렉터 규약 준수 (REQ-E2E-002)

**조건**: 모든 spec 파일에서 CSS 클래스 기반 셀렉터(`.btn-primary`, `.notice-card` 등) 사용 0건
- 리뷰 시 grep 확인: `grep -rE "\\.[\\w-]+'\\s*\\)" frontend/public/tests/e2e/` → 매칭 시 차단
- `data-testid`, ARIA, `role`, 의미론적 HTML만 허용

### Gate 6: 사이드이펙트 차단 (REQ-E2E-014)

**조건**: 백엔드 데이터 쓰기 API 호출 0건 (또는 격리/롤백 전략 명시)
- 코드 리뷰 시 `POST /qnas`, `DELETE /*`, `PUT /*` 호출 검토
- 사용된 경우 테스트 전용 계정 또는 afterEach cleanup hook 확인

---

## 6. Definition of Done (DoD)

본 SPEC이 완료(Tested → Implemented → Verified)되려면 다음을 모두 만족해야 한다:

- [ ] Task 1 ~ Task 10 모두 완료
- [ ] 9개 spec 파일 모두 Chromium에서 0 failed
- [ ] 9개 인수 시나리오 + 6개 엣지 케이스 모두 spec 파일에 구현됨
- [ ] REQ-E2E-001 ~ REQ-E2E-015 모두 검증되거나 명시적으로 해당 spec에서 다뤄짐
- [ ] CI `frontend-e2e` job이 main 브랜치에서 그린 빌드
- [ ] `playwright-report/` 아티팩트 다운로드 후 HTML 리포트 정상 열람
- [ ] REQ-E2E-014 위반 0건 (백엔드 쓰기 사이드이펙트 부재)
- [ ] REQ-E2E-002 위반 0건 (CSS 클래스 셀렉터 부재)
- [ ] @MX:TODO 태그 0건 (모두 @MX:NOTE/ANCHOR로 전환됨)
- [ ] TRUST 5 게이트 통과 (Tested/Readable/Unified/Secured/Trackable)
- [ ] `frontend/public/README.md`에 E2E 실행 방법 문서화 (sync 단계)
- [ ] `.moai/project/tech.md`의 Playwright 상태 "계획됨" → "도입 완료" 갱신 (sync 단계)
