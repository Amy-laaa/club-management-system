<template>
  <div class="dissolve-page">
    <header class="topbar">
      <div class="topbar-inner">
        <h1 class="logo">校园社团管理</h1>
        <nav class="nav">
          <router-link to="/clubList">社团</router-link>
          <router-link to="/activityList">活动</router-link>
          <router-link to="/notice">公告</router-link>
        </nav>
        <div class="topbar-right">
          <span class="welcome">{{ userStore.realName }}</span>
          <router-link to="/myClubs">
            <el-button text>我的社团</el-button>
          </router-link>
          <el-button text @click="logout">退出</el-button>
        </div>
      </div>
    </header>

    <main v-loading="loading" class="main">
      <div class="page-head">
        <h2 class="page-title">社团解散</h2>
        <p class="page-desc">
          负责人提交解散申请 → 管理员审批 → 负责人执行注销。注销后社团状态置为「已注销」，
          该社团全部生效成员记录同步转为已退出。
        </p>
      </div>

      <!-- 社长视角 -->
      <template v-if="leaderClubs.length">
        <el-card class="block" shadow="never">
          <div class="block-head"><span class="block-title">我负责的社团</span></div>
          <el-select v-model="clubId" placeholder="选择社团" class="club-select" @change="loadStatus">
            <el-option
              v-for="c in leaderClubs"
              :key="c.clubId"
              :label="c.clubName || `社团 #${c.clubId}`"
              :value="c.clubId"
            />
          </el-select>

          <div v-if="clubId" class="status-box">
            <div class="status-row">
              <span class="k">当前解散申请状态</span>
              <el-tag v-if="latest" :type="dissolveTag(latest.status).type" effect="light">
                {{ dissolveTag(latest.status).text }}
              </el-tag>
              <el-tag v-else type="info" effect="plain">无申请记录</el-tag>
            </div>
            <div v-if="latest" class="status-detail">
              <div><span class="k">解散理由</span>{{ latest.reason || '（未填写）' }}</div>
              <div><span class="k">申请时间</span>{{ fmt(latest.createdAt) }}</div>
              <div v-if="latest.reviewRemark">
                <span class="k">审批意见</span>{{ latest.reviewRemark }}
              </div>
              <div v-if="latest.executedAt">
                <span class="k">注销时间</span>{{ fmt(latest.executedAt) }}
              </div>
            </div>

            <div class="ops">
              <el-button
                v-if="canApply"
                type="primary"
                :loading="submitting"
                @click="applyVisible = true"
              >
                申请解散
              </el-button>
              <el-button
                v-if="latest && latest.status === 1"
                type="danger"
                :loading="submitting"
                @click="handleExecute"
              >
                执行注销
              </el-button>
              <span v-if="latest && latest.status === 0" class="hint">
                申请已提交，等待管理员审批
              </span>
            </div>
          </div>
        </el-card>
      </template>

      <el-empty
        v-else-if="!isAdmin && !loading"
        description="您不是任何社团的负责人，无法提交解散申请"
        :image-size="80"
      />

      <!-- 管理员视角：审批 -->
      <el-card v-if="isAdmin" class="block" shadow="never">
        <div class="block-head">
          <span class="block-title">解散申请审批（管理员）</span>
          <el-radio-group v-model="adminStatus" size="small" @change="loadAdmin">
            <el-radio-button :value="0">待审批</el-radio-button>
            <el-radio-button :value="1">已批准</el-radio-button>
            <el-radio-button :value="2">已驳回</el-radio-button>
            <el-radio-button :value="3">已注销</el-radio-button>
          </el-radio-group>
        </div>

        <el-table :data="adminRows" size="small" border>
          <el-table-column prop="dissolveId" label="编号" width="70" />
          <el-table-column label="社团" min-width="160">
            <template #default="{ row }">
              {{ row.clubName || `社团 #${row.clubId}` }}
              <el-tag v-if="row.category" size="small" effect="plain" class="ml">
                {{ row.category }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="applicantName" label="申请人" width="100" />
          <el-table-column label="解散理由" min-width="200">
            <template #default="{ row }">{{ row.reason || '（未填写）' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="dissolveTag(row.status).type" size="small" effect="light">
                {{ dissolveTag(row.status).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请时间" width="150">
            <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column v-if="adminStatus === 0" label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" text @click="openAudit(row)">审批</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="adminPage"
            :page-size="adminSize"
            :total="adminTotal"
            layout="total, prev, pager, next"
            @current-change="loadAdmin"
          />
        </div>
      </el-card>
    </main>

    <!-- 申请解散 -->
    <el-dialog v-model="applyVisible" title="提交解散申请" width="440px">
      <el-input
        v-model="reason"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="请说明解散理由"
      />
      <div class="dialog-tip">提交后需管理员审批，批准后才能执行注销。</div>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitApply">提交</el-button>
      </template>
    </el-dialog>

    <!-- 审批弹窗 -->
    <el-dialog v-model="auditVisible" title="审批解散申请" width="440px">
      <div v-if="current" class="audit-info">
        <div><span class="k">社团</span>{{ current.clubName || `社团 #${current.clubId}` }}</div>
        <div><span class="k">申请人</span>{{ current.applicantName }}</div>
        <div><span class="k">解散理由</span>{{ current.reason || '（未填写）' }}</div>
      </div>
      <el-form label-width="80px" class="audit-form">
        <el-form-item label="审批结果">
          <el-radio-group v-model="auditForm.result">
            <el-radio :value="1">批准</el-radio>
            <el-radio :value="0">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input
            v-model="auditForm.remark"
            type="textarea"
            :rows="3"
            maxlength="200"
            :placeholder="auditForm.result === 0 ? '驳回必须填写原因' : '选填'"
          />
        </el-form-item>
      </el-form>
      <div class="dialog-tip">批准后由负责人执行注销，社团才真正解散。</div>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAudit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  applyDissolve,
  executeDissolve,
  getDissolveStatus,
  listDissolves,
  reviewDissolve,
} from '../api/dissolve'
import { listMyMemberships } from '../api/membership'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)

const leaderClubs = ref([])
const clubId = ref(null)
const latest = ref(null)

const applyVisible = ref(false)
const reason = ref('')

const adminRows = ref([])
const adminTotal = ref(0)
const adminPage = ref(1)
const adminSize = ref(10)
const adminStatus = ref(0)

const auditVisible = ref(false)
const current = ref(null)
const auditForm = ref({ result: 1, remark: '' })

const isAdmin = computed(() => ['SYS_ADMIN', 'UNION_ADMIN'].includes(userStore.role))
/** 无申请记录 / 已驳回 / 已注销 时都可重新申请；待审批或已批准则不可 */
const canApply = computed(
  () => !latest.value || latest.value.status === 2 || latest.value.status === 3
)

async function loadMyClubs() {
  try {
    const list = (await listMyMemberships()) || []
    leaderClubs.value = list.filter((m) => m.memberRole === 'LEADER' && m.status === 1)
    if (leaderClubs.value.length && !clubId.value) {
      clubId.value = leaderClubs.value[0].clubId
    }
  } catch (e) {
    leaderClubs.value = []
  }
}

async function loadStatus() {
  if (!clubId.value) return
  try {
    latest.value = (await getDissolveStatus(clubId.value)) || null
  } catch (e) {
    latest.value = null
  }
}

async function loadAdmin() {
  if (!isAdmin.value) return
  try {
    const data = await listDissolves(adminStatus.value, {
      page: adminPage.value,
      size: adminSize.value,
    })
    adminRows.value = data?.rows || []
    adminTotal.value = data?.total || 0
  } catch (e) {
    adminRows.value = []
    adminTotal.value = 0
  }
}

async function submitApply() {
  submitting.value = true
  try {
    await applyDissolve(clubId.value, reason.value || null)
    ElMessage.success('解散申请已提交，等待管理员审批')
    applyVisible.value = false
    reason.value = ''
    await loadStatus()
    await loadAdmin()
  } finally {
    submitting.value = false
  }
}

async function handleExecute() {
  try {
    await ElMessageBox.confirm(
      '执行注销后社团将不可恢复，且该社团全部成员记录转为已退出。确认执行？',
      '危险操作确认',
      { type: 'warning', confirmButtonText: '确认注销', cancelButtonText: '取消' }
    )
  } catch (e) {
    return // 用户取消
  }
  submitting.value = true
  try {
    await executeDissolve(clubId.value)
    ElMessage.success('社团已注销')
    await loadStatus()
    await loadAdmin()
    await loadMyClubs()
  } finally {
    submitting.value = false
  }
}

function openAudit(row) {
  current.value = row
  auditForm.value = { result: 1, remark: '' }
  auditVisible.value = true
}

async function submitAudit() {
  if (auditForm.value.result === 0 && !auditForm.value.remark.trim()) {
    ElMessage.warning('驳回时请填写原因')
    return
  }
  submitting.value = true
  try {
    await reviewDissolve(current.value.dissolveId, {
      result: auditForm.value.result,
      remark: auditForm.value.remark || null,
    })
    ElMessage.success(auditForm.value.result === 1 ? '已批准，等待负责人执行注销' : '已驳回')
    auditVisible.value = false
    await loadAdmin()
    await loadStatus()
  } finally {
    submitting.value = false
  }
}

function dissolveTag(status) {
  return (
    {
      0: { text: '待审批', type: 'warning' },
      1: { text: '已批准待执行', type: 'primary' },
      2: { text: '已驳回', type: 'danger' },
      3: { text: '已注销', type: 'info' },
    }[status] || { text: '未知', type: 'info' }
  )
}

function fmt(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 16)
}

