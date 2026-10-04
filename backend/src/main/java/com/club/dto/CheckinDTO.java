package com.club.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 签到核销请求体 (用例 bu_活动签到): 负责人扫描或输入学生出示的凭证码。
 */
public class CheckinDTO {

    @NotBlank(message = "请输入凭证码")
    @Size(max = 16, message = "凭证码格式不正确")
    private String voucherCode;

    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }
}
