<template>
  <div class="club-detail-page">
    <header class="topbar">
      <div class="topbar-inner">
        <el-button text @click="$router.back()">← 返回</el-button>
        <span class="crumb">社团详情</span>
      </div>
    </header>

    <main v-loading="loading" class="main">
      <el-card v-if="club" shadow="never">
        <!-- 头部：名称 + 分类 + 纳新状态 -->
        <div class="detail-head">
          <div class="head-left">
            <h2 class="club-name">{{ club.clubName }}</h2>
            <el-tag :type="categoryTag(club.category)" effect="light">
              {{ club.category }}
            </el-tag>
            <el-tag v-if="recruiting" type="success" effect="plain" size="small">纳新中</el-tag>
            <el-tag v-else type="info" effect="plain" size="small">已停止纳新</el-tag>
          </div>
          <div class="head-right">
            <el-button
              v-if="!applied && !isMember"
              type="primary"
              :disabled="applyDisabled"
              :loading="submitting"
              @click="openApply"
            >
              申请入社
            </el-button>
            <el-tag v-else-if="applied" type="warning" size="large">申请审核中</el-tag>
            <el-tag v-else type="success" size="large">已是成员</el-tag>
          </div>
        </div>

        <!-- 基本信息 -->
        <el-descriptions :column="2" border class="desc">
          <el-descriptions-item label="负责人">
            {{ club.leaderName || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="指导老师">
            {{ club.advisor || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="成员数">
            {{ club.memberCount ?? '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="纳新截止">
            {{ formatTime(club.recruitDeadline) || '长期纳新' }}
          </el-descriptions-item>
          <el-descriptions-item label="成立时间" :span="2">
            {{ formatTime(club.createdAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 简介 -->
        <section class="block">
          <h3 class="block-title">社团简介</h3>
          <p class="text">{{ club.intro || '暂无简介' }}</p>
        </section>

        <!-- 章程 -->
        <section class="block">
          <h3 class="block-title">社团章程</h3>
          <p class="text charter">{{ club.charter || '暂无章程' }}</p>
        </section>

        <el-alert
          v-if="!userStore.isLogin"
          type="info"
          :closable="false"
          show-icon
          title="登录后即可申请加入社团"
          class="tip"
        />
        <el-alert
          v-else-if="!recruiting && !applied && !isMember"
          type="warning"
          :closable="false"
          show-icon
          title="该社团已停止纳新，暂不可申请"
          class="tip"
        />
      </el-card>

      <el-empty v-else-if="!loading" description="社团不存在或已被删除" />
    </main>

    <!-- 申请入社弹窗 -->
    <el-dialog v-model="applyVisible" title="申请加入社团" width="440px">
      <p class="dialog-tip">申请加入「{{ club?.clubName }}」，请填写申请理由：</p>
      <el-input
        v-model="applyReason"
        type="textarea"
        :rows="4"
        maxlength="200"
        show-word-limit
        placeholder="简要说明你的兴趣或相关经历（选填）"
      />
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitApply">
          提交申请
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getClub, applyJoinClub } from '../api/club'
import { listMyMemberships } from '../api/membership'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const clubId = Number(route.query.id)
const club = ref(null)
const loading = ref(false)
const submitting = ref(false)
const applyVisible = ref(false)
const applyReason = ref('')
/** 当前用户对该社团的成员记录 status: 0待审核 1正式成员 2已拒绝 3已退出 */
const myStatus = ref(null)

const applied = computed(() => myStatus.value === 0)
const isMember = computed(() => myStatus.value === 1)

/** 是否处于纳新期 */
const recruiting = computed(() => {
  if (!club.value) return false
  if (!club.value.recruitDeadline) return true
  return new Date(club.value.recruitDeadline) > new Date()
})

const applyDisabled = computed(() => !userStore.isLogin || !recruiting.value)

async function loadClub() {
  loading.value = true
  try {
    club.value = await getClub(clubId)
  } catch (e) {
    club.value = null
  } finally {
    loading.value = false
  }
}

/** 查询当前用户在该社团的成员状态（未登录则跳过） */
async function loadMyStatus() {
  if (!userStore.isLogin) {
    myStatus.value = null
    return
  }
  try {
    const list = await listMyMemberships()
    const mine = (list || []).find((m) => m.clubId === clubId)
    // 已退出(3)视为可重新申请
    myStatus.value = mine && mine.status !== 3 ? mine.status : null
  } catch (e) {
    myStatus.value = null
  }
}

function openApply() {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  applyReason.value = ''
  applyVisible.value = true
}

async function submitApply() {
  submitting.value = true
  try {
    await applyJoinClub(clubId, applyReason.value)
    ElMessage.success('申请已提交，等待社团负责人审核')
    applyVisible.value = false
    myStatus.value = 0
    loadMyStatus()
  } catch (e) {
    // 错误提示已由拦截器统一弹出（含 409 重复申请）
  } finally {
    submitting.value = false
  }
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

const categoryColors = {
  学术: '',
  体育: 'success',
  文艺: 'warning',
  实践: 'danger',
}
function categoryTag(category) {
  return categoryColors[category] ?? 'info'
}

onMounted(() => {
  loadClub()
  loadMyStatus()
})
</script>

<style scoped>
.club-detail-page {
  min-height: 100vh;
  background: #f0f2f5;
}
.topbar {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.topbar-inner {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 24px;
  height: 56px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.crumb {
  color: #909399;
  font-size: 14px;
}
.main {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
}
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.club-name {
  margin: 0;
  font-size: 22px;
  color: #303133;
}
.desc {
  margin-bottom: 20px;
}
.block {
  margin-top: 20px;
}
.block-title {
  font-size: 15px;
  margin: 0 0 8px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
  color: #303133;
}
.text {
  margin: 0;
  color: #606266;
  line-height: 1.8;
  white-space: pre-wrap;
}
.charter {
  color: #909399;
  font-size: 13px;
}
.tip {
  margin-top: 20px;
}
.dialog-tip {
  margin: 0 0 12px;
  color: #606266;
}
</style>
