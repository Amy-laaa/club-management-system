package com.club.service;

import com.club.common.BizException;
import com.club.dto.NoticeCreateDTO;
import com.club.dto.NoticePinDTO;
import com.club.entity.Notice;
import com.club.mapper.NoticeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公告服务: 公告列表(首页公告栏) + 详情 + 发布 + 置顶 + 撤回。
 * 用例: bu_发布公告 / 接口契约 #22 #23。
 *
 * <p>核心业务规则(设计说明书 t_notice 字段说明):
 * <ol>
 *   <li><b>角色决定公告级别</b>: 社长只能发「本社团公告」(scope=0, clubId=自己社团);
 *       社联管理员 / 系统管理员只能发「全校公告」(scope=1, clubId 为空)。
 *       客户端传的 scope 由本类按角色校正; isPinned 则认可客户端意愿。</li>
 *   <li><b>标题 ≤ 40 字</b>(与 t_notice.title VARCHAR(40) 对齐), 正文非空。</li>
 *   <li><b>校级置顶最多 3 条</b>(文档表 2-18), 超出直接 409, 由管理员先撤销旧置顶;
 *       该上限只约束校级公告, 社团公告置顶不限条数。</li>
 *   <li><b>本社团公告仅本社团可见</b>: 校级管理员、该社团负责人、该社团正式成员可读, 其余 403。</li>
 * </ol>
 *
 * <p>本类为纯业务逻辑, 数据访问全部通过 {@link NoticeMapper} 注入, 便于单元测试。
 */
@Service
public class NoticeService {

    /** 校级置顶公告上限 */
    public static final int MAX_UNION_PINNED = 3;

    private final NoticeMapper noticeMapper;

    public NoticeService(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    /**
     * 公告列表。默认查询「全校公告」(首页公告栏口径)。
     *
     * @param scope      0=本社团(需配合 clubId), 1=全校; 为空按 1 处理
     * @param clubId     scope=0 时必填
     * @param operatorId 当前登录用户
     * @param roleCode   当前登录用户角色
     */
    public Map<String, Object> list(Integer scope, Long clubId, Long operatorId, String roleCode,
                                    int pageNum, int pageSize) {
        if (scope == null) {
            scope = 1;
        }
        if (scope != 0 && scope != 1) {
            throw BizException.badRequest("scope 只能是 0(本社团) 或 1(全校)");
        }
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1 || pageSize > 50) pageSize = 10;

        if (scope == 0) {
            if (clubId == null) {
                throw BizException.badRequest("查询本社团公告时必须指定 clubId");
            }
            if (!canViewClubNotices(operatorId, roleCode, clubId)) {
                throw BizException.forbidden("本社团公告仅本社团成员可见");
            }
        } else {
            clubId = null;      // 校级公告与具体社团无关
        }

        long total = noticeMapper.count(scope, clubId);
        List<Notice> rows = noticeMapper.selectPage(scope, clubId, (pageNum - 1) * pageSize, pageSize);

        Map<String, Object> data = new HashMap<String, Object>();
        // 分页响应同时给出 list / rows 两套键名: 契约 §1 用 list, 项目其它模块用 rows,
        // 二者内容相同, 前端取任意一套都能跑通。
        data.put("list", rows);
        data.put("rows", rows);
        data.put("total", total);
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        data.put("page", pageNum);
        data.put("size", pageSize);
        return data;
    }

    /**
     * 发布公告。用例 bu_发布公告 (契约 #23)。
     * 前置: 标题非空且 ≤40 字; 正文非空; 角色为 LEADER 或 社联/系统管理员。
     * 后置: 生成 t_notice, status = 1(已发布)。
     *
     * @return 新公告 noticeId
     */
    @Transactional
    public Long publish(Long operatorId, String roleCode, NoticeCreateDTO dto) {
        String title = dto == null || dto.getTitle() == null ? null : dto.getTitle().trim();
        String content = dto == null || dto.getContent() == null ? null : dto.getContent().trim();
        if (title == null || title.isEmpty()) {
            throw BizException.badRequest("公告标题不能为空");
        }
        if (title.length() > 40) {
            throw BizException.badRequest("公告标题不能超过 40 个字");
        }
        if (content == null || content.isEmpty()) {
            throw BizException.badRequest("公告正文不能为空");
        }

        Notice notice = new Notice();
        notice.setPublisherId(operatorId);
        notice.setTitle(title);
        notice.setContent(content);

        if (isPlatformAdmin(roleCode)) {
            // 社联 / 系统管理员 → 全校公告, clubId 置空
            notice.setClubId(null);
            notice.setScope(1);
            boolean pinned = dto.getIsPinned() != null && dto.getIsPinned() == 1;
            if (pinned && noticeMapper.countPinnedUnion() >= MAX_UNION_PINNED) {
                throw BizException.conflict("校级置顶公告最多 " + MAX_UNION_PINNED + " 条, 请先取消其他置顶");
            }
            notice.setIsPinned(pinned ? 1 : 0);
        } else if ("LEADER".equals(roleCode)) {
            // 社长 → 本社团公告, 公告级别由服务端强制(不信客户端传的 scope)
            Long clubId = noticeMapper.selectClubIdByLeader(operatorId);
            if (clubId == null) {
                throw BizException.forbidden("仅社团负责人可发布本社团公告");
            }
            notice.setClubId(clubId);
            notice.setScope(0);
            // 社团公告置顶: 认可客户端意愿, 但「最多 3 条」只约束校级公告,
            // 社团公告在本社团列表内排序即可, 不设条数上限。
            notice.setIsPinned(dto.getIsPinned() != null && dto.getIsPinned() == 1 ? 1 : 0);
        } else {
            throw BizException.forbidden("仅社团负责人或社联管理员可发布公告");
        }

        notice.setStatus(1);
        noticeMapper.insert(notice);
        return notice.getNoticeId();
    }

