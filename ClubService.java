package com.club.service;

import com.club.common.BizException;
import com.club.dto.AuditDTO;
import com.club.dto.ClubCreateDTO;
import com.club.entity.AuditLog;
import com.club.entity.Club;
import com.club.mapper.AuditLogMapper;
import com.club.mapper.ClubMapper;
import com.club.mapper.MembershipMapper;
import com.club.entity.Membership;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 社团服务: 匿名浏览 + 创建社团 + 社联审核。
 * 用例: bu_创建社团 / bu_审核公开活动(社团成立部分)。
 */
@Service
public class ClubService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ClubMapper clubMapper;
    private final MembershipMapper membershipMapper;
    private final AuditLogMapper auditLogMapper;

    public ClubService(ClubMapper clubMapper, MembershipMapper membershipMapper, AuditLogMapper auditLogMapper) {
        this.clubMapper = clubMapper;
        this.membershipMapper = membershipMapper;
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 分页列表。匿名访问默认只返回已成立社团。
     */
    public Map<String, Object> list(Integer status, String keyword, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 50) size = 10;
        if (status == null) status = 1;   // 匿名默认: 已成立
        long total = clubMapper.count(status, keyword);
        List<Club> rows = clubMapper.selectPage(status, keyword, (page - 1) * size, size);
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("rows", rows);
        return data;
    }

    public Club detail(Long clubId) {
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            throw BizException.notFound("社团不存在");
        }
        return club;
    }

    /**
     * 创建社团(提交审核)。用例 bu_创建社团。
     * 业务规则: 名称全校唯一; 一个负责人同一时刻只能有一个待审核社团。
     */
    @Transactional
    public Long create(Long leaderId, ClubCreateDTO dto) {
        if (clubMapper.countByName(dto.getClubName().trim()) > 0) {
            throw BizException.conflict("社团名称已存在, 请更换名称");
        }
        if (clubMapper.countPendingByLeader(leaderId) > 0) {
            throw BizException.conflict("您已有一个待审核的社团, 不可重复创建");
        }
        LocalDateTime deadline = parseDeadline(dto.getRecruitDeadline());

        Club club = new Club();
        club.setLeaderId(leaderId);
        club.setClubName(dto.getClubName().trim());
        club.setCategory(dto.getCategory());
        club.setIntro(dto.getIntro());
        club.setCharter(dto.getCharter());
        club.setAdvisor(dto.getAdvisor());
        club.setRecruitDeadline(deadline);
        clubMapper.insert(club);      // status = 0 待审核

        // 负责人同时成为该社团的第一条成员资格(LEADER)
        Membership leaderMs = new Membership();
        leaderMs.setUserId(leaderId);
        leaderMs.setClubId(club.getClubId());
        leaderMs.setMemberRole("LEADER");
        leaderMs.setApplyReason("创建者");
        leaderMs.setStatus(1);        // 直接正式成员
        leaderMs.setJoinedAt(LocalDateTime.now());
        membershipMapper.insert(leaderMs);
        return club.getClubId();
    }

    /**
     * 社联审核社团成立申请。通过 -> 已成立(开放纳新); 驳回 -> 保持待审核, 负责人可修改后重提。
     * 审批结果写入 t_audit_log(用例后置条件: 审批操作必须可追溯到审核人账号)。
     */
    @Transactional
    public void auditClub(Long clubId, AuditDTO dto, Long auditorId) {
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            throw BizException.notFound("社团不存在");
        }
        if (club.getStatus() != 0) {
            throw BizException.conflict("该社团不在待审核状态, 不可审核");
        }
        int updated = 0;
        if (dto.getResult() == 1) {
            updated = clubMapper.updateStatusFromPending(clubId, 1);   // 已成立
        } else {
            updated = clubMapper.updateStatusFromPending(clubId, 2);   // 驳回 -> 已注销, 负责人需重新创建
        }
        if (updated == 0) {
            throw BizException.conflict("状态已变更, 请刷新后重试");
        }
        // 审核通过时防御性补录: 早期版本创建的社团可能缺少社长成员记录,
        // 导致"我负责的社团"统计为 0, 这里兜底补齐(UNIQUE 约束防重复)
        if (dto.getResult() == 1 && membershipMapper.countActive(club.getLeaderId(), clubId) == 0) {
            Membership leaderMs = new Membership();
            leaderMs.setUserId(club.getLeaderId());
            leaderMs.setClubId(clubId);
            leaderMs.setMemberRole("LEADER");
            leaderMs.setApplyReason("创建者(审核补录)");
            leaderMs.setStatus(1);        // 直接正式成员
            leaderMs.setJoinedAt(LocalDateTime.now());
            membershipMapper.insert(leaderMs);
        }
        AuditLog log = new AuditLog();
        log.setBizType("CLUB");
        log.setBizId(clubId);
        log.setAuditorId(auditorId);
        log.setResult(dto.getResult());
        log.setRemark(dto.getRemark());
        auditLogMapper.insert(log);
    }

    /** 待审核社团列表(社联工作台) */
    public Map<String, Object> pendingList(int page, int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 50) size = 10;
        long total = clubMapper.count(0, null);
        List<Club> rows = clubMapper.selectPage(0, null, (page - 1) * size, size);
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("rows", rows);
        return data;
    }

    /** 某社团审批历史 */
    public List<AuditLog> auditHistory(Long clubId) {
        return auditLogMapper.selectByBiz("CLUB", clubId);
    }

    private LocalDateTime parseDeadline(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return LocalDateTime.parse(s.trim(), DT);
        } catch (DateTimeParseException e) {
            throw BizException.badRequest("纳新截止时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }
}
