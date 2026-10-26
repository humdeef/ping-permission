package com.ping.user.pc.service.impl;

import com.ping.user.common.constant.UserConstants;

import com.ping.user.pc.service.OperationLogService;
import com.ping.user.pc.service.DataScopeService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ping.user.pc.entity.SysOperationLog;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.mapper.SysOperationLogMapper;
import com.ping.user.pc.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * OperationLogServiceImpl - 操作审计日志查询。
 *
 * <p>数据范围：管理员/全部范围可看全部；其他人只能看本人及数据范围内用户的日志，
 * 未登录或无 user_id 的日志（如登录失败）不对普通用户开放。</p>
 *
 * @author humdeef
 */
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {
    /** 操作日志数据访问 */
    private final SysOperationLogMapper operationLogMapper;
    /** 用户数据访问 */
    private final SysUserMapper userMapper;
    /** {@inheritDoc} */
    @Override
    public IPage<SysOperationLog> page(DataScopeService.Scope scope, int page, int size,
                                       String username, String action, Integer success) {
        return operationLogMapper.selectPage(
                new Page<>((long) Math.max(page, 0) + 1, size),
                wrapper(scope, username, action, success).orderByDesc("id"));
    }

    /**
     * 导出用：限制最大条数，避免一次拉爆内存。
     */
    @Override
    public List<SysOperationLog> listForExport(DataScopeService.Scope scope, String username,
                                               String action, Integer success) {
        return operationLogMapper.selectList(
                wrapper(scope, username, action, success)
                        .orderByDesc("id")
                        .last("LIMIT " + UserConstants.EXPORT_LIMIT));
    }

    private QueryWrapper<SysOperationLog> wrapper(DataScopeService.Scope scope, String username,
                                                  String action, Integer success) {
        QueryWrapper<SysOperationLog> wrapper = new QueryWrapper<>();
        if (username != null && !username.isBlank()) {
            wrapper.like("username", username);
        }
        if (action != null && !action.isBlank()) {
            wrapper.like("action", action);
        }
        if (success != null) {
            wrapper.eq("success", success);
        }
        applyScope(wrapper, scope);
        return wrapper;
    }

    /**
     * 把数据范围翻译成 user_id 条件：
     * 全部 -> 不加条件；仅本人 -> user_id = 自己；
     * 部门范围 -> user_id IN (范围内的用户) OR user_id = 自己。
     */
    private void applyScope(QueryWrapper<SysOperationLog> wrapper, DataScopeService.Scope scope) {
        if (scope.all()) {
            return;
        }
        if (scope.deptIds().isEmpty()) {
            wrapper.eq("user_id", scope.userId());
            return;
        }
        List<Long> userIds = userMapper.selectList(
                        new QueryWrapper<SysUser>().select("id").in("dept_id", scope.deptIds()))
                .stream().map(SysUser::getId).collect(Collectors.toList());
        wrapper.and(w -> {
            if (!userIds.isEmpty()) {
                w.in("user_id", userIds).or();
            }
            w.eq("user_id", scope.userId());
        });
    }
}