    /**
     * 撤回公告。发布者本人或社联/系统管理员可撤回; 撤回后不再出现在列表中(status = 0)。
     */
    @Transactional
    public void withdraw(Long noticeId, Long operatorId, String roleCode) {
        Notice notice = noticeMapper.selectById(noticeId);
        if (notice == null) {
            throw BizException.notFound("公告不存在");
        }
        if (notice.getStatus() == null || notice.getStatus() != 1) {
            throw BizException.conflict("该公告已撤回");
        }
        boolean owner = notice.getPublisherId() != null && notice.getPublisherId().equals(operatorId);
        if (!owner && !isPlatformAdmin(roleCode)) {
            throw BizException.forbidden("仅发布者本人或社联管理员可撤回公告");
        }
        if (noticeMapper.withdraw(noticeId) == 0) {
            throw BizException.conflict("公告状态已变更, 请刷新后重试");
        }
    }

    /**
     * 公告详情 (前端「公告中心」点开某条公告时用)。
     *
     * <p>可见性: 未撤回的公告按与列表相同的规则校验(本社团公告须本社团成员);
     * 已撤回的公告只对发布者本人与平台管理员可见, 其余一律按 404 处理, 不泄露存在性。
     */
    public Notice detail(Long noticeId, Long operatorId, String roleCode) {
        Notice notice = noticeMapper.selectById(noticeId);
        if (notice == null) {
            throw BizException.notFound("公告不存在");
        }
        boolean published = notice.getStatus() != null && notice.getStatus() == 1;
        if (published) {
            if (notice.getScope() != null && notice.getScope() == 0
                    && !canViewClubNotices(operatorId, roleCode, notice.getClubId())) {
                throw BizException.forbidden("本社团公告仅本社团成员可见");
            }
            return notice;
        }
        boolean owner = notice.getPublisherId() != null && notice.getPublisherId().equals(operatorId);
        if (!owner && !isPlatformAdmin(roleCode)) {
            throw BizException.notFound("公告不存在");
        }
        return notice;
    }

    /**
     * 置顶 / 取消置顶。前端「公告中心」的置顶按钮。
     *
     * @param dto isPinned = 1 置顶, 0 取消; 传 null 则按当前状态取反(切换)
     *
     * <p>规则:
     * <ol>
     *   <li>已撤回的公告不能置顶 (409);</li>
     *   <li>校级公告: 仅社联/系统管理员可操作, 且「同时最多 3 条置顶」;</li>
     *   <li>社团公告: 该社团负责人或平台管理员可操作, 不设条数上限。</li>
     * </ol>
     */
    @Transactional
    public void pin(Long noticeId, Long operatorId, String roleCode, NoticePinDTO dto) {
        Notice notice = noticeMapper.selectById(noticeId);
        if (notice == null) {
            throw BizException.notFound("公告不存在");
        }
        if (notice.getStatus() == null || notice.getStatus() != 1) {
            throw BizException.conflict("已撤回的公告不能置顶");
        }

        boolean currentlyPinned = notice.getIsPinned() != null && notice.getIsPinned() == 1;
        boolean target;
        if (dto != null && dto.getIsPinned() != null) {
            target = dto.getIsPinned() == 1;
        } else {
            target = !currentlyPinned;      // 未指定则切换
        }

        boolean platformAdmin = isPlatformAdmin(roleCode);
        if (notice.getScope() != null && notice.getScope() == 1) {
            if (!platformAdmin) {
                throw BizException.forbidden("仅社联管理员可置顶校级公告");
            }
            // 只有"从非置顶变为置顶"才受 3 条上限约束, 重复置顶/取消置顶不受限
            if (target && !currentlyPinned && noticeMapper.countPinnedUnion() >= MAX_UNION_PINNED) {
                throw BizException.conflict("校级置顶公告最多 " + MAX_UNION_PINNED + " 条, 请先取消其他置顶");
            }
        } else {
            if (!platformAdmin) {
                Long myClubId = noticeMapper.selectClubIdByLeader(operatorId);
                if (myClubId == null || !myClubId.equals(notice.getClubId())) {
                    throw BizException.forbidden("仅本社团负责人可置顶本社团公告");
                }
            }
        }

        if (noticeMapper.updatePinned(noticeId, target ? 1 : 0) == 0) {
            throw BizException.conflict("公告状态已变更, 请刷新后重试");
        }
    }

    // ------------------------------------------------------------------
    // 内部: 权限判定
    // ------------------------------------------------------------------

    /** 本社团公告可见性: 平台管理员 / 该社团负责人 / 该社团正式成员 */
    private boolean canViewClubNotices(Long operatorId, String roleCode, Long clubId) {
        if (isPlatformAdmin(roleCode)) {
            return true;
        }
        if (operatorId == null) {
            return false;
        }
        Long myClubId = noticeMapper.selectClubIdByLeader(operatorId);
        if (myClubId != null && myClubId.equals(clubId)) {
            return true;
        }
        return noticeMapper.countMember(operatorId, clubId) > 0;
    }

    private boolean isPlatformAdmin(String roleCode) {
        return "UNION_ADMIN".equals(roleCode) || "SYS_ADMIN".equals(roleCode);
    }
}
