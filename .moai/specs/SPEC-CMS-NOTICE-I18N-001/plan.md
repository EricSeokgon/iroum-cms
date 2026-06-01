---
id: SPEC-CMS-NOTICE-I18N-001
type: plan
version: 0.1.0
status: Draft
created: 2026-06-01
updated: 2026-06-01
---

# 구현 계획 — SPEC-CMS-NOTICE-I18N-001 공지사항 다중 언어 지원

## 1. 기술 접근 요약

NOTICE 게시글의 영어 번역을 `bbs_post`와 분리된 `bbs_post_i18n` 테이블에 저장한다. 한국어는 `bbs_post` 원본을 그대로 사용하며 데이터 마이그레이션이 없다. 공개 API는 `?lang` 쿼리 우선 → `Accept-Language` 헤더 폴백 → `ko` 기본 순으로 언어를 결정하고, 영어 번역이 없으면 한국어로 폴백하며 `Content-Language` 헤더로 실제 반환 언어를 명시한다.

## 2. 마일스톤 (우선순위 기반, 시간 추정 없음)

### M1 — 데이터 계층 (Priority High)
- V41 마이그레이션 작성 (`bbs_post_i18n`)
- 번역 엔티티/리포지터리 매핑
- 산출물: REQ-NI-001 충족, AC-NI-001-1~3 통과

### M2 — 백엔드 번역 API (Priority High)
- 관리자 번역 upsert/조회/삭제 엔드포인트 (`/api/v1/board/posts/{id}/translations`)
- NOTICE 타입 가드 (REQ-NI-009)
- 한국어 원본 필수 백엔드 검증 (REQ-NI-007 서버측)
- 산출물: AC-NI-003-*, AC-NI-007-2, AC-NI-008-*, AC-NI-009-1 통과

### M3 — 공개 조회 언어 협상 (Priority High)
- 단건/목록 공개 API에 `?lang` + `Accept-Language` 처리, 폴백, `Content-Language` 헤더
- 산출물: REQ-NI-004/005/010 충족, AC-NI-004-*, AC-NI-005-*, AC-NI-010-* 통과

### M4 — 관리자 프론트 언어 탭 (Priority Medium)
- PostFormView `el-tabs` (한국어 필수 / English 선택)
- boardApi 번역 메서드 추가
- 산출물: REQ-NI-002/003/007 충족, AC-NI-002-*, AC-NI-007-1 통과

### M5 — 관리자 목록 배지 (Priority Medium)
- PostListView EN 배지
- 산출물: REQ-NI-006 충족, AC-NI-006-* 통과

### M6 — 통합 검증 (Priority Medium)
- 전 시나리오 통합/컴포넌트 테스트, 폴백·NOTICE 가드 회귀
- 산출물: 전체 AC 그린

## 3. 의존성 순서

M1 → M2 → M3 (백엔드 직렬). M4/M5는 M2 완료 후 병렬 가능. M6는 전체 후행.

## 4. 위험 요소

| 위험 | 영향 | 완화 |
|------|------|------|
| 공개 API 실제 경로/시그니처가 SPEC 가정과 다름 | M3 지연 | Run 시작 시 SPEC-CMS-PUBLIC-001 컨트롤러 확인 후 경로 확정 |
| `content_text` 파생 방식 불명확 | 데이터 정합 | 기존 bbs_post 저장 경로(트리거/서비스) 추종, 동일 로직 재사용 |
| `el-tabs` 도입 시 기존 폼 검증 규칙 충돌 | 저장 동작 회귀 | 한국어 탭에 기존 rules 유지, 영어는 선택 검증 분리 |
| 번역 권한 분리 요구가 추후 발생 | 권한 재설계 | 1차는 기존 쓰기 권한 포섭(가정), 권한 분리는 후속 SPEC |

## 5. 전제 (Run 단계 확인 항목)

- 공개 게시글 컨트롤러 경로/DTO (`SPEC-CMS-PUBLIC-001`)
- `bbs_post.content_text` 생성 책임 위치 (트리거 vs 서비스)
- 게시글 쓰기 권한 코드 명칭
