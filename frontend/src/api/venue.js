import request from './request'

/**
 * 场地接口（对应后端 VenueController / 附录 A）
 * 列表: GET /api/venues?date=YYYY-MM-DD（需登录），返回场地 + occupiedSlots
 * 申请: POST /api/venues/applications（仅 LEADER）{ venueId, activityId?, useDate, timeSlot, purpose }
 * 我的申请: GET /api/venues/applications/mine
 */

/** 场地列表 + 指定日期时段占用（需登录）。date 格式 YYYY-MM-DD */
export function listVenues(date) {
  return request.get('/venues', { params: { date } })
}

/** 社团负责人申请场地 */
export function applyVenue(data) {
  return request.post('/venues/applications', data)
}

/** 我的场地申请列表 */
export function myVenueApplications() {
  return request.get('/venues/applications/mine')
}
