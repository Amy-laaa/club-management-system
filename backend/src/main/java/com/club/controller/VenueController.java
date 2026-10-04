package com.club.controller;

import com.club.common.Result;
import com.club.dto.VenueApplyDTO;
import com.club.security.UserContext;
import com.club.service.VenueService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

/**
 * 场地接口 (附录 A):
 *   GET  /api/venues                      场地列表 + 指定日期时段占用(需登录)
 *   POST /api/venues/applications         申请场地(社团负责人)
 *   GET  /api/venues/applications/mine    我的场地申请
 */
@RestController
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    /** 场地列表, date 不传默认今天; 返回每个场地的 occupiedSlots 供矩阵置灰 */
    @GetMapping("/api/venues")
    public Result<Object> list(@RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        if (date == null) {
            date = LocalDate.now();
        }
        return Result.ok(venueService.listWithSlots(date));
    }

    /** 社团负责人申请场地 */
    @PostMapping("/api/venues/applications")
    public Result<Object> apply(@Valid @RequestBody VenueApplyDTO dto) {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        if (!"LEADER".equals(user.getRoleCode())) {
            return Result.error(403, "仅社团负责人可申请场地");
        }
        Long appId = venueService.apply(user.getUserId(), dto);
        java.util.Map<String, Object> data = new java.util.HashMap<String, Object>();
        data.put("appId", appId);
        return Result.ok(data);
    }

    /** 我的场地申请 */
    @GetMapping("/api/venues/applications/mine")
    public Result<Object> mine() {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        return Result.ok(venueService.mine(user.getUserId()));
    }
}
