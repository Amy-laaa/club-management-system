package com.club.service;

import com.club.common.BizException;
import com.club.dto.AuditDTO;
import com.club.entity.AuditLog;
import com.club.entity.LeaderApply;
import com.club.entity.User;
import com.club.mapper.AuditLogMapper;
import com.club.mapper.LeaderApplyMapper;
import com.club.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 社团负责人资格申请服务(扩展功能)。
 *
 * 链路: 学生提交申请 -> 平台管理员审批 -> 通过后 t_user.role_code 升级为 LEADER。
 * 与 UserAdminService 的"直接指派"并存: 管理员既可直接指派, 也可审批学生申请。
 *
 * 并发控制: 状态迁移使用条件更新(WHERE status = 0) + 影响行数判定,
 * 保证同一申请只被审批一次, 不依赖应用层加锁。
 */
@Service
public class LeaderApplyService {

    private final LeaderApplyMapper leaderApplyMapper;
    private final UserMapper userMapper;
    private final AuditLogMapper auditLogMapper;

    public LeaderApplyService(LeaderApplyMapper leaderApplyMapper,
                              UserMapper userMapper,
                              AuditLogMapper auditLogMapper) {
        this.leaderApplyMapper = leaderApplyMapper;
        this.userMapper = userMapper;
        this.auditLogMapper = auditLogMapper;
    }

    /** 学生提交负责人资格申请 */
    @Transactional
    public void apply(Long userId, String reason) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if ("LEADER".equals(user.getRoleCode())) {
            throw BizException.conflict("您已是社团负责人, 无需重复申请");
        }
        if ("SYS_ADMIN".equals(user.getRoleCode()) || "UNION_ADMIN".equals(user.getRoleCode())) {
            throw BizException.forbidden("管理员账号无需申请社团负责人资格");
        }
        if (leaderApplyMapper.countPendingByUser(userId) > 0) {
            throw BizException.conflict("您已有待审核的申请, 请勿重复提交");
        }
        LeaderApply a = new LeaderApply();
        a.setUserId(userId);
        a.setReason(reason);
        a.setStatus(0);
        leaderApplyMapper.insert(a);
    }

    /** 学生: 我的申请记录 */
    public List<LeaderApply> mine(Long userId) {
        return leaderApplyMapper.selectMine(userId);
    }

    /** 管理员: 按状态分页查看申请 */
    public Map<String, Object> pageByStatus(Integer status, int page, int size) {
        if (status == null || status < 0 || status > 2) status = 0;
        if (page < 1) page = 1;
        if (size < 1 || size > 50) size = 10;
        long total = leaderApplyMapper.countByStatus(status);
        List<LeaderApply> rows = leaderApplyMapper.selectPageByStatus(status, (page - 1) * size, size);
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("rows", rows);
        return data;
    }

    /** 管理员审批: 通过则把申请人角色升级为 LEADER */
    @Transactional
    public void review(Long applyId, AuditDTO dto, Long operatorId) {
        LeaderApply a = leaderApplyMapper.selectById(applyId);
        if (a == null) {
            throw BizException.notFound("申请记录不存在");
        }
        if (a.getStatus() != 0) {
            throw BizException.conflict("该申请已被处理");
        }
        if (dto.isRejectWithoutReason()) {
            throw BizException.badRequest("拒绝申请时必须填写原因");
        }
        int toStatus = dto.getResult() == 1 ? 1 : 2;
        int updated = leaderApplyMapper.reviewFromPending(applyId, toStatus, operatorId, dto.getRemark());
        if (updated == 0) {
            throw BizException.conflict("申请状态已变更, 请刷新后重试");
        }
        // 审批通过: 角色升级为社团负责人(已是指定角色则跳过, 保证幂等)
        if (toStatus == 1) {
            User user = userMapper.selectById(a.getUserId());
            if (user != null && !"LEADER".equals(user.getRoleCode())) {
                userMapper.updateRoleCode(a.getUserId(), "LEADER");
            }
        }
        AuditLog log = new AuditLog();
        log.setBizType("LEADER_APPLY");
        log.setBizId(applyId);
        log.setAuditorId(operatorId);
        log.setResult(dto.getResult());
        String remark = (dto.getRemark() == null || dto.getRemark().trim().isEmpty())
                ? "负责人资格申请审批" : dto.getRemark().trim();
        log.setRemark(remark);
        auditLogMapper.insert(log);
    }
}
