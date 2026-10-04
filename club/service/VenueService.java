package com.club.service;

import com.club.common.BizException;
import com.club.dto.AuditDTO;
import com.club.dto.VenueApplyDTO;
import com.club.entity.AuditLog;
import com.club.entity.Venue;
import com.club.entity.VenueApplication;
import com.club.mapper.AuditLogMapper;
import com.club.mapper.VenueApplicationMapper;
import com.club.mapper.VenueMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 场地服务: 场地列表+时段占用、申请场地(冲突检测)、我的申请、社联审批。
 * 用例: bu_申请场地 / bu_审批场地申请。
 * 核心约束: 同一场地同一日期同一时段最多一条有效占用(0待审/1已通过),
 *          事务内先查冲突, 唯一键 uk_venue_slot_status 兜底并发。
 */
@Service
public class VenueService {

    private final VenueMapper venueMapper;
    private final VenueApplicationMapper venueApplicationMapper;
    private final AuditLogMapper auditLogMapper;

    public VenueService(VenueMapper venueMapper,
                        VenueApplicationMapper venueApplicationMapper,
                        AuditLogMapper auditLogMapper) {
        this.venueMapper = venueMapper;
        this.venueApplicationMapper = venueApplicationMapper;
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 场地列表(带指定日期的时段占用情况, 供场地×日期×时段矩阵渲染, 占用时段前端置灰)。
     */
    public List<Map<String, Object>> listWithSlots(LocalDate date) {
        List<Venue> venues = venueMapper.selectAll();
        List<VenueApplication> occupied = venueApplicationMapper.selectOccupiedByDate(date);

        // 按场地分组占用时段
        Map<Long, List<String>> occupiedMap = new HashMap<>();
        for (VenueApplication app : occupied) {
            List<String> slots = occupiedMap.get(app.getVenueId());
            if (slots == null) {
                slots = new ArrayList<String>();
                occupiedMap.put(app.getVenueId(), slots);
            }
            slots.add(app.getTimeSlot() + "(" + (app.getStatus() == 0 ? "待审" : "已通过") + ")");
        }

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Venue v : venues) {
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("venueId", v.getVenueId());
            item.put("venueName", v.getVenueName());
            item.put("capacity", v.getCapacity());
            item.put("status", v.getStatus());
            item.put("date", date.toString());
            item.put("occupiedSlots", occupiedMap.get(v.getVenueId()));
            result.add(item);
        }
        return result;
    }

    /**
     * 社团负责人申请场地。用例 bu_申请场地。
     * 前置: 场地可用; 日期不早于今天; 时段与已占用时段不冲突。
     * 后置: 生成 t_venue_application, 状态 = 待审核。
     */
    @Transactional
    public Long apply(Long applicantId, VenueApplyDTO dto) {
        Venue venue = venueMapper.selectById(dto.getVenueId());
        if (venue == null) {
            throw BizException.notFound("场地不存在");
        }
        if (venue.getStatus() != 1) {
            throw BizException.conflict("场地已停用, 不可申请");
        }
        if (dto.getUseDate().isBefore(LocalDate.now())) {
            throw BizException.badRequest("使用日期不能早于今天");
        }
        if (venueApplicationMapper.countOccupied(dto.getVenueId(), dto.getUseDate(), dto.getTimeSlot()) > 0) {
            throw BizException.conflict("该时段已被占用或存在待审申请, 请更换时段");
        }
        VenueApplication app = new VenueApplication();
        app.setVenueId(dto.getVenueId());
        app.setActivityId(dto.getActivityId());
        app.setApplicantId(applicantId);
        app.setUseDate(dto.getUseDate());
        app.setTimeSlot(dto.getTimeSlot());
        app.setPurpose(dto.getPurpose());
        app.setStatus(0);           // 待审核
        try {
            venueApplicationMapper.insert(app);
        } catch (DuplicateKeyException e) {
            // 并发兜底: 两人同时申请同一时段, 唯一键保证只有一条能插入
            throw BizException.conflict("该时段刚被他人申请, 请刷新后重试");
        }
        return app.getAppId();
    }

    /** 学生/负责人: 我的场地申请列表 */
    public List<VenueApplication> mine(Long applicantId) {
        return venueApplicationMapper.selectMine(applicantId);
    }

    /** 社联管理员: 待审核申请分页列表 */
    public Map<String, Object> pendingList(int page, int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 50) size = 10;
        long total = venueApplicationMapper.countByStatus(0);
        List<VenueApplication> rows = venueApplicationMapper.selectPageByStatus(0, (page - 1) * size, size);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("rows", rows);
        return data;
    }

    /**
     * 社联管理员审批场地申请。用例 bu_审批场地申请。
     * 通过 → 状态 1, 时段被锁定; 驳回 → 状态 2, 必须填原因。
     * 审批结果写入 t_audit_log (biz_type = VENUE_APP)。
     */
    @Transactional
    public void audit(Long appId, AuditDTO dto, Long operatorId) {
        VenueApplication app = venueApplicationMapper.selectById(appId);
        if (app == null) {
            throw BizException.notFound("场地申请不存在");
        }
        if (app.getStatus() != 0) {
            throw BizException.conflict("该申请已被处理");
        }
        if (dto.isRejectWithoutReason()) {
            throw BizException.badRequest("驳回申请时必须填写原因");
        }
        int toStatus = dto.getResult() == 1 ? 1 : 2;    // 1已通过(锁定时段) 2已驳回
        int updated = venueApplicationMapper.updateStatusFromPending(appId, toStatus, dto.getRemark());
        if (updated == 0) {
            throw BizException.conflict("申请状态已变更, 请刷新");
        }
        AuditLog log = new AuditLog();
        log.setBizType("VENUE_APP");
        log.setBizId(appId);
        log.setAuditorId(operatorId);
        log.setResult(dto.getResult());
        log.setRemark(dto.getRemark());
        auditLogMapper.insert(log);
    }
}
