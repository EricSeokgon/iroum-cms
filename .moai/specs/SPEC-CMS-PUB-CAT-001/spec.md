---
id: SPEC-CMS-PUB-CAT-001
title: "발간자료 카테고리 관리자 CRUD"
status: Implemented
version: 1.0.0
created_at: 2026-06-10
updated_at: 2026-06-12
author: ircp
priority: Medium
---

# SPEC-CMS-PUB-CAT-001 — 발간자료 카테고리 관리자 CRUD

## 개요

발간자료(Publication) 카테고리를 어드민 UI에서 직접 생성·수정·삭제할 수 있도록 백엔드 API와 프론트엔드 관리 뷰를 추가한다.

`publication_category` 테이블(계층형 최대 depth 3)과 DTO는 이미 존재하지만, 읽기 전용 API(`GET /categories`)만 있고 쓰기 API가 없는 상태다.

## 요구사항 (EARS)

### REQ-PCA-001: 카테고리 생성

WHEN 어드민이 유효한 카테고리 생성 요청을 제출하면,  
THE SYSTEM SHALL 새 `publication_category` 레코드를 저장하고 HTTP 201과 생성된 카테고리 DTO를 반환한다.

제약: code 는 전체 유니크, parentId 가 주어지면 해당 부모의 depth + 1 이 자동 계산됨 (DB 트리거).

### REQ-PCA-002: 카테고리 수정

WHEN 어드민이 존재하는 카테고리에 대해 유효한 수정 요청을 제출하면,  
THE SYSTEM SHALL name/sortOrder/status 를 갱신하고 HTTP 200과 수정된 DTO를 반환한다.

### REQ-PCA-003: 카테고리 삭제

WHEN 어드민이 자식 카테고리와 연결된 발간자료가 없는 카테고리 삭제를 요청하면,  
THE SYSTEM SHALL 해당 카테고리를 삭제하고 HTTP 204를 반환한다.

IF 카테고리에 자식 카테고리가 존재하거나 연결된 발간자료(bbs_post_publication_meta)가 있으면,  
THE SYSTEM SHALL HTTP 409 Conflict 를 반환한다.

### REQ-PCA-004: 어드민 카테고리 목록 조회

WHEN 어드민이 카테고리 목록을 조회하면,  
THE SYSTEM SHALL INACTIVE 포함 전체 카테고리를 트리 구조로 반환한다.

## 인수 조건 (Acceptance Criteria)

| ID | 조건 |
|---|---|
| AC-PCA-001 | POST /api/v1/admin/publication-categories with {code, name} → 201, body.id ≠ null |
| AC-PCA-002 | PUT /api/v1/admin/publication-categories/{id} with {name, status:"INACTIVE"} → 200, body.status == "INACTIVE" |
| AC-PCA-003a | DELETE /api/v1/admin/publication-categories/{id} (leaf, 발간자료 없음) → 204 |
| AC-PCA-003b | DELETE /api/v1/admin/publication-categories/{id} (자식 존재) → 409 |
| AC-PCA-004 | GET /api/v1/admin/publication-categories → 200, INACTIVE 카테고리 포함 |
| AC-PCA-005 | 미인증 요청 → 401 |

## 구현 범위

### 백엔드

- `PublicationCategoryCreateRequest` — code, name, parentId(nullable), sortOrder
- `PublicationCategoryUpdateRequest` — name, sortOrder, status
- `PublicationCategoryConflictException` — 삭제 시 자식/연결 발간자료 존재
- `PublicationCategoryMapper` 추가: insert, update, deleteById, countChildren, countLinkedPublications, existsByCode, findAllForAdmin
- `PublicationCategoryMapper.xml` SQL 추가
- `PublicationCategoryAdminService` 인터페이스 + Impl
- `PublicationCategoryAdminController` — `/api/v1/admin/publication-categories`
- `GlobalExceptionHandler` — `PublicationCategoryConflictException` 핸들러
- `PublicationCategoryAdminControllerIT` — AC-PCA-001~005 (IT)

### 프론트엔드

- `frontend/admin/src/api/publicationCategories.ts`
- `frontend/admin/src/views/board/PublicationCategoryManagerView.vue`
- `router/index.ts` — route 추가
- `locales/ko.json`, `locales/en.json` — i18n 키 추가

## 상태

- [ ] 계획
- [ ] 구현
- [ ] 테스트 완료
- [ ] PR 생성
- [ ] 병합
