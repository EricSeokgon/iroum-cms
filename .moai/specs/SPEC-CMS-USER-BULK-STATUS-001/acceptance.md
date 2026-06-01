---
id: SPEC-CMS-USER-BULK-STATUS-001
type: acceptance
version: 0.1.0
updated: 2026-06-01
---

# 수락 기준 (Given-When-Then) — 사용자 일괄 상태 변경

## API / 서비스 (REQ-UBS-003, 004, 006, 011, 012)

### AC-UBS-003-1 — 빈 userIds 거부
- **Given** USER:WRITE 권한 토큰
- **When** `PATCH /api/v1/users/bulk-status`에 `{ "userIds": [], "targetStatus": "INACTIVE" }` 전송
- **Then** 400 Bad Request 반환, DB 변경 없음

### AC-UBS-003-2 — 존재하지 않는 ID 부분 처리
- **Given** 유효 사용자 ID [10, 11]과 존재하지 않는 ID 9999
- **When** `{ "userIds": [10, 11, 9999], "targetStatus": "INACTIVE" }` 전송
- **Then** 응답 `succeeded=[10,11]`, `failed=[{userId:9999, reason:"존재하지 않는 사용자"}]`, ID 10·11 상태는 INACTIVE

### AC-UBS-003-3 — 정상 일괄 변경
- **Given** ACTIVE 상태 사용자 ID [1, 2, 3]
- **When** `{ "userIds": [1,2,3], "targetStatus": "INACTIVE" }` 전송
- **Then** 응답 `succeeded=[1,2,3]`, `failed=[]`, 세 사용자 모두 status=INACTIVE

### AC-UBS-004-1 — DELETED 상태 스킵
- **Given** 사용자 ID 5가 현재 DELETED 상태
- **When** `{ "userIds": [5], "targetStatus": "ACTIVE" }` 전송
- **Then** `failed=[{userId:5, reason:"DELETED 상태는 일괄 변경 불가"}]`, ID 5 상태 변경 없음

### AC-UBS-004-2 — DELETED 대상은 SUPER_ADMIN만
- **Given** SUPER_ADMIN이 아닌(예: DEPT_ADMIN) USER:WRITE 토큰
- **When** `{ "userIds": [1], "targetStatus": "DELETED" }` 전송
- **Then** 403 Forbidden

### AC-UBS-004-3 — LOCKED→ACTIVE는 unlock 동등 처리
- **Given** 로그인 실패 5회로 LOCKED 상태인 사용자 ID 7
- **When** `{ "userIds": [7], "targetStatus": "ACTIVE" }` 전송
- **Then** ID 7 상태 ACTIVE, 로그인 실패 카운트 0으로 초기화

### AC-UBS-004-4 — 허용되지 않는 targetStatus
- **Given** USER:WRITE 토큰
- **When** `{ "userIds": [1], "targetStatus": "FOO" }` 전송
- **Then** 400 Bad Request

### AC-UBS-006-1 — 혼합 부분 실패
- **Given** ID [1(ACTIVE), 2(ACTIVE), 3(ACTIVE), 5(DELETED), 9999(없음)]
- **When** `{ "userIds": [1,2,3,5,9999], "targetStatus": "INACTIVE" }` 전송
- **Then** `succeeded=[1,2,3]`, `failed=[{5,...},{9999,...}]`, ID 1·2·3만 INACTIVE로 변경

### AC-UBS-011-1 — userIds 누락
- **Given** USER:WRITE 토큰
- **When** `{ "targetStatus": "INACTIVE" }` (userIds 키 없음) 전송
- **Then** 400 Bad Request, DB 변경 없음

### AC-UBS-012-1 — 건별 트랜잭션 격리
- **Given** ID [1, 2, 3] 중 ID 2 처리에서 예외 발생하도록 구성
- **When** 일괄 변경 실행
- **Then** ID 1·3은 커밋되어 변경 유지, ID 2는 failed에 기록, 1·3의 변경이 롤백되지 않음

