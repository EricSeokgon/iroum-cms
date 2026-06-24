---
id: SPEC-CMS-POINTS-001
title: 게시판/댓글 참여 포인트 지급 시스템
status: Completed
priority: Medium
created_at: 2026-05-01
updated_at: 2026-06-24
github_issue: ""
---

# SPEC-CMS-POINTS-001: 게시판/댓글 참여 포인트 지급 시스템

## 개요

사용자가 게시글 작성, 댓글 작성, 게시글 좋아요 시 포인트를 지급하는 참여 보상 시스템을 구현한다.
포인트 지급은 best-effort 방식으로 메인 비즈니스 로직 실패에 영향을 주지 않아야 한다.

## 요구사항

### REQ-PNT-001: 포인트 정책 조회

- WHEN 시스템 설정에서 포인트 정책을 조회할 때
- THEN `POINTS:ENABLED`, `POINTS:POST_CREATED`, `POINTS:COMMENT_CREATED`, `POINTS:LIKE_GIVEN` 키를 system_setting에서 조회한다
- 캐시 없이 즉시 반영 (toggle 즉시 효과)

### REQ-PNT-002: 게시글 작성 포인트

- WHEN 사용자가 게시글을 작성할 때
- AND POINTS:ENABLED = true 이고 POINTS:POST_CREATED > 0 일 때
- THEN 해당 포인트를 user_point_ledger에 기록하고 user_point_summary를 갱신한다

### REQ-PNT-003: 댓글 작성 포인트

- WHEN 사용자가 댓글을 작성할 때
- AND POINTS:ENABLED = true 이고 POINTS:COMMENT_CREATED > 0 일 때
- THEN 해당 포인트를 user_point_ledger에 기록하고 user_point_summary를 갱신한다

### REQ-PNT-004: 좋아요 포인트

- WHEN 사용자가 게시글에 좋아요를 할 때
- AND POINTS:ENABLED = true 이고 POINTS:LIKE_GIVEN > 0 일 때
- AND 해당 사용자가 해당 게시글에 이미 좋아요를 하지 않았을 때
- THEN 해당 포인트를 user_point_ledger에 기록하고 user_point_summary를 갱신한다

### REQ-PNT-005: 좋아요 취소

- WHEN 사용자가 이미 좋아요한 게시글에 좋아요 API를 호출할 때
- THEN 좋아요가 취소된다 (toggle 방식)
- AND 포인트는 차감하지 않는다

### REQ-PNT-006: 관리자 포인트 정책 관리

- WHEN 관리자가 포인트 정책을 조회/수정할 때
- THEN POINTS:* 키들을 system_setting에서 읽고 쓸 수 있다
- 권한: POINTS:READ (조회), POINTS:WRITE (수정)

### REQ-PNT-007: 사용자 포인트 이력 조회

- WHEN 관리자 또는 사용자가 포인트 이력을 조회할 때
- THEN user_point_ledger를 페이징으로 조회한다

### REQ-PNT-008: Best-effort 지급

- WHEN 포인트 지급 과정에서 오류가 발생할 때
- THEN 메인 비즈니스 로직(게시글 작성, 댓글 작성)은 정상적으로 완료된다
- `@Transactional(propagation = REQUIRES_NEW)` 으로 격리

## DB 스키마

- `user_point_ledger`: 포인트 지급/차감 이력
- `user_point_summary`: 사용자별 포인트 합계
- `bbs_post_like`: 게시글 좋아요 (UNIQUE user_id + post_id)
- Migration: V45__points_system.sql

## 패키지

`kr.co.ircp.cms.domain.point`
