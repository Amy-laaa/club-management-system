<template>
  <div class="act-detail-page">
    <header class="topbar">
      <div class="topbar-inner">
        <el-button text @click="$router.back()">← 返回</el-button>
        <span class="crumb">活动详情</span>
      </div>
    </header>

    <main v-loading="loading" class="main">
      <el-card v-if="detail" shadow="never">
        <!-- 头部 -->
        <div class="detail-head">
          <div class="head-left">
            <h2 class="act-title">{{ act.title }}</h2>
            <el-tag :type="act.actType === 1 ? 'warning' : ''" effect="light" size="small">
              {{ act.actType === 1 ? '公开活动' : '社团内部' }}
            </el-tag>
            <el-tag :type="statusTag(act.status).type" effect="plain" size="small">
              {{ statusTag(act.status).text }}
            </el-tag>
          </div>
          <div class="head-right">
            <!-- 未报名：报名按钮 -->
            <el-button
              v-if="myReg === null"
              type="primary"
              :disabled="!canSignup"
              :loading="submitting"
              @click="submitSignup"
            >
              {{ act.remain > 0 ? '立即报名' : '满员，加入候补' }}
            </el-button>

            <!-- 已报名/已签到/候补：凭证卡片 -->
            <div v-else class="voucher-box" :class="voucherClass">
              <div class="voucher-status">{{ voucherStatusText }}</div>
              <div class="voucher-code">{{ myReg.voucherCode }}</div>
              <div class="voucher-tip" v-if="myReg.status === 1 && myReg.checkinTime">
                签到时间 {{ formatTime(myReg.checkinTime) }}
              </div>
              <el-button
                v-if="myReg.status === 0 || myReg.status === 3"
                text
                size="small"
                :loading="submitting"
                @click="submitCancel"
              >
                取消报名
              </el-button>
            </div>
          </div>
        </div>

        <!-- 信息 -->
        <el-descriptions :column="2" border class="desc">
          <el-descriptions-item label="时间">
            {{ formatTime(act.startTime) }} ~ {{ formatTime(act.endTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="地点">{{ act.location }}</el-descriptions-item>
          <el-descriptions-item label="名额">
            <span :class="remainClass">
              {{ act.remain === 0 ? '已满（可候补）' : `剩余 ${act.remain} / ${act.capacity}` }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="报名截止">
            {{ formatTime(act.signupDeadline) }}
          </el-descriptions-item>
          <el-descriptions-item label="报名情况" :span="2">
            已报名 <b>{{ detail.registeredCount }}</b> 人 ·
            候补 <b>{{ detail.waitingCount }}</b> 人 ·
            已签到 <b>{{ detail.checkedInCount }}</b> 人
          </el-descriptions-item>
        </el-descriptions>

        <!-- 简介 -->
        <section class="block">
          <h3 class="block-title">活动简介</h3>
          <p class="text">{{ act.intro || '暂无简介' }}</p>
        </section>

        <!-- 提示 -->
        <el-alert
          v-if="!userStore.isLogin"
          type="info" :closable="false" show-icon
          title="登录后即可报名参加活动"
          class="tip"
        />
        <el-alert
          v-else-if="!detail.signupOpen && myReg === null"
          type="warning" :closable="false" show-icon
          :title="signupClosedReason"
          class="tip"
        />
      </el-card>

      <el-empty v-else-if="!loading" description="活动不存在或已被删除" />
    </main>

    <!-- 报名成功弹窗（凭证码是核心交付物） -->
    <el-dialog v-model="voucherVisible" title="报名成功" width="420px">
      <div class="result-box">
        <el-icon :size="40" color="#67c23a"><CircleCheckFilled /></el-icon>
        <p class="result-msg">{{ lastResult.message }}</p>
        <div class="result-voucher">
          <span class="label">凭证码</span>
          <span class="code">{{ lastResult.voucherCode }}</span>
        </div>
        <p class="result-tip">活动当天出示此凭证码完成签到</p>
      </div>
      <template #footer>
        <el-button type="primary" @click="voucherVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheckFilled } from '@element-plus/icons-vue'
import { getActivity, signupActivity, cancelSignup, myRegistrations } from '../api/activity'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activityId = Number(route.query.id)
const detail = ref(null)
const loading = ref(false)
const submitting = ref(false)
const voucherVisible = ref(false)
const lastResult = ref({})
/** 当前用户对本活动的报名记录：null=未报名 */
const myReg = ref(null)

const act = computed(() => detail.value?.activity || {})

/** 报名总开关（后端综合状态+截止时间计算） */
const canSignup = computed(() => userStore.isLogin && detail.value?.signupOpen && act.value.status === 1)

const signupClosedReason = computed(() => {
  if (act.value.status === 4) return '活动已取消'
  if (act.value.status === 3) return '活动已结束'
  if (act.value.status === 2) return '活动进行中，报名通道已关闭'
  if (new Date(act.value.signupDeadline) < new Date()) return '已过报名截止时间'
  return '当前不可报名'
})

const voucherClass = computed(() => {
  const map = { 0: 'ok', 1: 'done', 2: 'cancelled', 3: 'wait' }
  return map[myReg.value?.status] ?? ''
})

const voucherStatusText = computed(() => {
  const map = { 0: '已报名', 1: '已签到', 2: '已取消', 3: '候补中' }
  return map[myReg.value?.status] ?? ''
})

const remainClass = computed(() =>
  act.value.remain === 0 ? 'full' : act.value.remain <= 3 ? 'danger' : 'ok'
)

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await getActivity(activityId)
  } catch (e) {
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function loadMyReg() {
  if (!userStore.isLogin) {
    myReg.value = null
    return
  }
  try {
    const list = await myRegistrations()
    const mine = (list || []).find((r) => r.activityId === activityId)
    // 已取消(2)视为可重新报名
    myReg.value = mine && mine.status !== 2 ? mine : null
  } catch (e) {
    myReg.value = null
  }
}

async function submitSignup() {
  submitting.value = true
  try {
    // 返回 {regId, voucherCode, status, waiting, message}
    const data = await signupActivity(activityId)
    lastResult.value = data
    voucherVisible.value = true
    await Promise.all([loadDetail(), loadMyReg()])
  } catch (e) {
    // 409（重复报名/已截止等）由拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

async function submitCancel() {
  try {
    await ElMessageBox.confirm('确定取消报名？已占用的名额将立即回补。', '取消报名', {
      confirmButtonText: '确定取消',
      cancelButtonText: '再想想',
      type: 'warning',
    })
  } catch (e) {
    return // 用户点了"再想想"
  }
  submitting.value = true
  try {
    await cancelSignup(activityId)
    ElMessage.success('已取消报名，名额已回补')
    await Promise.all([loadDetail(), loadMyReg()])
  } catch (e) {
    // 拦截器统一弹错（已过截止时间等）
  } finally {
    submitting.value = false
  }
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

/** 活动状态: 0待审核 1已发布 2进行中 3已结束 4已取消 */
function statusTag(status) {
  const map = {
    0: { type: 'info', text: '待审核' },
    1: { type: 'primary', text: '报名中' },
    2: { type: 'success', text: '进行中' },
    3: { type: 'info', text: '已结束' },
    4: { type: 'danger', text: '已取消' },
  }
  return map[status] ?? { type: 'info', text: '未知' }
}

onMounted(() => {
  loadDetail()
  loadMyReg()
})
</script>

<style scoped>
.act-detail-page {
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
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.act-title {
  margin: 0;
  font-size: 22px;
  color: #303133;
}
.desc {
  margin-bottom: 20px;
}
.remain.ok { color: #67c23a; font-weight: 600; }
.remain.danger { color: #e6a23c; font-weight: 600; }
.remain.full { color: #f56c6c; font-weight: 600; }

.block { margin-top: 20px; }
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
.tip { margin-top: 20px; }

/* 凭证卡片 */
.voucher-box {
  min-width: 240px;
  border: 1px dashed;
  border-radius: 8px;
  padding: 12px 16px;
  text-align: center;
}
.voucher-box.ok { border-color: #409eff; background: #ecf5ff; }
.voucher-box.done { border-color: #67c23a; background: #f0f9eb; }
.voucher-box.wait { border-color: #e6a23c; background: #fdf6ec; }
.voucher-box.cancelled { border-color: #909399; background: #f4f4f5; }
.voucher-status {
  font-size: 13px;
  color: #606266;
  margin-bottom: 4px;
}
.voucher-code {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 2px;
  font-family: Consolas, monospace;
  color: #303133;
}
.voucher-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

/* 弹窗 */
.result-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
}
.result-msg {
  margin: 0;
  color: #303133;
  font-size: 15px;
}
.result-voucher {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #ecf5ff;
  border: 1px dashed #409eff;
  border-radius: 8px;
  padding: 10px 20px;
}
.result-voucher .label {
  font-size: 13px;
  color: #909399;
}
.result-voucher .code {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 2px;
  font-family: Consolas, monospace;
}
.result-tip {
  margin: 0;
  font-size: 12px;
  color: #909399;
}
</style>
