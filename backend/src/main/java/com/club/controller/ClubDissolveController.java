package com.club.controller;

import com.club.common.BizException;
import com.club.common.Result;
import com.club.dto.AuditDTO;
import com.club.dto.DissolveApplyDTO;
import com.club.security.UserContext;
import com.club.service.ClubDissolveService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * 社团解散接口。
 *
 *   POST /api/clubs/{id}/dissolve/apply     负责人提交解散申请
 *   GET  /api/clubs/{id}/dissolve           负责人查看本社团解散申请状态
 *   POST /api/clubs/{id}/dissolve/execute   负责人执行注销(须已获批准)
 *   GET  /api/admin/dissolves               管理员按状态查看解散申请(默认待审批)
 *   POST /api/admin/dissolves/{id}/review   管理员审批(批准/驳回)
 *
 * 权限: 申请与执行仅该社团负责人; 审批仅系统管理员 / 社联管理员。
 * 注: /api/clubs/* 的 GET 匿名白名单只匹配单层路径, 故本控制器的路径均需登录。
 */
@RestController
@RequestMapping("/api")
public class ClubDissolveController {

    private final ClubDissolveService clubDissolveService;

    public ClubDissolveController(ClubDissolveService clubDissolveService) {
        this.clubDissolveService = clubDissolveService;
    }

    /** 负责人提交解散申请 */
    @PostMapping("/clubs/{id}/dissolve/apply")
    public Result<Void> apply(@PathVariable("id") Long clubId,
                              @RequestBody(required = false) DissolveApplyDTO dto) {
        UserContext.CurrentUser user = UserContext.require();
        clubDissolveService.apply(clubId, user.getUserId(), dto == null ? null : dto.getReason());
        return Result.ok();
    }

    /** 负责人查看本社团解散申请状态 */
    @GetMapping("/clubs/{id}/dissolve")
    public Result<Object> status(@PathVariable("id") Long clubId) {
        UserContext.CurrentUser user = UserContext.require();
        return Result.ok(clubDissolveService.latest(clubId, user.getUserId()));
    }

    /** 负责人执行注销 */
    @PostMapping("/clubs/{id}/dissolve/execute")
    public Result<Void> execute(@PathVariable("id") Long clubId) {
        UserContext.CurrentUser user = UserContext.require();
        clubDissolveService.execute(clubId, user.getUserId());
        return Result.ok();
    }

    /** 管理员: 解散申请列表(status 默认 0 待审批) */
    @GetMapping("/admin/dissolves")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "0") int status,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        requirePlatformAdmin();
        return Result.ok(clubDissolveService.pageByStatus(status, page, size));
    }

    /** 管理员审批解散申请 */
    @PostMapping("/admin/dissolves/{id}/review")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody AuditDTO dto) {
        UserContext.CurrentUser user = requirePlatformAdmin();
        clubDissolveService.review(id, dto, user.getUserId());
        return Result.ok();
    }

    private UserContext.CurrentUser requirePlatformAdmin() {
        UserContext.CurrentUser user = UserContext.require();
        if (!"SYS_ADMIN".equals(user.getRoleCode()) && !"UNION_ADMIN".equals(user.getRoleCode())) {
            throw BizException.forbidden("仅系统管理员或社联管理员可审批社团解散");
        }
        return user;
    }
}
