---
id: SPEC-CMS-NOTICE-I18N-001
type: acceptance
version: 0.1.0
status: Draft
created: 2026-06-01
updated: 2026-06-01
---

# 수락 기준 (Given-When-Then) — SPEC-CMS-NOTICE-I18N-001

## 시나리오 1 — V41 마이그레이션 및 스키마 (REQ-NI-001)

### GWT-1-1 (AC-NI-001-1)
- **Given** V40까지 적용된 데이터베이스
- **When** V41 마이그레이션을 실행하면
- **Then** `bbs_post_i18n` 테이블이 생성되고 (`post_id`, `language`) UNIQUE 제약이 존재한다.

### GWT-1-2 (AC-NI-001-2)
- **Given** `bbs_post_i18n` 테이블
- **When** `language='ja'` 행을 INSERT 하면
- **Then** CHECK 제약 위반으로 거부된다.

### GWT-1-3 (AC-NI-001-3)
- **Given** 영어 번역 행이 있는 공지 게시글
- **When** 해당 `bbs_post` 행을 삭제하면
- **Then** 연관 `bbs_post_i18n` 행이 CASCADE 삭제된다.

## 시나리오 2 — 관리자 언어 탭 (REQ-NI-002, REQ-NI-007)

### GWT-2-1 (AC-NI-002-1)
- **Given** 관리자가 공지 작성 폼을 연 상태
- **When** 폼이 렌더링되면
- **Then** 한국어 탭과 English 탭이 표시된다.

### GWT-2-2 (AC-NI-002-2 / AC-NI-007-1)
- **Given** 공지 작성 폼
- **When** 한국어 탭 제목을 비운 채 두면
- **Then** 저장 버튼이 비활성화된다.

### GWT-2-3 (AC-NI-002-3)
- **Given** 영어 번역이 있는 기존 공지
- **When** 수정 모드로 진입하면
- **Then** 한국어 탭에 원본이, English 탭에 기존 영어 번역이 프리필된다.

## 시나리오 3 — 영어 번역 저장 (REQ-NI-003)

### GWT-3-1 (AC-NI-003-1)
- **Given** 한국어 원본이 저장된 공지 (id=42)
- **When** English 탭에 제목/내용을 입력하고 저장하면
- **Then** `bbs_post_i18n`에 (42,'en') 행이 INSERT 된다.

### GWT-3-2 (AC-NI-003-2)
- **Given** 이미 (42,'en') 번역이 있는 공지
- **When** English 탭을 수정 후 다시 저장하면
- **Then** 기존 행이 UPDATE 되고 중복 행이 생기지 않는다.

### GWT-3-3 (AC-NI-003-3)
- **Given** 영어 번역이 없는 공지
- **When** English 탭을 비운 채 저장하면
- **Then** 영어 번역 행이 생성되지 않는다.

## 시나리오 4 — 공개 단건 조회 + 폴백 (REQ-NI-004, REQ-NI-010)

### GWT-4-1 (AC-NI-004-1)
- **Given** (42,'en') 영어 번역이 존재
- **When** `GET /api/v1/public/posts/42?lang=en` 요청
- **Then** 영어 title/content 반환 + `Content-Language: en` 헤더.

### GWT-4-2 (AC-NI-004-2)
- **Given** 영어 번역이 없는 공지 (id=43)
- **When** `GET /api/v1/public/posts/43?lang=en` 요청
- **Then** 한국어 원본 반환 + `Content-Language: ko` 헤더.

### GWT-4-3 (AC-NI-004-3 / AC-NI-010-3)
- **Given** 공지 (id=42)
- **When** `GET /api/v1/public/posts/42` (lang 미지정, Accept-Language 없음)
- **Then** 한국어 원본 반환 + `Content-Language: ko` 헤더.

### GWT-4-4 (AC-NI-010-1)
- **Given** (42,'en') 번역 존재
- **When** `?lang=en` + `Accept-Language: ko` 동시 요청
- **Then** 영어가 반환된다(쿼리 우선).

