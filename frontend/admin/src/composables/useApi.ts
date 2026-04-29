// API 호출 공통 composable
// 로딩·에러 상태를 함께 관리합니다

import { ref } from 'vue'
import type { Ref } from 'vue'
import { apiClient } from '@iroum/shared/api/client'
import type { ApiError } from '@iroum/shared/types/api'
import axios from 'axios'

interface UseApiResult<T> {
  data: Ref<T | null>
  loading: Ref<boolean>
  error: Ref<ApiError | null>
  execute: () => Promise<void>
}

// @MX:ANCHOR: [AUTO] useApi — HealthView, 향후 모든 views에서 공통 사용 예정
// @MX:REASON: fan_in >= 3: HealthView, HomeView, 추후 board/content views에서 사용

export function useApi<T>(endpoint: string): UseApiResult<T> {
  const data = ref<T | null>(null) as Ref<T | null>
  const loading = ref(false)
  const error = ref<ApiError | null>(null)

  async function execute(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const response = await apiClient.get<T>(endpoint)
      data.value = response.data
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.data) {
        error.value = err.response.data as ApiError
      } else {
        error.value = {
          code: 'NETWORK_ERROR',
          message: '서버와 통신할 수 없습니다.',
        }
      }
    } finally {
      loading.value = false
    }
  }

  return { data, loading, error, execute }
}
