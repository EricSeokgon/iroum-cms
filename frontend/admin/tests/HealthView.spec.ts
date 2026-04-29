/**
 * HealthView 단위 테스트
 * TDD RED-GREEN 의식적 적용:
 *  RED : axios mock 없이 실행 시 네트워크 에러 → 에러 상태 렌더링 확인
 *  GREEN: axios mock 적용 후 성공 응답 → "UP" 상태 표시 확인
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import HealthView from '../src/views/HealthView.vue'
import type { HealthResponse } from '@iroum/shared/types/api'

// Axios 모킹 — 네트워크 없이 단위 테스트 실행
vi.mock('@iroum/shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
  },
}))

import { apiClient } from '@iroum/shared/api/client'

const mockGet = vi.mocked(apiClient.get)

// 테스트용 i18n 인스턴스 (한국어 기본)
const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  fallbackLocale: 'en',
  messages: {
    ko: {
      health: {
        title: '서버 상태 확인',
        status: '상태',
        service: '서비스',
        version: '버전',
        loading: '서버 상태를 확인하는 중...',
        error: '서버와 통신할 수 없습니다.',
        retry: '다시 시도',
      },
    },
    en: {},
  },
})

function mountHealthView() {
  return mount(HealthView, {
    global: {
      plugins: [createPinia(), i18n, ElementPlus],
      stubs: {
        'el-icon': true,
        Loading: true,
      },
    },
  })
}

describe('HealthView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 시 서버 status "UP"을 표시한다 (GREEN)', async () => {
    const mockResponse: HealthResponse = {
      status: 'UP',
      service: 'iroum-cms',
      version: '0.1.0',
    }
    mockGet.mockResolvedValueOnce({ data: mockResponse })

    const wrapper = mountHealthView()

    // onMounted 비동기 완료 대기
    await new Promise((resolve) => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()

    const statusEl = wrapper.find('[data-testid="health-status"]')
    expect(statusEl.exists()).toBe(true)
    expect(statusEl.text()).toBe('UP')

    const serviceEl = wrapper.find('[data-testid="health-service"]')
    expect(serviceEl.text()).toBe('iroum-cms')

    const versionEl = wrapper.find('[data-testid="health-version"]')
    expect(versionEl.text()).toBe('0.1.0')
  })

  it('에러 발생 시 에러 메시지를 표시한다 (RED scenario)', async () => {
    mockGet.mockRejectedValueOnce(new Error('Network Error'))

    const wrapper = mountHealthView()

    await new Promise((resolve) => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()

    // 에러 상태 — el-alert 렌더링 확인
    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
  })

  it('로딩 중에는 loading 상태를 표시한다', () => {
    // 응답을 영원히 보류하여 로딩 상태 유지
    mockGet.mockReturnValueOnce(new Promise(() => {}))

    const wrapper = mountHealthView()

    const loadingEl = wrapper.find('[role="status"]')
    expect(loadingEl.exists()).toBe(true)
  })
})
