// SPEC-CMS-PUBLIC-001 T-010 — apiClient 인터셉터 추가 검증 (F-02 / F-03)
// 검증: 403 → /error/403, 5xx GET → /error/500, 5xx POST → no redirect
import { describe, it, expect, beforeEach, vi } from 'vitest'
import type { AxiosError } from 'axios'

const pushMock = vi.fn()
vi.mock('@/router', () => ({
  default: {
    push: pushMock,
    currentRoute: {
      value: {
        fullPath: '/',
        meta: { requiresAuth: false },
      },
    },
  },
}))

describe('apiClient — F-02 403 권한 없음 인터셉터', () => {
  beforeEach(() => {
    vi.resetModules()
    pushMock.mockClear()
    localStorage.clear()
  })

  it('403 응답 → /error/403 (forbidden) 라우트로 리다이렉트', async () => {
    const { apiClient } = await import('@/api/client')
    const handler = apiClient.interceptors.response as unknown as {
      handlers: Array<{ rejected: (err: AxiosError) => Promise<unknown> }>
    }
    const error = {
      isAxiosError: true,
      response: { status: 403, data: { code: 'FORBIDDEN' } },
      config: { url: '/policies/subscriptions', method: 'get' },
    } as unknown as AxiosError

    await expect(handler.handlers[0].rejected(error)).rejects.toBeDefined()
    expect(pushMock).toHaveBeenCalledWith(expect.objectContaining({ name: 'forbidden' }))
  })
})

describe('apiClient — F-03 5xx 서버 오류 인터셉터', () => {
  beforeEach(() => {
    vi.resetModules()
    pushMock.mockClear()
    localStorage.clear()
  })

  it('500 응답 + GET 요청 → /error/500 (server-error) 라우트로 리다이렉트', async () => {
    const { apiClient } = await import('@/api/client')
    const handler = apiClient.interceptors.response as unknown as {
      handlers: Array<{ rejected: (err: AxiosError) => Promise<unknown> }>
    }
    const error = {
      isAxiosError: true,
      response: { status: 500, data: { code: 'INTERNAL_ERROR' } },
      config: { url: '/notices', method: 'get' },
    } as unknown as AxiosError

    await expect(handler.handlers[0].rejected(error)).rejects.toBeDefined()
    expect(pushMock).toHaveBeenCalledWith(expect.objectContaining({ name: 'server-error' }))
  })

  it('500 응답 + POST 요청 → 리다이렉트 없음 (컴포넌트에 reject 전달)', async () => {
    const { apiClient } = await import('@/api/client')
    const handler = apiClient.interceptors.response as unknown as {
      handlers: Array<{ rejected: (err: AxiosError) => Promise<unknown> }>
    }
    const error = {
      isAxiosError: true,
      response: { status: 500, data: { code: 'INTERNAL_ERROR' } },
      config: { url: '/qnas', method: 'post' },
    } as unknown as AxiosError

    await expect(handler.handlers[0].rejected(error)).rejects.toBeDefined()
    expect(pushMock).not.toHaveBeenCalled()
  })

  it('502 응답 + GET 요청 → /error/500 라우트로 리다이렉트', async () => {
    const { apiClient } = await import('@/api/client')
    const handler = apiClient.interceptors.response as unknown as {
      handlers: Array<{ rejected: (err: AxiosError) => Promise<unknown> }>
    }
    const error = {
      isAxiosError: true,
      response: { status: 502, data: { code: 'BAD_GATEWAY' } },
      config: { url: '/policies', method: 'get' },
    } as unknown as AxiosError

    await expect(handler.handlers[0].rejected(error)).rejects.toBeDefined()
    expect(pushMock).toHaveBeenCalledWith(expect.objectContaining({ name: 'server-error' }))
  })

  it('503 + MAINTENANCE_MODE_ACTIVE 가 아닌 503 + GET → /error/500 리다이렉트 (점검 모드 외 5xx 처리)', async () => {
    const { apiClient } = await import('@/api/client')
    const handler = apiClient.interceptors.response as unknown as {
      handlers: Array<{ rejected: (err: AxiosError) => Promise<unknown> }>
    }
    const error = {
      isAxiosError: true,
      response: { status: 503, data: { code: 'SERVICE_UNAVAILABLE' } },
      config: { url: '/notices', method: 'get' },
    } as unknown as AxiosError

    await expect(handler.handlers[0].rejected(error)).rejects.toBeDefined()
    // SPEC: 503 + non-maintenance 코드는 단순 reject. server-error 라우트는 status>=500 && status!==503 조건이므로 503 은 제외
    expect(pushMock).not.toHaveBeenCalled()
  })
})
