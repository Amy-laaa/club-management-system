package com.club.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * JWT 工具类 (HMAC 签名)。
 * payload 只放 userId / studentNo / roleCode, 需要其它档案信息请查库。
 */
@Component
public class JwtUtil {

    @Value("${club.jwt.secret}")
    private String secret;

    @Value("${club.jwt.expire-minutes}")
    private long expireMinutes;

    public String generate(Long userId, String studentNo, String roleCode) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireMinutes * 60_000L);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("studentNo", studentNo)
                .claim("roleCode", roleCode)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    /**
     * 解析并校验令牌。合法返回 Claims, 非法/过期返回 null。
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
        } catch (Exception e) {
            return null;
        }
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String getRole(Claims claims) {
        return String.valueOf(claims.get("roleCode"));
    }
}
