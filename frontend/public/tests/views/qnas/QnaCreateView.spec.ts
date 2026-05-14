// SPEC-CMS-PUBLIC-001 T-006 — QnaCreateView 검증 (B-07 인증 가드)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

const createMock = vi.fn()
vi.mock('@/api/qnaApi', () => ({
  qnaApi: {
    list: vi.fn(),
    detail: vi.fn(),
    create: (...args: unknown[]) => createMock(...args),
  },
}))

const elMessageMock = { error: vi.fn(), success: vi.fn() }
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<Record<string, unknown>>('element-plus')
  return { ...actual, ElMessage: elMessageMock }
})

async function mountView() {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/qnas', name: 'qna-list', component: { template: '<div />' } },
      {
        path: '/qnas/new',
        name: 'qna-create',
        component: () => import('@/views/qnas/QnaCreateView.vue'),
      },
      { path: '/qnas/:id', name: 'qna-detail', component: { template: '<div />' } },
    ],
  })
  router.push('/qnas/new')
  await router.isReady()
  const QnaCreateView = (await import('@/views/qnas/QnaCreateView.vue')).default
  const wrapper = mount(QnaCreateView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('QnaCreateView — 폼 검증', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    createMock.mockReset()
    elMessageMock.error.mockReset()
    elMessageMock.success.mockReset()
    localStorage.clear()
  })

  it('제목 미입력 → 에러 메시지 + create 미호출', async () => {
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="qna-content-input"]').setValue('내용 10자 이상 입력합니다.')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(createMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('제목을 입력해 주세요')
  })

  it('내용 10자 미만 → 에러 메시지 + create 미호출', async () => {
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="qna-title-input"]').setValue('제목')
    await wrapper.find('[data-testid="qna-content-input"]').setValue('짧음')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(createMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('내용을 10자 이상')
  })

  it('정상 제출 → qnaApi.create 호출 + 상세로 라우팅', async () => {
    createMock.mockResolvedValue({
      id: 42,
      title: '신규',
      authorUsername: 'user1',
      status: 'PENDING',
      isPrivate: false,
      createdAt: '2026-04-15T09:00:00Z',
      questionHtml: '<p>내용입니다</p>',
    })
    const { wrapper, router } = await mountView()
    await wrapper.find('[data-testid="qna-title-input"]').setValue('신규 문의')
    await wrapper.find('[data-testid="qna-content-input"]').setValue('충분히 긴 본문입니다.')
    await wrapper.find('[data-testid="qna-private-input"]').setValue(true)
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(createMock).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '신규 문의',
        isPrivate: true,
      }),
    )
    expect(router.currentRoute.value.name).toBe('qna-detail')
    expect(router.currentRoute.value.params.id).toBe('42')
  })
})

describe('QnaCreateView — 라우트 메타 가드 (B-07)', () => {
  it('router/index.ts 의 qna-create 라우트는 requiresAuth=true 메타를 보유', async () => {
    const router = (await import('@/router')).default
    const route = router.getRoutes().find((r) => r.name === 'qna-create')
    expect(route).toBeDefined()
    expect(route?.meta.requiresAuth).toBe(true)
  })
})
