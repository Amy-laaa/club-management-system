package com.club.service;

import com.club.common.BizException;
import com.club.dto.AuditDTO;
import com.club.dto.VenueApplyDTO;
import com.club.entity.Venue;
import com.club.entity.VenueApplication;
import com.club.mapper.AuditLogMapper;
import com.club.mapper.VenueApplicationMapper;
import com.club.mapper.VenueMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 场地时段占用冲突单元测试。
 *
 * <p>被测契约: {@code VenueService.apply} 的双重防线 ——
 * <ol>
 *   <li>事务内先查 {@code countOccupied}(同场地 + 同日期 + 同时段, 状态 0待审/1已通过 视为占用),
 *       命中直接抛 409, 这是常态路径;</li>
 *   <li>两个人同时穿过第 1 步时, 由唯一键 {@code uk_venue_slot_status} 兜底,
 *       捕获 {@code DuplicateKeyException} 后同样翻译成 409, 而不是 500。</li>
 * </ol>
 *
 * <p>测试用一个线程安全的内存 Set 模拟"已被占用的时段", 插入时用
 * {@code Set.add} 的原子性复现数据库唯一键的语义, 因此并发用例能真实检验兜底逻辑。
 * 纯 Mockito 测试, 不启动 Spring 容器、不连数据库。
 */
class VenueSlotConflictTest {

    private static final long VENUE_ID = 1L;
    private static final LocalDate USE_DATE = LocalDate.now().plusDays(1);
    private static final String SLOT_EVENING = "18:00-21:00";
    private static final String SLOT_MORNING = "09:00-12:00";

    private static Venue venue(long venueId, int status) {
        Venue v = new Venue();
        v.setVenueId(venueId);
        v.setVenueName("测试场地" + venueId);
        v.setCapacity(100);
        v.setStatus(status);                        // 1 可用 / 0 停用
        return v;
    }

    private static VenueMapper venueMapper(int status) {
        VenueMapper vm = mock(VenueMapper.class);
        when(vm.selectById(anyLong())).thenAnswer(inv -> {
            Long venueId = inv.getArgument(0);
            return venue(venueId, status);
        });
        return vm;
    }

    private static VenueApplyDTO applyDto(long venueId, LocalDate useDate, String timeSlot) {
        VenueApplyDTO dto = new VenueApplyDTO();
        dto.setVenueId(venueId);
        dto.setUseDate(useDate);
        dto.setTimeSlot(timeSlot);
        dto.setPurpose("社团日常活动");
        return dto;
    }

    private static VenueService newService(VenueMapper venueMapper, VenueApplicationMapper appMapper) {
        return new VenueService(venueMapper, appMapper, mock(AuditLogMapper.class));
    }

    /**
     * 内存版的场地申请表。
     * 唯一性口径 = 场地 + 日期 + 时段(与业务规则"同一时段最多一条有效占用"一致,
     * 比数据库唯一键 uk_venue_slot_status 多了 status 维度的合并, 更严格)。
     */
    private static final class SlotStore {
        private final Set<String> occupiedSlots = ConcurrentHashMap.newKeySet();
        private final AtomicLong seq = new AtomicLong(1);

        private static String key(Object venueId, Object useDate, Object timeSlot) {
            return venueId + "|" + useDate + "|" + timeSlot;
        }

        int size() {
            return occupiedSlots.size();
        }