### GWT-4-5 (AC-NI-010-2)
- **Given** (42,'en') 번역 존재
- **When** `?lang` 미지정 + `Accept-Language: en` 요청
- **Then** 영어가 반환된다(헤더 폴백).

## 시나리오 5 — 공개 목록 조회 (REQ-NI-005)

### GWT-5-1 (AC-NI-005-1)
- **Given** NOTICE 게시판에 영어 번역 있는 글과 없는 글이 혼재
- **When** `GET /api/v1/public/posts?bbs=NOTICE&lang=en` 요청
- **Then** 번역 있는 항목은 en 제목, 없는 항목은 ko 제목으로 반환된다.

### GWT-5-2 (AC-NI-005-2)
- **Given** 위 목록 응답
- **When** 각 항목을 확인하면
- **Then** 실제 반환 언어를 식별할 수 있는 필드(`language`)가 포함된다.

## 시나리오 6 — 관리자 배지 (REQ-NI-006)

### GWT-6-1 (AC-NI-006-1)
- **Given** 영어 번역이 있는 공지
- **When** 관리자 공지 목록을 조회하면
- **Then** 해당 항목에 'EN' 배지가 표시된다.

### GWT-6-2 (AC-NI-006-2)
- **Given** 영어 번역이 없는 공지
- **When** 관리자 공지 목록을 조회하면
- **Then** 해당 항목에 'EN' 배지가 없다.

## 시나리오 7 — 번역 삭제 (REQ-NI-008)

### GWT-7-1 (AC-NI-008-1)
- **Given** (42,'en') 번역 존재
- **When** `DELETE /api/v1/board/posts/42/translations/en` 요청
- **Then** (42,'en') 행이 삭제된다.

### GWT-7-2 (AC-NI-008-2)
- **Given** 영어 번역 삭제 직후
- **When** `GET /api/v1/public/posts/42?lang=en` 요청
- **Then** 한국어 폴백 + `Content-Language: ko`.

### GWT-7-3 (AC-NI-008-3)
- **Given** (42,'en') 번역이 없는 상태
- **When** `DELETE .../posts/42/translations/en` 재요청
- **Then** 멱등하게 처리된다(일관된 404 또는 204 정책).

## 시나리오 8 — NOTICE 한정 가드 (REQ-NI-009)

### GWT-8-1 (AC-NI-009-1)
- **Given** NORMAL 타입 게시판의 게시글 (id=99)
- **When** `POST/PUT /api/v1/board/posts/99/translations/en` 요청
- **Then** 거부된다(예: 409/422, NOTICE 외 번역 불가).

## 시나리오 9 — 한국어 필수 서버 검증 (REQ-NI-007)

### GWT-9-1 (AC-NI-007-2)
- **Given** 한국어 제목이 빈 공지 생성 요청
- **When** 백엔드가 요청을 처리하면
- **Then** 검증 오류로 거부되고 게시글이 생성되지 않는다.

---

## Definition of Done

- [ ] REQ-NI-001~010 전부 구현 및 매핑된 AC 전부 통과
- [ ] V41 마이그레이션이 기존 테이블을 변경하지 않음(신규 테이블만)
- [ ] 한국어 데이터 마이그레이션 없음 확인 (기존 `bbs_post` 무변경)
- [ ] 공개 API 폴백 시 `Content-Language` 헤더 정확성 검증
- [ ] NOTICE 외 타입 번역 차단 회귀 테스트 통과
- [ ] 비범위 항목(제3언어/댓글·첨부 i18n/자동번역/검색 토큰화) 미구현 확인
- [ ] TRUST 5 품질 게이트 통과 (테스트 커버리지, 린트, 보안 입력 검증)

## 품질 게이트 기준

- 백엔드 번역 서비스/컨트롤러 라인 커버리지 85% 이상
- 공개 언어 협상 경로(쿼리/헤더/기본) 3분기 모두 테스트
- 입력 검증: 언어 코드 화이트리스트(ko/en), NOTICE 타입 가드
