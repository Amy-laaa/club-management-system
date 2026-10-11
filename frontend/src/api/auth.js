import request from './request'

/**
 * 账户认证接口（对应后端 AuthController / 附录 A）
 * 登录: POST /api/auth/login  { account, password }
 * 注册: POST /api/auth/register { studentNo, realName, password, mobile }
 * 登出: POST /api/auth/logout（前端清 token 即可）
 */

export function login(data) {
  // 返回 { token, userId, realName, roleCode }
  return request.post('/auth/login', data)
}

export function register(data) {
  return request.post('/auth/register', data)
}

export function logout() {
  return request.post('/auth/logout')
}