        VenueApplicationMapper mapper() {
            VenueApplicationMapper m = mock(VenueApplicationMapper.class);
            when(m.countOccupied(anyLong(), any(LocalDate.class), anyString())).thenAnswer(inv ->
                    occupiedSlots.contains(key(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2))) ? 1 : 0);
            when(m.insert(any(VenueApplication.class))).thenAnswer(inv -> {
                VenueApplication app = inv.getArgument(0);
                if (!occupiedSlots.add(key(app.getVenueId(), app.getUseDate(), app.getTimeSlot()))) {
                    // 复现唯一键 uk_venue_slot_status 冲突
                    throw new DuplicateKeyException("uk_venue_slot_status");
                }
                app.setAppId(seq.getAndIncrement());
                return 1;
            });
            return m;
        }
    }

    @Test
    @DisplayName("同一场地同一时段重复申请: 第二次抛 409 时段冲突")
    void shouldRejectSecondApplyForSameVenueSlot() {
        SlotStore store = new SlotStore();
        VenueService service = newService(venueMapper(1), store.mapper());

        Long firstAppId = service.apply(10L, applyDto(VENUE_ID, USE_DATE, SLOT_EVENING));
        assertNotNull(firstAppId, "第一个申请应受理成功, 状态为待审核");

        BizException ex = assertThrows(BizException.class,
                () -> service.apply(11L, applyDto(VENUE_ID, USE_DATE, SLOT_EVENING)));
        assertEquals(409, ex.getCode(), "时段被占用属于状态冲突, 应返回 409");
        assertEquals(1, store.size(), "同一时段只允许一条申请落库");
    }

    @Test
    @DisplayName("30 个社团并发申请同一场地同一时段: 仅 1 条成功, 其余全部 409")
    void shouldAllowExactlyOneApplicationUnderConcurrency() throws Exception {
        final int threads = 30;
        SlotStore store = new SlotStore();
        VenueService service = newService(venueMapper(1), store.mapper());

        List<Integer> codes = runConcurrently(threads, i -> {
            try {
                service.apply(100L + i, applyDto(VENUE_ID, USE_DATE, SLOT_EVENING));
                return 200;
            } catch (BizException e) {
                return e.getCode();
            }
        });

        int ok = 0;
        int conflict = 0;
        for (Integer code : codes) {
            if (code != null && code == 200) {
                ok++;
            } else if (code != null && code == 409) {
                conflict++;
            }
        }

        assertEquals(1, ok, "同一场地同一时段只能有一条有效占用");
        assertEquals(threads - 1, conflict, "其余并发请求都要拿到 409, 不能是 500");
        assertEquals(1, store.size());
    }

    @Test
    @DisplayName("同一场地的不同时段互不冲突, 都可以申请")
    void shouldAllowDifferentSlotsOfSameVenue() {
        SlotStore store = new SlotStore();
        VenueService service = newService(venueMapper(1), store.mapper());

        assertNotNull(service.apply(10L, applyDto(VENUE_ID, USE_DATE, SLOT_MORNING)));
        assertNotNull(service.apply(11L, applyDto(VENUE_ID, USE_DATE, SLOT_EVENING)));
        assertEquals(2, store.size());
    }

    @Test
    @DisplayName("不同场地的同一时段互不冲突, 都可以申请")
    void shouldAllowSameSlotOfDifferentVenues() {
        SlotStore store = new SlotStore();
        VenueService service = newService(venueMapper(1), store.mapper());

        assertNotNull(service.apply(10L, applyDto(1L, USE_DATE, SLOT_EVENING)));
        assertNotNull(service.apply(11L, applyDto(2L, USE_DATE, SLOT_EVENING)));
        assertEquals(2, store.size());
    }

    @Test
    @DisplayName("同一场地同一时段换一天则不算冲突")
    void shouldAllowSameSlotOnAnotherDate() {
        SlotStore store = new SlotStore();
        VenueService service = newService(venueMapper(1), store.mapper());

        assertNotNull(service.apply(10L, applyDto(VENUE_ID, USE_DATE, SLOT_EVENING)));
        assertNotNull(service.apply(11L, applyDto(VENUE_ID, USE_DATE.plusDays(1), SLOT_EVENING)));
        assertEquals(2, store.size());
    }

    @Test
    @DisplayName("场地已停用: 抛 409, 且不写入申请单")
    void shouldRejectApplyOnDisabledVenue() {
        SlotStore store = new SlotStore();
        VenueService service = newService(venueMapper(0), store.mapper());

        BizException ex = assertThrows(BizException.class,
                () -> service.apply(10L, applyDto(VENUE_ID, USE_DATE, SLOT_EVENING)));
        assertEquals(409, ex.getCode());
        assertEquals(0, store.size(), "校验失败就不该产生任何申请记录");
    }

    @Test
    @DisplayName("使用日期早于今天: 抛 400, 且不写入申请单")
    void shouldRejectApplyForPastDate() {
        SlotStore store = new SlotStore();
        VenueService service = newService(venueMapper(1), store.mapper());

        BizException ex = assertThrows(BizException.class,
                () -> service.apply(10L, applyDto(VENUE_ID, LocalDate.now().minusDays(1), SLOT_EVENING)));
        assertEquals(400, ex.getCode(), "日期非法属于参数错误, 应返回 400");
        assertEquals(0, store.size());
    }

    @Test
    @DisplayName("审批驳回时必须填写原因, 否则抛 400")
    void shouldRequireRemarkWhenRejecting() {
        VenueApplication pending = new VenueApplication();
        pending.setAppId(5L);
        pending.setStatus(0);

        VenueApplicationMapper appMapper = mock(VenueApplicationMapper.class);
        when(appMapper.selectById(5L)).thenReturn(pending);

        VenueService service = newService(venueMapper(1), appMapper);

        AuditDTO rejectWithoutReason = new AuditDTO();
        rejectWithoutReason.setResult(0);
        rejectWithoutReason.setRemark("   ");

        BizException ex = assertThrows(BizException.class, () -> service.audit(5L, rejectWithoutReason, 2L));
        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("重复审批: 条件更新影响行数为 0 时抛 409, 防止时段被重复批准")
    void shouldRejectConcurrentDuplicateAudit() {
        VenueApplication pending = new VenueApplication();
        pending.setAppId(5L);
        pending.setStatus(0);

        VenueApplicationMapper appMapper = mock(VenueApplicationMapper.class);
        when(appMapper.selectById(5L)).thenReturn(pending);
        // 模拟: 另一个管理员已经先审批过了, 条件更新(WHERE status = 0)影响 0 行
        when(appMapper.updateStatusFromPending(anyLong(), anyInt(), anyString())).thenReturn(0);

        VenueService service = newService(venueMapper(1), appMapper);

        AuditDTO approve = new AuditDTO();
        approve.setResult(1);
        approve.setRemark("同意");

        BizException ex = assertThrows(BizException.class, () -> service.audit(5L, approve, 2L));
        assertEquals(409, ex.getCode());
    }

    /** 并发执行入口: 同一时刻放行所有线程 */
    private interface IndexedTask {
        Integer run(int index) throws Exception;
    }

    private static List<Integer> runConcurrently(int threads, IndexedTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<Future<Integer>>();
        for (int i = 0; i < threads; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                startGate.await();
                return task.run(index);
            }));
        }
        startGate.countDown();
        try {
            List<Integer> codes = new ArrayList<Integer>();
            for (Future<Integer> f : futures) {
                codes.add(f.get(10, TimeUnit.SECONDS));
            }
            return codes;
        } finally {
            pool.shutdownNow();
        }
    }
}
