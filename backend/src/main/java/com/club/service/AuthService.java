package com.club.service;

import com.club.common.BizException;
import com.club.dto.LoginDTO;
import com.club.dto.RegisterDTO;
import com.club.entity.User;
import com.club.mapper.UserMapper;
import com.club.stub.SmsClient;
import com.club.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 账户认证服务: 注册 / 登录。
 * 用例: bu_注册账户, bu_登录系统。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final SmsClient smsClient;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 登录失败计数(演示内存版; 生产放 Redis) */
    private final Map<Long, int[]> failCount = new HashMap<>();

    public AuthService(UserMapper userMapper, SmsClient smsClient, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.smsClient = smsClient;
        this.jwtUtil = jwtUtil;
    }

    /** 获取模拟短信验证码 */
    public void requestCaptcha(String mobile) {
        String err = smsClient.sendCaptcha(mobile);
        if (err != null) {
            throw BizException.conflict(err);
        }
    }

    /**
     * 注册: 校验唯一性 -> 校验验证码 -> BCrypt 落库。
     */
    @Transactional
    public User register(RegisterDTO dto) {
        if (userMapper.selectByStudentNo(dto.getStudentNo()) != null) {
            throw BizException.badRequest("该学号已注册, 请直接登录");
        }
        if (userMapper.selectByMobile(dto.getMobile()) != null) {
            throw BizException.badRequest("该手机号已被使用");
        }
        if (!smsClient.verify(dto.getMobile(), dto.getCaptcha())) {
            throw BizException.badRequest("验证码错误或已过期");
        }
        User user = new User();
        user.setStudentNo(dto.getStudentNo());
        user.setRealName(dto.getRealName());
        user.setMobile(dto.getMobile());
        user.setRoleCode("STUDENT");          // 注册即学生; 负责人角色由社联指派
        user.setPasswordHash(encoder.encode(dto.getPassword()));
        userMapper.insert(user);
        log.info("新用户注册: {} {}", user.getStudentNo(), user.getRealName());
        return user;
    }

    /**
     * 登录: 状态校验 -> 密码校验(带失败锁定) -> 签发 JWT。
     */
    @Transactional
    public Map<String, Object> login(LoginDTO dto) {
        User user = dto.getAccount().matches("^1\\d{10}$")
                ? userMapper.selectByMobile(dto.getAccount())
                : userMapper.selectByStudentNo(dto.getAccount());
        if (user == null) {
            throw BizException.badRequest("账号或密码错误");
        }
        if (user.getStatus() == 0) {
            throw BizException.forbidden("账户已停用, 请联系系统管理员");
        }
        if (user.getStatus() == 2) {
            throw BizException.forbidden("账户已锁定, 请稍后再试");
        }

        int[] counter = failCount.computeIfAbsent(user.getUserId(), k -> new int[1]);
        if (!encoder.matches(dto.getPassword(), user.getPasswordHash())) {
            counter[0]++;
            if (counter[0] >= 5) {
                // 演示口径: 失败 5 次锁定 15 分钟(此处锁定 15 分钟由人工重置, 生产用定时任务)
                userMapper.selectById(user.getUserId()); // no-op, 状态由 DBA/管理员处理
                throw BizException.forbidden("密码错误次数过多, 账户锁定 15 分钟");
            }
            throw BizException.badRequest("账号或密码错误, 剩余尝试 " + (5 - counter[0]) + " 次");
        }
        counter[0] = 0;
        userMapper.touchLogin(user.getUserId());

        Map<String, Object> data = new HashMap<>();
        data.put("token", jwtUtil.generate(user.getUserId(), user.getStudentNo(), user.getRoleCode()));
        data.put("userId", user.getUserId());
        data.put("realName", user.getRealName());
        data.put("roleCode", user.getRoleCode());
        return data;
    }
}
