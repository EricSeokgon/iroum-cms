// SPEC-CMS-PUBLIC-001 T-002 — RED 단계: public API client 인터셉터 검증
// 검증 대상: src/api/client.ts
//
// 시나리오:
//  1. baseURL은 VITE_API_BASE_URL 또는 /api/v1 (fallback)
//  2. 요청 인터셉터: localStorage 토큰이 있으면 Authorization 헤더 부착
//  3. 요청 인터셉터: 토큰 없으면 익명(anonymous) 요청 — Authorization 헤더 미부착
//  4. 응답 401: requiresAuth=true 라우트면 토큰 클리어 + /login 리다이렉트, 아니면 통과
//  5. 응답 503 / MAINTENANCE_MODE_ACTIVE: /maintenance 리다이렉트

import { describe, it, expect, beforeEach, vi } from 'vitest'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'

// 라우터 mock — push 호출 추적
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

describe('apiClient — 기본 설정', () => {
  beforeEach(() => {
    vi.resetModules()
    pushMock.mockClear()
    localStorage.clear()
  })

  it('baseURL이 /api/v1 fallback으로 설정된다', async () => {
    const { apiClient } = await import('@/api/client')
    expect(apiClient.defaults.baseURL).toBeDefined()
    expect(apiClient.defaults.baseURL).toMatch(/api\/v1/)
  })

  it('timeout이 15000ms로 설정된다 (PER-003 안전 마진)', async () => {
    const { apiClient } = await import('@/api/client')
    expect(apiClient.defaults.timeout).toBe(15000)
  })
})

describe('apiClient — 요청 인터셉터 (토큰 주입)', () => {
  beforeEach(() => {
    vi.resetModules()
    pushMock.mockClear()
    localStorage.clear()
  })

  it('localStorage 토큰이 있으면 Authorization Bearer 헤더를 추가한다', async () => {
    localStorage.setItem('public.accessToken', 'test-token-abc')
    const { apiClient } = await import('@/api/client')
    const handler = apiClient.interceptors.request as unknown as {
      handlers: Array<{
        fulfilled: (cfg: InternalAxiosRequestConfig) => InternalAxiosRequestConfig
      }>
    }
    const config = {
      url: '/policies',
      headers: {},
    } as unknown as InternalAxiosRequestConfig

    const result = handler.handlers[0].fulfilled(config)
    expect(result.headers.Authorization).toBe('Bearer test-token-abc')
  })

  it('localStorage 토큰이 없으면 익명 요청 — Authorization 헤더 미부착', async () => {
    const { apiClient } = await import('@/api/client')
    const handler = apiClient.interceptors.request as unknown as {
      handlers: Array<{
        fulfilled: (cfg: InternalAxiosRequestConfig) => InternalAxiosRequestConfig
      }>
    }
    const config = {
      url: '/policies',
      headers: {},
    } as unknown as InternalAxiosRequestConfig

    const result = handler.handlers[0].fulfilled(config)
    expect(result.headers.Authorization).toBeUndefined()
  })
})

describe('apiClient — 응답 인터셉터 (401)', () => {
  beforeEach(() => {
    vi.resetModules()
    pushMock.mockClear()
    localStorage.clear()
  })

  it('401 응답 + requiresAuth=true 라우트면 토큰 클리어 + /login 리다이렉트', async () => {
    localStorage.setItem('public.accessToken', 'expired-token')
    const routerModule = await import('@/router')
    // requiresAuth=true 라우트로 설정
    Object.assign(routerModule.default.currentRoute.value, {
      fullPath: '/qnas/new',
      meta: { requiresAuth: true },
    })

    const { apiClient } = await import('@/api/client')
    const handler = apiClient.interceptors.response as unknown as {
      handlers: Array<{
        rejected: (err: AxiosError) => Promise<unknown>
      }>
    }
    const error = {
      isAxiosError: true,
      response: { status: 401, data: { code: 'UNAUTHORIZED' } },
      config: { url: '/me/qnas' },
    } as unknown as AxiosError

    await expect(handler.handlers[0].rejected(error)).rejects.toBeDefined()
    expect(localStorage.getItem('public.accessToken')).toBeNull()
    expect(pushMock).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'login', query: expect.objectContaining({ redirect: '/qnas/new' }) }),
    )
  })

  it('401 응답 + requiresAuth=false 라우트면 단순 reject — /login 리다이렉트 없음', async () => {
    const routerModule = await import('@/router')
    Object.assign(routerModule.default.currentRoute.value, {
      fullPath: '/policies/match',
      meta: { requiresAuth: false },
    })

    const { apiClient } = await import('@/api/client')
    const handler = apiClient.interceptors.response as unknown as {
      handlers: Array<{
        rejected: (err: AxiosError) => Promise<unknown>
      }>
    }
    const error = {
      isAxiosError: true,
      response: { status: 401, data: { code: 'UNAUTHORIZED' } },
      config: { url: '/policies' },
    } as unknown as AxiosError

    await expect(handler.handlers[0].rejected(error)).rejects.toBeDefined()
    expect(pushMock).not.toHaveBeenCalled()
  })
})

describe('apiClient — 응답 인터셉터 (503 점검 모드)', () => {
  beforeEach(() => {
    vi.resetModules()
    pushMock.mockClear()
    localStorage.clear()
  })

  it('503 + MAINTENANCE_MODE_ACTIVE 코드면 /maintenance 리다이렉트', async () => {
    const { apiClient } = await import('@/api/client')
    const handler = apiClient.interceptors.response as unknown as {
      handlers: Array<{
        rejected: (err: AxiosError) => Promise<unknown>
      }>
    }
    const error = {
      isAxiosError: true,
      response: { status: 503, data: { code: 'MAINTENANCE_MODE_ACTIVE' } },
      config: { url: '/notices' },
    } as unknown as AxiosError

    await expect(handler.handlers[0].rejected(error)).rejects.toBeDefined()
    expect(pushMock).toHaveBeenCalledWith(expect.objectContaining({ name: 'maintenance' }))
  })

  it('503 + 다른 코드면 단순 reject — 리다이렉트 없음', async () => {
    const { apiClient } = await import('@/api/client')
    const handler = apiClient.interceptors.response as unknown as {
      handlers: Array<{
        rejected: (err: AxiosError) => Promise<unknown>
      }>
    }
    const error = {
      isAxiosError: true,
      response: { status: 503, data: { code: 'SERVICE_UNAVAILABLE' } },
      config: { url: '/policies' },
    } as unknown as AxiosError

    await expect(handler.handlers[0].rejected(error)).rejects.toBeDefined()
    expect(pushMock).not.toHaveBeenCalled()
  })
})
