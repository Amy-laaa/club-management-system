package com.club.controller;

import com.club.common.Result;
import com.club.dto.AuditDTO;
import com.club.security.UserContext;
import com.club.service.MembershipService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * 成员资格接口 (附录 A):
 *   GET  /api/memberships/mine                 我的社团与申请(学生)
 *   GET  /api/club/memberships?clubId=1        社团待审核申请列表(社长)
 *   POST /api/club/memberships/{id}/audit      审批入社(社长)
 *   GET  /api/clubs/{id}/members               社团正式成员名单(社长, 成员管理页)
 */
@RestController
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping("/api/memberships/mine")
    public Result<Object> mine() {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        return Result.ok(membershipService.mine(user.getUserId()));
    }

    /** 社长查看本社团待审核的入社申请 */
    @GetMapping("/api/club/memberships")
    public Result<Map<String, Object>> pendingList(@RequestParam Long clubId,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        return Result.ok(membershipService.pendingByClub(clubId, user.getUserId(), page, size));
    }

    /** 社长审批入社申请 */
    @PostMapping("/api/club/memberships/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody AuditDTO dto) {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        membershipService.audit(id, dto, user.getUserId());
        return Result.ok(null);
    }

    /**
     * 社长查看本社团正式成员名单(成员管理页)。
     * GET /api/clubs/{id}/members?page=&size=
     */
    @GetMapping("/api/clubs/{id}/members")
    public Result<Map<String, Object>> memberList(@PathVariable("id") Long clubId,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        return Result.ok(membershipService.memberList(clubId, user.getUserId(), page, size));
    }
}
