package com.club.dto;

import javax.validation.constraints.*;

/**
 * 审批请求(社团成立 / 入社申请 / 公开活动共用)。
 */
public class AuditDTO {

    @NotNull(message = "请选择审批结果")
    @Min(value = 0, message = "审批结果不合法")
    @Max(value = 1, message = "审批结果不合法")
    private Integer result;          // 0驳回 1通过

    @Size(max = 255, message = "审批意见不超过 255 字")
    private String remark;

    /** 拒绝入社申请时必须填写原因(用例 bu_审批入社申请) */
    public boolean isRejectWithoutReason() {
        return result != null && result == 0 && (remark == null || remark.trim().isEmpty());
    }

    public Integer getResult() { return result; }
    public void setResult(Integer result) { this.result = result; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
