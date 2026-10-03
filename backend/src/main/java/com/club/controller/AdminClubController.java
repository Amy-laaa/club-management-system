package com.club.controller;

import com.club.common.BizException;
import com.club.common.Result;
import com.club.dto.AuditDTO;
import com.club.security.UserContext;
import com.club.service.ClubService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * 社联管理员接口 (附录 A):
 *   GET  /api/admin/clubs?status=pending  待审核社团列表
 *   POST /api/admin/clubs/{id}/audit      审核社团成立
 *   GET  /api/admin/clubs/{id}/audits     某社团审批历史
 */
@RestController
@RequestMapping("/api/admin/clubs")
public class AdminClubController {

    private final ClubService clubService;

    public AdminClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    /** 待审核社团列表(社联工作台) */
    @GetMapping
    public Result<Map<String, Object>> pendingList(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        requireUnionAdmin();
        return Result.ok(clubService.pendingList(page, size));
    }

    /** 审核社团成立申请 */
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody AuditDTO dto) {
        UserContext.CurrentUser user = requireUnionAdmin();
        clubService.auditClub(id, dto, user.getUserId());
        return Result.ok(null);
    }

    /** 某社团审批历史 */
    @GetMapping("/{id}/audits")
    public Result<Object> history(@PathVariable Long id) {
        requireUnionAdmin();
        return Result.ok(clubService.auditHistory(id));
    }

    private UserContext.CurrentUser requireUnionAdmin() {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null) {
            throw BizException.unauthorized("请先登录");
        }
        if (!"UNION_ADMIN".equals(user.getRoleCode()) && !"SYS_ADMIN".equals(user.getRoleCode())) {
            throw BizException.forbidden("仅社联管理员可操作");
        }
        return user;
    }
}
