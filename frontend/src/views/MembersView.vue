<template>
  <div class="members-page">
    <header class="topbar">
      <div class="topbar-inner">
        <el-button text @click="$router.push('/myClubs')">← 我的社团</el-button>
        <span class="crumb">成员管理</span>
        <div class="topbar-right">
          <span class="welcome">{{ userStore.realName }}</span>
          <router-link v-if="clubId" to="/memberAudit">
            <el-button text>入社审批</el-button>
          </router-link>
        </div>
      </div>
    </header>

    <main v-loading="loading" class="main">
      <el-empty v-if="!loading && leaderClubs.length === 0" description="您还不是社团负责人，暂无可管理的社团">
        <el-button type="primary" @click="$router.push('/myClubs')">查看我的社团</el-button>
      </el-empty>

      <template v-else-if="leaderClubs.length">
        <div class="toolbar">
          <el-select v-model="clubId" style="width: 260px" @change="loadPendingCount">
            <el-option
              v-for="c in leaderClubs"
              :key="c.clubId"
              :label="c.clubName || `社团 #${c.clubId}`"
              :value="c.clubId"
            />
          </el-select>
          <el-tag type="danger" effect="plain">我的角色：社长（LEADER）</el-tag>
        </div>

        <!-- 指标卡 -->
        <div class="cards">
          <div class="stat-card">
            <span class="num warn">{{ pendingTotal }}</span>
            <span class="label">待审核入社申请</span>
          </div>
          <div class="stat-card">
            <span class="num muted">—</span>
            <span class="label">正式成员（接口待补）</span>
          </div>
          <div class="stat-card">
            <span class="num">{{ leaderClubs.length }}</span>
            <span class="label">我负责的社团</span>
          </div>
        </div>

        <!-- 入社申请概览（真实数据） -->
        <el-card class="block" shadow="never">
          <div class="block-head">
            <span class="block-title">入社申请</span>
            <el-button type="primary" size="small" @click="$router.push('/memberAudit')">
              去审批（{{ pendingTotal }}）
            </el-button>
          </div>
          <div class="block-body">
            <template v-if="pendingTotal > 0">
              当前有 <b>{{ pendingTotal }}</b> 条待审核申请等待处理。
              通过后申请人立即成为正式成员并写
              入入社时间；驳回必须填写原因，操作落
              <span class="mono">t_audit_log</span> 审计表。
            </template>
            <template v-else>
              暂无待审核申请。学生可在社团详情页点「申请入社」发起，申请会出现在这里。
            </template>
          </div>
        </el-card>

        <!-- 正式成员名单：后端缺口显性化 -->
        <el-card class="block" shadow="never">
          <div class="block-head">
            <span class="block-title">正式成员名单</span>
            <el-tag type="warning" size="small" effect="light">后端接口未实现</el-tag>
          </div>

          <el-alert
            type="warning"
            :closable="false"
            show-icon
            title="成员名单查询接口缺失"
            description="t_membership 表已建好、数据齐全（含角色、入社时间），但后端仅提供 status=0 的待审核申请查询，没有 status=1 的正式成员名单接口，因此本区域暂无法渲染真实名单。已按表 2-13 拟定下列契约，待后端实现后前端无需改结构、直接接数据。"
          />

          <el-table :data="suggestedApis" size="small" border class="api-table">
            <el-table-column prop="method" label="方法" width="80" />
            <el-table-column prop="path" label="路径" min-width="300" />
            <el-table-column prop="desc" label="说明" min-width="220" />
            <el-table-column prop="role" label="权限" width="100" />
          </el-table>

          <div class="block-body gap-note">
            替代方案：也可复用 <span class="mono">GET /api/clubs/{clubId}</span> 增加
            <span class="mono">memberCount</span> 统计字段（当前该字段返回 null），
            先支撑"成员规模"展示，再补名单接口。
          </div>
        </el-card>

        <!-- 角色与状态说明 -->
        <el-card class="block" shadow="never">
          <div class="block-head"><span class="block-title">成员角色与状态（文档表 2-13 / 2-21）</span></div>
          <el-table :data="roleRows" size="small" border>
            <el-table-column prop="code" label="取值" width="120" />
            <el-table-column prop="text" label="含义" min-width="220" />
            <el-table-column prop="note" label="说明" min-width="280" />
          </el-table>
        </el-card>
      </template>
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listMyMemberships, listPendingMemberships } from '../api/membership'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const leaderClubs = ref([])
const clubId = ref(null)
const pendingTotal = ref(0)

/** 建议契约（依据文档表 2-13 与 2.2 节 MVC 资源表） */
const suggestedApis = [
  { method: 'GET', path: '/api/clubs/{clubId}/members?status=&page=&size=', desc: '社团成员分页名单（含角色、入社时间、脱敏学号）', role: '社长' },
  { method: 'POST', path: '/api/club/memberships/{id}/remove', desc: '移出成员（status → 3 已退出，软删除保留审计）', role: '社长' },
  { method: 'POST', path: '/api/club/memberships/{id}/role', desc: '调整成员角色 ADMIN/MEMBER', role: '社长' },
]

const roleRows = [
  { code: 'LEADER', text: '社长 / 负责人', note: '创建社团者；唯一可审批入社、发布活动、申请场地' },
  { code: 'ADMIN', text: '社团管理员', note: '辅助管理（按文档预留，当前后端未开放设置入口）' },
  { code: 'MEMBER', text: '普通成员', note: '可查看本社团内部活动' },
  { code: 'status=0', text: '待审核', note: '申请已提交，等待社长审批' },
  { code: 'status=1', text: '正式成员', note: '审批通过，joined_at 写入时间' },
  { code: 'status=2', text: '已拒绝', note: '驳回，学生可重新申请' },
  { code: 'status=3', text: '已退出', note: '主动退出或被移出，可重新申请' },
]

/** 待审条数：GET /api/club/memberships?clubId=&page=1&size=1 → 只取 total */
async function loadPendingCount() {
  if (!clubId.value) return
  try {
    const data = await listPendingMemberships(clubId.value, { page: 1, size: 1 })
    pendingTotal.value = data.total || 0
  } catch (e) {
    pendingTotal.value = 0
  }
}

onMounted(async () => {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: '/members' } })
    return
  }
  loading.value = true
  try {
    const list = (await listMyMemberships()) || []
    leaderClubs.value = list.filter((m) => m.memberRole === 'LEADER' && m.status === 1)
    if (leaderClubs.value.length) {
      // 默认选中"有待审申请"的社团（便于直接处理）
      const counts = await Promise.all(
        leaderClubs.value.map((c) =>
          listPendingMemberships(c.clubId, { page: 1, size: 1 })
            .then((d) => d.total || 0)
            .catch(() => 0)
        )
      )
      const hit = counts.findIndex((n) => n > 0)
      clubId.value = leaderClubs.value[hit >= 0 ? hit : 0].clubId
      await loadPendingCount()
    }
  } catch (e) {
    // 统一提示
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.members-page {
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
  max-width: 1000px;
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
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
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
.stat-card .num.warn {
  color: #e6a23c;
}
.stat-card .num.muted {
  color: #c0c4cc;
}
.stat-card .label {
  display: block;
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.block {
  margin-bottom: 16px;
}
.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.block-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.block-body {
  font-size: 14px;
  color: #606266;
  line-height: 1.9;
}
.gap-note {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e4e7ed;
  font-size: 13px;
}
.api-table {
  margin-top: 12px;
}
.mono {
  font-family: Consolas, monospace;
  background: #f5f7fa;
  padding: 1px 4px;
  border-radius: 3px;
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
}
</style>
