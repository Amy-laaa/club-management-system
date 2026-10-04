package com.club.controller;

import com.club.common.Result;
import com.club.security.TokenResolver;
import com.club.security.UserContext;
import com.club.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 社联管理员-数据统计接口 (设计说明书 表 2-8 / 附录 A 第 21 项):
 *   GET /api/admin/stats              统计看板总览(三组卡片一次返回)
 *   GET /api/admin/stats/clubs        社团活跃度
 *   GET /api/admin/stats/activities   活动参与率
 *   GET /api/admin/stats/venues       场地使用率
 *   GET /api/admin/stats/export       报表导出(CSV 附件下载)
 *
 * <p>只读聚合接口, 全部为 GET; 仅社联管理员 / 系统管理员可访问。
 * <p>export 以流式 CSV 下载实现, 不落地文件, 故不再依赖 FileStorage 桩。
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
     * 报表导出: 三组指标导出为 CSV 附件下载(设计说明书 表 2-8 的 export)。
     *
     * <p>该接口返回文件流而非统一 JSON 包装, 因此直接操作响应对象; 权限不足时
     * 仍返回 JSON 结构, 便于前端沿用统一的错误处理分支。
     * <p>文件名使用纯 ASCII(stats-yyyyMMdd.csv), 避免 Content-Disposition 头
     * 需要 RFC 5987 编码; 中文由文件内容本身承担。
     */
    @GetMapping("/api/admin/stats/export")
    public void export(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(200);
        response.setCharacterEncoding("UTF-8");

        Result<Map<String, Object>> denied = guard(request);
        if (denied != null) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + denied.getCode()
                    + ",\"message\":\"" + denied.getMessage() + "\",\"data\":null}");
            return;
        }

        String filename = "stats-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.getWriter().write(statsService.exportCsv());
        response.getWriter().flush();
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
