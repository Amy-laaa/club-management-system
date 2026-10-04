package com.club.controller;

import com.club.common.Result;
import com.club.dto.AuditDTO;
import com.club.security.UserContext;
import com.club.service.VenueService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * 社联管理员-场地审批接口 (附录 A):
 *   GET  /api/admin/venue-applications                 待审核申请列表(分页)
 *   POST /api/admin/venue-applications/{id}/audit      审批(通过锁定时段/驳回)
 */
@RestController
public class AdminVenueController {

    private final VenueService venueService;

    public AdminVenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    /** 社联管理员查看待审核的场地申请 */
    @GetMapping("/api/admin/venue-applications")
    public Result<Map<String, Object>> pendingList(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        if (!isPlatformAdmin(user.getRoleCode())) {
            return Result.error(403, "仅社联管理员可操作");
        }
        return Result.ok(venueService.pendingList(page, size));
    }

    /** 社联管理员审批场地申请, 结果写入 t_audit_log */
    @PostMapping("/api/admin/venue-applications/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody AuditDTO dto) {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        if (!isPlatformAdmin(user.getRoleCode())) {
            return Result.error(403, "仅社联管理员可操作");
        }
        venueService.audit(id, dto, user.getUserId());
        return Result.ok(null);
    }

    private boolean isPlatformAdmin(String roleCode) {
        return "UNION_ADMIN".equals(roleCode) || "SYS_ADMIN".equals(roleCode);
    }
}
