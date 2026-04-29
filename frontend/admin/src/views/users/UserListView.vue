<template>
  <div>
    <h2 class="mb-6 text-xl font-semibold text-gray-800">{{ t('users.title') }}</h2>

    <!-- 검색 및 필터 영역 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <el-input
        v-model="searchQuery"
        :placeholder="t('users.search')"
        clearable
        style="width: 240px"
        :aria-label="t('users.search')"
        @input="debouncedSearch"
      >
        <template #prefix>
          <el-icon><i-ep-search /></el-icon>
        </template>
      </el-input>

      <el-select
        v-model="statusFilter"
        :placeholder="t('users.filterStatus')"
        clearable
        style="width: 160px"
        :aria-label="t('users.filterStatus')"
      >
        <el-option :label="t('users.status.all')" value="" />
        <el-option :label="t('users.status.active')" value="ACTIVE" />
        <el-option :label="t('users.status.inactive')" value="INACTIVE" />
        <el-option :label="t('users.status.locked')" value="LOCKED" />
      </el-select>
    </div>

    <!-- 사용자 테이블 -->
    <el-table
      :data="mockUsers"
      stripe
      :empty-text="t('users.empty')"
      :aria-label="t('users.title')"
      class="w-full"
    >
      <el-table-column prop="username" :label="t('users.col.username')" min-width="120" />
      <el-table-column prop="email" :label="t('users.col.email')" min-width="200" />
      <el-table-column prop="name" :label="t('users.col.name')" min-width="120" />
      <el-table-column prop="status" :label="t('users.col.status')" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ t(`users.status.${row.status.toLowerCase()}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" :label="t('users.col.lastLogin')" min-width="160" />
      <el-table-column :label="t('users.col.actions')" width="120" fixed="right">
        <template #default>
          <el-button size="small" type="primary" plain disabled>
            {{ t('users.action.edit') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 안내 메시지 (데이터 없음 시) -->
    <el-empty
      v-if="mockUsers.length === 0"
      :description="t('users.emptyDescription')"
      :image-size="120"
      class="mt-8"
    />

    <!-- 페이지네이션 -->
    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="0"
        layout="prev, pager, next, sizes, total"
        :page-sizes="[10, 20, 50]"
        :aria-label="t('a11y.pagination')"
        @change="onPageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { UserStatus } from '@iroum/shared/types/api'

const { t } = useI18n()

// @MX:TODO: [AUTO] 사용자 CRUD API 미구현 — SPEC-CMS-002 REQ-AUTH-006 다음 사이클에서 구현
const mockUsers: never[] = []

const searchQuery = ref('')
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

function debouncedSearch(): void {
  // TODO: debounce 후 API 호출
}

function onPageChange(): void {
  // TODO: 페이지 변경 시 API 호출
}

function statusTagType(status: UserStatus): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<UserStatus, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    ACTIVE: 'success',
    INACTIVE: 'info',
    LOCKED: 'danger',
    DELETED: 'warning',
  }
  return map[status] ?? ''
}
</script>
