<template>
  <div class="member-audit-page">
    <header class="topbar">
      <div class="topbar-inner">
        <el-button text @click="$router.push('/members')">← 成员管理</el-button>
        <span class="crumb">入社审批</span>
        <div class="topbar-right">
          <span class="welcome">{{ userStore.realName }}</span>
        </div>
      </div>
    </header>

    <main v-loading="loading" class="main">
      <!-- 不是负责人 -->
      <el-empty
        v-if="!authChecked"
        :description="userStore.isLogin ? '正在加载…' : '请先登录'"
      />

      <el-empty v-else-if="leaderClubs.length === 0" description="您还不是社团负责人，无法审批入社申请">
        <el-button type="primary" @click="$router.push('/myClubs')">查看我的社团</el-button>
      </el-empty>

      <template v-else>
        <div class="toolbar">
          <el-select v-model="clubId" style="width: 260px" @change="onClubChange">
            <el-option
              v-for="c in leaderClubs"
              :key="c.clubId"
              :label="c.clubName || `社团 #${c.clubId}`"
              :value="c.clubId"
            />
          </el-select>
          <el-tag type="info" effect="plain">待审核 {{ total }} 条</el-tag>
        </div>

        <el-alert
          class="hint"
          type="info"
          :closable="false"
          show-icon
          title="审批规则"
          description="仅本社团负责人可审批；通过后申请人立即成为正式成员（写入入社时间），驳回必须填写原因，操作全部落 t_audit_log 审计表。"
        />

        <el-table v-loading="tableLoading" :data="rows" border stripe class="table">
          <el-table-column prop="membershipId" label="申请编号" width="90" />
          <el-table-column label="学号" width="130">
            <template #default="{ row }">
              <span class="mono">{{ maskStudentNo(row.studentNo) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="realName" label="姓名" width="100" />
          <el-table-column
            prop="applyReason"
            label="申请理由"
            min-width="220"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{ row.applyReason || '（未填写）' }}</template>
          </el-table-column>
          <el-table-column label="申请时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="openAudit(row)">审批</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="当前没有待审核的入社申请" :image-size="80" />
          </template>
        </el-table>

        <div class="pager">
          <el-pagination
            layout="prev, pager, next, total"
            :total="total"
            :page-size="PAGE_SIZE"
            :current-page="page"
            @current-change="onPageChange"
          />
        </div>
      </template>
    </main>

    <!-- 审批弹窗 -->
    <el-dialog v-model="dialogVisible" title="审批入社申请" width="480px">
      <div class="audit-summary">
        <div class="row">
          <span class="k">申请人</span>
          <span class="v">
            {{ current?.realName }}
            <span class="mono muted">（{{ maskStudentNo(current?.studentNo) }}）</span>
          </span>
        </div>
        <div class="row">
          <span class="k">申请理由</span>
          <span class="v">{{ current?.applyReason || '（未填写）' }}</span>
        </div>
      </div>

      <el-form label-width="80px" style="margin-top: 12px">
        <el-form-item label="审批结果">
          <el-radio-group v-model="auditForm.result">
            <el-radio :value="1">通过（转为正式成员）</el-radio>
            <el-radio :value="0">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input
            v-model="auditForm.remark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            :placeholder="
              auditForm.result === 0
                ? '驳回时必填原因（申请人可见）'
                : '选填，如：欢迎加入，请关注群公告'
            "
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAudit">提交审批</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listMyMemberships, listPendingMemberships, auditMembership } from '../api/membership'
import { useUserStore } from '../stores/user'
import { maskStudentNo } from '../utils/mask'

const router = useRouter()
const userStore = useUserStore()

const PAGE_SIZE = 10

const authChecked = ref(false)
const leaderClubs = ref([])
const clubId = ref(null)

const rows = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const tableLoading = ref(false)

const dialogVisible = ref(false)
const submitting = ref(false)
const current = ref(null)
const auditForm = ref({ result: 1, remark: '' })

const currentClub = computed(() => leaderClubs.value.find((c) => c.clubId === clubId.value))

function fmtTime(t) {
  return (t || '').replace('T', ' ').slice(0, 16)
}

/** 待审列表：GET /api/club/memberships?clubId=&page=&size= → { total, page, size, rows } */
async function loadPending() {
  if (!clubId.value) return
  tableLoading.value = true
  try {
    const data = await listPendingMemberships(clubId.value, { page: page.value, size: PAGE_SIZE })
    rows.value = data.rows || []
    total.value = data.total || 0
  } catch (e) {
    // 403 非本社团负责人 / 401 未登录 由统一拦截提示
    rows.value = []
    total.value = 0
  } finally {
    tableLoading.value = false
  }
}

async function init() {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: '/memberAudit' } })
    return
  }
  loading.value = true
  try {
    const list = (await listMyMemberships()) || []
    // 我负责的社团 = membership 中角色为 LEADER 且已生效的记录
    leaderClubs.value = list.filter((m) => m.memberRole === 'LEADER' && m.status === 1)
    if (leaderClubs.value.length) {
      // 默认选中"有待审申请"的社团（便于直接处理，避免打开时看到空列表）
      const counts = await Promise.all(
        leaderClubs.value.map((c) =>
          listPendingMemberships(c.clubId, { page: 1, size: 1 })
            .then((d) => d.total || 0)
            .catch(() => 0)
        )
      )
      const hit = counts.findIndex((n) => n > 0)
      clubId.value = leaderClubs.value[hit >= 0 ? hit : 0].clubId
      await loadPending()
    }
  } catch (e) {
    // 统一提示
  } finally {
    loading.value = false
    authChecked.value = true
  }
}

