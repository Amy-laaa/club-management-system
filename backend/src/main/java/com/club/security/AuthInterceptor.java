package com.club.security;

import com.club.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

/**
 * 登录与角色拦截器。
 * 白名单外的接口必须携带 Authorization: Bearer <jwt>。
 * 带有 requiredRole 属性的接口校验角色(见 WebConfig 注册处)。
 *
 * 匿名白名单分两类:
 * 1. 认证接口(/api/auth/**): 任意方法放行(登录注册本身)。
 * 2. 只读浏览接口(/api/clubs、/api/activities 的 GET): 仅 GET 放行。
 *    同路径的 POST/PUT/DELETE(报名/签到/发布等)一律需要登录,
 *    修复原 WebConfig 按路径排除导致写操作绕过校验的问题。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    /** 任意方法都放行的匿名接口(认证本身)。 */
    private static final List<String> ANON_ANY_METHOD = Arrays.asList(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/captcha"
    );

    /** 仅 GET 放行的匿名只读接口(附录A标注匿名的浏览类接口)。 */
    private static final List<String> ANON_GET_ONLY = Arrays.asList(
            "/api/clubs",
            "/api/clubs/*",
            "/api/activities",
            "/api/activities/*"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行预检请求
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();

        // 匿名白名单: 认证接口任意方法放行
        if (ANON_ANY_METHOD.stream().anyMatch(p -> pathMatcher.match(p, path))) {
            return true;
        }
        // 匿名白名单: 浏览接口仅 GET 放行(POST/PUT/DELETE 继续走登录校验)
        boolean isGet = HttpMethod.GET.matches(request.getMethod());
        if (isGet && ANON_GET_ONLY.stream().anyMatch(p -> pathMatcher.match(p, path))) {
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
