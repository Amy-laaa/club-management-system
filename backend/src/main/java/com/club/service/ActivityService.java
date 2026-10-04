package com.club.service;

import com.club.common.BizException;
import com.club.dto.ActivityCreateDTO;
import com.club.dto.AuditDTO;
import com.club.entity.Activity;
import com.club.entity.AuditLog;
import com.club.entity.Club;
import com.club.entity.Registration;
import com.club.mapper.ActivityMapper;
import com.club.mapper.AuditLogMapper;
import com.club.mapper.ClubMapper;
import com.club.mapper.RegistrationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 活动服务: 发布活动、活动列表/详情、报名(防超卖)、取消报名、签到核销、审核公开活动、取消活动。
 * 用例: bu_发布活动 / bu_活动报名 / bu_活动签到 / bu_审核公开活动。
 *
 * 两个强一致资源在本模块的处理:
 *   1) 报名名额: 条件更新 remain = remain - 1 WHERE remain > 0 AND status = 1, 影响行数 0 即满额转候补;
 *   2) 凭证码: 随机唯一 + 核销条件更新(status = 0 才可核销), 保证一码一核防重放。
 */
@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    /** 签到窗口: 活动开始前 1 小时 ~ 结束后 2 小时(用例 bu_活动签到 业务规则) */
    private static final int CHECKIN_BEFORE_MINUTES = 60;
    private static final int CHECKIN_AFTER_MINUTES = 120;

    /** 凭证码字符集: 去掉了易混淆的 0/O/1/I */
    private static final char[] VOUCHER_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ActivityMapper activityMapper;
    private final RegistrationMapper registrationMapper;
    private final ClubMapper clubMapper;
    private final AuditLogMapper auditLogMapper;

    public ActivityService(ActivityMapper activityMapper,
                           RegistrationMapper registrationMapper,
                           ClubMapper clubMapper,
                           AuditLogMapper auditLogMapper) {
        this.activityMapper = activityMapper;
        this.registrationMapper = registrationMapper;
        this.clubMapper = clubMapper;
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 社团负责人发布活动。用例 bu_发布活动。
     * 前置: 社团状态 = 已成立; 操作者为该社团负责人; 活动时间在未来。
     * 后置: 内部活动直接已发布; 公开活动状态 = 待审核。
     */
    @Transactional
    public Long publish(Long operatorId, ActivityCreateDTO dto) {
        Club club = clubMapper.selectById(dto.getClubId());
        if (club == null) {
            throw BizException.notFound("社团不存在");
        }
        if (club.getStatus() != 1) {
            throw BizException.conflict("社团未成立或已注销, 不可发布活动");
        }
        if (!club.getLeaderId().equals(operatorId)) {
            throw BizException.forbidden("仅本社团负责人可发布活动");
        }
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw BizException.badRequest("结束时间必须晚于开始时间");
        }
        if (!dto.getSignupDeadline().isBefore(dto.getStartTime())) {
            throw BizException.badRequest("报名截止时间必须早于活动开始时间");
        }
        // 业务规则: 同社团同一时段最多一场活动(演示口径)
        if (activityMapper.countTimeOverlap(dto.getClubId(), dto.getStartTime(), dto.getEndTime()) > 0) {
            throw BizException.conflict("本社团在该时段已有活动, 同一时段最多一场");
        }
        Activity activity = new Activity();
        activity.setClubId(dto.getClubId());
        activity.setVenueAppId(dto.getVenueAppId());
        activity.setTitle(dto.getTitle());
        activity.setActType(dto.getActType());
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
        activity.setLocation(dto.getLocation());
        activity.setCapacity(dto.getCapacity());
        activity.setRemain(dto.getCapacity());            // 初始剩余 = 名额
        activity.setSignupDeadline(dto.getSignupDeadline());
        activity.setIntro(dto.getIntro());
        activity.setStatus(dto.getActType() == 1 ? 0 : 1);  // 公开→待审核; 内部→已发布
        activityMapper.insert(activity);
        return activity.getActivityId();
    }

    /** 活动列表(匿名可访问, 仅展示已发布/进行中), 支持社团、类型、标题筛选 */
    public Map<String, Object> list(Long clubId, Integer actType, String keyword, int page, int size) {
        refreshStatus();      // 惰性推进状态: 到点的活动自动转为进行中/已结束
        if (page < 1) page = 1;
        if (size < 1 || size > 50) size = 10;
        long total = activityMapper.countPublished(clubId, actType, keyword);
        List<Activity> rows = activityMapper.selectPublishedPage(clubId, actType, keyword, (page - 1) * size, size);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("rows", rows);
        return data;
    }

    /** 活动详情 + 报名统计(剩余名额、已报名/候补人数) */
    public Map<String, Object> detail(Long activityId) {
        refreshStatus();
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw BizException.notFound("活动不存在");
        }
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("activity", activity);
        data.put("registeredCount", registrationMapper.countByActivityAndStatus(activityId, 0));
        data.put("checkedInCount", registrationMapper.countByActivityAndStatus(activityId, 1));
        data.put("waitingCount", registrationMapper.countByActivityAndStatus(activityId, 3));
        data.put("signupOpen", isSignupOpen(activity));
        return data;
    }

    /**
     * 学生报名。用例 bu_活动报名。
     * 前置: 活动状态 = 已发布; 当前时间在报名窗口内。
     * 后置: 生成 t_registration(已报名 或 候补), 名额占用或候补增加。
     * 并发安全: 名额用条件更新扣减; 重复报名由唯一键兜底。
     */
    @Transactional
    public Map<String, Object> signUp(Long activityId, Long userId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw BizException.notFound("活动不存在");
        }
        if (activity.getStatus() != 1) {
            if (activity.getStatus() == 0) {
                throw BizException.conflict("公开活动审核通过后才能报名");
            }
            throw BizException.conflict("活动当前不可报名");
        }
        if (LocalDateTime.now().isAfter(activity.getSignupDeadline())) {
            throw BizException.conflict("已过报名截止时间");
        }

        // 已报名/已签到/候补 → 拒绝重复报名(展示已有凭证)
        Registration exist = registrationMapper.selectByActivityAndUser(activityId, userId);
        if (exist != null && (exist.getStatus() == 0 || exist.getStatus() == 1 || exist.getStatus() == 3)) {
            throw BizException.conflict("您已报名该活动, 请查看已有凭证");
        }

        // 核心: 有名额则占位成功, 否则进入候补队列
        int toStatus;
        if (activityMapper.deductRemain(activityId) == 1) {
            toStatus = 0;   // 已报名
        } else {
            toStatus = 3;   // 满额 → 候补
        }

        String voucher = nextVoucherCode();
        Registration reg;
        if (exist != null) {
            // 曾取消过(status=2): 复用记录重新报名, 避免撞唯一键
            reg = exist;
            reg.setStatus(toStatus);
            reg.setVoucherCode(voucher);
            reg.setActivityTitle(activity.getTitle());
            reg.setStartTime(activity.getStartTime());
            if (registrationMapper.reactivate(reg) == 0) {
                throw BizException.conflict("报名状态已变更, 请刷新后重试");
            }
        } else {
            reg = new Registration();
            reg.setActivityId(activityId);
            reg.setUserId(userId);
            reg.setActivityTitle(activity.getTitle());
            reg.setStartTime(activity.getStartTime());
            reg.setVoucherCode(voucher);
            reg.setStatus(toStatus);
            try {
                registrationMapper.insert(reg);
            } catch (DuplicateKeyException e) {
                // 并发兜底: 同一学生对同一活动同时点了两次报名
                throw BizException.conflict("您已报名该活动, 请勿重复提交");
            }
        }

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("regId", reg.getRegId());
        data.put("voucherCode", reg.getVoucherCode());
        data.put("status", toStatus);
        data.put("waiting", toStatus == 3);
        data.put("message", toStatus == 3 ? "名额已满, 已为您加入候补队列" : "报名成功, 请凭凭证码签到");
        return data;
    }

    /**
     * 取消报名: 报名截止前可取消, 取消后名额回补(候补记录不占名额, 无需回补)。
     */
    @Transactional
    public void cancelSignUp(Long activityId, Long userId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw BizException.notFound("活动不存在");
        }
        Registration reg = registrationMapper.selectByActivityAndUser(activityId, userId);
        if (reg == null || reg.getStatus() == 2) {
            throw BizException.conflict("您尚未报名该活动");
        }
        if (LocalDateTime.now().isAfter(activity.getSignupDeadline())) {
            throw BizException.conflict("已过报名截止时间, 不可取消");
        }
        if (reg.getStatus() == 0) {
            if (registrationMapper.cancelFromRegistered(reg.getRegId()) == 0) {
                throw BizException.conflict("报名状态已变更, 请刷新");
            }
            activityMapper.increaseRemain(activityId);   // 名额回补
        } else if (reg.getStatus() == 3) {
            if (registrationMapper.cancelFromWaiting(reg.getRegId()) == 0) {
                throw BizException.conflict("报名状态已变更, 请刷新");
            }
        } else {
            throw BizException.conflict("已签到的报名不可取消");
        }
    }

    /** 我的报名记录 */
    public List<Registration> myRegistrations(Long userId) {
        return registrationMapper.selectMine(userId);
    }

    /** 负责人查看本社团某活动的报名名单(status 可选: 0已报名 1已签到 2已取消 3候补) */
    public List<Registration> registrationsOfActivity(Long activityId, Integer status, Long operatorId) {
        requireActivityLeader(activityId, operatorId);
        return registrationMapper.selectByActivityAndStatus(activityId, status == null ? 0 : status);
    }

    /**
     * 负责人核销签到。用例 bu_活动签到。
     * 前置: 活动处于签到窗口(开始前 1 小时 ~ 结束后 2 小时); 报名状态 = 已报名。
     * 后置: 报名记录 status = 1, 写入签到时间。
     * 防重放: 条件更新 status = 0 才可核销, 一码仅可核销一次。
     */
    @Transactional
    public Map<String, Object> checkIn(Long activityId, String voucherCode, Long operatorId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw BizException.notFound("活动不存在");
        }
        requireActivityLeader(activityId, operatorId);

        Registration reg = registrationMapper.selectByVoucher(voucherCode);
        if (reg == null) {
            log.warn("签到失败-凭证无效: activityId={}, voucher={}, operatorId={}", activityId, voucherCode, operatorId);
            throw BizException.notFound("凭证无效");
        }
        if (!reg.getActivityId().equals(activityId)) {
            log.warn("签到失败-凭证不属于本活动: voucher={}, regActivityId={}, pathActivityId={}",
                    voucherCode, reg.getActivityId(), activityId);
            throw BizException.conflict("该凭证不属于本活动");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime openFrom = activity.getStartTime().minusMinutes(CHECKIN_BEFORE_MINUTES);
        LocalDateTime openTo = activity.getEndTime().plusMinutes(CHECKIN_AFTER_MINUTES);
        if (now.isBefore(openFrom) || now.isAfter(openTo)) {
            throw BizException.conflict("不在签到窗口内(活动开始前 1 小时至结束后 2 小时)");
        }
        if (registrationMapper.checkin(reg.getRegId()) == 0) {
            log.warn("签到失败-重复核销或状态不符: regId={}, voucher={}", reg.getRegId(), voucherCode);
            throw BizException.conflict("该凭证已核销或状态不允许核销");
        }
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("regId", reg.getRegId());
        data.put("userId", reg.getUserId());
        data.put("voucherCode", voucherCode);
        return data;
    }

    /**
     * 负责人取消活动: 状态 → 已取消, 该活动下 已报名/候补 记录一并取消, 剩余名额回补。
     */
    @Transactional
    public void cancelActivity(Long activityId, Long operatorId) {
        requireActivityLeader(activityId, operatorId);
        int checkedIn = registrationMapper.countByActivityAndStatus(activityId, 1);
        if (activityMapper.cancelById(activityId) == 0) {
            throw BizException.conflict("活动当前状态不可取消");
        }
        registrationMapper.cancelAllByActivity(activityId);
        activityMapper.resetRemainAfterCancel(activityId, checkedIn);
    }

    /**
     * 社联管理员审核公开活动。用例 bu_审核公开活动。
     * 通过 → 已发布(对全校可见, 可报名); 驳回 → 已取消(意见记入审批记录)。
     * 审批结果写入 t_audit_log (biz_type = ACTIVITY)。
     */
    @Transactional
    public void audit(Long activityId, AuditDTO dto, Long operatorId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw BizException.notFound("活动不存在");
        }
        if (activity.getStatus() != 0) {
            throw BizException.conflict("该活动不在待审核状态");
        }
        if (dto.isRejectWithoutReason()) {
            throw BizException.badRequest("驳回活动时必须填写原因");
        }
        int toStatus = dto.getResult() == 1 ? 1 : 4;    // 通过→已发布; 驳回→已取消
        if (activityMapper.auditFromPending(activityId, toStatus) == 0) {
            throw BizException.conflict("活动状态已变更, 请刷新");
        }
        AuditLog logRow = new AuditLog();
        logRow.setBizType("ACTIVITY");
        logRow.setBizId(activityId);
        logRow.setAuditorId(operatorId);
        logRow.setResult(dto.getResult());
        logRow.setRemark(dto.getRemark());
        auditLogMapper.insert(logRow);
    }

    /** 社联管理员: 待审核公开活动分页列表 */
    public Map<String, Object> pendingList(int page, int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 50) size = 10;
        long total = activityMapper.countPending();
        List<Activity> rows = activityMapper.selectPendingPage((page - 1) * size, size);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("rows", rows);
        return data;
    }

    /** 负责人: 本社团活动列表(含待审核、已结束) */
    public Map<String, Object> myClubActivities(Long clubId, Long operatorId, int page, int size) {
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            throw BizException.notFound("社团不存在");
        }
        if (!club.getLeaderId().equals(operatorId)) {
            throw BizException.forbidden("仅社团负责人可查看");
        }
        if (page < 1) page = 1;
        if (size < 1 || size > 50) size = 10;
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("rows", activityMapper.selectPageByClub(clubId, (page - 1) * size, size));
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /** 报名窗口是否开放(前端据此置灰报名按钮) */
    private boolean isSignupOpen(Activity activity) {
        return activity.getStatus() == 1
                && activity.getRemain() > 0
                && LocalDateTime.now().isBefore(activity.getSignupDeadline());
    }

    /** 校验操作者是该活动所属社团的负责人 */
    private void requireActivityLeader(Long activityId, Long operatorId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw BizException.notFound("活动不存在");
        }
        Club club = clubMapper.selectById(activity.getClubId());
        if (club == null || !club.getLeaderId().equals(operatorId)) {
            throw BizException.forbidden("仅本社团负责人可操作");
        }
    }

    /** 惰性状态推进: 到点的活动自动转为进行中/已结束(避免引入定时任务) */
    private void refreshStatus() {
        activityMapper.advanceToOngoing();
        activityMapper.advanceToFinished();
    }

    /** 生成 16 位随机凭证码(唯一键兜底) */
    private String nextVoucherCode() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(VOUCHER_CHARS[RANDOM.nextInt(VOUCHER_CHARS.length)]);
        }
        return sb.toString();
    }
}
