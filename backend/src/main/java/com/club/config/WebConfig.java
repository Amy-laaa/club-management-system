package com.club.config;

import com.club.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置: 跨域 + 登录拦截。
 * 白名单 = 设计说明书附录 A 中标注"匿名"的接口。
 * 新增匿名接口时记得同步维护这里。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")     // 联调阶段放开, 上线前收紧
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/captcha",          // 获取验证码(模拟短信)
                        "/api/clubs",                  // GET 社团列表(匿名)
                        "/api/clubs/*",                // GET 社团详情(匿名)
                        "/api/activities",             // GET 活动列表(匿名)
                        "/api/activities/*"            // GET 活动详情(匿名)
                );
    }
}
