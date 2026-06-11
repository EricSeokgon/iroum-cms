// SPEC-CMS-SIM-001 — 시뮬레이션 상태 스토어 (위저드 단계 간 결과 공유)
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  simulationApi,
  type SimulationResult,
  type SimulationStartRequest,
} from '@/api/simulationApi'

export const useSimulationStore = defineStore('simulation', () => {
  const currentResult = ref<SimulationResult | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  // 시뮬레이션 시작 — 결과 저장 후 sessionId 반환
  async function startSimulation(req: SimulationStartRequest): Promise<string> {
    loading.value = true
    error.value = null
    try {
      const result = await simulationApi.start(req)
      currentResult.value = result
      return result.sessionId
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'SIMULATION_FAILED'
      throw e
    } finally {
      loading.value = false
    }
  }

  // 기존 세션 결과 로드 (결과 화면 마운트 시)
  async function loadResult(sessionId: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      currentResult.value = await simulationApi.getResult(sessionId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'SIMULATION_LOAD_FAILED'
      throw e
    } finally {
      loading.value = false
    }
  }

  // 상태 초기화 (새 시뮬레이션 시작 시)
  function clearResult(): void {
    currentResult.value = null
    loading.value = false
    error.value = null
  }

  return {
    currentResult,
    loading,
    error,
    startSimulation,
    loadResult,
    clearResult,
  }
})
