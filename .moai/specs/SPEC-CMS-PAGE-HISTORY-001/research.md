# Research: SPEC-CMS-PAGE-HISTORY-001 페이지 버전 이력 관리 고도화

작성일: 2026-06-22 / 대상: 기구현 페이지 이력·롤백 기능의 누락 갭 분석

## 1. 기구현 현황 (코드 근거)

### 1.1 백엔드 서비스 — PageServiceImpl.java

- `updatePage()` (line 97~137): 수정 전 스냅샷을 `page_history`에 INSERT (line 102~110). snapshot은 `{"title":"...","slug":"..."}` 형태 문자열로 직접 구성(line 105). `changeSummary`는 `request.changeSummary()`를 그대로 사용(line 108) → **자동 생성 없음**.
- `getPageHistory()` (line 206~210): `findByPageId` → version DESC 정렬 응답.
- `rollbackPage()` (line 218~242): 핵심 한계 확인.
  - line 224 주석: "snapshot 기반 복원 (간단히 status만 DRAFT로 강제, **실제 필드 복원은 snapshot JSON 파싱 필요**)".
  - line 225~228: status="DRAFT" 강제 + currentVersion+1 + updatedBy만 갱신. **title/slug 등 snapshot 필드는 복원하지 않음.**
  - line 231~239: 롤백 이력을 `ROLLBACK_FROM_v{version}` changeSummary로 기록.
  - **감사 로그 호출 없음** (`@AuditLog` 미적용, AuditLogService 미호출).

→ REQ-PHIST-002는 "검증 강화"가 표면 목적이나, **실제 복원 로직(snapshot 파싱)이 미구현**이므로 구현 보완이 선행되어야 함. SPEC에 명시함.

### 1.2 엔티티 — PageHistory.java

- 필드: id, pageId, version, snapshot(String/jsonb), editedBy, editedAt, changeSummary.
- 주석(line 24): snapshot은 "page row + content_block 배열 + i18n_resource 배열을 jsonb로 통째 저장" 의도이나, 실제 updatePage는 title/slug만 기록 → **의도와 구현 괴리**. 전체 스냅샷 복원은 본 SPEC 제외.

### 1.3 매퍼 — PageHistoryMapper.xml

- 존재 쿼리: `findByPageId`(version DESC), `findByPageIdAndVersion`, `insert`(snapshot::jsonb).
- **부재**: count, delete(정리용) 쿼리. REQ-PHIST-001에서 `countByPageId`, `deleteOldestByPageId` 추가 필요.

### 1.4 컨트롤러 — PageController.java

- `GET /{id}/history` (line 107): `@PreAuthorize("hasAuthority('PAGE:HISTORY:READ')")`.
- `POST /{id}/rollback/{version}` (line 114): `@PreAuthorize("hasAuthority('PAGE:ROLLBACK')")`. **`@AuditLog` 미적용.**

### 1.5 프론트엔드

- `PageHistoryDialog.vue`: 이력 목록 테이블 + 행별 롤백 popconfirm + 2개 선택 시 `JsonDiffPanel` 비교. props=`pageId`, emit=`rolledBack`. **재사용 가능**.
- `PageEditorView.vue`: History 버튼으로 다이얼로그 오픈(컨텍스트상). 
- `PageListView.vue`: 액션 열(line 57~82)에 수정/발행/예약/철회 버튼만 존재. **"이력" 버튼 없음** → REQ-PHIST-005 갭 확정.

### 1.6 테스트 — PageIT.java

- AC-PAGE-9 (line 231~243): `GET /history` 200 + 배열만 검증.
- AC-PAGE-10 (line 252~270): `POST /rollback/1` 호출 가능(권한 게이트 통과)만 검증. line 254 주석: "**실 롤백 로직은 page_history 시드 필요**. 본 IT는 권한 게이트 + 엔드포인트 매핑까지만 검증". 미존재 version이라 4xx도 허용 → **실제 복원 미검증** 확정.

## 2. 감사 로그 인프라 분석 (REQ-PHIST-004 핵심 제약)

### 2.1 @AuditLog 어노테이션 (annotation/AuditLog.java)

