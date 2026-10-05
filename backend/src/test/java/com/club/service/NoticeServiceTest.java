package com.club.service;

import com.club.common.BizException;
import com.club.dto.NoticeCreateDTO;
import com.club.dto.NoticePinDTO;
import com.club.entity.Notice;
import com.club.mapper.NoticeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公告服务单元测试: 发布公告的角色分级 / 参数校验 / 置顶上限, 以及列表可见性。
 *
 * <p>被测契约: 接口契约 #22 #23 与 t_notice 字段说明 ——
 * <ol>
 *   <li>社长只能发本社团公告(scope=0, clubId 取自己社团), 客户端传的 scope / isPinned 被忽略;</li>
 *   <li>社联/系统管理员发全校公告(scope=1, clubId 为空);</li>
 *   <li>标题 ≤40 字、正文非空;</li>
 *   <li>校级置顶公告最多 3 条, 第 4 条被 409 拒绝;</li>
 *   <li>本社团公告只有本社团成员(或管理员/该社团负责人)可见。</li>
 * </ol>
 *
 * <p>纯 Mockito 测试, 不启动 Spring 容器、不连数据库, 因此断言聚焦在
 * 「Service 传给 Mapper 的 Notice 长什么样」和「异常码是什么」。
 */
class NoticeServiceTest {

    private static final String LEADER = "LEADER";
    private static final String UNION_ADMIN = "UNION_ADMIN";
    private static final String SYS_ADMIN = "SYS_ADMIN";
    private static final String STUDENT = "STUDENT";

    private final NoticeMapper mapper = Mockito.mock(NoticeMapper.class);
    private final NoticeService service = new NoticeService(mapper);

    /** insert 时回填自增主键, 模拟 MyBatis useGeneratedKeys 的行为 */
    private void stubInsertReturns(Long noticeId) {
        when(mapper.insert(any(Notice.class))).thenAnswer(invocation -> {
            Notice n = invocation.getArgument(0);
            n.setNoticeId(noticeId);
            return 1;
        });
    }

    private NoticeCreateDTO dto(String title, String content, Integer scope, Integer isPinned) {
        NoticeCreateDTO d = new NoticeCreateDTO();
        d.setTitle(title);
        d.setContent(content);
        d.setScope(scope);
        d.setIsPinned(isPinned);
        return d;
    }

    private Notice existing(Long id, Long publisherId, int status) {
        Notice n = new Notice();
        n.setNoticeId(id);
        n.setPublisherId(publisherId);
        n.setStatus(status);
        return n;
    }

    /** 完整构造一条公告, 供详情/置顶用例使用 */
    private Notice full(Long id, Long publisherId, int scope, Long clubId, int isPinned) {
        Notice n = existing(id, publisherId, 1);
        n.setScope(scope);
        n.setClubId(clubId);
        n.setIsPinned(isPinned);
        n.setTitle("标题");
        n.setContent("正文");
        return n;
    }

    private int codeOf(Executable exec) {
        BizException e = assertThrows(BizException.class, exec::run);
        return e.getCode();
    }

    /** 便于 assertThrows 使用的函数式接口 */
    private interface Executable {
        void run();
    }

    // ------------------------------------------------------------------
    // 发布: 角色分级
    // ------------------------------------------------------------------

    @Test
    @DisplayName("社长发布: 强制转为本社团公告, scope=0 且 clubId 取自己社团")
    void leaderPublishesClubNotice() {
        when(mapper.selectClubIdByLeader(7L)).thenReturn(3L);
        stubInsertReturns(101L);

        Long id = service.publish(7L, LEADER, dto("招新通知", "本周五下午面试", null, null));

        assertEquals(101L, id);
        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(mapper).insert(captor.capture());
        Notice saved = captor.getValue();
        assertEquals(0, saved.getScope().intValue(), "社长公告应为本社团可见");
        assertEquals(3L, saved.getClubId().longValue());
        assertEquals(7L, saved.getPublisherId().longValue());
        assertEquals(1, saved.getStatus().intValue(), "发布即为已发布状态");
    }

