// 수정 이력(Revision) 관련 타입 — SPEC-CMS-CONTENT-REVISION-001 M4
// 게시글/페이지의 버전 diff 및 충돌 처리에 공통으로 사용된다.

/** 한 줄 단위 diff 항목 */
export interface DiffLine {
  /** 라인 변경 유형: 동일/추가/삭제 */
  type: 'EQUAL' | 'INSERT' | 'DELETE'
  /** 이전 버전 라인 번호 (추가 라인이면 null) */
  oldLineNo: number | null
  /** 새 버전 라인 번호 (삭제 라인이면 null) */
  newLineNo: number | null
  /** 라인 텍스트 */
  text: string
}

/** 필드 단위 버전 비교 응답 */
export interface RevisionDiffResponse {
  /** 비교 대상 필드명 (예: title, contentHtml) */
  field: string
  /** 시작 버전 */
  fromVersion: number
  /** 종료 버전 */
  toVersion: number
  /** 라인 단위 diff 목록 */
  lines: DiffLine[]
}

/**
 * 이력 목록 항목의 통합 표현.
 * 게시글(PostHistoryItem)·페이지(PageHistoryResponse) 응답을 RevisionPanel에서
 * 공통으로 렌더링하기 위해 정규화한 형태이다.
 */
export interface RevisionHistoryEntry {
  /** 버전 번호 */
  version: number
  /** 수정 일시 (ISO-8601) */
  editedAt: string
  /** 수정자 표시명 (없으면 null) */
  editorName?: string | null
  /** 변경 요약/사유 (없으면 null) */
  summary?: string | null
}

/** 409 REVISION_CONFLICT 응답 본문 형태 */
export interface RevisionConflictPayload {
  code: 'REVISION_CONFLICT'
  currentVersion: number
}