### AC-UBS-009-2 — 서버측 100건 초과 거부
- **Given** USER:WRITE 토큰
- **When** `userIds`에 101개 ID 전송
- **Then** 400 Bad Request

## 권한 (REQ-UBS-007)

### AC-UBS-007-1 — USER:WRITE 없으면 403
- **Given** USER:WRITE 권한이 없는 토큰
- **When** `PATCH /api/v1/users/bulk-status` 호출
- **Then** 403 Forbidden

### AC-UBS-007-2 — DEPT_ADMIN 일반 상태 변경 허용
- **Given** USER:WRITE 보유한 DEPT_ADMIN 토큰
- **When** `{ "userIds": [1,2], "targetStatus": "INACTIVE" }` 전송
- **Then** 정상 처리(succeeded 포함)

## 감사 로그 (REQ-UBS-008)

### AC-UBS-008-1 — 성공 건별 audit_log 기록
- **Given** ACTIVE 사용자 30명
- **When** 30건 INACTIVE 일괄 변경 성공
- **Then** `audit_log`에 30건 INSERT, 각 레코드 `action='UPDATE'`, `entity_type='USER'`, before/after status 포함

### AC-UBS-008-2 — 실패 항목은 미기록
- **Given** 5건 중 2건이 실패하는 요청
- **When** 일괄 변경 실행
- **Then** `audit_log`에는 성공 3건만 INSERT(실패 2건 미기록)

## 프론트엔드 UI (REQ-UBS-001, 002, 005, 009, 010)

### AC-UBS-001-1 — 체크박스 다중선택
- **Given** 사용자 목록이 로드된 상태
- **When** 화면을 렌더링
- **Then** 각 행 좌측에 체크박스 열 표시, 헤더 체크박스 클릭 시 현재 페이지 전체 선택

### AC-UBS-002-1 — 툴바 토글 및 카운터
- **Given** 사용자 목록 화면
- **When** 0건 선택 → 1건 이상 선택으로 변경
- **Then** 0건일 때 툴바 숨김, 1건 이상이면 툴바 표시 + "N명 선택됨" 카운터

### AC-UBS-005-1 — 확인 다이얼로그 내용
- **Given** 3명을 선택하고 대상 상태를 INACTIVE로 설정
- **When** 실행 버튼 클릭
- **Then** "3명의 계정 상태를 INACTIVE로 변경합니다" 메시지의 확인 다이얼로그 표시

### AC-UBS-005-2 — 확인 취소
- **Given** 확인 다이얼로그가 표시된 상태
- **When** 취소 선택
- **Then** API 미호출, 선택 상태 유지

### AC-UBS-009-1 — 클라이언트 100건 제한
- **Given** 목록에서 사용자 선택 중
- **When** 101번째 선택을 시도
- **Then** "최대 100건만 선택 가능합니다" 경고 표시, 초과분 선택 해제(100건 유지)

### AC-UBS-010-1 — 전체 성공 피드백
- **Given** 30명 선택 후 INACTIVE 일괄 변경
- **When** API가 succeeded=30, failed=0 반환
- **Then** 토스트 "30명의 상태가 변경되었습니다", 목록 자동 갱신, 선택 초기화

### AC-UBS-010-2 — 부분 실패 피드백
- **Given** 30명 선택 후 일괄 변경
- **When** API가 succeeded=28, failed=2 반환
- **Then** 토스트 "28명 성공, 2명 실패 (상세 보기)", 상세 보기로 실패 항목 확인 가능

## Definition of Done

- [ ] 모든 AC(22개) 통과
- [ ] 백엔드 단위·통합 테스트 통과 (부분 실패·권한·감사·100건 상한 포함)
- [ ] 프론트 컴포넌트 테스트 통과 (선택·툴바·확인·피드백)
- [ ] LSP 에러 0 / 타입 에러 0 / 린트 에러 0
- [ ] 변경 성공 건별 audit_log 기록 검증
- [ ] SPEC-ID 참조 커밋 메시지
- [ ] Exclusions 범위 외 기능 미구현 확인
