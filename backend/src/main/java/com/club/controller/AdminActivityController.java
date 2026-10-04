package com.club.controller;

import com.club.common.Result;
import com.club.dto.AuditDTO;
import com.club.security.UserContext;
import com.club.service.ActivityService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * 社联管理员-公开活动审核接口 (附录 A):
 *   GET  /api/admin/activities                  待审核公开活动列表(分页)
 *   POST /api/admin/activities/{id}/audit       审核(通过→已发布; 驳回→已取消)
 */
@RestController
public class AdminActivityController {

    private final ActivityService activityService;

    public AdminActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    /** 待审核公开活动列表 */
    @GetMapping("/api/admin/activities")
    public Result<Map<String, Object>> pendingList(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        if (!isPlatformAdmin(user.getRoleCode())) {
            return Result.error(403, "仅社联管理员可操作");
        }
        return Result.ok(activityService.pendingList(page, size));
    }

    /** 审核公开活动, 结果写入 t_audit_log */
    @PostMapping("/api/admin/activities/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody AuditDTO dto) {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        if (!isPlatformAdmin(user.getRoleCode())) {
            return Result.error(403, "仅社联管理员可操作");
        }
        activityService.audit(id, dto, user.getUserId());
        return Result.ok(null);
    }

    private boolean isPlatformAdmin(String roleCode) {
        return "UNION_ADMIN".equals(roleCode) || "SYS_ADMIN".equals(roleCode);
    }
}
