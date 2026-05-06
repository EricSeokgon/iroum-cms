// 거버넌스 Pinia 스토어 — SPEC-CMS-009
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { governanceApi } from '@/api/governance'
import type {
  DataDictionary,
  DataDictionaryDetail,
  DataDictionaryFilter,
  DataDictionaryRequest,
  FreshnessResult,
  RetentionPolicy,
  RetentionPolicyRequest,
  RetentionRunResult,
  BatchExecutionLog,
  BatchLogFilter,
  StatsRecomputeRequest,
  BoardStatRow,
  ContentStatRow,
  PolicyStatRow,
  SafetyStatRow,
  QualityRule,
  QualityRuleFilter,
  QualityRuleRequest,
  QualityReport,
  QualityReportFilter,
  RecoveryDrill,
  RecoveryDrillFilter,
  RecoveryDrillRequest,
  BackupStatus,
} from '@/api/governance'

// @MX:ANCHOR: [AUTO] useGovernanceStore — SPEC-CMS-009 6개 거버넌스 뷰 공통 상태
// @MX:REASON: fan_in >= 3: DataDictionary/RetentionPolicy/BatchLogs/QualityRule/QualityReport/RecoveryDrill 뷰

export const useGovernanceStore = defineStore('governance', () => {
  // ── 데이터 사전 상태 ────────────────────────────────────────────────────────
  const dictionary = ref<DataDictionary[]>([])
  const dictionaryTotal = ref(0)
  const currentDictionary = ref<DataDictionaryDetail | null>(null)
  const dictionaryLoading = ref(false)
  const freshness = ref<FreshnessResult | null>(null)

  // ── 보존 정책 상태 ──────────────────────────────────────────────────────────
  const retentionPolicies = ref<RetentionPolicy[]>([])
  const retentionLoading = ref(false)

  // ── 배치 로그 상태 ──────────────────────────────────────────────────────────
  const batchLogs = ref<BatchExecutionLog[]>([])
  const batchLogsTotal = ref(0)
  const currentBatchLog = ref<BatchExecutionLog | null>(null)
  const batchLogsLoading = ref(false)

  // ── 통계 (차트용) 상태 ──────────────────────────────────────────────────────
  const boardStats = ref<BoardStatRow[]>([])
  const contentStats = ref<ContentStatRow[]>([])
  const policyStats = ref<PolicyStatRow[]>([])
  const safetyStats = ref<SafetyStatRow[]>([])

  // ── 품질 룰 상태 ────────────────────────────────────────────────────────────
  const qualityRules = ref<QualityRule[]>([])
  const qualityRulesTotal = ref(0)
  const qualityRulesLoading = ref(false)

  // ── 품질 리포트 상태 ────────────────────────────────────────────────────────
  const qualityReports = ref<QualityReport[]>([])
  const qualityReportsTotal = ref(0)
  const currentQualityReport = ref<QualityReport | null>(null)
  const qualityReportsLoading = ref(false)

  // ── 복구 시험 상태 ──────────────────────────────────────────────────────────
  const recoveryDrills = ref<RecoveryDrill[]>([])
  const recoveryDrillsLoading = ref(false)

  // ── 백업 상태 ───────────────────────────────────────────────────────────────
  const backupStatus = ref<BackupStatus | null>(null)

  // ── 공통 에러 ───────────────────────────────────────────────────────────────
  const error = ref<string | null>(null)

  function setError(e: unknown, fallback: string): void {
    error.value = e instanceof Error ? e.message : fallback
  }

  // ── 데이터 사전 액션 ────────────────────────────────────────────────────────
  async function fetchDictionary(filter: DataDictionaryFilter): Promise<void> {
    dictionaryLoading.value = true
    error.value = null
    try {
      const res = await governanceApi.dictionary.list(filter)
      dictionary.value = res.data.content
      dictionaryTotal.value = res.data.totalElements
    } catch (e) {
      setError(e, '데이터 사전 조회 실패')
    } finally {
      dictionaryLoading.value = false
    }
  }

  async function fetchDictionaryDetail(id: number): Promise<void> {
    const res = await governanceApi.dictionary.get(id)
    currentDictionary.value = res.data
  }

  async function createDictionary(req: DataDictionaryRequest): Promise<DataDictionary> {
    const res = await governanceApi.dictionary.create(req)
    return res.data
  }

  async function updateDictionary(id: number, req: DataDictionaryRequest): Promise<DataDictionary> {
    const res = await governanceApi.dictionary.update(id, req)
    return res.data
  }

  async function removeDictionary(id: number): Promise<void> {
    await governanceApi.dictionary.remove(id)
  }

  async function exportDictionary(format: 'csv' | 'xlsx'): Promise<Blob> {
    const res = await governanceApi.dictionary.exportFile(format)
    return res.data
  }

  async function fetchFreshness(): Promise<FreshnessResult> {
    const res = await governanceApi.dictionary.freshness()
    freshness.value = res.data
    return res.data
  }

  // ── 보존 정책 액션 ──────────────────────────────────────────────────────────
  async function fetchRetentionPolicies(): Promise<void> {
    retentionLoading.value = true
    error.value = null
    try {
      const res = await governanceApi.retention.list()
      retentionPolicies.value = res.data
    } catch (e) {
      setError(e, '보존 정책 조회 실패')
    } finally {
      retentionLoading.value = false
    }
  }

  async function createRetentionPolicy(req: RetentionPolicyRequest): Promise<RetentionPolicy> {
    const res = await governanceApi.retention.create(req)
    return res.data
  }

  async function updateRetentionPolicy(id: number, req: RetentionPolicyRequest): Promise<RetentionPolicy> {
    const res = await governanceApi.retention.update(id, req)
    return res.data
  }

  async function runRetentionPolicy(id: number): Promise<RetentionRunResult> {
    const res = await governanceApi.retention.runNow(id)
    return res.data
  }

  // ── 배치 로그 액션 ──────────────────────────────────────────────────────────
  async function fetchBatchLogs(filter: BatchLogFilter): Promise<void> {
    batchLogsLoading.value = true
    error.value = null
    try {
      const res = await governanceApi.batchLogs.list(filter)
      batchLogs.value = res.data.content
      batchLogsTotal.value = res.data.totalElements
    } catch (e) {
      setError(e, '배치 실행 이력 조회 실패')
    } finally {
      batchLogsLoading.value = false
    }
  }

  async function fetchBatchLog(id: number): Promise<void> {
    const res = await governanceApi.batchLogs.get(id)
    currentBatchLog.value = res.data
  }

  async function recomputeStats(req: StatsRecomputeRequest): Promise<BatchExecutionLog> {
    const res = await governanceApi.batchLogs.recompute(req)
    return res.data
  }

  // ── 통계 액션 ───────────────────────────────────────────────────────────────
  async function fetchBoardStats(params: { boardId?: number; from?: string; to?: string; period?: 'daily' | 'monthly' }): Promise<void> {
    const res = await governanceApi.stats.boards(params)
    boardStats.value = res.data
  }

  async function fetchContentStats(params: { contentId?: number; from?: string; to?: string }): Promise<void> {
    const res = await governanceApi.stats.contents(params)
    contentStats.value = res.data
  }

  async function fetchPolicyStats(params: { policyId?: number; from?: string; to?: string }): Promise<void> {
    const res = await governanceApi.stats.policies(params)
    policyStats.value = res.data
  }

  async function fetchSafetyStats(params: { category?: string; from?: string; to?: string }): Promise<void> {
    const res = await governanceApi.stats.safety(params)
    safetyStats.value = res.data
  }

  // ── 품질 룰 액션 ────────────────────────────────────────────────────────────
  async function fetchQualityRules(filter: QualityRuleFilter): Promise<void> {
    qualityRulesLoading.value = true
    error.value = null
    try {
      const res = await governanceApi.qualityRules.list(filter)
      qualityRules.value = res.data.content
      qualityRulesTotal.value = res.data.totalElements
    } catch (e) {
      setError(e, '품질 룰 조회 실패')
    } finally {
      qualityRulesLoading.value = false
    }
  }

  async function createQualityRule(req: QualityRuleRequest): Promise<QualityRule> {
    const res = await governanceApi.qualityRules.create(req)
    return res.data
  }

  async function updateQualityRule(id: number, req: QualityRuleRequest): Promise<QualityRule> {
    const res = await governanceApi.qualityRules.update(id, req)
    return res.data
  }

  async function removeQualityRule(id: number): Promise<void> {
    await governanceApi.qualityRules.remove(id)
  }

  async function runQualityRule(id: number): Promise<BatchExecutionLog> {
    const res = await governanceApi.qualityRules.runNow(id)
    return res.data
  }

  // ── 품질 리포트 액션 ────────────────────────────────────────────────────────
  async function fetchQualityReports(filter: QualityReportFilter): Promise<void> {
    qualityReportsLoading.value = true
    error.value = null
    try {
      const res = await governanceApi.qualityReports.list(filter)
      qualityReports.value = res.data.content
      qualityReportsTotal.value = res.data.totalElements
    } catch (e) {
      setError(e, '품질 리포트 조회 실패')
    } finally {
      qualityReportsLoading.value = false
    }
  }

  async function fetchQualityReport(id: number): Promise<void> {
    const res = await governanceApi.qualityReports.get(id)
    currentQualityReport.value = res.data
  }

  // ── 복구 시험 액션 ──────────────────────────────────────────────────────────
  async function fetchRecoveryDrills(filter: RecoveryDrillFilter): Promise<void> {
    recoveryDrillsLoading.value = true
    error.value = null
    try {
      const res = await governanceApi.recoveryDrills.list(filter)
      recoveryDrills.value = res.data
    } catch (e) {
      setError(e, '복구 시험 조회 실패')
    } finally {
      recoveryDrillsLoading.value = false
    }
  }

  async function createRecoveryDrill(req: RecoveryDrillRequest): Promise<RecoveryDrill> {
    const res = await governanceApi.recoveryDrills.create(req)
    return res.data
  }

  // ── 백업 상태 액션 ──────────────────────────────────────────────────────────
  async function fetchBackupStatus(): Promise<void> {
    try {
      const res = await governanceApi.backup.status()
      backupStatus.value = res.data
    } catch (e) {
      setError(e, '백업 상태 조회 실패')
    }
  }

  return {
    // 상태
    dictionary,
    dictionaryTotal,
    currentDictionary,
    dictionaryLoading,
    freshness,
    retentionPolicies,
    retentionLoading,
    batchLogs,
    batchLogsTotal,
    currentBatchLog,
    batchLogsLoading,
    boardStats,
    contentStats,
    policyStats,
    safetyStats,
    qualityRules,
    qualityRulesTotal,
    qualityRulesLoading,
    qualityReports,
    qualityReportsTotal,
    currentQualityReport,
    qualityReportsLoading,
    recoveryDrills,
    recoveryDrillsLoading,
    backupStatus,
    error,
    // 액션 — 데이터 사전
    fetchDictionary,
    fetchDictionaryDetail,
    createDictionary,
    updateDictionary,
    removeDictionary,
    exportDictionary,
    fetchFreshness,
    // 액션 — 보존 정책
    fetchRetentionPolicies,
    createRetentionPolicy,
    updateRetentionPolicy,
    runRetentionPolicy,
    // 액션 — 배치 로그
    fetchBatchLogs,
    fetchBatchLog,
    recomputeStats,
    // 액션 — 통계
    fetchBoardStats,
    fetchContentStats,
    fetchPolicyStats,
    fetchSafetyStats,
    // 액션 — 품질 룰
    fetchQualityRules,
    createQualityRule,
    updateQualityRule,
    removeQualityRule,
    runQualityRule,
    // 액션 — 품질 리포트
    fetchQualityReports,
    fetchQualityReport,
    // 액션 — 복구 시험
    fetchRecoveryDrills,
    createRecoveryDrill,
    // 액션 — 백업 상태
    fetchBackupStatus,
  }
})
