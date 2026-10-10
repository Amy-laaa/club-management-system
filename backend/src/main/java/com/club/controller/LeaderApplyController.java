package com.club.controller;

import com.club.common.BizException;
import com.club.common.Result;
import com.club.dto.AuditDTO;
import com.club.dto.LeaderApplyDTO;
import com.club.security.UserContext;
import com.club.service.LeaderApplyService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * 社团负责人资格申请(扩展功能)。
 *
 *   POST /api/leader-applies                 学生提交申请
 *   GET  /api/leader-applies/mine            学生查看自己的申请记录
 *   GET  /api/admin/leader-applies           管理员按状态查看申请列表(默认待审)
 *   POST /api/admin/leader-applies/{id}/review  管理员审批(通过即升级为 LEADER)
 *
 * 权限: 审批侧与 AdminUserController 一致 —— 系统管理员 / 社联管理员;
 * 提交侧仅登录用户(Service 内再校验角色)。本组接口均需登录, 不在匿名白名单内。
 */
@RestController
@RequestMapping("/api")
public class LeaderApplyController {

    private final LeaderApplyService leaderApplyService;

    public LeaderApplyController(LeaderApplyService leaderApplyService) {
        this.leaderApplyService = leaderApplyService;
    }

    /** 学生提交负责人资格申请 */
    @PostMapping("/leader-applies")
    public Result<Void> apply(@RequestBody(required = false) LeaderApplyDTO dto) {
        UserContext.CurrentUser user = UserContext.require();
        leaderApplyService.apply(user.getUserId(), dto == null ? null : dto.getReason());
        return Result.ok();
    }

    /** 学生查看自己的申请记录 */
    @GetMapping("/leader-applies/mine")
    public Result<Object> mine() {
        UserContext.CurrentUser user = UserContext.require();
        return Result.ok(leaderApplyService.mine(user.getUserId()));
    }

    /** 管理员: 申请列表(status 默认 0 待审核) */
    @GetMapping("/admin/leader-applies")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "0") int status,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        requirePlatformAdmin();
        return Result.ok(leaderApplyService.pageByStatus(status, page, size));
    }

    /** 管理员审批: 通过后申请人升级为社团负责人 */
    @PostMapping("/admin/leader-applies/{id}/review")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody AuditDTO dto) {
        UserContext.CurrentUser user = requirePlatformAdmin();
        leaderApplyService.review(id, dto, user.getUserId());
        return Result.ok();
    }

    private UserContext.CurrentUser requirePlatformAdmin() {
        UserContext.CurrentUser user = UserContext.require();
        if (!"SYS_ADMIN".equals(user.getRoleCode()) && !"UNION_ADMIN".equals(user.getRoleCode())) {
            throw BizException.forbidden("仅系统管理员或社联管理员可审批负责人资格申请");
        }
        return user;
    }
}
