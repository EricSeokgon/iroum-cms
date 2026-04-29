// admin과 동일 composable — 공유 가능하지만 workspace 독립성 유지를 위해 복사
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
