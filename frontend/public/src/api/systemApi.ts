// SPEC-CMS-009 시스템 헬스 + 점검 모드
import { apiClient } from './client'

export interface HealthStatus {
  status: 'UP' | 'DOWN' | 'DEGRADED'
  maintenanceMode: boolean
  until?: string
  reason?: string
}

export interface MaintenanceNotice {
  id: number
  title: string
  message: string
  startsAt: string
  endsAt: string
  active: boolean
}

export const systemApi = {
  health(): Promise<HealthStatus> {
    return apiClient.get<HealthStatus>('/system/health').then((r) => r.data)
  },
  maintenanceNotices(active = true): Promise<MaintenanceNotice[]> {
    return apiClient
      .get<MaintenanceNotice[]>('/system/maintenance-notices', { params: { active } })
      .then((r) => r.data)
  },
}
