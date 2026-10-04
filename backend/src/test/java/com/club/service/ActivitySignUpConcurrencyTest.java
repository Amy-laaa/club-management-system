package com.club.service;

import com.club.common.BizException;
import com.club.entity.Activity;
import com.club.entity.Registration;
import com.club.mapper.ActivityMapper;
import com.club.mapper.AuditLogMapper;
import com.club.mapper.ClubMapper;
import com.club.mapper.RegistrationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 活动报名名额并发扣减单元测试。
 *
 * <p>被测契约: {@code ActivityService.signUp} 的名额扣减完全依赖 Mapper 的
 * <b>条件更新</b>({@code UPDATE t_activity SET remain = remain - 1
 * WHERE activity_id = ? AND remain > 0 AND status = 1})。
 * 本测试用 Mockito 模拟该语句的真实语义(带 CAS 的原子递减),
 * 验证在 N 个线程同时抢 M 个名额时, 报名成功数恰好为 M:
 * <ul>
 *   <li>不会超卖(成功数 &gt; 名额);</li>
 *   <li>超出名额的请求进入候补(status=3), 而不是直接报错;</li>
 *   <li>剩余名额 remain 恰好扣到 0, 不会变成负数。</li>
 * </ul>
 *
 * <p><b>测试范围说明</b>: 这里验证的是"给定原子的条件更新, Service 层不会超卖"。
 * SQL 语句本身在 MySQL 上的原子性由端到端测试覆盖(40 并发报名实测无超卖)。
 * 最后一个用例 {@link #shouldOversellWhenDeductIsNotAtomic()} 是<b>反例</b>,
 * 用同样的写法换掉扣减实现, 断言它确实会超卖 —— 以此证明本测试对超卖问题敏感,
 * 能真正抓到 bug, 而不是一个恒过的空测试。
 *
 * <p>纯 Mockito 测试, 不启动 Spring 容器、不连数据库, 可在任意机器上运行。
 */
class ActivitySignUpConcurrencyTest {

    private static final long ACTIVITY_ID = 100L;
    private static final long CLUB_ID = 1L;

    /** 已发布、报名窗口未关闭的活动 */
    private static Activity publishedActivity(int capacity) {
        Activity a = new Activity();
        a.setActivityId(ACTIVITY_ID);
        a.setClubId(CLUB_ID);
        a.setTitle("并发抢名额压测活动");
        a.setActType(0);
        a.setCapacity(capacity);
        a.setRemain(capacity);
        a.setStatus(1);                                                  // 已发布, 可报名
        a.setStartTime(LocalDateTime.now().plusDays(3));
        a.setEndTime(LocalDateTime.now().plusDays(3).plusHours(2));
        a.setSignupDeadline(LocalDateTime.now().plusDays(1));             // 尚未截止
        return a;
    }

    private static ActivityService newService(ActivityMapper activityMapper, RegistrationMapper registrationMapper) {
        return new ActivityService(activityMapper, registrationMapper,
                mock(ClubMapper.class), mock(AuditLogMapper.class));
    }

    /** 默认报名的 Mapper: 从未报名过, 插入即成功并回填主键 */
    private static RegistrationMapper registrationMapper() {
        RegistrationMapper rm = mock(RegistrationMapper.class);
        when(rm.selectByActivityAndUser(anyLong(), anyLong())).thenReturn(null);
        AtomicLong seq = new AtomicLong(1);
        when(rm.insert(any(Registration.class))).thenAnswer(inv -> {
            Registration reg = inv.getArgument(0);
            reg.setRegId(seq.getAndIncrement());
            return 1;
        });
        return rm;
    }

    @Test
    @DisplayName("40 名学生同抢 5 个名额: 恰好 5 人报名成功, 其余全部进候补, 名额扣到 0 不为负")
    void shouldNotOversellWhenManyStudentsSignUpAtOnce() throws Exception {
        final int threads = 40;
        final int capacity = 5;
        AtomicInteger remain = new AtomicInteger(capacity);

        ActivityMapper activityMapper = mock(ActivityMapper.class);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(publishedActivity(capacity));
        when(activityMapper.deductRemain(ACTIVITY_ID)).thenAnswer(inv -> {
            // 模拟 UPDATE ... WHERE remain > 0 的原子性: 读出前值 -> CAS 写回
            while (true) {
                int current = remain.get();
                if (current <= 0) {
                    return 0;                       // 影响行数 0 = 名额已抢空
                }
                if (remain.compareAndSet(current, current - 1)) {
                    return 1;                       // 影响行数 1 = 占位成功
                }
            }
        });

        ActivityService service = newService(activityMapper, registrationMapper());

        List<Map<String, Object>> results = runConcurrently(threads, i -> service.signUp(ACTIVITY_ID, (long) (i + 1)));

        int registered = 0;
        int waiting = 0;
        for (Map<String, Object> r : results) {
            int status = (Integer) r.get("status");
            if (status == 0) {
                registered++;
            } else if (status == 3) {
                waiting++;
            }
        }

        assertEquals(capacity, registered, "报名成功人数必须恰好等于名额, 一个都不能超卖");
        assertEquals(threads - capacity, waiting, "超出名额的学生必须进入候补队列, 而不是报名失败");
        assertEquals(0, remain.get(), "名额应恰好扣完");
        assertTrue(remain.get() >= 0, "剩余名额不允许为负数");
    }

    @Test
    @DisplayName("反例: 扣减若退化成\"先查后改\"就必然超卖 —— 证明上面的测试确实能抓到问题")
    void shouldOversellWhenDeductIsNotAtomic() throws Exception {
        final int threads = 20;
        final int capacity = 3;
        AtomicInteger remain = new AtomicInteger(capacity);
        // 让所有线程都先读到同一个前值, 再一起写回 —— 把"先查后改"的竞态放大到必现
        CyclicBarrier allReadSameValue = new CyclicBarrier(threads);

        ActivityMapper activityMapper = mock(ActivityMapper.class);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(publishedActivity(capacity));
        when(activityMapper.deductRemain(ACTIVITY_ID)).thenAnswer(inv -> {
            int current = remain.get();                      // 先查
            allReadSameValue.await(5, TimeUnit.SECONDS);
            if (current <= 0) {
                return 0;
            }
            remain.set(current - 1);                         // 后改
            return 1;
        });

        ActivityService service = newService(activityMapper, registrationMapper());

        List<Map<String, Object>> results = runConcurrently(threads, i -> service.signUp(ACTIVITY_ID, (long) (i + 1)));

        int registered = 0;
        for (Map<String, Object> r : results) {
            if (((Integer) r.get("status")) == 0) {
                registered++;
            }
        }
        assertTrue(registered > capacity,
                "非原子的扣减必然超卖(实际成功 " + registered + " 人 > 名额 " + capacity + "),"
                        + " 说明本测试对超卖是敏感的");
    }

    @Test
    @DisplayName("同一学生重复报名: 命中前置校验, 抛 409 并提示查看已有凭证")
    void shouldRejectDuplicateSignUpByPreCheck() {
        ActivityMapper activityMapper = mock(ActivityMapper.class);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(publishedActivity(5));

        Registration existing = new Registration();
        existing.setRegId(9L);
        existing.setStatus(0);                                      // 已报名

        RegistrationMapper registrationMapper = mock(RegistrationMapper.class);
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, 7L)).thenReturn(existing);

        ActivityService service = newService(activityMapper, registrationMapper);

        BizException ex = assertThrows(BizException.class, () -> service.signUp(ACTIVITY_ID, 7L));
        assertEquals(409, ex.getCode(), "重复报名属于状态冲突, 应返回 409");
    }

    @Test
    @DisplayName("并发下同一学生双击报名: 前置校验没拦住时, 由唯一键 uk_act_user 兜底抛 409")
    void shouldRejectDuplicateSignUpByUniqueKey() {
        ActivityMapper activityMapper = mock(ActivityMapper.class);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(publishedActivity(5));
        when(activityMapper.deductRemain(ACTIVITY_ID)).thenReturn(1);

        RegistrationMapper registrationMapper = mock(RegistrationMapper.class);
        when(registrationMapper.selectByActivityAndUser(anyLong(), anyLong())).thenReturn(null);
        // 两个请求都通过了前置校验, 第二个插入时撞唯一键
        when(registrationMapper.insert(any(Registration.class)))
                .thenThrow(new DuplicateKeyException("uk_act_user"));

        ActivityService service = newService(activityMapper, registrationMapper);

        BizException ex = assertThrows(BizException.class, () -> service.signUp(ACTIVITY_ID, 7L));
        assertEquals(409, ex.getCode(), "唯一键冲突应被翻译成 409, 而不是 500");
    }

    @Test
    @DisplayName("活动待审核(未发布)时不可报名, 抛 409")
    void shouldRejectSignUpWhenActivityNotPublished() {
        Activity pending = publishedActivity(5);
        pending.setStatus(0);                                        // 公开活动待审核

        ActivityMapper activityMapper = mock(ActivityMapper.class);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(pending);

        ActivityService service = newService(activityMapper, registrationMapper());

        BizException ex = assertThrows(BizException.class, () -> service.signUp(ACTIVITY_ID, 7L));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("审核"), "应提示审核通过后才能报名");
    }

    @Test
    @DisplayName("已过报名截止时间: 抛 409 且不扣减名额")
    void shouldRejectSignUpAfterDeadline() {
        Activity expired = publishedActivity(5);
        expired.setSignupDeadline(LocalDateTime.now().minusMinutes(1));

        AtomicInteger deductCalls = new AtomicInteger(0);
        ActivityMapper activityMapper = mock(ActivityMapper.class);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(expired);
        when(activityMapper.deductRemain(ACTIVITY_ID)).thenAnswer(inv -> {
            deductCalls.incrementAndGet();
            return 1;
        });

        ActivityService service = newService(activityMapper, registrationMapper());

        BizException ex = assertThrows(BizException.class, () -> service.signUp(ACTIVITY_ID, 7L));
        assertEquals(409, ex.getCode());
        assertEquals(0, deductCalls.get(), "已过截止时间就不该再碰名额");
    }

    /** 并发执行入口: 所有线程在同一时刻放行, 最大化对同一资源的竞争 */
    private interface IndexedTask {
        Map<String, Object> run(int index) throws Exception;
    }

    private static List<Map<String, Object>> runConcurrently(int threads, IndexedTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Map<String, Object>>> futures = new ArrayList<Future<Map<String, Object>>>();
        for (int i = 0; i < threads; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                startGate.await();
                return task.run(index);
            }));
        }
        startGate.countDown();
        try {
            List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
            for (Future<Map<String, Object>> f : futures) {
                results.add(f.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
