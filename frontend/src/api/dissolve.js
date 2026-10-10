import request from './request'

/**
 * 社团解散（对应后端 ClubDissolveController）
 *
 * 负责人申请: POST /api/clubs/{id}/dissolve/apply     { reason }
 * 查看状态:   GET  /api/clubs/{id}/dissolve           → ClubDissolve | null
 * 执行注销:   POST /api/clubs/{id}/dissolve/execute   （须已获批准 status=1）
 * 管理员列表: GET  /api/admin/dissolves?status=&page=&size=
 * 管理员审批: POST /api/admin/dissolves/{id}/review   { result, remark }
 *
 * 元素字段: dissolveId, clubId, applicantId, reason, status, reviewerId,
 *          reviewRemark, reviewedAt, executedAt, createdAt
 *          + clubName, category, applicantName
 */

export function applyDissolve(clubId, reason) {
  return request.post(`/clubs/${clubId}/dissolve/apply`, { reason })
}

export function getDissolveStatus(clubId) {
  return request.get(`/clubs/${clubId}/dissolve`)
}

export function executeDissolve(clubId) {
  return request.post(`/clubs/${clubId}/dissolve/execute`)
}

export function listDissolves(status, params) {
  return request.get('/admin/dissolves', { params: { status, ...params } })
}

export function reviewDissolve(id, { result, remark }) {
  return request.post(`/admin/dissolves/${id}/review`, { result, remark })
}
