<template>
  <div class="checkin-page">
    <header class="topbar">
      <div class="topbar-inner">
        <el-button text @click="$router.push('/activityList')">← 活动列表</el-button>
        <span class="crumb">活动签到核销</span>
        <div class="topbar-right">
          <span class="welcome">{{ userStore.realName }}</span>
        </div>
      </div>
    </header>

    <main class="main">
      <!-- 活动选择 + 凭证码输入 -->
      <el-card shadow="never" class="op-card">
        <h3 class="op-title">凭证码核销</h3>
        <p class="op-tip">
          选择您管理的活动，输入学生的报名凭证码完成签到核销。<br />
          签到窗口：活动开始前 1 小时 ~ 结束后 2 小时。
        </p>

        <el-form label-position="top" class="op-form">
          <el-form-item label="活动">
            <el-select
              v-model="selectedActivityId"
              placeholder="选择要核销的活动"
              class="act-select"
              :loading="loadingActs"
            >
              <el-option
                v-for="a in myActivities"
                :key="a.activityId"
                :label="`${a.title}（${formatTime(a.startTime)}）`"
                :value="a.activityId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="凭证码">
            <el-input
              v-model="voucherCode"
              placeholder="输入 16 位报名凭证码"
              size="large"
              clearable
              class="voucher-input"
              @keyup.enter="submitCheckin"
            >
              <template #append>
                <el-button type="primary" :loading="submitting" @click="submitCheckin">
                  核销签到
                </el-button>
              </template>
            </el-input>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 本活动报名名单 -->
      <el-card v-if="selectedActivityId" shadow="never" class="list-card">
        <template #header>
          <div class="list-head">
            <span>报名名单</span>
            <el-radio-group v-model="regStatus" size="small" @change="loadRegistrations">
              <el-radio-button :value="0">已报名</el-radio-button>
              <el-radio-button :value="1">已签到</el-radio-button>
              <el-radio-button :value="3">候补</el-radio-button>
            </el-radio-group>
          </div>
        </template>

        <el-table :data="registrations" v-loading="loadingRegs" size="small">
          <el-table-column prop="activityTitle" label="活动" min-width="140" show-overflow-tooltip />
          <el-table-column label="凭证码" width="180">
            <template #default="{ row }">
              <span class="code">{{ row.voucherCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status).type" size="small">
                {{ statusTag(row.status).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="签到时间" width="150">
            <template #default="{ row }">
              {{ formatTime(row.checkinTime) || '—' }}
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="!loadingRegs && registrations.length === 0"
          description="该状态下暂无记录"
          :image-size="60"
        />
      </el-card>
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  checkinActivity,
  listActivityRegistrations,
} from '../api/activity'
import { listActivities } from '../api/activity'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()

const myActivities = ref([])
const loadingActs = ref(false)
const selectedActivityId = ref(null)
const voucherCode = ref('')
const submitting = ref(false)

const registrations = ref([])
const loadingRegs = ref(false)
const regStatus = ref(0)

/** 加载活动列表（社长本人全部社团活动；接口匿名返回已发布/进行中） */
async function loadActivities() {
  loadingActs.value = true
  try {
    const data = await listActivities({ page: 1, size: 50 })
    myActivities.value = data.rows || []
  } catch (e) {
    myActivities.value = []
  } finally {
    loadingActs.value = false
  }
}

watch(selectedActivityId, () => {
  voucherCode.value = ''
  loadRegistrations()
})

async function loadRegistrations() {
  if (!selectedActivityId.value) return
  loadingRegs.value = true
  try {
    const list = await listActivityRegistrations(selectedActivityId.value, {
      status: regStatus.value,
    })
    registrations.value = list || []
  } catch (e) {
    registrations.value = []
    // 403 非负责人 / 401 未登录由拦截器统一弹错
  } finally {
    loadingRegs.value = false
  }
}

async function submitCheckin() {
  if (!selectedActivityId.value) {
    ElMessage.warning('请先选择活动')
    return
  }
  if (!voucherCode.value.trim()) {
    ElMessage.warning('请输入凭证码')
    return
  }
  submitting.value = true
  try {
    // 成功返回核销信息；失败（凭证无效/不在窗口/已核销）由拦截器弹出后端 message
    await checkinActivity(selectedActivityId.value, voucherCode.value.trim())
    ElMessage.success('签到核销成功')
    voucherCode.value = ''
    loadRegistrations()
  } catch (e) {
    // 拦截器统一弹错
  } finally {
    submitting.value = false
  }
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

/** 报名状态: 0已报名 1已签到 2已取消 3候补 */
function statusTag(status) {
  const map = {
    0: { type: 'primary', text: '已报名' },
    1: { type: 'success', text: '已签到' },
    2: { type: 'info', text: '已取消' },
    3: { type: 'warning', text: '候补' },
  }
  return map[status] ?? { type: 'info', text: '未知' }
}

onMounted(loadActivities)
</script>

<style scoped>
.checkin-page {
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
.op-title {
  margin: 0 0 8px;
  font-size: 16px;
  color: #303133;
}
.op-tip {
  margin: 0 0 16px;
  font-size: 13px;
  color: #909399;
  line-height: 1.7;
}
.op-form {
  max-width: 520px;
}
.act-select {
  width: 100%;
}
.voucher-input {
  font-family: Consolas, monospace;
}
.list-card {
  margin-top: 16px;
}
.list-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.code {
  font-family: Consolas, monospace;
  letter-spacing: 1px;
}
</style>
