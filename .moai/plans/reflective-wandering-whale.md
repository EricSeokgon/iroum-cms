# Plan: Sync & v1.7.0 릴리즈 PR 정리

## Context

c32e6c2 (`test(e2e): auth refresh mock 추가 및 URL/selector 안정성 개선`) 커밋으로 admin/public e2e GREEN 안정화 작업이 끝났고, 직전 커밋 `cfb246f`에서 CHANGELOG `[1.7.0] - 2026-05-27` 섹션이 확정되었다. 현재 `main`은 `origin/main`보다 1 커밋 앞서 있고, `[Unreleased]`는 비어 있어 **v1.7.0 릴리즈 PR을 만들기 가장 자연스러운 시점**이다.

다만 PR 전에 정리할 워킹 트리 잔여물이 있다.

**Tracked 파일 변경 (M)** — 단순 session_id 갱신
- `.moai/state/session-memo.md`
- `backend/.moai/state/session-memo.md`
- `frontend/public/.moai/state/session-memo.md`

**Untracked 파일 (??)** — 두 부류로 나뉜다.

1) **런타임/로그 산출물 (gitignore 대상)**
   - `.moai/backups/sync-20260518T124748/` — 이전 `/moai sync` 백업
   - `.moai/reports/session-*.md` (4건) — PreCompact hook 세션 로그
   - `deploy/.moai/state/` — 빈 session memo
   - `ml-service/.moai/state/` — 빈 session memo

2) **계획 산출물 (커밋 대상)** — 동일 패턴의 `frontend/.moai/plans/tingly-spinning-rose-agent-*.md`가 이미 추적되어 있음
   - `frontend/.moai/plans/drifting-swinging-stonebraker-agent-afc33f0d8ca145815.md` — SPEC-CMS-AI-001 구현 계획 (manager-tdd 착수 전 결정 4건 포함)
   - `frontend/public/.moai/plans/fluffy-purring-forest.md` — `policy-match.spec.ts` E2E 수정 계획 (c32e6c2 작업의 원본 계획서)

목표는 **v1.7.0 PR이 노이즈 없이 깨끗한 변경만 담도록** 정리한 뒤, 안전하게 GitHub PR을 생성하는 것이다. 푸시·PR 생성은 사용자가 명시적으로 승인할 때만 진행한다.

## Approach

### Step 1 — `.gitignore` 보강 (런타임 산출물 제외)

`/home/sklee/moai/iroum-cms/.gitignore`에 다음 4개 패턴을 기존 `# backend/.moai/ 런타임 산출물 (로컬 전용)` 블록 인근에 추가한다. 기존 `backend/.moai/` (245), `frontend/admin/.moai/` (256) 패턴과 동일한 의도다.

```
# 워크스페이스별 .moai 런타임 산출물 (로컬 전용)
deploy/.moai/
ml-service/.moai/

# 루트 .moai 런타임 로그/백업
.moai/backups/
.moai/reports/session-*.md
```

`!.moai/reports/.gitkeep` 라인은 디렉토리 보존이 필요하다고 판단되면 함께 추가한다 (현재 `.moai/reports/`는 추적된 `.gitkeep`이 없으므로 일단 생략).

### Step 2 — 계획 산출물 2건 커밋

기존 추적 패턴 `frontend/.moai/plans/tingly-spinning-rose-agent-*.md`와 일치하는 신규 계획 2건을 명시적으로 추가한다.

```
git add frontend/.moai/plans/drifting-swinging-stonebraker-agent-afc33f0d8ca145815.md
git add frontend/public/.moai/plans/fluffy-purring-forest.md
```

커밋 메시지 (conventional commit, 한국어):
```
docs(plans): SPEC-CMS-AI-001 / policy-match e2e 계획 산출물 추가

- SPEC-CMS-AI-001 manager-tdd 착수 전 사전 결정 4건 포함 구현 계획
- policy-match.spec.ts E2E URL/스키마 정렬 수정 계획 (c32e6c2 의 원본)
```

### Step 3 — `.gitignore` 변경 + 자동 갱신된 session-memo 커밋

```
git add .gitignore .moai/state/session-memo.md backend/.moai/state/session-memo.md frontend/public/.moai/state/session-memo.md
```

커밋 메시지:
```
chore(gitignore): 워크스페이스별 .moai 런타임 산출물 제외 패턴 추가

- deploy/.moai/, ml-service/.moai/ 추가 (backend/frontend-admin 와 동일 정책)
- 루트 .moai/backups/, .moai/reports/session-*.md 추가
- 세션 memo 라우틴 갱신 동반 커밋
```

이후 `git status`로 트리가 깨끗한지 검증한다 (M 파일과 ?? 파일이 모두 사라져야 함).

### Step 4 — E2E GREEN 사전 검증 (PR 생성 전 게이트)

PR 생성 전에 c32e6c2 변경이 실제로 GREEN인지 확인한다. 시간이 오래 걸리는 작업이므로 **사용자가 PR 생성 직전에 명시적으로 승인할 때만 실행**한다.

