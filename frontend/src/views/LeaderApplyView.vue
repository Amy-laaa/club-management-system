<template>
  <div class="leader-apply-page">
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
        <h2 class="page-title">社团负责人资格申请</h2>
        <p class="page-desc">
          学生提交申请后由平台管理员审批，通过后账号角色升级为「社团负责人」，即可创建社团、发布活动。
        </p>
      </div>

      <!-- 学生视角：提交申请 -->
      <el-card v-if="isStudent" class="block" shadow="never">
        <div class="block-head"><span class="block-title">提交申请</span></div>
        <el-input
          v-model="reason"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="请说明申请理由，例如：拟发起成立 XX 社团，已有 X 名同学响应"
        />
        <div class="form-foot">
          <el-button type="primary" :loading="submitting" @click="submitApply">
            提交申请
          </el-button>
          <span class="hint">已有待审核申请时不可重复提交</span>
        </div>
      </el-card>

      <el-alert
        v-else-if="isLeader"
        class="block"
        type="success"
        :closable="false"
        show-icon
        title="您已是社团负责人"
        description="当前账号已具备社团负责人权限，可直接创建社团、发布活动与申请场地，无需重复申请。"
      />

      <!-- 我的申请记录 -->
      <el-card class="block" shadow="never">
        <div class="block-head">
          <span class="block-title">我的申请记录</span>
          <el-tag type="info" effect="plain">共 {{ myRows.length }} 条</el-tag>
        </div>
        <el-table :data="myRows" size="small" border>
          <el-table-column prop="applyId" label="编号" width="70" />
          <el-table-column label="申请理由" min-width="240">
            <template #default="{ row }">
              {{ row.reason || '（未填写）' }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status).type" size="small" effect="light">
                {{ statusTag(row.status).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审批意见" min-width="180">
            <template #default="{ row }">
              {{ row.reviewRemark || '—' }}
            </template>
          </el-table-column>
          <el-table-column label="申请时间" width="150">
            <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="审批时间" width="150">
            <template #default="{ row }">{{ fmt(row.reviewedAt) }}</template>
          </el-table-column>
          <el-empty
            v-if="myRows.length === 0"
            description="暂无申请记录"
            :image-size="60"
          />
        </el-table>
      </el-card>

      <!-- 管理员视角：审批 -->
      <el-card v-if="isAdmin" class="block" shadow="never">
        <div class="block-head">
          <span class="block-title">申请审批（管理员）</span>
          <el-radio-group v-model="adminStatus" size="small" @change="loadAdmin">
            <el-radio-button :value="0">待审核</el-radio-button>
            <el-radio-button :value="1">已通过</el-radio-button>
            <el-radio-button :value="2">已拒绝</el-radio-button>
          </el-radio-group>
        </div>

        <el-table :data="adminRows" size="small" border>
          <el-table-column prop="applyId" label="编号" width="70" />
          <el-table-column label="申请人" width="110">
            <template #default="{ row }">{{ row.realName }}</template>
          </el-table-column>
          <el-table-column label="学号" width="110">
            <template #default="{ row }">{{ maskStudentNo(row.studentNo) }}</template>
          </el-table-column>
          <el-table-column label="申请理由" min-width="200">
            <template #default="{ row }">{{ row.reason || '（未填写）' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status).type" size="small" effect="light">
                {{ statusTag(row.status).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请时间" width="150">
            <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column v-if="adminStatus === 0" label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" text @click="openAudit(row)">
                审批
              </el-button>
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

    <!-- 审批弹窗 -->
    <el-dialog v-model="auditVisible" title="审批负责人资格申请" width="440px">
      <div v-if="current" class="audit-info">
        <div><span class="k">申请人</span>{{ current.realName }}（{{ maskStudentNo(current.studentNo) }}）</div>
        <div><span class="k">申请理由</span>{{ current.reason || '（未填写）' }}</div>
      </div>
      <el-form label-width="80px" class="audit-form">
        <el-form-item label="审批结果">
          <el-radio-group v-model="auditForm.result">
            <el-radio :value="1">通过</el-radio>
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
      <div class="dialog-tip">
        通过后申请人角色升级为「社团负责人」，即可创建社团。
      </div>
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
import { ElMessage } from 'element-plus'
import {
  applyLeader,
  listLeaderApplies,
  listMyLeaderApplies,
  reviewLeaderApply,
} from '../api/leaderApply'
import { maskStudentNo } from '../utils/mask'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)

const reason = ref('')
const myRows = ref([])

const adminRows = ref([])
const adminTotal = ref(0)
const adminPage = ref(1)
const adminSize = ref(10)
const adminStatus = ref(0)

const auditVisible = ref(false)
const current = ref(null)
const auditForm = ref({ result: 1, remark: '' })

const isStudent = computed(() => userStore.role === 'STUDENT')
const isLeader = computed(() => userStore.role === 'LEADER')
const isAdmin = computed(() => ['SYS_ADMIN', 'UNION_ADMIN'].includes(userStore.role))

async function loadMine() {
  try {
    myRows.value = (await listMyLeaderApplies()) || []
  } catch (e) {
    myRows.value = []
  }
}

async function loadAdmin() {
  if (!isAdmin.value) return
  try {
    const data = await listLeaderApplies(adminStatus.value, {
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
    await applyLeader(reason.value || null)
    ElMessage.success('申请已提交，请等待管理员审批')
    reason.value = ''
    await loadMine()
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
    await reviewLeaderApply(current.value.applyId, {
      result: auditForm.value.result,
      remark: auditForm.value.remark || null,
    })
    ElMessage.success(auditForm.value.result === 1 ? '已通过，申请人已成为社团负责人' : '已驳回')
    auditVisible.value = false
    await loadAdmin()
    await loadMine()
  } finally {
    submitting.value = false
  }
}

function statusTag(status) {
  return (
    { 0: { text: '待审核', type: 'warning' }, 1: { text: '已通过', type: 'success' }, 2: { text: '已拒绝', type: 'danger' } }[
      status
    ] || { text: '未知', type: 'info' }
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
    await loadMine()
    await loadAdmin()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.leader-apply-page {
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

.form-foot {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.hint {
  font-size: 12px;
  color: #c0c4cc;
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
