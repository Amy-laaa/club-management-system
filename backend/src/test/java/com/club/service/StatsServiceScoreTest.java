package com.club.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 社团活跃度算分单元测试(纯计算, 不依赖 Spring 容器与数据库)。
 *
 * <p>被测口径: 得分 = 100 × (0.6 × 成员数/最大成员数 + 0.4 × 活动数/最大活动数),
 * 会重点覆盖除零边界与权重方向, 这两处是统计模块最容易出错的地方。
 */
class StatsServiceScoreTest {

    private static final double DELTA = 0.0001;

    @Test
    @DisplayName("两个维度都取到全体最大值的社团得满分 100")
    void shouldBeFullScoreWhenBothDimensionsAreMax() {
        double score = StatsService.activityScore(5, 5, 4, 4);
        assertEquals(100.0, score, DELTA);
    }

    @Test
    @DisplayName("全体为 0 时不发生除零, 得分为 0")
    void shouldReturnZeroWhenNoData() {
        assertEquals(0.0, StatsService.activityScore(0, 0, 0, 0), DELTA);
    }

    @Test
    @DisplayName("成员数占 60% 权重: 仅成员拉满得 60 分")
    void shouldWeightMemberDimensionBy60Percent() {
        double score = StatsService.activityScore(4, 4, 0, 4);
        assertEquals(60.0, score, DELTA);
    }

    @Test
    @DisplayName("活动数占 40% 权重: 仅活动拉满得 40 分")
    void shouldWeightActivityDimensionBy40Percent() {
        double score = StatsService.activityScore(0, 4, 6, 6);
        assertEquals(40.0, score, DELTA);
    }

    @Test
    @DisplayName("成员规模优先于活动频次(0.6 > 0.4)")
    void memberDimensionShouldOutweighActivityDimension() {
        // A: 成员拉满(60) + 活动一半(20) = 80
        double clubA = StatsService.activityScore(2, 2, 1, 2);
        // B: 成员一半(30) + 活动拉满(40) = 70
        double clubB = StatsService.activityScore(1, 2, 2, 2);
        assertEquals(80.0, clubA, DELTA);
        assertEquals(70.0, clubB, DELTA);
        assertTrue(clubA > clubB, "成员规模权重更高, 得分应更高");
    }

    @Test
    @DisplayName("得分单调不减: 成员或活动增加, 得分不会下降")
    void scoreShouldBeMonotonic() {
        double low = StatsService.activityScore(1, 10, 1, 10);
        double high = StatsService.activityScore(2, 10, 1, 10);
        assertTrue(high >= low);
    }

    @Test
    @DisplayName("单社团独大时(自己就是最大值)得满分")
    void singleClubShouldScoreFull() {
        double score = StatsService.activityScore(1, 1, 1, 1);
        assertEquals(100.0, score, DELTA);
    }
}
