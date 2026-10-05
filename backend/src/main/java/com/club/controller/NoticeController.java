package com.club.controller;

import com.club.common.Result;
import com.club.dto.NoticeCreateDTO;
import com.club.dto.NoticePinDTO;
import com.club.entity.Notice;
import com.club.security.TokenResolver;
import com.club.security.UserContext;
import com.club.service.NoticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 公告接口 (接口契约 #22 #23 + 前端「公告中心」建议契约):
 *   GET  /api/notices                  公告列表(默认全校公告, 供首页公告栏)
 *   GET  /api/notices/{id}             公告详情
 *   POST /api/notices                  发布公告(社长 → 本社团; 社联/系统管理员 → 全校)
 *   POST /api/notices/{id}/pin          置顶 / 取消置顶(校级同时最多 3 条)
 *   POST /api/notices/{id}/withdraw     撤回公告(发布者本人或社联管理员)
 *
 * <p>路径 /api/notices 不在 AuthInterceptor 的匿名白名单里(白名单只有
 * /api/auth/**、以及 /api/clubs 与 /api/activities 的 GET), 因此本控制器的
 * 全部接口都要求登录 —— 与契约 #22 的「登录」口径一致, 且无需改 WebConfig。
 * 登录态优先取拦截器写入的上下文, 并用 TokenResolver 兜底, 与其它控制器一致。
 */
@RestController
public class NoticeController {

    private final NoticeService noticeService;
    private final TokenResolver tokenResolver;

    public NoticeController(NoticeService noticeService, TokenResolver tokenResolver) {
        this.noticeService = noticeService;
        this.tokenResolver = tokenResolver;
    }

    /**
     * 公告列表。
     * <ul>
     *   <li>不传 scope: 返回全校公告(scope=1), 即首页公告栏;</li>
     *   <li>scope=0 且 clubId=X: 返回某社团公告(需为该校团成员);</li>
     *   <li>分页参数按契约用 pageNum / pageSize, 同时兼容 page / size 命名。</li>
     * </ul>
     */
    @GetMapping("/api/notices")
    public Result<Map<String, Object>> list(@RequestParam(required = false) Integer scope,
                                            @RequestParam(required = false) Long clubId,
                                            @RequestParam(required = false) Integer pageNum,
                                            @RequestParam(required = false) Integer pageSize,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size,
                                            HttpServletRequest request) {
        UserContext.CurrentUser user = tokenResolver.resolve(request);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        int pn = pageNum != null ? pageNum : (page != null ? page : 1);
        int ps = pageSize != null ? pageSize : (size != null ? size : 10);
        return Result.ok(noticeService.list(scope, clubId, user.getUserId(), user.getRoleCode(), pn, ps));
    }

    /** 公告详情: 本社团公告仍受可见性约束; 已撤回的仅发布者本人与管理员可见 */
    @GetMapping("/api/notices/{id}")
    public Result<Notice> detail(@PathVariable Long id, HttpServletRequest request) {
        UserContext.CurrentUser user = tokenResolver.resolve(request);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        return Result.ok(noticeService.detail(id, user.getUserId(), user.getRoleCode()));
    }

    /** 发布公告: 社长发本社团公告, 社联/系统管理员发全校公告 */
    @PostMapping("/api/notices")
    public Result<Map<String, Object>> publish(@Valid @RequestBody NoticeCreateDTO dto,
                                               HttpServletRequest request) {
        UserContext.CurrentUser user = tokenResolver.resolve(request);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        Long noticeId = noticeService.publish(user.getUserId(), user.getRoleCode(), dto);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("noticeId", noticeId);
        return Result.ok(data);
    }

    /**
     * 置顶 / 取消置顶。
     * <p>body 可省略: 传 {@code {"isPinned":1}} 置顶、{@code {"isPinned":0}} 取消,
     * 不传 body 或 isPinned 为空则按当前状态取反(切换), 方便一个按钮搞定。
     */
    @PostMapping("/api/notices/{id}/pin")
    public Result<Void> pin(@PathVariable Long id,
                            @RequestBody(required = false) NoticePinDTO dto,
                            HttpServletRequest request) {
        UserContext.CurrentUser user = tokenResolver.resolve(request);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        noticeService.pin(id, user.getUserId(), user.getRoleCode(), dto);
        return Result.ok(null);
    }

    /** 撤回公告: 发布者本人或社联/系统管理员 */
    @PostMapping("/api/notices/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable Long id, HttpServletRequest request) {
        UserContext.CurrentUser user = tokenResolver.resolve(request);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        noticeService.withdraw(id, user.getUserId(), user.getRoleCode());
        return Result.ok(null);
    }
}
