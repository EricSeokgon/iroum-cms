// 안전관리 Pinia 스토어 — SPEC-CMS-006
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { safetyApi } from '@/api/safety'
import type {
  IncidentSummary,
  IncidentDetail,
  IncidentFilter,
  IncidentCreateRequest,
  KeywordResponse,
  KeywordRequest,
  SafetyProfileResponse,
  SafetyProfileUpsertRequest,
  MatchResultResponse,
  ReportDetail,
  ReportSummary,
  ReportCreateRequest,
  ChecklistItemResult,
  ChecklistUpdateRequest,
  ChecklistStats,
  TemplateSummary,
  TemplateDetail,
  TemplateRequest,
  TemplateChecklistItem,
} from '@/api/safety'

// @MX:ANCHOR: [AUTO] useSafetyStore — SPEC-CMS-006 6개 뷰에서 공통 참조
// @MX:REASON: fan_in >= 3: IncidentList/Detail/Profile/Match/Report/Template 뷰가 공통 상태 사용

export const useSafetyStore = defineStore('safety', () => {
  // ── 사고사례 상태 ───────────────────────────────────────────────────────────
  const incidents = ref<IncidentSummary[]>([])
  const incidentTotal = ref(0)
  const currentIncident = ref<IncidentDetail | null>(null)
  const incidentLoading = ref(false)

  // ── 키워드 상태 ─────────────────────────────────────────────────────────────
  const keywords = ref<KeywordResponse[]>([])
  const keywordsLoading = ref(false)

  // ── 안전 프로필 상태 ────────────────────────────────────────────────────────
  const myProfile = ref<SafetyProfileResponse | null>(null)
  const profileLoading = ref(false)

  // ── 매칭 상태 ───────────────────────────────────────────────────────────────
  const matchResult = ref<MatchResultResponse | null>(null)
  const matchLoading = ref(false)

  // ── 보고서 상태 ─────────────────────────────────────────────────────────────
  const myReports = ref<ReportSummary[]>([])
  const allReports = ref<ReportSummary[]>([])
  const reportsTotal = ref(0)
  const currentReport = ref<ReportDetail | null>(null)
  const reportLoading = ref(false)

  // ── 체크리스트 상태 ─────────────────────────────────────────────────────────
  const checklist = ref<ChecklistItemResult[]>([])
  const checklistStats = ref<ChecklistStats | null>(null)

  // ── 템플릿 상태 ─────────────────────────────────────────────────────────────
  const templates = ref<TemplateSummary[]>([])
  const currentTemplate = ref<TemplateDetail | null>(null)
  const templatesLoading = ref(false)

  // ── 공통 에러 ───────────────────────────────────────────────────────────────
  const error = ref<string | null>(null)

  function setError(e: unknown, fallback: string): void {
    error.value = e instanceof Error ? e.message : fallback
  }

  // ── 사고사례 액션 ───────────────────────────────────────────────────────────
  async function fetchIncidents(filter: IncidentFilter): Promise<void> {
    incidentLoading.value = true
    error.value = null
    try {
      const res = await safetyApi.incidents.list(filter)
      incidents.value = res.data.content
      incidentTotal.value = res.data.totalElements
    } catch (e) {
      setError(e, '사고사례 목록 조회 실패')
    } finally {
      incidentLoading.value = false
    }
  }

  async function fetchIncident(id: number): Promise<void> {
    incidentLoading.value = true
    error.value = null
    try {
      const res = await safetyApi.incidents.get(id)
      currentIncident.value = res.data
    } catch (e) {
      setError(e, '사고사례 상세 조회 실패')
    } finally {
      incidentLoading.value = false
    }
  }

  async function createIncident(req: IncidentCreateRequest): Promise<IncidentDetail> {
    const res = await safetyApi.incidents.create(req)
    return res.data
  }

  async function updateIncident(id: number, req: Partial<IncidentCreateRequest>): Promise<IncidentDetail> {
    const res = await safetyApi.incidents.update(id, req)
    return res.data
  }

  async function deleteIncident(id: number): Promise<void> {
    await safetyApi.incidents.delete(id)
  }

  async function triggerSync(): Promise<{ triggered_at: string; job_id?: string }> {
    const res = await safetyApi.incidents.sync()
    return res.data
  }

  // ── 키워드 액션 ─────────────────────────────────────────────────────────────
  async function fetchKeywords(): Promise<void> {
    keywordsLoading.value = true
    try {
      const res = await safetyApi.keywords.list()
      keywords.value = res.data
    } catch (e) {
      setError(e, '키워드 조회 실패')
    } finally {
      keywordsLoading.value = false
    }
  }

  async function createKeyword(req: KeywordRequest): Promise<KeywordResponse> {
    const res = await safetyApi.keywords.create(req)
    return res.data
  }

  async function updateKeyword(id: number, req: Partial<KeywordRequest>): Promise<KeywordResponse> {
    const res = await safetyApi.keywords.update(id, req)
    return res.data
  }

  async function deleteKeyword(id: number): Promise<void> {
    await safetyApi.keywords.delete(id)
  }

  // ── 프로필 액션 ─────────────────────────────────────────────────────────────
  async function fetchMyProfile(): Promise<void> {
    profileLoading.value = true
    error.value = null
    try {
      const res = await safetyApi.profile.me()
      myProfile.value = res.data
    } catch (e) {
      setError(e, '안전 프로필 조회 실패')
    } finally {
      profileLoading.value = false
    }
  }

  async function upsertProfile(req: SafetyProfileUpsertRequest): Promise<SafetyProfileResponse> {
    profileLoading.value = true
    error.value = null
    try {
      const res = await safetyApi.profile.upsert(req)
      myProfile.value = res.data
      return res.data
    } finally {
      profileLoading.value = false
    }
  }

  // ── 매칭 액션 ───────────────────────────────────────────────────────────────
  async function runMatch(): Promise<MatchResultResponse> {
    matchLoading.value = true
    error.value = null
    try {
      const res = await safetyApi.matching.run()
      matchResult.value = res.data
      return res.data
    } finally {
      matchLoading.value = false
    }
  }

  async function fetchCachedMatch(): Promise<void> {
    matchLoading.value = true
    error.value = null
    try {
      const res = await safetyApi.matching.cached()
      matchResult.value = res.data
    } catch (e) {
      setError(e, '캐시된 매칭 결과 조회 실패')
    } finally {
      matchLoading.value = false
    }
  }

  // ── 보고서 액션 ─────────────────────────────────────────────────────────────
  async function createReport(req: ReportCreateRequest): Promise<ReportDetail> {
    reportLoading.value = true
    error.value = null
    try {
      const res = await safetyApi.reports.create(req)
      currentReport.value = res.data
      return res.data
    } finally {
      reportLoading.value = false
    }
  }

  async function fetchReport(uuid: string): Promise<void> {
    reportLoading.value = true
    error.value = null
    try {
      const res = await safetyApi.reports.get(uuid)
      currentReport.value = res.data
    } catch (e) {
      setError(e, '보고서 조회 실패')
    } finally {
      reportLoading.value = false
    }
  }

  async function downloadPdf(uuid: string): Promise<Blob> {
    const res = await safetyApi.reports.pdf(uuid)
    return res.data
  }

  async function fetchMyReports(params?: { page?: number; size?: number }): Promise<void> {
    const res = await safetyApi.reports.listMine(params)
    myReports.value = res.data.content
    reportsTotal.value = res.data.totalElements
  }

  async function fetchAllReports(params?: { page?: number; size?: number; user_id?: number }): Promise<void> {
    const res = await safetyApi.reports.listAll(params)
    allReports.value = res.data.content
    reportsTotal.value = res.data.totalElements
  }

  // ── 체크리스트 액션 ─────────────────────────────────────────────────────────
  async function fetchChecklist(reportUuid: string): Promise<void> {
    const res = await safetyApi.checklist.list(reportUuid)
    checklist.value = res.data
  }

  async function updateCheckResult(
    reportUuid: string,
    itemId: number,
    req: ChecklistUpdateRequest,
  ): Promise<ChecklistItemResult> {
    const res = await safetyApi.checklist.update(reportUuid, itemId, req)
    // 로컬 상태 업데이트
    const idx = checklist.value.findIndex(item => item.id === itemId)
    if (idx >= 0) checklist.value[idx] = res.data
    return res.data
  }

  async function fetchStats(reportUuid: string): Promise<void> {
    const res = await safetyApi.checklist.stats(reportUuid)
    checklistStats.value = res.data
  }

  // ── 템플릿 액션 ─────────────────────────────────────────────────────────────
  async function fetchTemplates(): Promise<void> {
    templatesLoading.value = true
    error.value = null
    try {
      const res = await safetyApi.templates.list()
      templates.value = res.data
    } catch (e) {
      setError(e, '템플릿 목록 조회 실패')
    } finally {
      templatesLoading.value = false
    }
  }

  async function fetchTemplate(id: number): Promise<void> {
    const res = await safetyApi.templates.get(id)
    currentTemplate.value = res.data
  }

  async function createTemplate(req: TemplateRequest): Promise<TemplateDetail> {
    const res = await safetyApi.templates.create(req)
    return res.data
  }

  async function updateTemplate(id: number, req: Partial<TemplateRequest>): Promise<TemplateDetail> {
    const res = await safetyApi.templates.update(id, req)
    return res.data
  }

  async function deleteTemplate(id: number): Promise<void> {
    await safetyApi.templates.delete(id)
  }

  async function previewTemplate(id: number): Promise<string> {
    const res = await safetyApi.templates.preview(id)
    return res.data.html
  }

  async function fetchTemplateChecklist(templateId: number): Promise<TemplateChecklistItem[]> {
    const res = await safetyApi.templates.checklist(templateId)
    return res.data
  }

  async function addChecklistItem(
    templateId: number,
    req: TemplateChecklistItem,
  ): Promise<TemplateChecklistItem> {
    const res = await safetyApi.templates.addChecklistItem(templateId, req)
    return res.data
  }

  async function deleteTemplateChecklistItem(templateId: number, itemId: number): Promise<void> {
    await safetyApi.templates.deleteChecklistItem(templateId, itemId)
  }

  return {
    // 상태
    incidents,
    incidentTotal,
    currentIncident,
    incidentLoading,
    keywords,
    keywordsLoading,
    myProfile,
    profileLoading,
    matchResult,
    matchLoading,
    myReports,
    allReports,
    reportsTotal,
    currentReport,
    reportLoading,
    checklist,
    checklistStats,
    templates,
    currentTemplate,
    templatesLoading,
    error,
    // 액션
    fetchIncidents,
    fetchIncident,
    createIncident,
    updateIncident,
    deleteIncident,
    triggerSync,
    fetchKeywords,
    createKeyword,
    updateKeyword,
    deleteKeyword,
    fetchMyProfile,
    upsertProfile,
    runMatch,
    fetchCachedMatch,
    createReport,
    fetchReport,
    downloadPdf,
    fetchMyReports,
    fetchAllReports,
    fetchChecklist,
    updateCheckResult,
    fetchStats,
    fetchTemplates,
    fetchTemplate,
    createTemplate,
    updateTemplate,
    deleteTemplate,
    previewTemplate,
    fetchTemplateChecklist,
    addChecklistItem,
    deleteTemplateChecklistItem,
  }
})