- 속성: `action()`, `entityType()`, `severity()`, `captureArgs()`, `captureReturn()`.
- AOP(`AuditLogAspect`)가 Around로 감싸 성공/실패·소요시간·행위자 기록.

### 2.2 audit_log.action CHECK 제약 (AuditLog.java line 37, annotation line 25~28)

허용 코드: `CREATE/READ/UPDATE/DELETE/LOGIN/LOGIN_FAILURE/LOGOUT/PERMISSION_CHANGE/PERMISSION_DENIED/PASSWORD_CHANGE/PASSWORD_RESET/TOKEN_REFRESH/TOKEN_REVOKE/EXPORT/BATCH`.

→ **`PAGE_ROLLBACK`은 허용 코드가 아님.** 사용자 요청서의 `action=PAGE_ROLLBACK`을 그대로 쓰면 CHECK 제약 위반.

해결 두 가지(SPEC REQ-PHIST-004 제약에 명시):
- (A 권장) `@AuditLog(action="UPDATE", entityType="Page", captureArgs=true)` 적용, from/to version은 afterValue JSON으로. 마이그레이션 불필요.
- (B) V56에서 CHECK 제약에 `PAGE_ROLLBACK` 추가.

### 2.3 audit_log 필드

- entityType(String), entityId(String), beforeValue/afterValue(jsonb→String), severity, result(SUCCESS/FAILURE).
- 사용자 요청의 `detail={"from_version":N,"to_version":M}`는 `afterValue` JSON에 매핑.
- APPEND-ONLY 정책(DB 트리거가 UPDATE/DELETE 차단) — 감사 항목은 불변.

## 3. 마이그레이션 상태

- 디렉토리 최신: **V56**(`V56__review_system_rbac.sql`, SPEC-CMS-REVIEW-001 draft). V55=survey.
- 본 SPEC 마이그레이션 필요 여부:
  - REQ-PHIST-001: `page_history(page_id, version)` 인덱스가 없으면 V56 추가(정리 쿼리 성능). SPEC-CMS-004 마이그레이션의 기존 인덱스 확인 후 결정.
  - REQ-PHIST-004: 채택안 A면 불필요, B면 V56 CHECK ALTER.
- **주의**: 미머지 draft SPEC(SURVEY=V54, REVIEW=V55)이 잠정 번호 사용 중. run 직전 실제 최신 재확인 필수.

## 4. 갭 요약 매핑

| 요구 | 갭 근거 | 영향 파일 |
|------|---------|-----------|
| REQ-PHIST-001 | 정리 로직·count/delete 쿼리 부재 | PageHistoryMapper.xml, 신규 RetentionJob, application.yml, (V56 인덱스?) |
| REQ-PHIST-002 | rollbackPage line 224 status만 강제, IT 미검증 | PageServiceImpl(복원 보완), PageIT(시드 헬퍼) |
| REQ-PHIST-003 | updatePage line 108 사용자 입력 직사용 | PageServiceImpl.updatePage, 신규 SummaryGenerator |
| REQ-PHIST-004 | rollback에 @AuditLog 미적용, action 코드 제약 | PageController/Service, (V56 CHECK?) |
| REQ-PHIST-005 | PageListView 액션열에 이력 버튼 없음 | PageListView.vue (Dialog 재사용) |

## 5. 리스크 / 결정 필요 사항

1. **REQ-PHIST-002 범위 해석**: "IT 완성"으로 요청됐으나 실제 복원 로직이 미구현. 구현 보완을 포함하지 않으면 AC-PHIST-005(실제 복원 검증)가 불가. → SPEC에 구현 보완 포함 명시.
2. **REQ-PHIST-004 action 코드**: A안(UPDATE 재사용) vs B안(PAGE_ROLLBACK 신규). plan 단계 결정 항목. 기본 A안.
3. **인덱스 존재 여부**: SPEC-CMS-004 마이그레이션의 page_history 인덱스를 run 전 확인하여 V56 필요성 판단.
4. **snapshot 포맷 확장 위험**: 현재 title/slug만. content_block 확장 시 복원 로직 영향 — 본 SPEC 제외로 경계 설정.
