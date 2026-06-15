// SPEC-CMS-AI-004 — 태그 추천 컴포저블 단위 테스트
// NFR-001(디바운스 500ms) / REQ-AI-TAG-008(20자 가드) / 그레이스풀 폴백(ML 장애 시 빈 배열)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, h, ref, type Ref } from 'vue'
import { mount } from '@vue/test-utils'

// aiAdminApi 모킹
vi.mock('@/api/aiAdminApi', () => ({
  aiAdminApi: {
    recommendTags: vi.fn(),
    tagFeedback: vi.fn(),
  },
}))

import { aiAdminApi } from '@/api/aiAdminApi'
import { useTagRecommendation } from '@/composables/useTagRecommendation'

const recommendMock = vi.mocked(aiAdminApi.recommendTags)
const feedbackMock = vi.mocked(aiAdminApi.tagFeedback)

// composable 호스트 — content/tags ref를 외부에서 조작, api를 노출
function createHost(content: Ref<string>, tags: Ref<string[]>) {
  let api: ReturnType<typeof useTagRecommendation> | null = null
  const Host = defineComponent({
    setup() {
      api = useTagRecommendation(content, tags, 'POST')
      return () => h('div')
    },
  })
  const wrapper = mount(Host)
  return {
    wrapper,
    get api() {
      if (!api) throw new Error('composable not initialized')
      return api
    },
  }
}

describe('useTagRecommendation — SPEC-CMS-AI-004', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    recommendMock.mockReset()
    feedbackMock.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('20자 이상 본문은 디바운스(500ms) 후 추천을 요청한다 (NFR-001)', async () => {
    recommendMock.mockResolvedValue({ data: { recommendedTags: ['안전', '교육'] } })
    const content = ref('')
    const tags = ref<string[]>([])
    const { wrapper, api } = createHost(content, tags)

    content.value = '이것은 충분히 긴 본문 텍스트입니다 추천 요청 대상'
    await wrapper.vm.$nextTick()

    // 500ms 이전에는 호출되지 않음
    await vi.advanceTimersByTimeAsync(400)
    expect(recommendMock).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(100)
    expect(recommendMock).toHaveBeenCalledTimes(1)
    expect(api.recommendations.value).toEqual(['안전', '교육'])
    wrapper.unmount()
  })

  it('20자 미만 본문은 추천을 요청하지 않고 목록을 비운다 (REQ-AI-TAG-008)', async () => {
    const content = ref('')
    const tags = ref<string[]>([])
    const { wrapper, api } = createHost(content, tags)

    content.value = '짧은 글'
    await wrapper.vm.$nextTick()
    await vi.advanceTimersByTimeAsync(600)

    expect(recommendMock).not.toHaveBeenCalled()
    expect(api.recommendations.value).toEqual([])
    wrapper.unmount()
  })

  it('ML 장애 시 빈 배열을 유지하고 오류를 노출하지 않는다 (그레이스풀 폴백)', async () => {
    recommendMock.mockRejectedValue(new Error('ML down'))
    const content = ref('')
    const tags = ref<string[]>([])
    const { wrapper, api } = createHost(content, tags)

    content.value = '충분히 긴 본문이지만 ML 서비스가 다운된 상황 테스트'
    await wrapper.vm.$nextTick()
    await vi.advanceTimersByTimeAsync(600)

    expect(recommendMock).toHaveBeenCalledTimes(1)
    expect(api.recommendations.value).toEqual([])
    expect(api.loading.value).toBe(false)
    wrapper.unmount()
  })

  it('추천 결과는 최대 5개로 제한된다', async () => {
    recommendMock.mockResolvedValue({
      data: { recommendedTags: ['a', 'b', 'c', 'd', 'e', 'f', 'g'] },
    })
    const content = ref('')
    const tags = ref<string[]>([])
    const { wrapper, api } = createHost(content, tags)

    content.value = '추천 개수 제한을 검증하기 위한 충분히 긴 본문입니다'
    await wrapper.vm.$nextTick()
    await vi.advanceTimersByTimeAsync(600)

    expect(api.recommendations.value).toHaveLength(5)
    wrapper.unmount()
  })

  it('빠른 연속 입력은 디바운스되어 마지막 1회만 요청한다', async () => {
    recommendMock.mockResolvedValue({ data: { recommendedTags: [] } })
    const content = ref('')
    const tags = ref<string[]>([])
    const { wrapper } = createHost(content, tags)

    content.value = '첫 번째로 입력된 충분히 긴 본문 텍스트 1'
    await wrapper.vm.$nextTick()
    await vi.advanceTimersByTimeAsync(200)
    content.value = '두 번째로 갱신된 충분히 긴 본문 텍스트 2'
    await wrapper.vm.$nextTick()
    await vi.advanceTimersByTimeAsync(600)

    expect(recommendMock).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('acceptTag는 ACCEPTED 피드백을 전송하고 실패해도 throw하지 않는다', async () => {
    feedbackMock.mockRejectedValue(new Error('feedback fail'))
    const content = ref('피드백 테스트용 본문')
    const tags = ref<string[]>([])
    const { wrapper, api } = createHost(content, tags)

    await expect(api.acceptTag('안전')).resolves.toBeUndefined()
    expect(feedbackMock).toHaveBeenCalledWith({
      content: '피드백 테스트용 본문',
      contentType: 'POST',
      eventType: 'ACCEPTED',
      tagValue: '안전',
    })
    wrapper.unmount()
  })
})
