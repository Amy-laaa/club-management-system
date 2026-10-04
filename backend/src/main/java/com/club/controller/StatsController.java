package com.club.controller;

import com.club.common.Result;
import com.club.security.TokenResolver;
import com.club.security.UserContext;
import com.club.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 社联管理员-数据统计接口 (设计说明书 表 2-8 / 附录 A 第 21 项):
 *   GET /api/admin/stats              统计看板总览(三组卡片一次返回)
 *   GET /api/admin/stats/clubs        社团活跃度
 *   GET /api/admin/stats/activities   活动参与率
 *   GET /api/admin/stats/venues       场地使用率
 *
 * <p>只读聚合接口, 全部为 GET; 仅社联管理员 / 系统管理员可访问。
 * <p>说明: 表 2-8 中的 export(报表文件导出) 依赖 FileStorage 桩, 本期未纳入
 * 接口契约, 故未实现, 前端如需导出可先用浏览器打印/前端导出。
 */
@RestController
public class StatsController {

    private final StatsService statsService;
    private final TokenResolver tokenResolver;

    public StatsController(StatsService statsService, TokenResolver tokenResolver) {
        this.statsService = statsService;
        this.tokenResolver = tokenResolver;
    }

    /** 统计看板总览: 社团 / 活动 / 场地三组指标 */
    @GetMapping("/api/admin/stats")
    public Result<Map<String, Object>> overview(HttpServletRequest request) {
        UserContext.CurrentUser user = tokenResolver.resolve(request);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        if (!isPlatformAdmin(user.getRoleCode())) {
            return Result.error(403, "仅社联管理员可查看统计报表");
        }
        return Result.ok(statsService.overview());
    }

    /** 社团活跃度(单独取用, 便于前端按需加载) */
    @GetMapping("/api/admin/stats/clubs")
    public Result<Map<String, Object>> clubStats(HttpServletRequest request) {
        Result<Map<String, Object>> denied = guard(request);
        return denied != null ? denied : Result.ok(statsService.clubStats());
    }

    /** 活动参与率 */
    @GetMapping("/api/admin/stats/activities")
    public Result<Map<String, Object>> activityStats(HttpServletRequest request) {
        Result<Map<String, Object>> denied = guard(request);
        return denied != null ? denied : Result.ok(statsService.activityStats());
    }

    /** 场地使用率 */
    @GetMapping("/api/admin/stats/venues")
    public Result<Map<String, Object>> venueStats(HttpServletRequest request) {
        Result<Map<String, Object>> denied = guard(request);
        return denied != null ? denied : Result.ok(statsService.venueStats());
    }

    /**
     * 登录 + 角色校验, 通过返回 null, 不通过直接返回错误结果。
     * <p>注意: 登录态优先取拦截器写入的上下文, 为空时由 TokenResolver 从
     * Authorization 头兜底解析, 避免 WebConfig 白名单调整后此处失效。
     */
    private Result<Map<String, Object>> guard(HttpServletRequest request) {
        UserContext.CurrentUser user = tokenResolver.resolve(request);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        if (!isPlatformAdmin(user.getRoleCode())) {
            return Result.error(403, "仅社联管理员可查看统计报表");
        }
        return null;
    }

    private boolean isPlatformAdmin(String roleCode) {
        return "UNION_ADMIN".equals(roleCode) || "SYS_ADMIN".equals(roleCode);
    }
}
