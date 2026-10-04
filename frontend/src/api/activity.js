import request from './request'

/**
 * 活动接口（对应后端 ActivityController / 附录 A）
 * 列表: GET /api/activities?clubId&actType&keyword&page&size（匿名，仅已发布/进行中）
 * 详情: GET /api/activities/{id}
 * 报名: POST /api/activities/{id}/signup
 * 取消报名: POST /api/activities/{id}/cancel-signup
 * 我的报名: GET /api/activities/mine-registrations
 * 报名名单(社长): GET /api/club/activities/{id}/registrations?status=
 * 签到核销(社长): POST /api/activities/{id}/checkin { voucherCode }
 * 取消活动(社长): POST /api/activities/{id}/cancel
 */

export function listActivities(params) {
  // 返回 { total, page, size, rows: [...] }
  return request.get('/activities', { params })
}

export function getActivity(id) {
  return request.get(`/activities/${id}`)
}

export function signupActivity(id) {
  return request.post(`/activities/${id}/signup`)
}

export function cancelSignup(id) {
  return request.post(`/activities/${id}/cancel-signup`)
}

export function myRegistrations() {
  return request.get('/activities/mine-registrations')
}

export function listActivityRegistrations(id, params) {
  return request.get(`/club/activities/${id}/registrations`, { params })
}

export function checkinActivity(id, voucherCode) {
  return request.post(`/activities/${id}/checkin`, { voucherCode })
}

export function cancelActivity(id) {
  return request.post(`/activities/${id}/cancel`)
}
