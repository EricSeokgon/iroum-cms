// SPEC-CMS-PUBLIC-001 §5.4 — maintenanceStore (점검 모드)
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { systemApi } from '@/api/systemApi'

export const useMaintenanceStore = defineStore('maintenance', () => {
  const isMaintenanceMode = ref(false)
  const maintenanceMessage = ref<string>('')
  const estimatedEndTime = ref<string>('')

  async function checkMaintenance(): Promise<void> {
    try {
      const health = await systemApi.health()
      isMaintenanceMode.value = health.maintenanceMode === true
      maintenanceMessage.value = health.reason ?? ''
      estimatedEndTime.value = health.until ?? ''
    } catch {
      // 헬스 체크 실패 시 점검 모드 미적용 (사이트는 정상 동작)
      isMaintenanceMode.value = false
    }
  }

  return {
    isMaintenanceMode,
    maintenanceMessage,
    estimatedEndTime,
    checkMaintenance,
  }
})