    @Test
    @DisplayName("社长传 scope=1 被校正为本社团; 但 isPinned 意愿被认可, 且不查校级置顶上限")
    void leaderScopeForcedButPinHonoured() {
        when(mapper.selectClubIdByLeader(7L)).thenReturn(3L);
        stubInsertReturns(102L);

        service.publish(7L, LEADER, dto("社团例会", "周三晚 7 点", 1, 1));

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(mapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getScope().intValue(), "客户端传的 scope 必须被忽略");
        assertEquals(1, captor.getValue().getIsPinned().intValue(), "社团公告可置顶, 意愿被认可");
        verify(mapper, never()).countPinnedUnion();   // 3 条上限只约束校级公告
    }

    @Test
    @DisplayName("自称社长但名下无已成立社团: 403")
    void leaderWithoutClubRejected() {
        when(mapper.selectClubIdByLeader(9L)).thenReturn(null);

        assertEquals(403, codeOf(() -> service.publish(9L, LEADER, dto("标题", "正文", null, null))));
        verify(mapper, never()).insert(any(Notice.class));
    }

    @Test
    @DisplayName("社联管理员发布: 校级公告, clubId 为空")
    void unionAdminPublishesUnionNotice() {
        stubInsertReturns(103L);

        service.publish(1L, UNION_ADMIN, dto("全校停课通知", "因台风停课一天", null, 0));

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(mapper).insert(captor.capture());
        Notice saved = captor.getValue();
        assertEquals(1, saved.getScope().intValue());
        assertNull(saved.getClubId(), "校级公告不挂社团");
        assertEquals("全校停课通知", saved.getTitle());
        assertEquals("因台风停课一天", saved.getContent());
    }

    @Test
    @DisplayName("系统管理员同样可发校级公告")
    void sysAdminPublishesUnionNotice() {
        stubInsertReturns(104L);
        assertEquals(104L, service.publish(2L, SYS_ADMIN, dto("系统维护", "周日凌晨维护", null, null)));
        verify(mapper).insert(any(Notice.class));
    }

    @Test
    @DisplayName("普通学生发布公告: 403")
    void studentCannotPublish() {
        assertEquals(403, codeOf(() -> service.publish(5L, STUDENT, dto("标题", "正文", null, null))));
        verify(mapper, never()).insert(any(Notice.class));
    }

    // ------------------------------------------------------------------
    // 发布: 参数校验与置顶上限
    // ------------------------------------------------------------------

    @Test
    @DisplayName("标题为空 / 全空格: 400")
    void blankTitleRejected() {
        assertEquals(400, codeOf(() -> service.publish(1L, UNION_ADMIN, dto("   ", "正文", null, null))));
        assertEquals(400, codeOf(() -> service.publish(1L, UNION_ADMIN, dto(null, "正文", null, null))));
        verify(mapper, never()).insert(any(Notice.class));
    }

