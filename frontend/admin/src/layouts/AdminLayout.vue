<template>
  <!-- 스킵 내비게이션 — KWCAG 2.4.1 블록 건너뛰기 -->
  <a
    href="#main-content"
    class="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded focus:bg-blue-600 focus:px-4 focus:py-2 focus:text-white focus-visible:outline-none"
  >
    {{ t('a11y.skipNav') }}
  </a>

  <el-container class="min-h-screen" data-testid="admin-layout">
    <!-- 사이드바 -->
    <el-aside
      width="220px"
      class="admin-sidebar border-r border-gray-200 bg-gray-900"
      role="navigation"
      :aria-label="t('nav.sidebarLabel')"
    >
      <!-- 로고 영역 -->
      <div class="flex h-14 items-center px-5 border-b border-gray-700">
        <span class="text-lg font-bold text-white">{{ t('app.title') }}</span>
      </div>

      <!-- 메뉴 -->
      <el-menu
        :router="true"
        :default-active="currentPath"
        background-color="#111827"
        text-color="#d1d5db"
        active-text-color="#ffffff"
        class="border-none"
        :aria-label="t('nav.mainMenu')"
      >
        <el-menu-item index="/system/dashboard">
          <el-icon><i-ep-home-filled /></el-icon>
          <span>{{ t('nav.dashboard') }}</span>
        </el-menu-item>

        <el-menu-item index="/users">
          <el-icon><i-ep-user /></el-icon>
          <span>{{ t('nav.users') }}</span>
        </el-menu-item>

        <el-menu-item index="/organizations">
          <el-icon><i-ep-office-building /></el-icon>
          <span>{{ t('nav.organizations') }}</span>
        </el-menu-item>

        <el-menu-item index="/health">
          <el-icon><i-ep-monitor /></el-icon>
          <span>{{ t('nav.health') }}</span>
        </el-menu-item>

        <!-- 통합 검색 그룹 (SPEC-CMS-010) -->
        <el-sub-menu index="search" :aria-label="t('nav.search')">
          <template #title>
            <el-icon><i-ep-search /></el-icon>
            <span>{{ t('nav.search') }}</span>
          </template>
          <el-menu-item index="/search">
            <span>{{ t('nav.searchUnified') }}</span>
          </el-menu-item>
          <el-menu-item
            v-if="hasPermission(auth, 'ROLE:READ')"
            index="/search/synonyms"
          >
            <span>{{ t('nav.searchSynonyms') }}</span>
          </el-menu-item>
          <el-menu-item
            v-if="hasPermission(auth, 'ROLE:READ')"
            index="/search/analytics"
          >
            <span>{{ t('nav.searchAnalytics') }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- 역할/권한 관리 — SUPER_ADMIN 또는 DEPT_ADMIN -->
        <el-menu-item
          v-if="hasPermission(auth, 'ROLE:READ')"
          index="/roles"
          :aria-label="t('nav.roles')"
        >
          <el-icon><i-ep-lock /></el-icon>
          <span>{{ t('nav.roles') }}</span>
        </el-menu-item>

        <!-- 감사 그룹 — AUDIT:READ 권한 보유자 (모든 인증 사용자) -->
        <el-sub-menu index="audit" :aria-label="t('nav.audit')">
          <template #title>
            <el-icon><i-ep-document-checked /></el-icon>
            <span>{{ t('nav.audit') }}</span>
          </template>
          <el-menu-item index="/audit/permission-changes">
            <span>{{ t('nav.permissionChanges') }}</span>
          </el-menu-item>
          <!-- 회원정보 접근 이력 — AUDIT:READ + USER:READ 권한 필요 -->
          <el-menu-item
            v-if="hasPermission(auth, 'AUDIT:READ')"
            index="/audit/personal-data-access"
          >
            <span>{{ t('nav.personalDataAccess') }}</span>
          </el-menu-item>
          <!-- 로그인 이력 — AUDIT:READ 권한 -->
          <el-menu-item
            v-if="hasPermission(auth, 'AUDIT:READ')"
            index="/audit/login-history"
          >
            <span>{{ t('nav.loginHistoryAudit') }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- 콘텐츠 그룹 — 게시판 + 미디어 관리 -->
        <el-sub-menu index="board" :aria-label="t('nav.board')">
          <template #title>
            <el-icon><i-ep-document /></el-icon>
            <span>{{ t('nav.board') }}</span>
          </template>
          <!-- 게시판 마스터 관리 — SUPER_ADMIN만 -->
          <el-menu-item
            v-if="hasPermission(auth, 'ROLE:READ')"
            index="/board/masters"
          >
            <span>{{ t('nav.boardMasters') }}</span>
          </el-menu-item>
          <!-- FAQ 관리 (SPEC-CMS-003) -->
          <el-menu-item index="/board/faqs">
            <span>{{ t('nav.faq') }}</span>
          </el-menu-item>
          <!-- Q&A 관리 (SPEC-CMS-003) -->
          <el-menu-item index="/board/qnas">
            <span>{{ t('nav.qna') }}</span>
          </el-menu-item>
          <!-- 발간자료 관리 (SPEC-CMS-003) -->
          <el-menu-item index="/board/publications">
            <span>{{ t('nav.publication') }}</span>
          </el-menu-item>
          <!-- 설문조사 관리 (SPEC-CMS-003) -->
          <el-menu-item index="/board/surveys">
            <span>{{ t('nav.survey') }}</span>
          </el-menu-item>
          <!-- 미디어 라이브러리 (SPEC-CMS-MEDIA-001) -->
          <el-menu-item index="/media">
            <span>{{ t('nav.media') }}</span>
          </el-menu-item>
          <el-menu-item index="/media/collections">
            <span>{{ t('nav.mediaCollections') }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- 콘텐츠 관리 그룹 (SPEC-CMS-004) -->
        <el-sub-menu index="content" :aria-label="t('nav.content')">
          <template #title>
            <el-icon><i-ep-grid /></el-icon>
            <span>{{ t('nav.content') }}</span>
          </template>
          <el-menu-item index="/content/site">
            <span>{{ t('nav.contentSite') }}</span>
          </el-menu-item>
          <el-menu-item index="/content/menus">
            <span>{{ t('nav.contentMenus') }}</span>
          </el-menu-item>
          <el-menu-item index="/content/templates">
            <span>{{ t('nav.contentTemplates') }}</span>
          </el-menu-item>
          <el-menu-item index="/content/pages">
            <span>{{ t('nav.contentPages') }}</span>
          </el-menu-item>
          <el-menu-item index="/content/popups">
            <span>{{ t('nav.contentPopups') }}</span>
          </el-menu-item>
          <el-menu-item index="/content/banners">
            <span>{{ t('nav.contentBanners') }}</span>
          </el-menu-item>
          <el-menu-item index="/content/i18n">
            <span>{{ t('nav.contentI18n') }}</span>
          </el-menu-item>
          <el-menu-item index="/content/seo-redirects">
            <span>{{ t('nav.contentSeoRedirects') }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- 시스템 관리 그룹 (SPEC-CMS-005) -->
        <el-sub-menu index="system" :aria-label="t('nav.system')">
          <template #title>
            <el-icon><i-ep-setting /></el-icon>
            <span>{{ t('nav.system') }}</span>
          </template>
          <el-menu-item index="/system/access-logs">
            <span>{{ t('nav.systemAccessLogs') }}</span>
          </el-menu-item>
          <el-menu-item index="/system/codes">
            <span>{{ t('nav.systemCodes') }}</span>
          </el-menu-item>
          <el-menu-item index="/system/settings">
            <span>{{ t('nav.systemSettings') }}</span>
          </el-menu-item>
          <el-menu-item index="/system/maintenance">
            <span>{{ t('nav.systemMaintenance') }}</span>
          </el-menu-item>
          <el-menu-item index="/system/audit-logs">
            <span>{{ t('nav.systemAuditLogs') }}</span>
          </el-menu-item>
          <el-menu-item index="/system/menu-stats">
            <span>메뉴별 방문 통계</span>
          </el-menu-item>
          <!-- 동의어 관리 (SPEC-CMS-010) — ADMIN 전용 -->
          <el-menu-item
            v-if="hasPermission(auth, 'ROLE:READ')"
            index="/synonyms"
          >
            <span>{{ t('nav.synonyms') }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- 데이터 거버넌스 그룹 (SPEC-CMS-009) -->
        <el-sub-menu index="governance" :aria-label="t('nav.governance')">
          <template #title>
            <el-icon><i-ep-data-line /></el-icon>
            <span>{{ t('nav.governance') }}</span>
          </template>
          <el-menu-item index="/governance/dictionary">
            <span>{{ t('nav.governanceDictionary') }}</span>
          </el-menu-item>
          <el-menu-item index="/governance/retention-policies">
            <span>{{ t('nav.governanceRetention') }}</span>
          </el-menu-item>
          <el-menu-item index="/governance/batch-logs">
            <span>{{ t('nav.governanceBatchLogs') }}</span>
          </el-menu-item>
          <el-menu-item index="/governance/quality-rules">
            <span>{{ t('nav.governanceQualityRules') }}</span>
          </el-menu-item>
          <el-menu-item index="/governance/quality-reports">
            <span>{{ t('nav.governanceQualityReports') }}</span>
          </el-menu-item>
          <el-menu-item index="/governance/recovery-drills">
            <span>{{ t('nav.governanceRecoveryDrills') }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- AI 모델 운영 그룹 (SPEC-CMS-AI-001) — ADMIN 전용 -->
        <el-sub-menu
          v-if="hasPermission(auth, 'ROLE:READ')"
          index="ai"
          :aria-label="t('nav.ai')"
        >
          <template #title>
            <el-icon><i-ep-cpu /></el-icon>
            <span>{{ t('nav.ai') }}</span>
          </template>
          <el-menu-item index="/ai/dashboard">
            <span>{{ t('nav.aiDashboard') }}</span>
          </el-menu-item>
          <el-menu-item index="/ai/drift-alerts">
            <span>{{ t('nav.aiDriftAlerts') }}</span>
          </el-menu-item>
          <el-menu-item index="/ai/retrain-queue">
            <span>{{ t('nav.aiRetrainQueue') }}</span>
          </el-menu-item>
        </el-sub-menu>

      </el-menu>

      <!-- 구분선 + 로그아웃 (el-menu 라우터 이동 충돌 방지를 위해 메뉴 외부 배치) -->
      <div class="mx-4 my-2 border-t border-gray-700" role="separator" />
      <button
        class="flex w-full cursor-pointer items-center gap-2 px-5 py-3 text-sm text-gray-300 hover:bg-gray-700 hover:text-white"
        :aria-label="t('nav.logout')"
        data-testid="btn-logout"
        :disabled="loggingOut"
        @click="handleLogout"
      >
        <el-icon><i-ep-switch-button /></el-icon>
        <span>{{ t('nav.logout') }}</span>
      </button>

      <!-- 빌드 버전 표시 -->
      <div class="px-4 py-2 text-center text-xs text-gray-500 border-t border-gray-700">
        v{{ appVersion }} · {{ buildDate }}
      </div>
    </el-aside>

    <!-- 오른쪽 메인 영역 -->
    <el-container direction="vertical" class="flex-1">
      <!-- 헤더 -->
      <el-header
        height="56px"
        class="flex items-center justify-between border-b border-gray-200 bg-white px-6"
        role="banner"
      >
        <h1 class="text-base font-semibold text-gray-800">{{ pageTitle }}</h1>

        <div class="flex items-center gap-4">
          <!-- 언어 전환 -->
          <el-select
            v-model="currentLocale"
            size="small"
            style="width: 90px"
            :aria-label="t('a11y.languageSelect')"
          >
            <el-option label="한국어" value="ko" />
            <el-option label="English" value="en" />
          </el-select>

          <!-- 사용자 드롭다운 — 비밀번호 변경 / 로그아웃 -->
          <el-dropdown
            v-if="auth.user"
            trigger="click"
            @command="handleUserCommand"
          >
            <span
              class="flex cursor-pointer items-center gap-1 text-sm text-gray-600 hover:text-gray-900"
              :aria-label="`${t('a11y.currentUser')}: ${auth.user.username}`"
              tabindex="0"
            >
              {{ auth.user.username }}
              <el-icon class="text-xs"><i-ep-arrow-down /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password-change">
                  {{ t('account.password.title') }}
                </el-dropdown-item>
                <el-dropdown-item command="my-personal-data-access">
                  {{ t('nav.myPersonalData') }}
                </el-dropdown-item>
                <el-dropdown-item command="my-login-history">
                  {{ t('nav.myLoginHistory') }}
                </el-dropdown-item>
                <el-dropdown-item command="notifications">
                  {{ t('nav.notifications') }}
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided :loading="loggingOut">
                  {{ t('nav.logout') }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 점검 모드 배너 (SPEC-CMS-005) -->
      <MaintenanceBanner />

      <!-- 메인 콘텐츠 -->
      <el-main id="main-content" role="main" class="bg-gray-50 p-6" tabindex="-1">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import MaintenanceBanner from '@/components/system/MaintenanceBanner.vue'
import { useDashboardPreferenceApply } from '@/composables/useDashboardPreferenceApply'

declare const __APP_VERSION__: string
declare const __BUILD_TIME__: string

const appVersion = __APP_VERSION__
const buildDate = new Date(__BUILD_TIME__).toLocaleDateString('ko-KR', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

// 권한 기반 메뉴 노출 헬퍼
function hasPermission(auth: ReturnType<typeof useAuthStore>, permission: string): boolean {
  const roles: string[] = auth.user?.roleCodes ?? []
  if (roles.includes('SUPER_ADMIN')) return true
  if (permission === 'ROLE:READ' && roles.includes('DEPT_ADMIN')) return true
  return false
}

// 테마/밀도/폰트 스케일 CSS 변수를 <html>에 반응형으로 적용 (REQ-DP-002)
useDashboardPreferenceApply()

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loggingOut = ref(false)
const currentPath = computed(() => route.path)
const pageTitle = computed(() => (route.meta.title as string | undefined) ?? t('app.title'))

// 언어 전환 양방향 바인딩
const currentLocale = computed({
  get: () => locale.value,
  set: (val: string) => { locale.value = val },
})

async function handleLogout(): Promise<void> {
  loggingOut.value = true
  try {
    await auth.logout()
    ElMessage.success(t('auth.logout.success'))
  } catch {
    ElMessage.error(t('auth.logout.error'))
  } finally {
    loggingOut.value = false
  }
}

async function handleUserCommand(command: string): Promise<void> {
  if (command === 'password-change') {
    router.push({ name: 'password-change' })
  } else if (command === 'my-personal-data-access') {
    router.push({ name: 'my-personal-data-access' })
  } else if (command === 'my-login-history') {
    router.push({ name: 'my-login-history' })
  } else if (command === 'notifications') {
    router.push({ name: 'notifications' })
  } else if (command === 'logout') {
    await handleLogout()
  }
}
</script>

<style scoped>
/* Element Plus 사이드바 메뉴 오버라이드 */
.admin-sidebar :deep(.el-menu) {
  border-right: none;
}

.admin-sidebar :deep(.el-menu-item.is-active) {
  background-color: #1d4ed8 !important;
}

.admin-sidebar :deep(.el-menu-item:hover) {
  background-color: #1f2937 !important;
}

/* 포커스 가시성 보장 — KWCAG 2.4.7 */
.admin-sidebar :deep(.el-menu-item:focus-visible) {
  outline: 2px solid #60a5fa;
  outline-offset: -2px;
}
</style>
