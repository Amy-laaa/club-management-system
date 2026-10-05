import request from './request'

/**
 * 社联管理员审批接口（对应三个 Admin Controller / 附录 A）
 * 权限：UNION_ADMIN 或 SYS_ADMIN
 */

// ---------- 待审列表 ----------

/** 待审核社团列表 */
export function listPendingClubs(params) {
  return request.get('/admin/clubs', { params })
}

/** 待审核公开活动列表 */
export function listPendingActivities(params) {
  return request.get('/admin/activities', { params })
}

/** 待审核场地申请列表 */
export function listPendingVenueApps(params) {
  return request.get('/admin/venue-applications', { params })
}

// ---------- 审批操作（body: { result: 0驳回/1通过, remark }）----------

export function auditClub(id, data) {
  return request.post(`/admin/clubs/${id}/audit`, data)
}

export function auditActivity(id, data) {
  return request.post(`/admin/activities/${id}/audit`, data)
}

export function auditVenueApp(id, data) {
  return request.post(`/admin/venue-applications/${id}/audit`, data)
}
