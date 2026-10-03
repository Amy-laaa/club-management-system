package com.club.dto;

import javax.validation.constraints.NotBlank;

/**
 * 登录请求: 学号或手机号 + 密码。
 */
public class LoginDTO {

    @NotBlank(message = "请输入学号或手机号")
    private String account;

    @NotBlank(message = "请输入密码")
    private String password;

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
