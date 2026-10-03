package com.club.dto;

import javax.validation.constraints.*;

/**
 * 创建社团请求 (用例 bu_创建社团)。
 */
public class ClubCreateDTO {

    @NotBlank(message = "请输入社团名称")
    @Size(max = 40, message = "社团名称不超过 40 字")
    private String clubName;

    @NotBlank(message = "请选择社团类别")
    @Size(max = 20)
    private String category;

    @Size(max = 500, message = "简介不超过 500 字")
    private String intro;

    private String charter;          // 章程(创建时提交)

    @Size(max = 20, message = "指导老师姓名不超过 20 字")
    private String advisor;

    /** 纳新截止时间, 空表示长期纳新 */
    private String recruitDeadline;

    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getCharter() { return charter; }
    public void setCharter(String charter) { this.charter = charter; }
    public String getAdvisor() { return advisor; }
    public void setAdvisor(String advisor) { this.advisor = advisor; }
    public String getRecruitDeadline() { return recruitDeadline; }
    public void setRecruitDeadline(String recruitDeadline) { this.recruitDeadline = recruitDeadline; }
}
