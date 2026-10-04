package com.club.controller;

import com.club.common.Result;
import com.club.dto.ApplyJoinDTO;
import com.club.dto.ClubCreateDTO;
import com.club.security.UserContext;
import com.club.service.ClubService;
import com.club.service.MembershipService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 社团接口 (附录 A):
 *   GET  /api/clubs                    社团列表(匿名)
 *   GET  /api/clubs/{id}               社团详情(匿名)
 *   POST /api/clubs                    创建社团(负责人)
 *   POST /api/clubs/{id}/applications  申请入社(学生)
 *
 * 注意: /api/clubs* 在拦截器白名单里(GET 匿名), 写操作在方法内手动校验登录。
 */
@RestController
@RequestMapping("/api/clubs")
public class ClubController {

    private final ClubService clubService;
    private final MembershipService membershipService;

    public ClubController(ClubService clubService, MembershipService membershipService) {
        this.clubService = clubService;
        this.membershipService = membershipService;
    }

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(clubService.list(status, keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<Object> detail(@PathVariable Long id) {
        return Result.ok(clubService.detail(id));
    }

    /** 创建社团(提交审核)。权限: 社团负责人角色。 */
    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody ClubCreateDTO dto) {
        UserContext.CurrentUser user = UserContext.require();
        if (!"LEADER".equals(user.getRoleCode())) {
            return Result.error(403, "仅社团负责人角色可创建社团");
        }
        Long clubId = clubService.create(user.getUserId(), dto);
        Map<String, Object> data = new HashMap<>();
        data.put("clubId", clubId);
        data.put("message", "已提交, 等待社联审核");
        return Result.ok(data);
    }

    /** 学生申请入社。 */
    @PostMapping("/{id}/applications")
    public Result<Void> apply(@PathVariable Long id, @Valid @RequestBody ApplyJoinDTO dto) {
        UserContext.CurrentUser user = UserContext.require();
        membershipService.apply(user.getUserId(), id, dto.getApplyReason());
        return Result.ok(null);
    }
}
