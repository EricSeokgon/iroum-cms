// useDebounce — 검색 input 디바운스 헬퍼
import { ref, watch, type Ref } from 'vue'

/**
 * 입력값을 지정 딜레이(ms) 후에 반영하는 디바운스 composable
 * @param source 감시할 반응형 값
 * @param delay  밀리초 단위 딜레이 (기본 300ms)
 */
export function useDebounce<T>(source: Ref<T>, delay = 300): Ref<T> {
  const debounced = ref(source.value) as Ref<T>
  let timer: ReturnType<typeof setTimeout> | null = null

  watch(source, (val) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      debounced.value = val
    }, delay)
  })

  return debounced
}