function onClubChange() {
  page.value = 1
  loadPending()
}

function onPageChange(p) {
  page.value = p
  loadPending()
}

function openAudit(row) {
  current.value = row
  auditForm.value = { result: 1, remark: '' }
  dialogVisible.value = true
}

/**
 * 提交审批：POST /api/club/memberships/{id}/audit  { result: 0驳回/1通过, remark }
 * 后端强校验：拒绝必须填原因；申请已被处理会返回 409
 */
async function submitAudit() {
  if (auditForm.value.result === 0 && !auditForm.value.remark.trim()) {
    ElMessage.warning('驳回时请填写原因')
    return
  }
  submitting.value = true
  try {
    await auditMembership(current.value.membershipId, {
      result: auditForm.value.result,
      remark: auditForm.value.remark || null,
    })
    dialogVisible.value = false
    ElMessage.success(
      auditForm.value.result === 1
        ? `已通过，${current.value.realName} 成为「${currentClub.value?.clubName || '本社团'}」正式成员`
        : '已驳回该申请'
    )
    loadPending()
  } catch (e) {
    // 409 申请状态已变更 等由统一拦截提示，此处刷新以同步最新状态
    loadPending()
  } finally {
    submitting.value = false
  }
}

onMounted(init)
</script>

<style scoped>
.member-audit-page {
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
}
.hint {
  margin-bottom: 16px;
}
.table {
  background: #fff;
}
.mono {
  font-family: Consolas, monospace;
  letter-spacing: 0.5px;
}
.muted {
  color: #909399;
  margin-left: 4px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.audit-summary {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px 14px;
}
.audit-summary .row {
  display: flex;
  gap: 10px;
  font-size: 14px;
  line-height: 1.9;
}
.audit-summary .k {
  color: #909399;
  flex-shrink: 0;
  width: 60px;
}
.audit-summary .v {
  color: #303133;
}

/* 响应式：<768px 单列（文档要求） */
@media (max-width: 768px) {
  .topbar-inner,
  .main {
    padding: 0 12px;
  }
  .main {
    padding: 16px 12px;
  }
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
