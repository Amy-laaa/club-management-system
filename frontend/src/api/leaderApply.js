import request from './request'

/**
 * 社团负责人资格申请（对应后端 LeaderApplyController）
 *
 * 学生提交:   POST /api/leader-applies                { reason }
 * 我的申请:   GET  /api/leader-applies/mine           → List<LeaderApply>
 * 管理员列表: GET  /api/admin/leader-applies?status=&page=&size=
 * 管理员审批: POST /api/admin/leader-applies/{id}/review  { result, remark }
 *
 * 元素字段: applyId, userId, reason, status, reviewerId, reviewRemark,
 *          reviewedAt, createdAt + studentNo, realName, mobile, roleCode
 */

export function applyLeader(reason) {
  return request.post('/leader-applies', { reason })
}

export function listMyLeaderApplies() {
  return request.get('/leader-applies/mine')
}

export function listLeaderApplies(status, params) {
  return request.get('/admin/leader-applies', { params: { status, ...params } })
}

export function reviewLeaderApply(id, { result, remark }) {
  return request.post(`/admin/leader-applies/${id}/review`, { result, remark })
}
