package com.club.security;

import com.club.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 当前用户解析兜底组件。
 *
 * <p>背景: {@code WebConfig} 的拦截器白名单是按"路径"排除的, 因此像
 * {@code /api/activities} 这种"读匿名、写需登录"的路径下, 写操作(POST)
 * 也不会经过 {@code AuthInterceptor}, 导致 {@code UserContext} 为空。
 *
 * <p>本组件优先复用拦截器写入的上下文; 若为空则从请求头
 * {@code Authorization: Bearer <jwt>} 自行解析, 保证同一路径下
 * 匿名读与登录写可以共存。
 */
@Component
public class TokenResolver {

    private final JwtUtil jwtUtil;

    public TokenResolver(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /** 取当前登录用户; 未登录返回 null。 */
    public UserContext.CurrentUser resolve(HttpServletRequest request) {
        UserContext.CurrentUser cached = UserContext.get();
        if (cached != null) {
            return cached;
        }
        if (request == null) {
            return null;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        Claims claims = jwtUtil.parse(auth.substring(7));
        if (claims == null) {
            return null;
        }
        return new UserContext.CurrentUser(
                jwtUtil.getUserId(claims),
                String.valueOf(claims.get("studentNo")),
                jwtUtil.getRole(claims));
    }
}
