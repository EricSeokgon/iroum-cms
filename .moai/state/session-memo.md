# Session Memo

## P1: Session Context

session_id: 28a1116c-1008-45f6-9edf-b7f5f0f560db
cwd: /home/sklee/moai/iroum-cms
event: SessionComplete

## P2: 완료된 SPEC 현황 (2026-05-06)

| SPEC | 상태 | 백엔드 테스트 | 프론트엔드 |
|------|------|-------------|-----------|
| SPEC-CMS-002 | Implemented | - | 완료 |
| SPEC-CMS-003 | Implemented | - | 완료 |
| SPEC-CMS-004 | Implemented | - | 완료 |
| SPEC-CMS-005 | Implemented | - | 완료 |
| SPEC-CMS-006 | Implemented | 27 GREEN | 완료 |
| SPEC-CMS-007 | Implemented | 49 GREEN | 완료 |
| SPEC-CMS-008 | Implemented | 41 GREEN | 완료 |
| SPEC-CMS-MEDIA-001 | Implemented | 15 GREEN | 완료 |
| SPEC-CMS-009 | Implemented (BE) | 554 GREEN | 미구현 (향후) |

## P3: 빌드 상태

- Frontend: `vue-tsc -b && vite build` 0 오류 GREEN
- Backend: 554 GREEN (SPEC-CMS-009 포함)
- Docker: 검증 완료

## P4: 기술 메모

- JAVA_HOME=/home/sklee/denodo/vdp9/jre (Temurin 17.0.17)
- vite.config.ts: `from 'vitest/config'` + `vue() as any` + `new URL('.', import.meta.url).pathname`
- tsconfig.node.json: `skipLibCheck: true` 필수 (@types/node 없는 환경)
- SPEC-CMS-009 백엔드 완료 — Frontend(Step 3) 미구현

## P5: 테스트 수정 이력

- 137 → 63 → 26 실패 감소 (두 세션에 걸쳐 진행)
- 잔여 26개: Testcontainers IT 클래스 8개 (AuthFlowIT, MigrationOrderIT 등) — Docker 소켓 연결 불가 환경 이슈
- 주요 수정: V13 menu_permissions CREATE 추가, V14/V16/V17 마이그레이션 오류, MyBatis UuidTypeHandler, JwtPrincipal ROLE_ 접두사, @EnableMethodSecurity
- 커밋: 942b19e
