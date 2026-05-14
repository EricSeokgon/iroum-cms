// HIGH-10: localStorage 토큰 보안 래퍼 — 시민 사이트 부분 완화 조치
// HIGH-10: 완전한 HttpOnly cookie 전환은 백엔드 세션 관리 변경 필요 (향후 과제)
//
// 현재 시민 사이트는 admin 과 달리 LocalStorage 에 Bearer 토큰을 저장한다.
// DOMPurify 로 XSS 1차 방어가 적용되어 있으나, localStorage 노출 자체는
// 향후 백엔드 작업 전까지 남아있다. 본 모듈은:
//   1) 토큰 read/write 를 단일 진입점으로 통일해 추후 cookie 전환 비용을 낮춤
//   2) iframe 임베드(clickjacking + 토큰 탈취 보조 벡터) 1회 경고
//   3) 개발 모드에서 사용 위치 가시화 경고
//
// 인터페이스를 변경할 일이 생기면 secureGetToken/secureSetToken/secureRemoveToken
// 단일 함수만 수정하면 전 코드베이스에 전파된다.

// @MX:ANCHOR: [AUTO] secureGetToken — authStore, axios 인터셉터, refresh 경로에서 사용
// @MX:REASON: fan_in >= 3: authStore.ts, api/client.ts 등에서 토큰 read 통합 진입점
let _iframeWarned = false
let _devUsageWarned = false

function _warnIframeOnce(): void {
  if (_iframeWarned) return
  _iframeWarned = true
  // 프레임 내부에서 실행 중이면 clickjacking + 토큰 탈취 위험 가능성 표시
  try {
    if (window.top !== window.self) {
      // 운영(prod) 환경에서도 1회만 콘솔 경고 (CSP/디버그 흔적용)
      // 메시지는 영문으로 — 보안 도구 로그 수집 호환성 확보
      // eslint-disable-next-line no-console
      console.warn(
        '[security] Public SPA is embedded in an iframe; token storage in localStorage may be exposed to framing attacks.',
      )
    }
  } catch {
    // 크로스 오리진 프레임 접근 시 SecurityError 발생 가능 — 그 자체가 iframe 임베드 신호
    // eslint-disable-next-line no-console
    console.warn(
      '[security] Public SPA appears to be cross-origin iframed; storage isolation cannot be verified.',
    )
  }
}

function _warnDevOnce(): void {
  if (_devUsageWarned) return
  _devUsageWarned = true
  if (import.meta.env.DEV) {
    // eslint-disable-next-line no-console
    console.info(
      '[security] tokenSecurity wrapper active. HIGH-10 mitigation: localStorage usage will migrate to HttpOnly cookie in future backend work.',
    )
  }
}

/**
 * localStorage 기반 토큰 read 래퍼.
 * - production 빌드에서 iframe 임베드 감지 시 세션당 1회 경고
 * - dev 모드에서는 wrapper 사용 사실을 1회 안내
 */
export function secureGetToken(key: string): string | null {
  _warnDevOnce()
  if (import.meta.env.PROD) {
    _warnIframeOnce()
  }
  try {
    return localStorage.getItem(key)
  } catch {
    // SecurityError(스토리지 비활성/incognito) 시 안전하게 null 반환
    return null
  }
}

/**
 * localStorage 기반 토큰 write 래퍼.
 * value 가 null/빈 문자열이면 removeItem 으로 정규화.
 */
export function secureSetToken(key: string, value: string | null): void {
  _warnDevOnce()
  if (import.meta.env.PROD) {
    _warnIframeOnce()
  }
  try {
    if (value && value.length > 0) {
      localStorage.setItem(key, value)
    } else {
      localStorage.removeItem(key)
    }
  } catch {
    // 스토리지 접근 실패 — 메모리 ref 상태에 의존 (호출부에서 처리)
  }
}

/**
 * 토큰 제거 래퍼. logout/refresh 실패 경로에서 사용.
 */
export function secureRemoveToken(key: string): void {
  try {
    localStorage.removeItem(key)
  } catch {
    // ignore
  }
}
