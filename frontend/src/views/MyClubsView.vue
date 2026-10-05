<template>
  <div class="my-clubs-page">
    <header class="topbar">
      <div class="topbar-inner">
        <el-button text @click="$router.push('/clubList')">← 社团列表</el-button>
        <span class="crumb">我的社团</span>
        <div class="topbar-right">
          <span class="welcome">{{ userStore.realName }}</span>
          <router-link v-if="leaderClubs.length" to="/members">
            <el-button text>成员管理</el-button>
          </router-link>
        </div>
      </div>
    </header>

    <main v-loading="loading" class="main">
      <!-- 概览 -->
      <div class="cards">
        <div class="stat-card">
          <span class="num">{{ joinedCount }}</span>
          <span class="label">已加入社团</span>
        </div>
        <div class="stat-card">
          <span class="num">{{ pendingCount }}</span>
          <span class="label">待审核申请</span>
        </div>
        <div class="stat-card">
          <span class="num">{{ leaderClubs.length }}</span>
          <span class="label">我负责的社团</span>
        </div>
      </div>

      <!-- 状态筛选（前端本地过滤） -->
      <div class="toolbar">
        <el-radio-group v-model="statusFilter">
          <el-radio-button :value="'all'">全部</el-radio-button>
          <el-radio-button :value="0">待审核</el-radio-button>
          <el-radio-button :value="1">正式成员</el-radio-button>
          <el-radio-button :value="2">已拒绝</el-radio-button>
          <el-radio-button :value="3">已退出</el-radio-button>
        </el-radio-group>
        <el-tag type="info" effect="plain">共 {{ filtered.length }} 条</el-tag>
      </div>

      <!-- 成员记录卡片 -->
      <el-card
        v-for="m in filtered"
        :key="m.membershipId"
        class="club-card"
        shadow="hover"
        @click="goClub(m.clubId)"
      >
        <div class="card-main">
          <div class="card-info">
            <div class="title-row">
              <span class="club-name">{{ m.clubName || `社团 #${m.clubId}` }}</span>
              <el-tag :type="myStatusTag(m.status).type" size="small" effect="light">
                {{ myStatusTag(m.status).text }}
              </el-tag>
              <el-tag :type="roleTag(m.memberRole).type" size="small" effect="plain">
                {{ roleTag(m.memberRole).text }}
              </el-tag>
              <el-tag v-if="clubOf(m.clubId)" :type="clubStatusTag(clubOf(m.clubId).status).type" size="small" effect="plain">
                社团{{ clubStatusTag(clubOf(m.clubId).status).text }}
              </el-tag>
            </div>
            <span v-if="m.applyReason" class="reason">申请理由：{{ m.applyReason }}</span>
            <span class="times">
              申请于 {{ fmtTime(m.createdAt) }}
              <template v-if="m.joinedAt"> · 加入于 {{ fmtTime(m.joinedAt) }}</template>
            </span>
          </div>

          <div class="card-actions" @click.stop>
            <template v-if="isLeaderRecord(m)">
              <el-button size="small" @click="$router.push('/members')">成员管理</el-button>
              <el-button size="small" type="primary" @click="$router.push('/memberAudit')">
                入社审批
              </el-button>
            </template>
            <el-button v-else size="small" text @click="goClub(m.clubId)">查看社团</el-button>
          </div>
        </div>
      </el-card>

      <el-empty
        v-if="!loading && filtered.length === 0"
        :description="list.length === 0 ? '你还没有加入任何社团' : '该状态下没有记录'"
      >
        <el-button type="primary" @click="$router.push('/clubList')">去社团列表逛逛</el-button>
      </el-empty>

      <el-card class="note-card" shadow="never">
        <div class="note-title">成员状态说明（文档表 2-13 / 2-21）</div>
        <div class="note-body">
          0 待审核：已提交入社申请，等社长审批 ·
          1 正式成员：审批通过，已写入入社时间 ·
          2 已拒绝：社长驳回，可直接重新申请 ·
          3 已退出：主动退社或被动移出，可重新申请。
          <br />
          数据库另有 <span class="mono">UNIQUE(user_id, club_id)</span> 约束兜底：同一学生对同一社团只存在一条成员记录，因此"重复申请"在库层即被拒绝。
        </div>
      </el-card>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listMyMemberships } from '../api/membership'
