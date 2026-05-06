// 정책사업 매칭 + 발송 Pinia 스토어 — SPEC-CMS-007
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { policyApi } from '@/api/policy'
import type {
  PolicyProgramSummary,
  PolicyProgramDetail,
  PolicyFilter,
  PolicyProgramRequest,
  CompanyProfile,
  PolicyMatchResponse,
  DispatchScheduleSummary,
  DispatchScheduleDetail,
  DispatchScheduleRequest,
  DispatchStatus,
  DispatchSimulateRequest,
  DispatchSimulateResponse,
  NotificationPreferences,
  NotificationPreferencesUpdate,
  TrackAction,
  SyncTriggerResponse,
} from '@/api/policy'

// @MX:ANCHOR: [AUTO] usePolicyStore — SPEC-CMS-007 5개 뷰에서 공통 참조
// @MX:REASON: fan_in >= 3: PolicyList/Detail/Match/Subscription/Dispatch 뷰가 공통 상태 사용

export const usePolicyStore = defineStore('policy', () => {
  // ── 정책사업 상태 ───────────────────────────────────────────────────────────
  const programs = ref<PolicyProgramSummary[]>([])
  const programsTotal = ref(0)
  const currentProgram = ref<PolicyProgramDetail | null>(null)
  const programLoading = ref(false)

  // ── 매칭 상태 ───────────────────────────────────────────────────────────────
  const myProfile = ref<CompanyProfile | null>(null)
  const profileLoading = ref(false)
  const matchResult = ref<PolicyMatchResponse | null>(null)
  const matchLoading = ref(false)

  // ── 발송 예약 상태 ──────────────────────────────────────────────────────────
  const schedules = ref<DispatchScheduleSummary[]>([])
  const schedulesTotal = ref(0)
  const currentSchedule = ref<DispatchScheduleDetail | null>(null)
  const scheduleLoading = ref(false)

  // ── 수신 동의 상태 ──────────────────────────────────────────────────────────
  const myPreferences = ref<NotificationPreferences | null>(null)
  const preferencesLoading = ref(false)

  // ── 공통 에러 ───────────────────────────────────────────────────────────────
  const error = ref<string | null>(null)

  function setError(e: unknown, fallback: string): void {
    error.value = e instanceof Error ? e.message : fallback
  }

  // ── 정책사업 액션 ───────────────────────────────────────────────────────────
  async function fetchPrograms(filter: PolicyFilter): Promise<void> {
    programLoading.value = true
    error.value = null
    try {
      const res = await policyApi.programs.list(filter)
      programs.value = res.data.content
      programsTotal.value = res.data.totalElements
    } catch (e) {
      setError(e, '정책사업 목록 조회 실패')
    } finally {
      programLoading.value = false
    }
  }

  async function fetchProgram(id: number): Promise<void> {
    programLoading.value = true
    error.value = null
    try {
      const res = await policyApi.programs.get(id)
      currentProgram.value = res.data
    } catch (e) {
      setError(e, '정책사업 상세 조회 실패')
    } finally {
      programLoading.value = false
    }
  }

  async function createProgram(req: PolicyProgramRequest): Promise<PolicyProgramDetail> {
    const res = await policyApi.programs.create(req)
    return res.data
  }

  async function updateProgram(id: number, req: Partial<PolicyProgramRequest>): Promise<PolicyProgramDetail> {
    const res = await policyApi.programs.update(id, req)
    return res.data
  }

  async function deleteProgram(id: number): Promise<void> {
    await policyApi.programs.delete(id)
  }

  async function syncPrograms(): Promise<SyncTriggerResponse> {
    const res = await policyApi.programs.sync()
    return res.data
  }

  // ── 매칭 액션 ───────────────────────────────────────────────────────────────
  async function fetchMyProfile(): Promise<void> {
    profileLoading.value = true
    error.value = null
    try {
      const res = await policyApi.matching.fetchProfile()
      myProfile.value = res.data
    } catch (e) {
      // 프로필이 없을 수 있음 — 새 등록 흐름으로 처리
      setError(e, '기업 프로필 조회 실패')
    } finally {
      profileLoading.value = false
    }
  }

  async function upsertCompanyProfile(req: CompanyProfile): Promise<CompanyProfile> {
    profileLoading.value = true
    error.value = null
    try {
      const res = await policyApi.matching.upsertProfile(req)
      myProfile.value = res.data
      return res.data
    } finally {
      profileLoading.value = false
    }
  }

  async function runMatch(): Promise<PolicyMatchResponse> {
    matchLoading.value = true
    error.value = null
    try {
      const res = await policyApi.matching.run()
      matchResult.value = res.data
      return res.data
    } finally {
      matchLoading.value = false
    }
  }

  async function fetchMatchResults(): Promise<void> {
    matchLoading.value = true
    error.value = null
    try {
      const res = await policyApi.matching.fetchMine()
      matchResult.value = res.data
    } catch (e) {
      setError(e, '매칭 결과 조회 실패')
    } finally {
      matchLoading.value = false
    }
  }

  // ── 발송 예약 액션 ──────────────────────────────────────────────────────────
  async function fetchSchedules(params?: { page?: number; size?: number; status?: DispatchStatus }): Promise<void> {
    scheduleLoading.value = true
    error.value = null
    try {
      const res = await policyApi.dispatch.list(params)
      schedules.value = res.data.content
      schedulesTotal.value = res.data.totalElements
    } catch (e) {
      setError(e, '발송 예약 목록 조회 실패')
    } finally {
      scheduleLoading.value = false
    }
  }

  async function fetchSchedule(uuid: string): Promise<void> {
    scheduleLoading.value = true
    error.value = null
    try {
      const res = await policyApi.dispatch.get(uuid)
      currentSchedule.value = res.data
    } catch (e) {
      setError(e, '발송 예약 상세 조회 실패')
    } finally {
      scheduleLoading.value = false
    }
  }

  async function createSchedule(req: DispatchScheduleRequest): Promise<DispatchScheduleDetail> {
    const res = await policyApi.dispatch.create(req)
    return res.data
  }

  async function triggerSchedule(uuid: string): Promise<DispatchScheduleDetail> {
    const res = await policyApi.dispatch.trigger(uuid)
    // 로컬 상태 갱신
    const idx = schedules.value.findIndex(s => s.uuid === uuid)
    if (idx >= 0) schedules.value[idx] = { ...schedules.value[idx], status: res.data.status }
    return res.data
  }

  async function cancelSchedule(uuid: string): Promise<void> {
    await policyApi.dispatch.cancel(uuid)
    // 로컬 상태 갱신
    const idx = schedules.value.findIndex(s => s.uuid === uuid)
    if (idx >= 0) schedules.value[idx] = { ...schedules.value[idx], status: 'CANCELLED' }
  }

  async function simulateDispatch(req: DispatchSimulateRequest): Promise<DispatchSimulateResponse> {
    const res = await policyApi.dispatch.simulate(req)
    return res.data
  }

  // ── 수신 동의 액션 ──────────────────────────────────────────────────────────
  async function fetchMySubscriptions(): Promise<void> {
    preferencesLoading.value = true
    error.value = null
    try {
      const res = await policyApi.subscription.fetchMine()
      myPreferences.value = res.data
    } catch (e) {
      setError(e, '수신 동의 조회 실패')
    } finally {
      preferencesLoading.value = false
    }
  }

  async function updateSubscriptions(req: NotificationPreferencesUpdate): Promise<NotificationPreferences> {
    preferencesLoading.value = true
    error.value = null
    try {
      const res = await policyApi.subscription.updateMine(req)
      myPreferences.value = res.data
      return res.data
    } finally {
      preferencesLoading.value = false
    }
  }

  // ── 추적 액션 ───────────────────────────────────────────────────────────────
  async function trackEvent(policyId: number, action: TrackAction, metadata?: Record<string, unknown>): Promise<void> {
    await policyApi.tracking.track(policyId, { action, metadata })
  }

  return {
    // 상태
    programs,
    programsTotal,
    currentProgram,
    programLoading,
    myProfile,
    profileLoading,
    matchResult,
    matchLoading,
    schedules,
    schedulesTotal,
    currentSchedule,
    scheduleLoading,
    myPreferences,
    preferencesLoading,
    error,
    // 액션
    fetchPrograms,
    fetchProgram,
    createProgram,
    updateProgram,
    deleteProgram,
    syncPrograms,
    fetchMyProfile,
    upsertCompanyProfile,
    runMatch,
    fetchMatchResults,
    fetchSchedules,
    fetchSchedule,
    createSchedule,
    triggerSchedule,
    cancelSchedule,
    simulateDispatch,
    fetchMySubscriptions,
    updateSubscriptions,
    trackEvent,
  }
})