    @Test
    @DisplayName("标题超过 40 字: 400; 正好 40 字通过")
    void titleLengthValidated() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 41; i++) {
            sb.append("字");
        }
        String tooLong = sb.toString();
        assertEquals(400, codeOf(() -> service.publish(1L, UNION_ADMIN, dto(tooLong, "正文", null, null))));

        stubInsertReturns(105L);
        assertNotNull(service.publish(1L, UNION_ADMIN, dto(tooLong.substring(0, 40), "正文", null, null)));
    }

    @Test
    @DisplayName("正文为空: 400")
    void blankContentRejected() {
        assertEquals(400, codeOf(() -> service.publish(1L, UNION_ADMIN, dto("标题", "  ", null, null))));
        verify(mapper, never()).insert(any(Notice.class));
    }

    @Test
    @DisplayName("校级置顶已满 3 条时再置顶: 409, 且不落库")
    void unionPinnedLimitEnforced() {
        when(mapper.countPinnedUnion()).thenReturn(NoticeService.MAX_UNION_PINNED);

        assertEquals(409, codeOf(() -> service.publish(1L, UNION_ADMIN, dto("第四条置顶", "正文", 1, 1))));
        verify(mapper, never()).insert(any(Notice.class));
    }

    @Test
    @DisplayName("校级置顶未满: 允许置顶, isPinned=1")
    void unionPinnedAllowedWhenRoomLeft() {
        when(mapper.countPinnedUnion()).thenReturn(2);
        stubInsertReturns(106L);

        service.publish(1L, UNION_ADMIN, dto("重要通知", "正文", 1, 1));

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(mapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getIsPinned().intValue());
    }

    @Test
    @DisplayName("不置顶时不查询置顶条数(避免无谓 SQL)")
    void pinnedCountNotQueriedWhenNotPinned() {
        stubInsertReturns(107L);
        service.publish(1L, UNION_ADMIN, dto("普通通知", "正文", 1, 0));
        verify(mapper, never()).countPinnedUnion();
    }

    // ------------------------------------------------------------------
    // 列表可见性与分页
    // ------------------------------------------------------------------

    @Test
    @DisplayName("不传 scope: 默认查全校公告, 且返回 list/rows 两套键与总数")
    void listDefaultsToUnionScope() {
        when(mapper.count(eq(1), any())).thenReturn(2L);
        when(mapper.selectPage(eq(1), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(existing(1L, 1L, 1), existing(2L, 1L, 1)));

        Map<String, Object> data = service.list(null, null, 5L, STUDENT, 1, 10);

        assertEquals(2L, data.get("total"));
        assertEquals(1, data.get("pageNum"));
        assertEquals(10, data.get("pageSize"));
        assertNotNull(data.get("list"));
        assertNotNull(data.get("rows"));
        assertEquals(2, ((java.util.List<?>) data.get("list")).size());
        // 校级查询不应把 clubId 传下去
        verify(mapper).count(eq(1), eq(null));
    }

    @Test
    @DisplayName("分页参数越界被归一化: pageNum<1→1, pageSize>50→10")
    void paginationNormalised() {
        when(mapper.count(any(), any())).thenReturn(0L);
        when(mapper.selectPage(any(), any(), anyInt(), anyInt())).thenReturn(new ArrayList<>());

        Map<String, Object> data = service.list(1, null, 5L, STUDENT, 0, 999);

        assertEquals(1, data.get("pageNum"));
        assertEquals(10, data.get("pageSize"));
        // 偏移量 = (1-1)*10 = 0, 页大小 = 10
        verify(mapper).selectPage(eq(1), any(), eq(0), eq(10));
    }

    @Test
    @DisplayName("scope=0 未指定 clubId: 400")
    void clubScopeRequiresClubId() {
        assertEquals(400, codeOf(() -> service.list(0, null, 5L, STUDENT, 1, 10)));
    }

    @Test
    @DisplayName("非本社团成员查本社团公告: 403")
    void nonMemberCannotReadClubNotices() {
        when(mapper.selectClubIdByLeader(5L)).thenReturn(null);
        when(mapper.countMember(5L, 3L)).thenReturn(0);

        assertEquals(403, codeOf(() -> service.list(0, 3L, 5L, STUDENT, 1, 10)));
    }

    @Test
    @DisplayName("本社团正式成员可读本社团公告")
    void memberCanReadClubNotices() {
        when(mapper.selectClubIdByLeader(5L)).thenReturn(null);
        when(mapper.countMember(5L, 3L)).thenReturn(1);
        when(mapper.count(eq(0), eq(3L))).thenReturn(1L);
        when(mapper.selectPage(eq(0), eq(3L), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(existing(8L, 7L, 1)));

        Map<String, Object> data = service.list(0, 3L, 5L, STUDENT, 1, 10);

        assertEquals(1L, data.get("total"));
        verify(mapper).countMember(5L, 3L);
    }

    @Test
    @DisplayName("社联管理员可读任意社团公告")
    void platformAdminCanReadAnyClubNotices() {
        when(mapper.count(eq(0), eq(3L))).thenReturn(0L);
        when(mapper.selectPage(eq(0), eq(3L), anyInt(), anyInt())).thenReturn(new ArrayList<>());

        Map<String, Object> data = service.list(0, 3L, 1L, UNION_ADMIN, 1, 10);

        assertEquals(0L, data.get("total"));
        verify(mapper, never()).countMember(anyLong(), anyLong());
    }

    // ------------------------------------------------------------------
    // 撤回
    // ------------------------------------------------------------------

    @Test
    @DisplayName("发布者本人撤回: 调用条件更新")
    void publisherCanWithdraw() {
        when(mapper.selectById(10L)).thenReturn(existing(10L, 7L, 1));
        when(mapper.withdraw(10L)).thenReturn(1);

        service.withdraw(10L, 7L, LEADER);

        verify(mapper).withdraw(10L);
    }

    @Test
    @DisplayName("社联管理员可撤回他人公告")
    void adminCanWithdrawOthersNotice() {
        when(mapper.selectById(10L)).thenReturn(existing(10L, 7L, 1));
        when(mapper.withdraw(10L)).thenReturn(1);

        service.withdraw(10L, 1L, UNION_ADMIN);

        verify(mapper).withdraw(10L);
    }

    @Test
    @DisplayName("无关学生撤回他人公告: 403, 不调用更新")
    void strangerCannotWithdraw() {
        when(mapper.selectById(10L)).thenReturn(existing(10L, 7L, 1));

        assertEquals(403, codeOf(() -> service.withdraw(10L, 5L, STUDENT)));
        verify(mapper, never()).withdraw(anyLong());
    }

    @Test
    @DisplayName("公告不存在: 404; 已撤回再撤回: 409")
    void withdrawEdgeCases() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertEquals(404, codeOf(() -> service.withdraw(99L, 7L, LEADER)));

        when(mapper.selectById(10L)).thenReturn(existing(10L, 7L, 0));
        assertEquals(409, codeOf(() -> service.withdraw(10L, 7L, LEADER)));
        verify(mapper, never()).withdraw(anyLong());
    }

    @Test
    @DisplayName("并发下条件更新影响 0 行: 409(状态已被他人改变)")
    void withdrawLostUpdateReported() {
        when(mapper.selectById(10L)).thenReturn(existing(10L, 7L, 1));
        when(mapper.withdraw(10L)).thenReturn(0);

        assertEquals(409, codeOf(() -> service.withdraw(10L, 7L, LEADER)));
    }

    // ------------------------------------------------------------------
    // 置顶 / 取消置顶
    // ------------------------------------------------------------------

    private NoticePinDTO pin(Integer v) {
        NoticePinDTO d = new NoticePinDTO();
        d.setIsPinned(v);
        return d;
    }

    @Test
    @DisplayName("管理员置顶校级公告: 上限未满时放行")
    void adminPinsUnionNotice() {
        when(mapper.selectById(10L)).thenReturn(full(10L, 2L, 1, null, 0));
        when(mapper.countPinnedUnion()).thenReturn(1);
        when(mapper.updatePinned(10L, 1)).thenReturn(1);

        service.pin(10L, 1L, UNION_ADMIN, pin(1));

        verify(mapper).updatePinned(10L, 1);
    }

    @Test
    @DisplayName("校级置顶已满 3 条时再置顶第 4 条: 409, 且不更新")
    void adminPinUnionRejectedWhenFull() {
        when(mapper.selectById(10L)).thenReturn(full(10L, 2L, 1, null, 0));
        when(mapper.countPinnedUnion()).thenReturn(NoticeService.MAX_UNION_PINNED);

        assertEquals(409, codeOf(() -> service.pin(10L, 1L, UNION_ADMIN, pin(1))));
        verify(mapper, never()).updatePinned(anyLong(), anyInt());
    }

    @Test
    @DisplayName("对已置顶的校级公告重复置顶不受 3 条上限影响")
    void repinAlreadyPinnedNotBlockedByLimit() {
        when(mapper.selectById(10L)).thenReturn(full(10L, 2L, 1, null, 1));
        when(mapper.countPinnedUnion()).thenReturn(NoticeService.MAX_UNION_PINNED);
        when(mapper.updatePinned(10L, 1)).thenReturn(1);

        service.pin(10L, 1L, UNION_ADMIN, pin(1));

        verify(mapper).updatePinned(10L, 1);
    }

    @Test
    @DisplayName("不传 isPinned 时按当前状态切换: 已置顶 → 取消置顶")
    void pinWithoutFlagToggles() {
        when(mapper.selectById(10L)).thenReturn(full(10L, 2L, 1, null, 1));
        when(mapper.updatePinned(10L, 0)).thenReturn(1);

        service.pin(10L, 1L, UNION_ADMIN, null);

        verify(mapper).updatePinned(10L, 0);
    }

    @Test
    @DisplayName("社长可置顶本社团公告")
    void leaderPinsOwnClubNotice() {
        when(mapper.selectById(10L)).thenReturn(full(10L, 7L, 0, 3L, 0));
        when(mapper.selectClubIdByLeader(7L)).thenReturn(3L);
        when(mapper.updatePinned(10L, 1)).thenReturn(1);

        service.pin(10L, 7L, LEADER, pin(1));

        verify(mapper).updatePinned(10L, 1);
    }

    @Test
    @DisplayName("社长置顶别的社团的公告: 403")
    void leaderCannotPinOtherClubNotice() {
        when(mapper.selectById(10L)).thenReturn(full(10L, 4L, 0, 2L, 0));
        when(mapper.selectClubIdByLeader(7L)).thenReturn(3L);

        assertEquals(403, codeOf(() -> service.pin(10L, 7L, LEADER, pin(1))));
        verify(mapper, never()).updatePinned(anyLong(), anyInt());
    }

    @Test
    @DisplayName("社长不能置顶校级公告: 403")
    void leaderCannotPinUnionNotice() {
        when(mapper.selectById(10L)).thenReturn(full(10L, 2L, 1, null, 0));

        assertEquals(403, codeOf(() -> service.pin(10L, 7L, LEADER, pin(1))));
        verify(mapper, never()).updatePinned(anyLong(), anyInt());
    }

    @Test
    @DisplayName("已撤回的公告不能置顶: 409; 公告不存在: 404")
    void pinStatusAndExistenceChecks() {
        when(mapper.selectById(10L)).thenReturn(existing(10L, 2L, 0));
        assertEquals(409, codeOf(() -> service.pin(10L, 1L, UNION_ADMIN, pin(1))));

        when(mapper.selectById(99L)).thenReturn(null);
        assertEquals(404, codeOf(() -> service.pin(99L, 1L, UNION_ADMIN, pin(1))));

        verify(mapper, never()).updatePinned(anyLong(), anyInt());
    }

    @Test
    @DisplayName("并发下条件更新影响 0 行: 409")
    void pinLostUpdateReported() {
        when(mapper.selectById(10L)).thenReturn(full(10L, 2L, 1, null, 0));
        when(mapper.countPinnedUnion()).thenReturn(0);
        when(mapper.updatePinned(10L, 1)).thenReturn(0);

        assertEquals(409, codeOf(() -> service.pin(10L, 1L, UNION_ADMIN, pin(1))));
    }

    // ------------------------------------------------------------------
    // 详情
    // ------------------------------------------------------------------

    @Test
    @DisplayName("详情: 校级公告任何登录用户可看")
    void detailUnionNoticeVisible() {
        Notice n = full(10L, 2L, 1, null, 1);
        when(mapper.selectById(10L)).thenReturn(n);

        Notice got = service.detail(10L, 8L, STUDENT);

        assertNotNull(got);
        assertEquals("标题", got.getTitle());
    }

    @Test
    @DisplayName("详情: 本社团公告对非成员 403")
    void detailClubNoticeForbiddenForNonMember() {
        when(mapper.selectById(10L)).thenReturn(full(10L, 7L, 0, 3L, 0));
        when(mapper.selectClubIdByLeader(8L)).thenReturn(null);
        when(mapper.countMember(8L, 3L)).thenReturn(0);

        assertEquals(403, codeOf(() -> service.detail(10L, 8L, STUDENT)));
    }

    @Test
    @DisplayName("详情: 已撤回公告对无关用户按 404 处理, 不泄露存在性")
    void detailWithdrawnHiddenFromStranger() {
        when(mapper.selectById(10L)).thenReturn(existing(10L, 7L, 0));

        assertEquals(404, codeOf(() -> service.detail(10L, 8L, STUDENT)));
    }

    @Test
    @DisplayName("详情: 已撤回公告对发布者本人仍可见")
    void detailWithdrawnVisibleToPublisher() {
        when(mapper.selectById(10L)).thenReturn(existing(10L, 7L, 0));

        assertNotNull(service.detail(10L, 7L, LEADER));
    }

    @Test
    @DisplayName("业务异常信息可直接展示给用户, 并包含关键约束值")
    void errorMessageReadable() {
        BizException e = assertThrows(BizException.class,
                () -> service.publish(1L, STUDENT, dto("标题", "正文", null, null)));
        assertTrue(e.getMessage() != null && !e.getMessage().isEmpty());

        when(mapper.countPinnedUnion()).thenReturn(NoticeService.MAX_UNION_PINNED);
        BizException pinned = assertThrows(BizException.class,
                () -> service.publish(1L, UNION_ADMIN, dto("标题", "正文", 1, 1)));
        assertTrue(pinned.getMessage().contains(String.valueOf(NoticeService.MAX_UNION_PINNED)),
                "置顶超限提示应说明上限");
    }
}