import { listClubs } from '../api/club'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const list = ref([])
const clubs = ref([])
const loading = ref(false)
const statusFilter = ref('all')

const filtered = computed(() =>
  statusFilter.value === 'all'
    ? list.value
    : list.value.filter((m) => m.status === statusFilter.value)
)

const leaderClubs = computed(() => list.value.filter(isLeaderRecord))
const joinedCount = computed(() => list.value.filter((m) => m.status === 1).length)
const pendingCount = computed(() => list.value.filter((m) => m.status === 0).length)

function isLeaderRecord(m) {
  return m.memberRole === 'LEADER' && m.status === 1
}

function clubOf(clubId) {
  return clubs.value.find((c) => c.clubId === clubId)
}

function fmtTime(t) {
  return (t || '').replace('T', ' ').slice(0, 16)
}

/** 我的成员状态: 0待审核 1正式成员 2已拒绝 3已退出（文档表 2-13） */
function myStatusTag(status) {
  const map = {
    0: { type: 'warning', text: '待审核' },
    1: { type: 'success', text: '正式成员' },
    2: { type: 'danger', text: '已拒绝' },
    3: { type: 'info', text: '已退出' },
  }
  return map[status] ?? { type: 'info', text: '未知' }
}

/** 社团状态: 0待审核 1已成立 2已注销（文档表 2-12） */
function clubStatusTag(status) {
  const map = {
    0: { type: 'warning', text: '待审核' },
    1: { type: 'success', text: '已成立' },
    2: { type: 'info', text: '已注销' },
  }
  return map[status] ?? { type: 'info', text: '—' }
}

function roleTag(role) {
  const map = {
    LEADER: { type: 'danger', text: '社长' },
    ADMIN: { type: 'warning', text: '管理员' },
    MEMBER: { type: 'primary', text: '成员' },
  }
  return map[role] ?? { type: 'info', text: role || '成员' }
}

/** GET /api/memberships/mine → List<Membership>（元素含 clubName） */
async function load() {
  loading.value = true
  try {
    list.value = (await listMyMemberships()) || []
  } catch (e) {
    // 统一拦截提示
  } finally {
    loading.value = false
  }
  // 补充社团状态（列表接口匿名可访问，失败不影响主数据）
  try {
    const data = await listClubs({ page: 1, size: 100 })
    clubs.value = data.rows || []
  } catch (e) {
    /* 忽略 */
  }
}

function goClub(clubId) {
  router.push({ path: '/clubDetail', query: { id: clubId } })
}

onMounted(() => {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: '/myClubs' } })
    return
  }
  load()
})
</script>

<style scoped>
.my-clubs-page {
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

.cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}
.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
}
.stat-card .num {
  display: block;
  font-size: 26px;
  font-weight: 700;
  color: #409eff;
}
.stat-card .label {
  display: block;
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.club-card {
  margin-bottom: 12px;
  cursor: pointer;
}
.card-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.card-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.club-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.reason {
  font-size: 13px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.times {
  font-size: 12px;
  color: #909399;
}
.card-actions {
  flex-shrink: 0;
}

.note-card {
  margin-top: 20px;
  background: #fafafa;
}
.note-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.note-body {
  font-size: 13px;
  color: #606266;
  line-height: 1.9;
}
.mono {
  font-family: Consolas, monospace;
}

/* 响应式：<768px 单列（文档要求） */
@media (max-width: 768px) {
  .cards {
    grid-template-columns: 1fr;
  }
  .topbar-inner {
    padding: 0 12px;
  }
  .main {
    padding: 16px 12px;
  }
  .card-main {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
