package com.club.controller;

import com.club.common.Result;
import com.club.dto.ActivityCreateDTO;
import com.club.dto.CheckinDTO;
import com.club.security.TokenResolver;
import com.club.security.UserContext;
import com.club.service.ActivityService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;

/**
 * 活动接口 (附录 A):
 *   GET  /api/activities                       活动列表(匿名, 仅已发布/进行中)
 *   GET  /api/activities/{id}                  活动详情(匿名)
 *   POST /api/activities                       发布活动(社团负责人; 公开活动转待审核)
 *   POST /api/activities/{id}/signup           报名(学生; 满额转候补)
 *   POST /api/activities/{id}/cancel-signup    取消报名(名额回补)
 *   GET  /api/activities/mine-registrations    我的报名与凭证
 *   GET  /api/club/activities/{id}/registrations  报名名单(负责人)
 *   POST /api/activities/{id}/checkin          签到核销(负责人, 凭证码)
 *   POST /api/activities/{id}/cancel           取消活动(负责人)
 */
@RestController
public class ActivityController {

    private final ActivityService activityService;
    private final TokenResolver tokenResolver;

    public ActivityController(ActivityService activityService, TokenResolver tokenResolver) {
        this.activityService = activityService;
        this.tokenResolver = tokenResolver;
    }

    /** 活动列表: 无需登录; 可按社团、类型、标题关键字筛选 */
    @GetMapping("/api/activities")
    public Result<Map<String, Object>> list(@RequestParam(required = false) Long clubId,
                                            @RequestParam(required = false) Integer actType,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(activityService.list(clubId, actType, keyword, page, size));
    }

    /** 活动详情(含报名统计与报名窗口是否开放) */
    @GetMapping("/api/activities/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(activityService.detail(id));
    }

    /** 社团负责人发布活动 */
    @PostMapping("/api/activities")
    public Result<Object> publish(@Valid @RequestBody ActivityCreateDTO dto, HttpServletRequest request) {
        UserContext.CurrentUser user = tokenResolver.resolve(request);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        if (!"LEADER".equals(user.getRoleCode())) {
            return Result.error(403, "仅社团负责人可发布活动");
        }
        Long activityId = activityService.publish(user.getUserId(), dto);
        java.util.Map<String, Object> data = new java.util.HashMap<String, Object>();
        data.put("activityId", activityId);
        return Result.ok(data);
    }

    /** 学生报名活动 */
    @PostMapping("/api/activities/{id}/signup")
    public Result<Object> signup(@PathVariable Long id, HttpServletRequest request) {
        UserContext.CurrentUser user = tokenResolver.resolve(request);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        return Result.ok(activityService.signUp(id, user.getUserId()));
    }

    /** 学生取消报名 */
    @PostMapping("/api/activities/{id}/cancel-signup")
    public Result<Void> cancelSignup(@PathVariable Long id, HttpServletRequest request) {
        UserContext.CurrentUser user = tokenResolver.resolve(request);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        activityService.cancelSignUp(id, user.getUserId());
        return Result.ok(null);
    }

    /** 我的报名与电子凭证 */
    @GetMapping("/api/activities/mine-registrations")
    public Result<Object> myRegistrations(HttpServletRequest request) {
        UserContext.CurrentUser user = tokenResolver.resolve(request);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        return Result.ok(activityService.myRegistrations(user.getUserId()));
    }

    /** 负责人查看报名名单 */
    @GetMapping("/api/club/activities/{id}/registrations")
    public Result<Object> registrations(@PathVariable Long id,
                                        @RequestParam(required = false) Integer status,
                                        HttpServletRequest request) {
        UserContext.CurrentUser user = tokenResolver.resolve(request);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        return Result.ok(activityService.registrationsOfActivity(id, status, user.getUserId()));
    }

    /** 负责人核销签到(凭证码) */
    @PostMapping("/api/activities/{id}/checkin")
    public Result<Object> checkin(@PathVariable Long id, @Valid @RequestBody CheckinDTO dto, HttpServletRequest request) {
        UserContext.CurrentUser user = tokenResolver.resolve(request);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        return Result.ok(activityService.checkIn(id, dto.getVoucherCode(), user.getUserId()));
    }

    /** 负责人取消活动(释放名额) */
    @PostMapping("/api/activities/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, HttpServletRequest request) {
        UserContext.CurrentUser user = tokenResolver.resolve(request);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        activityService.cancelActivity(id, user.getUserId());
        return Result.ok(null);
    }
}
