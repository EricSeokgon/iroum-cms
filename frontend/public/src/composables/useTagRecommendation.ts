// 태그 추천 컴포저블 (public) — SPEC-CMS-AI-004
// 시민 Q&A 작성 중 본문 변경을 디바운스 감시하여 AI 태그 후보를 비동기로 가져온다 (비인증 허용).
// @MX:NOTE: [AUTO] 디바운스 500ms(NFR-001) + 20자 미만 미전송 — ML API 호출 빈도/노이즈 제한
import { ref, watch } from 'vue'
import type { Ref } from 'vue'
import { aiApi } from '@/api/aiApi'

// 최소 본문 길이 — 20자 미만은 추천 요청을 보내지 않는다 (REQ-AI-TAG-008)
const MIN_CONTENT_LENGTH = 20
// 디바운스 대기 시간 — 500ms (NFR-001)
const DEBOUNCE_MS = 500
// 추천 최대 노출 개수
const MAX_RECOMMENDATIONS = 5

/**
 * @param content      추천 입력 본문(plain text) 반응형 값
 * @param existingTags 이미 선택된 태그 — 중복 추천 회피 컨텍스트
 * @param contentType  콘텐츠 유형 (기본 QNA)
 */
export function useTagRecommendation(
  content: Ref<string>,
  existingTags: Ref<string[]>,
  contentType: 'POST' | 'QNA' = 'QNA',
) {
  const recommendations = ref<string[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  let timer: ReturnType<typeof setTimeout> | null = null

  async function requestRecommendations(text: string, tags: string[]): Promise<void> {
    if (!text || text.length < MIN_CONTENT_LENGTH) {
      recommendations.value = []
      loading.value = false
      return
    }
    loading.value = true
    error.value = null
    try {
      const res = await aiApi.recommendTags({ content: text, existingTags: tags, contentType })
      recommendations.value = (res.recommendedTags ?? []).slice(0, MAX_RECOMMENDATIONS)
    } catch {
      // ML 장애 시 빈 배열 유지 — 사용자에게 오류 미노출 (그레이스풀 폴백)
      recommendations.value = []
    } finally {
      loading.value = false
    }
  }

  watch(
    [content, existingTags],
    ([newContent, newTags]) => {
      if (timer) clearTimeout(timer)
      timer = setTimeout(() => {
        void requestRecommendations(newContent, [...newTags])
      }, DEBOUNCE_MS)
    },
    { immediate: false },
  )

  async function acceptTag(tag: string): Promise<void> {
    try {
      await aiApi.tagFeedback({ content: content.value, contentType, eventType: 'ACCEPTED', tagValue: tag })
    } catch {
      /* 피드백 실패 무시 */
    }
  }

  async function rejectTag(tag: string): Promise<void> {
    try {
      await aiApi.tagFeedback({ content: content.value, contentType, eventType: 'REJECTED', tagValue: tag })
    } catch {
      /* 피드백 실패 무시 */
    }
  }

  return { recommendations, loading, error, acceptTag, rejectTag }
}
