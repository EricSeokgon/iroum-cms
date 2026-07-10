## SPEC-CMS-NOTI-EXT-001 Progress

- Started: 2026-06-18T00:00:00+09:00
- Phase 0.9 complete: Java project detected → moai-lang-java
- Phase 0.95 complete: Full Pipeline Mode (24 files, 3 domains)
- Development mode: TDD (RED-GREEN-REFACTOR)
- Harness level: standard
- Phase 1 complete: 전략 분석 완료 (4개 HIGH 리스크 수정, V61 DDL 교정, 경로 검증)
- Phase 2B complete: 백엔드 TDD 구현 완료 (42 files, 50 unit tests pass, BUILD SUCCESSFUL)
- Phase 2B complete: 프런트엔드 구현 완료 (5 files, TypeScript 0 errors)
- Phase 2.5/2.8 complete: 커밋 완료 (efe2ed3, feat/SPEC-CMS-NOTI-EXT-001)
- Status: Implemented — /moai sync 준비 완료

### 인수 기준 달성 현황
- AC-NE-001~005: 알림 템플릿 CRUD (create/read/update/delete/preview) ✅
- AC-NE-006: (code, language) 중복 409 처리 ✅
- AC-NE-007: 존재하지 않는 템플릿 404 처리 ✅
- AC-NE-008: RBAC (NOTIFICATION_TEMPLATE:READ/WRITE/DELETE) ✅
- AC-NE-009: INAPP → user_notification_inbox 전용 기록 ✅
- AC-NE-010: PII 이메일 평문 미로깅 ✅
- AC-NE-011: 발송 idempotency (status=PENDING 기반) ✅
- AC-NE-012: EmailDispatchExecutor HTML 이메일 (MimeMessage) ✅
- AC-NE-013: 옵트아웃 사용자 발송 스킵 ✅
- AC-NE-014: @Scheduled 워커 fixedDelay=60s, LIMIT 10 ✅
- AC-NE-015: FOR UPDATE SKIP LOCKED (다중 인스턴스 안전) ✅
- AC-NE-016: PolicyDispatchController @PreAuthorize DISPATCH:WRITE 수정 ✅
- AC-NE-017: SUPER_ADMIN role_permissions 매핑 (V61) ✅
