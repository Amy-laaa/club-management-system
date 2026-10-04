<template>
  <div class="my-reg-page">
    <header class="topbar">
      <div class="topbar-inner">
        <el-button text @click="$router.push('/activityList')">← 活动列表</el-button>
        <span class="crumb">我的报名</span>
        <div class="topbar-right">
          <span class="welcome">{{ userStore.realName }}</span>
        </div>
      </div>
    </header>

    <main v-loading="loading" class="main">
      <!-- 状态筛选 -->
      <div class="toolbar">
        <el-radio-group v-model="statusFilter" @change="applyFilter">
          <el-radio-button :value="'all'">全部</el-radio-button>
          <el-radio-button :value="0">已报名</el-radio-button>
          <el-radio-button :value="1">已签到</el-radio-button>
          <el-radio-button :value="3">候补中</el-radio-button>
          <el-radio-button :value="2">已取消</el-radio-button>
        </el-radio-group>
        <el-tag type="info" effect="plain">共 {{ filtered.length }} 条</el-tag>
      </div>

      <!-- 报名记录列表 -->
      <el-card
        v-for="reg in filtered"
        :key="reg.regId"
        class="reg-card"
        shadow="hover"
        @click="goActivity(reg.activityId)"
      >
        <div class="reg-main">
          <div class="reg-info">
            <div class="reg-title-row">
              <span class="reg-title">{{ reg.activityTitle }}</span>
              <el-tag :type="statusTag(reg.status).type" size="small" effect="light">
                {{ statusTag(reg.status).text }}
              </el-tag>
            </div>
            <span class="reg-time">🕐 {{ formatTime(reg.startTime) }}</span>
            <span class="reg-voucher">
              凭证码
              <b class="code">{{ reg.voucherCode }}</b>
            </span>
          </div>
          <div class="reg-right">
            <div class="reg-status-block" :class="statusClass(reg.status)">
              <span class="st-text">{{ statusTag(reg.status).text }}</span>
              <span class="st-checkin" v-if="reg.status === 1 && reg.checkinTime">
                {{ formatTime(reg.checkinTime) }}
              </span>
            </div>
            <el-button
              v-if="reg.status === 0 || reg.status === 3"
              text
              size="small"
              @click.stop="cancelOf(reg)"
            >
              取消报名
            </el-button>
          </div>
        </div>
      </el-card>

      <el-empty
        v-if="!loading && filtered.length === 0"
        description="还没有报名记录，去活动列表逛逛吧"
      >
        <el-button type="primary" @click="$router.push('/activityList')">
          浏览活动
        </el-button>
      </el-empty>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { myRegistrations, cancelSignup } from '../api/activity'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const list = ref([])
const loading = ref(false)
const statusFilter = ref('all')

const filtered = computed(() =>
  statusFilter.value === 'all'
    ? list.value
    : list.value.filter((r) => r.status === statusFilter.value)
)

async function load() {
  loading.value = true
  try {
    // 返回 List<Registration>: regId/activityId/activityTitle/startTime/voucherCode/status/checkinTime
    list.value = (await myRegistrations()) || []
  } catch (e) {
    // 拦截器统一弹错（401 会被守卫提前拦下）
  } finally {
    loading.value = false
  }
}

function applyFilter() {
  // 前端本地过滤，无需请求
}

async function cancelOf(reg) {
  try {
    await ElMessageBox.confirm(
      `确定取消「${reg.activityTitle}」的报名？已占用的名额将立即回补。`,
      '取消报名',
      { confirmButtonText: '确定取消', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await cancelSignup(reg.activityId)
    ElMessage.success('已取消报名，名额已回补')
    load()
  } catch (e) {
    // 拦截器统一弹错（已过截止时间等）
  }
}

function goActivity(activityId) {
  router.push({ path: '/activityDetail', query: { id: activityId } })
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

/** 报名状态: 0已报名 1已签到 2已取消 3候补（文档表 2-21） */
function statusTag(status) {
  const map = {
    0: { type: 'primary', text: '已报名' },
    1: { type: 'success', text: '已签到' },
    2: { type: 'info', text: '已取消' },
    3: { type: 'warning', text: '候补中' },
  }
  return map[status] ?? { type: 'info', text: '未知' }
}

function statusClass(status) {
  return { 0: 'ok', 1: 'done', 2: 'cancelled', 3: 'wait' }[status] ?? ''
}

onMounted(load)
</script>

<style scoped>
.my-reg-page {
  min-height: 100vh;
  background: #f0f2f5;
}
.topbar {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  position: sticky;
  top: 0;
  z-index: 10;
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
  flex: 1;
}
.welcome {
  color: #606266;
}

.main {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.reg-card {
  margin-bottom: 12px;
  cursor: pointer;
}
.reg-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.reg-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.reg-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.reg-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.reg-time {
  font-size: 13px;
  color: #909399;
}
.reg-voucher {
  font-size: 13px;
  color: #909399;
}
.reg-voucher .code {
  font-family: Consolas, monospace;
  letter-spacing: 1px;
  color: #409eff;
  margin-left: 4px;
}

.reg-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.reg-status-block {
  text-align: center;
  border-radius: 6px;
  padding: 6px 14px;
  min-width: 80px;
}
.reg-status-block.ok { background: #ecf5ff; }
.reg-status-block.done { background: #f0f9eb; }
.reg-status-block.wait { background: #fdf6ec; }
.reg-status-block.cancelled { background: #f4f4f5; }
.st-text {
  display: block;
  font-size: 13px;
  font-weight: 600;
}
.st-checkin {
  display: block;
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
</style>