- Admin E2E: `cd frontend/admin && pnpm test:e2e -- tests/e2e/notices.spec.ts tests/e2e/roles.spec.ts tests/e2e/users.spec.ts`
- Public E2E: `cd frontend/public && pnpm test:e2e -- tests/e2e/policy-match.spec.ts`
- (필요 시) Backend 회귀: `cd backend && ./gradlew test`

검증 산출물이 GREEN이면 다음 단계로, RED면 PR을 보류하고 추가 수정을 진행한다.

### Step 5 — v1.7.0 릴리즈 PR 생성

브랜치 전략 결정이 필요하다. **권장안은 직접 `main` → `main` PR 없이, 신규 `release/v1.7.0` 브랜치를 만들어 PR을 올리는 방식**이다. 이유:
- 현재 `main`에 직접 푸시하면 PR 검토 단계가 없다
- 기존 `feature/spec-cms-ai-001` 같은 feature 브랜치 명명 규칙과 일관성 유지
- 머지 후 `release/v1.7.0` 브랜치는 보존하거나 자동 삭제 가능

실행 순서 (사용자 명시적 승인 후):
```
git switch -c release/v1.7.0
git push -u origin release/v1.7.0
gh pr create --base main --head release/v1.7.0 \
  --title "release: v1.7.0 — 공개 프론트엔드 완성 및 신규 API" \
  --body "<CHANGELOG [1.7.0] 섹션을 HEREDOC 으로 본문에 삽입>"
```

PR 본문은 `CHANGELOG.md`의 `## [1.7.0] - 2026-05-27` 섹션을 그대로 가져온 뒤, 끝에 Test plan 체크리스트(admin E2E / public E2E / backend test)와 `🗿 MoAI <email@mo.ai.kr>` 시그니처를 붙인다.

**대안**: 사용자가 fast-track을 원하면 `release/v1.7.0` 브랜치 없이 `git push origin main`만 수행하고 PR을 생략한다. 단, 이 경우 검토 기록이 남지 않으므로 권장하지 않는다.

## Critical Files

- `/home/sklee/moai/iroum-cms/.gitignore` — Step 1 (라인 244 근방의 `backend/.moai/` 블록 직후에 신규 패턴 추가)
- `/home/sklee/moai/iroum-cms/frontend/.moai/plans/drifting-swinging-stonebraker-agent-afc33f0d8ca145815.md` — Step 2 (그대로 add)
- `/home/sklee/moai/iroum-cms/frontend/public/.moai/plans/fluffy-purring-forest.md` — Step 2 (그대로 add)
- `/home/sklee/moai/iroum-cms/.moai/state/session-memo.md` + `backend/.moai/state/session-memo.md` + `frontend/public/.moai/state/session-memo.md` — Step 3 (자동 갱신본 그대로 커밋)
- `/home/sklee/moai/iroum-cms/CHANGELOG.md` — Step 5 PR 본문 소스 (수정하지 않음, 발췌만)

## 재사용 자원

- 기존 추적 패턴 `frontend/.moai/plans/tingly-spinning-rose-agent-*.md` → 신규 plans 커밋의 선례
- `.gitignore` 244~256 라인 `backend/.moai/` / `frontend/admin/.moai/` 블록 → deploy·ml-service 동일 패턴 참조
- conventional commit + `🗿 MoAI <email@mo.ai.kr>` 시그니처 → 직전 c32e6c2 커밋과 동일 포맷

## Verification

각 Step 종료 시 검증 명령:

| Step | 검증 |
|------|------|
| 1 | `git check-ignore -v .moai/backups/ .moai/reports/session-*.md deploy/.moai/ ml-service/.moai/` — 4건 모두 ignored 출력 |
| 2 | `git log --oneline -1` 메시지 확인 + `git show --stat HEAD` 으로 plans 2건만 포함되는지 확인 |
| 3 | `git status` 가 "nothing to commit, working tree clean" 반환 |
| 4 | admin E2E `notices/roles/users` + public E2E `policy-match` 4 spec 모두 PASS, exit code 0 |
| 5 | `gh pr view <PR번호>` 또는 `gh pr list --head release/v1.7.0` 로 PR 생성 확인, 사용자에게 PR URL 회신 |

## Out of Scope

- CHANGELOG.md 수정 — `[1.7.0] - 2026-05-27`은 cfb246f 에서 이미 확정됨
- SPEC-CMS-AI-001 본 구현 진행 — 계획서 커밋만 수행, 사전 결정 4건은 manager-tdd 착수 시점에 별도 라운드로 처리
- `.moai-backups/` (하이픈 변형) 정리 — 이미 `.gitignore` 116 라인에서 제외 중
- `git push` / `gh pr create` 의 실제 실행 — 본 plan 승인과 별개로 Step 5 직전 명시적 승인이 필요

## Open Questions

1. **PR 브랜치 전략**: `release/v1.7.0` feature 브랜치 vs `main` 직접 푸시 — 권장은 전자
2. **E2E 사전 검증 범위**: 본 PR 변경 spec 4건만 vs admin/public 전체 회귀 — 권장은 변경 spec 4건만 (시간 절약, 다른 spec은 c32e6c2 영향 범위 밖)
3. **Session memo 정책**: 매번 커밋 vs `.moai/state/session-memo.md` 도 .gitignore — 본 plan은 "현 정책 유지(커밋)" 선택, 변경 필요 시 별도 라운드
