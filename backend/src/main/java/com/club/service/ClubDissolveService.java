package com.club.service;

import com.club.common.BizException;
import com.club.dto.AuditDTO;
import com.club.entity.AuditLog;
import com.club.entity.Club;
import com.club.entity.ClubDissolve;
import com.club.mapper.ClubDissolveMapper;
import com.club.mapper.ClubMapper;
import com.club.mapper.MembershipMapper;
import com.club.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 社团解散服务。
 *
 * 依据设计说明书表 1-1: 社团负责人可"解散社团", 社团联合会管理员"审批社团成立/注销"。
 * 三段式流程:
 *   1) 负责人提交解散申请         -> t_club_dissolve.status = 0 待审批
 *   2) 社联/系统管理员审批          -> 1 已批准(待执行) / 2 已驳回
 *   3) 负责人执行注销               -> t_club.status = 2 已注销, 申请记录 status = 3
 *
 * 两处状态迁移都用「条件更新 + 影响行数判定」保证并发下只被执行一次。
 * 审批与注销结果写入 t_audit_log(biz_type=CLUB, biz_id=clubId),
 * 与"社团成立审核"共用同一条审批历史, 便于用工单式流水展示。
 */
@Service
public class ClubDissolveService {

    private final ClubDissolveMapper dissolveMapper;
    private final ClubMapper clubMapper;
    private final MembershipMapper membershipMapper;
    private final AuditLogMapper auditLogMapper;

    public ClubDissolveService(ClubDissolveMapper dissolveMapper,
                               ClubMapper clubMapper,
                               MembershipMapper membershipMapper,
                               AuditLogMapper auditLogMapper) {
        this.dissolveMapper = dissolveMapper;
        this.clubMapper = clubMapper;
        this.membershipMapper = membershipMapper;
        this.auditLogMapper = auditLogMapper;
    }

    /** 负责人提交解散申请 */
    @Transactional
    public void apply(Long clubId, Long userId, String reason) {
        Club club = requireLeaderClub(clubId, userId);
        if (club.getStatus() != 1) {
            throw BizException.conflict("社团不在已成立状态, 不可申请解散");
        }
        if (dissolveMapper.countPendingByClub(clubId) > 0) {
            throw BizException.conflict("已有一条待审批的解散申请, 请勿重复提交");
        }
        if (dissolveMapper.selectApprovedByClub(clubId) != null) {
            throw BizException.conflict("解散申请已获批准, 请直接执行解散");
        }
        ClubDissolve d = new ClubDissolve();
        d.setClubId(clubId);
        d.setApplicantId(userId);
        d.setReason(reason);
        d.setStatus(0);
        dissolveMapper.insert(d);
    }

    /** 负责人查看本社团最近一条解散申请(无记录返回 null) */
    public ClubDissolve latest(Long clubId, Long userId) {
        requireLeaderClub(clubId, userId);
        return dissolveMapper.selectLatestByClub(clubId);
    }

    /** 管理员: 按状态分页查看解散申请 */
    public Map<String, Object> pageByStatus(Integer status, int page, int size) {
        if (status == null || status < 0 || status > 3) status = 0;
        if (page < 1) page = 1;
        if (size < 1 || size > 50) size = 10;
        long total = dissolveMapper.countByStatus(status);
        List<ClubDissolve> rows = dissolveMapper.selectPageByStatus(status, (page - 1) * size, size);
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("rows", rows);
        return data;
    }

    /** 管理员审批: 批准后进入"待执行", 由负责人最终确认注销 */
    @Transactional
    public void review(Long dissolveId, AuditDTO dto, Long operatorId) {
        ClubDissolve d = dissolveMapper.selectById(dissolveId);
        if (d == null) {
            throw BizException.notFound("解散申请不存在");
        }
        if (d.getStatus() != 0) {
            throw BizException.conflict("该申请已被处理");
        }
        if (dto.isRejectWithoutReason()) {
            throw BizException.badRequest("驳回申请时必须填写原因");
        }
        int toStatus = dto.getResult() == 1 ? 1 : 2;
        int updated = dissolveMapper.reviewFromPending(dissolveId, toStatus, operatorId, dto.getRemark());
        if (updated == 0) {
            throw BizException.conflict("申请状态已变更, 请刷新后重试");
        }
        writeLog(d.getClubId(), operatorId, dto.getResult(),
                (toStatus == 1 ? "社团解散申请已批准: " : "社团解散申请已驳回: ") + safe(dto.getRemark()));
    }

    /** 负责人执行注销: 须已有审批通过的解散申请 */
    @Transactional
    public void execute(Long clubId, Long userId) {
        Club club = requireLeaderClub(clubId, userId);
        if (club.getStatus() != 1) {
            throw BizException.conflict("社团不在已成立状态, 无需解散");
        }
        ClubDissolve approved = dissolveMapper.selectApprovedByClub(clubId);
        if (approved == null) {
            throw BizException.conflict("尚未有审批通过的解散申请, 不可解散");
        }
        int updated = clubMapper.updateStatusToDissolved(clubId);   // 1 -> 2, 条件更新
        if (updated == 0) {
            throw BizException.conflict("社团状态已变更, 请刷新后重试");
        }
        if (dissolveMapper.markExecuted(approved.getDissolveId()) == 0) {
            throw BizException.conflict("解散申请状态已变更, 请刷新后重试");
        }
        // 社团注销后, 该社团所有生效成员记录置为已退出, 避免"我负责的社团"仍显示已解散社团
        membershipMapper.dissolveAllByClub(clubId);
        writeLog(clubId, userId, 1, "社团已解散(负责人执行注销): " + safe(approved.getReason()));
    }

    /** 校验 clubId 存在且 operatorId 是其负责人 */
    private Club requireLeaderClub(Long clubId, Long userId) {
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            throw BizException.notFound("社团不存在");
        }
        if (!club.getLeaderId().equals(userId)) {
            throw BizException.forbidden("仅该社团负责人可操作");
        }
        return club;
    }

    private void writeLog(Long clubId, Long operatorId, Integer result, String remark) {
        AuditLog log = new AuditLog();
        log.setBizType("CLUB");
        log.setBizId(clubId);
        log.setAuditorId(operatorId);
        log.setResult(result);
        log.setRemark(remark);
        auditLogMapper.insert(log);
    }

    private String safe(String s) {
        return (s == null || s.trim().isEmpty()) ? "无" : s.trim();
    }
}
