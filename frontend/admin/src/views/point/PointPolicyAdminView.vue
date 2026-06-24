<template>
  <!-- 포인트 정책 관리 — SPEC-CMS-POINTS-001 REQ-PNT-001, REQ-PNT-006 -->
  <div>
    <h2 class="mb-4 text-xl font-semibold text-gray-800">포인트 정책 관리</h2>

    <el-card shadow="never" v-loading="loading">
      <template #header>
        <span>포인트 지급 설정</span>
      </template>

      <el-form :model="form" label-width="180px" label-position="left" v-if="form">
        <el-form-item label="포인트 시스템 활성화">
          <el-switch v-model="form.enabled" @change="save" />
        </el-form-item>

        <el-divider />

        <el-form-item label="게시글 작성 포인트">
          <el-input-number
            v-model="form.postCreated"
            :min="0"
            :max="10000"
            :controls="false"
            @change="save"
          />
          <span class="ml-2 text-gray-500 text-sm">포인트</span>
        </el-form-item>

        <el-form-item label="댓글 작성 포인트">
          <el-input-number
            v-model="form.commentCreated"
            :min="0"
            :max="10000"
            :controls="false"
            @change="save"
          />
          <span class="ml-2 text-gray-500 text-sm">포인트</span>
        </el-form-item>

        <el-form-item label="좋아요 포인트">
          <el-input-number
            v-model="form.likeGiven"
            :min="0"
            :max="10000"
            :controls="false"
            @change="save"
          />
          <span class="ml-2 text-gray-500 text-sm">포인트</span>
        </el-form-item>
      </el-form>

      <el-empty v-if="!loading && !form" description="정책을 불러올 수 없습니다." />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pointApi, type PointPolicy } from '@/api/point'

const loading = ref(false)
const form = ref<PointPolicy | null>(null)

async function load() {
  loading.value = true
  try {
    form.value = await pointApi.getPolicy()
  } catch {
    ElMessage.error('포인트 정책을 불러오는데 실패했습니다.')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.value) return
  try {
    form.value = await pointApi.updatePolicy(form.value)
    ElMessage.success('저장되었습니다.')
  } catch {
    ElMessage.error('저장에 실패했습니다.')
    await load()
  }
}

onMounted(load)
</script>
