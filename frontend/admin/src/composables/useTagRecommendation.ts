// 태그 추천 컴포저블 (admin) — SPEC-CMS-AI-004
// 게시글 작성 중 본문 변경을 디바운스 감시하여 AI 태그 후보를 비동기로 가져온다.
// @MX:NOTE: [AUTO] 디바운스 500ms(NFR-001) + 20자 미만 미전송 — ML API 호출 빈도/노이즈 제한
import { ref, watch } from 'vue'
import type { Ref } from 'vue'
import { aiAdminApi } from '@/api/aiAdminApi'

// 최소 본문 길이 — 20자 미만은 추천 요청을 보내지 않는다 (NFR / REQ-AI-TAG-008)
const MIN_CONTENT_LENGTH = 20
// 디바운스 대기 시간 — 500ms (NFR-001)
const DEBOUNCE_MS = 500
// 추천 최대 노출 개수 — 프론트에서도 안전망으로 5개 제한
const MAX_RECOMMENDATIONS = 5

/**
 * @param content      추천 입력 본문(plain text) 반응형 값
 * @param existingTags 이미 선택된 태그 — 중복 추천 회피 컨텍스트로 함께 전송
 * @param contentType  콘텐츠 유형 (기본 POST)
 */
export function useTagRecommendation(
  content: Ref<string>,
  existingTags: Ref<string[]>,
  contentType: 'POST' | 'QNA' = 'POST',
) {
  const recommendations = ref<string[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  let timer: ReturnType<typeof setTimeout> | null = null

  async function requestRecommendations(text: string, tags: string[]): Promise<void> {
    // 최소 길이 미달 시 추천 초기화 (요청 미전송)
    if (!text || text.length < MIN_CONTENT_LENGTH) {
      recommendations.value = []
      loading.value = false
      return
    }
    loading.value = true
    error.value = null
    try {
      const res = await aiAdminApi.recommendTags({ content: text, existingTags: tags, contentType })
      recommendations.value = (res.data.recommendedTags ?? []).slice(0, MAX_RECOMMENDATIONS)
    } catch {
      // ML 장애 시 빈 배열 유지 — 사용자에게 오류를 노출하지 않는다 (그레이스풀 폴백)
      recommendations.value = []
    } finally {
      loading.value = false
    }
  }

  // content/existingTags 변경 시 디바운스 후 추천 요청
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

  // 채택 피드백 — 모델 파인튜닝 입력 (실패는 무시, 글쓰기 흐름 비차단)
  async function acceptTag(tag: string): Promise<void> {
    try {
      await aiAdminApi.tagFeedback({
        content: content.value,
        contentType,
        eventType: 'ACCEPTED',
        tagValue: tag,
      })
    } catch {
      /* 피드백 실패 무시 */
    }
  }

  // 거부 피드백 (현재 UI 미연결이나 향후 확장 대비 제공)
  async function rejectTag(tag: string): Promise<void> {
    try {
      await aiAdminApi.tagFeedback({
        content: content.value,
        contentType,
        eventType: 'REJECTED',
        tagValue: tag,
      })
    } catch {
      /* 피드백 실패 무시 */
    }
  }

  return { recommendations, loading, error, acceptTag, rejectTag }
}