function logout() {
  userStore.clear()
  router.push('/login')
}

onMounted(async () => {
  loading.value = true
  try {
    await loadMyClubs()
    await loadStatus()
    await loadAdmin()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.dissolve-page {
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
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 24px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.logo {
  font-size: 18px;
  margin: 0 32px 0 0;
  color: #409eff;
}
.nav {
  display: flex;
  gap: 16px;
  flex: 1;
}
.nav a {
  color: #606266;
  text-decoration: none;
  font-size: 14px;
  padding: 4px 0;
  border-bottom: 2px solid transparent;
}
.nav a:hover {
  color: #409eff;
  border-bottom-color: #409eff;
}
.welcome {
  margin-right: 12px;
  color: #606266;
}

.main {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px;
}
.page-head {
  margin-bottom: 16px;
}
.page-title {
  font-size: 20px;
  margin: 0 0 6px;
  color: #303133;
}
.page-desc {
  margin: 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.7;
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

.club-select {
  width: 280px;
}

.status-box {
  margin-top: 16px;
  padding: 16px;
  background: #fafafa;
  border-radius: 4px;
}
.status-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}
.status-detail {
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
  line-height: 2;
}
.status-detail .k,
.status-row .k {
  display: inline-block;
  width: 120px;
  color: #909399;
}
.ops {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.hint {
  font-size: 12px;
  color: #c0c4cc;
}

.ml {
  margin-left: 6px;
}

.audit-info {
  font-size: 13px;
  color: #606266;
  line-height: 2;
  margin-bottom: 8px;
}
.audit-info .k {
  display: inline-block;
  width: 70px;
  color: #909399;
}
.audit-form {
  margin-top: 8px;
}
.dialog-tip {
  margin-top: 12px;
  font-size: 12px;
  color: #909399;
  background: #fafafa;
  padding: 8px 12px;
  border-radius: 4px;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
