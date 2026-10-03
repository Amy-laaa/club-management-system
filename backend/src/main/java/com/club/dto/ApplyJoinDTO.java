package com.club.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 入社申请请求 (用例 bu_申请加入社团)。
 */
public class ApplyJoinDTO {

    @NotBlank(message = "请填写申请理由")
    @Size(max = 200, message = "申请理由不超过 200 字")
    private String applyReason;

    public String getApplyReason() { return applyReason; }
    public void setApplyReason(String applyReason) { this.applyReason = applyReason; }
}
