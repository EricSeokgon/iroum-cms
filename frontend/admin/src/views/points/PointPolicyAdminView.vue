<template>
  <div data-testid="point-policy-admin">
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-gray-800">포인트 정책 관리</h2>
      <p class="mt-1 text-sm text-gray-500">
        참여 활동(게시글·댓글·좋아요)에 지급할 포인트와 시스템 활성화 여부를 설정합니다.
      </p>
    </div>

    <el-card v-loading="loading" style="max-width: 560px">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="160px"
        data-testid="point-policy-form"
      >
        <el-form-item label="포인트 시스템 활성화">
          <el-switch v-model="form.enabled" data-testid="policy-enabled" />
          <span class="ml-3 text-sm text-gray-500">
            비활성화 시 어떤 활동에도 포인트가 지급되지 않습니다.
          </span>
        </el-form-item>
        <el-form-item label="게시글 작성 포인트" prop="postPoints">
          <el-input-number
            v-model="form.postPoints"
            :min="0"
            controls-position="right"
            data-testid="policy-post"
          />
        </el-form-item>
        <el-form-item label="댓글 작성 포인트" prop="commentPoints">
          <el-input-number
            v-model="form.commentPoints"
            :min="0"
            controls-position="right"
            data-testid="policy-comment"
          />
        </el-form-item>
        <el-form-item label="좋아요 포인트" prop="likePoints">
          <el-input-number
            v-model="form.likePoints"
            :min="0"
            controls-position="right"
            data-testid="policy-like"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="saving"
            data-testid="policy-save-btn"
            @click="onSave"
          >
            저장
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
// SPEC-CMS-POINTS-001 — 포인트 정책 관리 화면 (REQ-PNT-005)
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getPointPolicy, updatePointPolicy, type PointPolicy } from '@/api/point'

const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<PointPolicy>({
  enabled: false,
  postPoints: 0,
  commentPoints: 0,
  likePoints: 0,
})

const rules: FormRules<PointPolicy> = {
  postPoints: [{ type: 'number', min: 0, message: '0 이상의 값을 입력하세요.', trigger: 'blur' }],
  commentPoints: [{ type: 'number', min: 0, message: '0 이상의 값을 입력하세요.', trigger: 'blur' }],
  likePoints: [{ type: 'number', min: 0, message: '0 이상의 값을 입력하세요.', trigger: 'blur' }],
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await getPointPolicy()
    form.enabled = data.enabled
    form.postPoints = data.postPoints
    form.commentPoints = data.commentPoints
    form.likePoints = data.likePoints
  } catch {
    ElMessage.error('포인트 정책을 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

async function onSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const { data } = await updatePointPolicy({
      enabled: form.enabled,
      postPoints: form.postPoints,
      commentPoints: form.commentPoints,
      likePoints: form.likePoints,
    })
    form.enabled = data.enabled
    form.postPoints = data.postPoints
    form.commentPoints = data.commentPoints
    form.likePoints = data.likePoints
    ElMessage.success('포인트 정책이 저장되었습니다.')
  } catch {
    ElMessage.error('저장에 실패했습니다.')
  } finally {
    saving.value = false
  }
}
</script>
