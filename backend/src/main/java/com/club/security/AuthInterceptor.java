package com.club.security;

import com.club.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录与角色拦截器。
 * 白名单外的接口必须携带 Authorization: Bearer <jwt>。
 * 带有 requiredRole 属性的接口校验角色(见 WebConfig 注册处)。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行预检请求
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            writeError(response, 401, "未登录或令牌缺失");
            return false;
        }
        Claims claims = jwtUtil.parse(auth.substring(7));
        if (claims == null) {
            writeError(response, 401, "令牌无效或已过期");
            return false;
        }
        UserContext.set(new UserContext.CurrentUser(
                jwtUtil.getUserId(claims),
                String.valueOf(claims.get("studentNo")),
                jwtUtil.getRole(claims)));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();   // 必须清理, 防止线程池串号
    }

    private void writeError(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + msg + "\",\"data\":null}");
    }
}
