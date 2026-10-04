import request from './request'

/**
 * 账户认证接口（对应后端 AuthController / 附录 A）
 * 登录: POST /api/auth/login  { account, password }
 * 注册: POST /api/auth/register { studentNo, realName, password, mobile, captcha }
 * 验证码: POST /api/auth/captcha?mobile=xxx（桩实现，验证码打印在后端日志）
 * 登出: POST /api/auth/logout（前端清 token 即可）
 */

export function login(data) {
  // 返回 { token, userId, realName, roleCode }
  return request.post('/auth/login', data)
}

export function register(data) {
  return request.post('/auth/register', data)
}

export function requestCaptcha(mobile) {
  return request.post('/auth/captcha', null, { params: { mobile } })
}

export function logout() {
  return request.post('/auth/logout')
}
