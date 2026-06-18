// 알림 템플릿 관리 Pinia 스토어 — SPEC-CMS-NOTI-EXT-001
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  createTemplate as apiCreate,
  deleteTemplate as apiDelete,
  getTemplate as apiGet,
  getTemplates as apiList,
  previewTemplate as apiPreview,
  updateTemplate as apiUpdate,
} from '@/api/notificationTemplate'
import type {
  NotificationTemplateCreateRequest,
  NotificationTemplateListParams,
  NotificationTemplatePreviewResult,
  NotificationTemplateResponse,
  NotificationTemplateUpdateRequest,
} from '@/api/notificationTemplate'

// @MX:ANCHOR: [AUTO] useNotificationTemplateStore — NotificationTemplateListView, PolicyDispatchView에서 공통 참조
// @MX:REASON: fan_in >= 3: fetch/create/update/delete/preview 액션이 목록 뷰 + 발송 예약 뷰에서 공통 사용

export const useNotificationTemplateStore = defineStore('notificationTemplate', () => {
  // ── 상태 ────────────────────────────────────────────────────────────────────
  const templates = ref<NotificationTemplateResponse[]>([])
  const totalCount = ref(0)
  const currentTemplate = ref<NotificationTemplateResponse | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  function setError(e: unknown, fallback: string): void {
    error.value = e instanceof Error ? e.message : fallback
  }

  // ── 액션 ────────────────────────────────────────────────────────────────────
  async function fetchTemplates(params: NotificationTemplateListParams = {}): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const { data } = await apiList(params)
      templates.value = data.content
      totalCount.value = data.totalElements
    } catch (e) {
      setError(e, '알림 템플릿 목록 조회 실패')
      throw e
    } finally {
      loading.value = false
    }
  }

  async function fetchTemplate(id: number): Promise<NotificationTemplateResponse> {
    try {
      const { data } = await apiGet(id)
      currentTemplate.value = data
      return data
    } catch (e) {
      setError(e, '알림 템플릿 상세 조회 실패')
      throw e
    }
  }

  async function createTemplate(
    payload: NotificationTemplateCreateRequest,
  ): Promise<NotificationTemplateResponse> {
    try {
      const { data } = await apiCreate(payload)
      return data
    } catch (e) {
      setError(e, '알림 템플릿 생성 실패')
      throw e
    }
  }

  async function updateTemplate(
    id: number,
    payload: NotificationTemplateUpdateRequest,
  ): Promise<NotificationTemplateResponse> {
    try {
      const { data } = await apiUpdate(id, payload)
      return data
    } catch (e) {
      setError(e, '알림 템플릿 수정 실패')
      throw e
    }
  }

  async function deleteTemplate(id: number): Promise<void> {
    try {
      await apiDelete(id)
    } catch (e) {
      setError(e, '알림 템플릿 삭제 실패')
      throw e
    }
  }

  async function previewTemplate(
    id: number,
    sampleVariables?: Record<string, string>,
  ): Promise<NotificationTemplatePreviewResult> {
    try {
      const { data } = await apiPreview(id, sampleVariables)
      return data
    } catch (e) {
      setError(e, '알림 템플릿 미리보기 실패')
      throw e
    }
  }

  return {
    // 상태
    templates,
    totalCount,
    currentTemplate,
    loading,
    error,
    // 액션
    fetchTemplates,
    fetchTemplate,
    createTemplate,
    updateTemplate,
    deleteTemplate,
    previewTemplate,
  }
})
