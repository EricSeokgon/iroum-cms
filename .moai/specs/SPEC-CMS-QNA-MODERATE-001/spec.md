---
id: SPEC-CMS-QNA-MODERATE-001
title: "Q&A 관리자 모더레이션 패널"
status: Completed
version: 1.0.1
created_at: 2026-06-10
updated_at: 2026-06-15
author: ircp
priority: Medium
---

# SPEC-CMS-QNA-MODERATE-001: Q&A 관리자 모더레이션 패널

## Overview

관리자가 전체 Q&A를 조회·상태 변경·삭제할 수 있는 전용 어드민 API와 프론트엔드 관리 화면을 제공한다.
기존 `/api/v1/qnas` 엔드포인트는 공개 인터페이스이므로, 어드민 전용 `/api/v1/admin/qnas` 경로를 신설한다.

## Requirements

### REQ-QNA-ADM-001: 전체 Q&A 목록 조회
WHEN ADMIN or MANAGER calls `GET /api/v1/admin/qnas`
THEN the system SHALL return paginated list of ALL Q&As (including HIDDEN and private)
WITH optional filters: status (PENDING/ANSWERED/CLOSED/HIDDEN), keyword (title search)
AND pagination params: page (default 0), size (default 20)

### REQ-QNA-ADM-002: Q&A 상태 변경
WHEN ADMIN or MANAGER calls `PATCH /api/v1/admin/qnas/{id}/status`
WITH body `{"status": "HIDDEN"|"PENDING"|"CLOSED"}`
THEN the system SHALL update the Q&A status
AND return 200 with updated QnaSummary
IF Q&A not found THEN return 404

### REQ-QNA-ADM-003: Q&A 삭제 (어드민)
WHEN ADMIN or MANAGER calls `DELETE /api/v1/admin/qnas/{id}`
THEN the system SHALL soft-delete the Q&A regardless of status or owner
AND return 204 No Content
IF Q&A not found THEN return 404

## Acceptance Criteria

### AC-QNA-ADM-001: 목록 조회
- ADMIN GET /admin/qnas → 200, HIDDEN 포함 전체 목록 반환
- status=HIDDEN 필터 → HIDDEN 항목만 반환
- keyword 필터 → title 포함 항목만 반환

### AC-QNA-ADM-002: 상태 변경
- ADMIN PATCH /admin/qnas/{id}/status {"status":"HIDDEN"} → 200, status 반영
- 존재하지 않는 id → 404

### AC-QNA-ADM-003: 삭제
- ADMIN DELETE /admin/qnas/{id} → 204
- 존재하지 않는 id → 404

### AC-QNA-ADM-004: 권한 가드
- 미인증 요청 → 401
- USER 역할 → 403

## Scope

**In scope:**
- Backend: QnaAdminController, QnaAdminService, QnaAdminServiceImpl
- Mapper: listForAdmin, countForAdmin (재사용: updateStatus, deleteById 기존 메서드 사용)
- Frontend: QnaManagementView.vue, router 등록, i18n 추가

**Out of scope:**
- 답변 등록 (기존 /qnas/{id}/answer 활용)
- 벌크 삭제
