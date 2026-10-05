<template>
  <div class="stats">
    <!-- 顶栏 -->
    <header class="topbar">
      <h1 class="logo">校园社团管理</h1>
      <nav class="nav">
        <router-link to="/clubList"><el-button text>社团</el-button></router-link>
        <router-link to="/activityList"><el-button text>活动</el-button></router-link>
        <router-link v-if="isAdmin" to="/applyList"><el-button text>审批工作台</el-button></router-link>
      </nav>
      <div class="topbar-right">
        <template v-if="userStore.isLogin">
          <span class="welcome">{{ userStore.realName }}</span>
          <el-button text @click="logout">退出</el-button>
        </template>
        <template v-else>
          <router-link to="/login"><el-button text>登录</el-button></router-link>
        </template>
      </div>
    </header>

    <main class="content" v-loading="loading">
      <div class="page-head">
        <div>
          <h2>数据统计看板</h2>
          <p class="tip">社团活跃度 / 活动参与率 / 场地使用率三组指标（社联管理员视图）</p>
        </div>
        <el-button type="primary" :disabled="!isAdmin" @click="doExport">
          导出 CSV 报表
        </el-button>
      </div>

      <el-alert
        v-if="!isAdmin"
        title="统计报表仅社联管理员可查看，请用 20260002 账号体验"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

      <!-- ============ 三组指标卡片 ============ -->
      <div class="cards">
        <el-card shadow="never" class="metric-card">
          <div class="metric-title">社团活跃度</div>
          <div class="metric-main">
            <span class="metric-value">{{ s.clubStats.activeClubs }}</span>
            <span class="metric-unit">/ {{ s.clubStats.total }} 个</span>
          </div>
          <div class="metric-desc">活跃社团（评分 ≥ 阈值）· 平均 {{ s.clubStats.avgMembers }} 名成员</div>
        </el-card>

        <el-card shadow="never" class="metric-card">
          <div class="metric-title">活动参与率</div>
          <div class="metric-main">
            <span class="metric-value">{{ pct(s.activityStats.avgSignupRate) }}</span>
            <span class="metric-unit">签到 / 名额</span>
          </div>
          <div class="metric-desc">
            共 {{ s.activityStats.total }} 场活动，本月 {{ s.activityStats.monthCount }} 场 ·
            {{ s.activityStats.checkedInTotal }}/{{ s.activityStats.capacityTotal }} 人
          </div>
        </el-card>

        <el-card shadow="never" class="metric-card">
          <div class="metric-title">场地使用率</div>
          <div class="metric-main">
            <span class="metric-value">{{ pct(s.venueStats.usageRate * 100, 2) }}</span>
            <span class="metric-unit">未来 {{ s.venueStats.windowDays }} 天</span>
          </div>
          <div class="metric-desc">
            {{ s.venueStats.usedSlots }} / {{ s.venueStats.slotsTotal }} 个时段被占用 ·
            {{ s.venueStats.total }} 个可用场地
          </div>
        </el-card>
      </div>

      <!-- ============ 明细榜 ============ -->
      <div class="tables">
        <el-card shadow="never">
          <template #header>社团活跃度 TOP</template>
          <el-table :data="s.clubStats.top3 || []" border>
            <el-table-column prop="clubName" label="社团" min-width="140" />
            <el-table-column prop="memberCount" label="成员数" width="90" align="center" />
            <el-table-column prop="activityCount" label="活动数" width="90" align="center" />
            <el-table-column label="活跃度评分" width="140" align="center">
              <template #default="{ row }">
                <el-progress :percentage="row.score" :stroke-width="12" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never">
          <template #header>场地使用 TOP</template>
          <el-table :data="s.venueStats.top3 || []" border>
            <el-table-column prop="venueName" label="场地" min-width="180" />
            <el-table-column prop="usedSlots" label="已占用时段" width="110" align="center" />
            <el-table-column label="使用强度" min-width="140">
              <template #default="{ row }">
                <el-progress
                  :percentage="strength(row.usedSlots)"
                  :stroke-width="12"
                  :color="row.usedSlots > 0 ? '#409eff' : '#e4e7ed'"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import { statsOverview, exportStatsCsv } from '../api/stats'

const router = useRouter()
const userStore = useUserStore()

const isAdmin = computed(() => ['UNION_ADMIN', 'SYS_ADMIN'].includes(userStore.role))
const loading = ref(false)

const emptyStats = () => ({
  clubStats: { activeClubs: 0, total: 0, avgMembers: 0, top3: [] },
  activityStats: { total: 0, monthCount: 0, avgSignupRate: 0, capacityTotal: 0, checkedInTotal: 0 },
  venueStats: { total: 0, usageRate: 0, usedSlots: 0, slotsTotal: 0, windowDays: 30, top3: [] }
})
const s = ref(emptyStats())

function pct(v, digits = 1) {
  return `${Number(v || 0).toFixed(digits)}%`
}

// 场地使用强度：以榜首场地为 100% 做相对刻度
function strength(usedSlots) {
  const max = Math.max(1, ...(s.value.venueStats.top3 || []).map(x => x.usedSlots || 0))
  return Math.round((usedSlots || 0) / max * 100)
}

async function load() {
  if (!isAdmin.value) return
  loading.value = true
  try {
    const res = await statsOverview()
    s.value = Object.assign(emptyStats(), res || {})
  } catch (e) {
    // 401/403 统一提示
  } finally {
    loading.value = false
  }
}

async function doExport() {
  try {
    await exportStatsCsv()
    ElMessage.success('CSV 报表已开始下载')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

function logout() {
  userStore.logout()
  router.push('/login')
}

onMounted(() => {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  load()
})
</script>

<style scoped>
.stats {
  min-height: 100vh;
  background: #f5f7fa;
}
.topbar {
  display: flex;
  align-items: center;
  gap: 24px;
  background: #fff;
  padding: 0 24px;
  height: 56px;
  border-bottom: 1px solid #e4e7ed;
}
.logo {
  font-size: 18px;
  margin: 0;
  color: #409eff;
}
.nav { display: flex; gap: 4px; }
.topbar-right { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.welcome { color: #606266; font-size: 14px; }
.content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-head h2 { margin: 0 0 4px; }
.tip { color: #909399; font-size: 13px; margin: 0; }
.cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
@media (max-width: 900px) {
  .cards { grid-template-columns: 1fr; }
}
.metric-title { color: #909399; font-size: 13px; margin-bottom: 8px; }
.metric-main {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.metric-value {
  font-size: 30px;
  font-weight: 700;
  color: #409eff;
}
.metric-unit { color: #909399; font-size: 13px; }
.metric-desc {
  margin-top: 8px;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
}
.tables {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
@media (max-width: 900px) {
  .tables { grid-template-columns: 1fr; }
}
</style>
