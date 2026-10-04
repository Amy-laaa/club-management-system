package com.club.controller;

import com.club.common.Result;
import com.club.dto.LoginDTO;
import com.club.dto.RegisterDTO;
import com.club.service.AuthService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 账户认证接口 (附录 A)。
 *   POST /api/auth/captcha  获取验证码(匿名)
 *   POST /api/auth/register 注册(匿名)
 *   POST /api/auth/login    登录(匿名)
 *   POST /api/auth/logout   登出
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/captcha")
    public Result<Void> captcha(@RequestParam String mobile) {
        authService.requestCaptcha(mobile);
        // 桩实现: 验证码在后端日志打印, 演示时看控制台
        return Result.ok();
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        Map<String, Object> data = new HashMap<>();
        data.put("studentNo", dto.getStudentNo());
        return Result.ok(data);
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        // JWT 无状态, 前端删除本地令牌即可; 如需服务端注销可加黑名单
        return Result.ok();
    }
}
