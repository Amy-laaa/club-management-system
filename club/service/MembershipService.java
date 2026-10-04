package com.club.service;

import com.club.common.BizException;
import com.club.dto.AuditDTO;
import com.club.entity.Club;
import com.club.entity.Membership;
import com.club.mapper.ClubMapper;
import com.club.mapper.MembershipMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 成员资格服务: 入社申请 + 社长审批 + 我的申请。
 * 用例: bu_申请加入社团 / bu_审批入社申请。
 */
@Service
public class MembershipService {

    private final MembershipMapper membershipMapper;
    private final ClubMapper clubMapper;

    public MembershipService(MembershipMapper membershipMapper, ClubMapper clubMapper) {
        this.membershipMapper = membershipMapper;
        this.clubMapper = clubMapper;
    }

    /**
     * 学生提交入社申请。用例 bu_申请加入社团。
     * 前置条件: 社团已成立且处于纳新期; 同一学生同一社团只能有一条生效申请。
     */
    @Transactional
    public void apply(Long userId, Long clubId, String applyReason) {
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            throw BizException.notFound("社团不存在");
        }
        if (club.getStatus() != 1) {
            throw BizException.conflict("社团未成立或已注销, 不可申请");
        }
        if (club.getRecruitDeadline() != null && LocalDateTime.now().isAfter(club.getRecruitDeadline())) {
            throw BizException.conflict("该社团已停止纳新");
        }
        if (membershipMapper.countActive(userId, clubId) > 0) {
            throw BizException.conflict("您已申请或已加入该社团, 不可重复申请");
        }
        Membership m = new Membership();
        m.setUserId(userId);
        m.setClubId(clubId);
        m.setMemberRole("MEMBER");
        m.setApplyReason(applyReason);
        m.setStatus(0);      // 待审核
        membershipMapper.insert(m);
    }

    /**
     * 社长审批入社申请。用例 bu_审批入社申请。
     * 权限: 仅该社团负责人(创建者)可审批; 拒绝必须填原因。
     */
    @Transactional
    public void audit(Long membershipId, AuditDTO dto, Long operatorId) {
        Membership m = membershipMapper.selectById(membershipId);
        if (m == null) {
            throw BizException.notFound("申请记录不存在");
        }
        if (m.getStatus() != 0) {
            throw BizException.conflict("该申请已被处理");
        }
        Club club = clubMapper.selectById(m.getClubId());
        if (club == null || !club.getLeaderId().equals(operatorId)) {
            throw BizException.forbidden("仅社团负责人可审批");
        }
        if (dto.isRejectWithoutReason()) {
            throw BizException.badRequest("拒绝申请时必须填写原因");
        }
        int toStatus = dto.getResult() == 1 ? 1 : 2;    // 1正式成员 2已拒绝
        LocalDateTime joinedAt = dto.getResult() == 1 ? LocalDateTime.now() : null;
        int updated = membershipMapper.updateStatusFromPending(membershipId, toStatus, joinedAt);
        if (updated == 0) {
            throw BizException.conflict("申请状态已变更, 请刷新");
        }
    }

    /** 学生: 我的社团与入社申请 */
    public List<Membership> mine(Long userId) {
        return membershipMapper.selectMine(userId);
    }

    /** 社长: 本社团待审核申请分页列表 */
    public Map<String, Object> pendingByClub(Long clubId, Long operatorId, int page, int size) {
        requireClubLeader(clubId, operatorId);
        if (page < 1) page = 1;
        if (size < 1 || size > 50) size = 10;
        long total = membershipMapper.countByClub(clubId, 0);
        List<Membership> rows = membershipMapper.selectPageByClub(clubId, 0, (page - 1) * size, size);
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("rows", rows);
        return data;
    }

    /** 校验 operatorId 是 clubId 的负责人 */
    private void requireClubLeader(Long clubId, Long operatorId) {
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            throw BizException.notFound("社团不存在");
        }
        if (!club.getLeaderId().equals(operatorId)) {
            throw BizException.forbidden("仅社团负责人可操作");
        }
    }
}
