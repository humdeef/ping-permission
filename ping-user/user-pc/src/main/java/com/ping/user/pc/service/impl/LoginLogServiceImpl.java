package com.ping.user.pc.service.impl;

import com.ping.user.pc.service.LoginLogService;
import com.ping.user.pc.service.DataScopeService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ping.user.pc.entity.SysLoginLog;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.mapper.SysLoginLogMapper;
import com.ping.user.pc.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * LoginLogServiceImpl - 登录日志。
 *
 * @author humdeef
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogServiceImpl implements LoginLogService {
    /** 登录日志数据访问 */
    private final SysLoginLogMapper loginLogMapper;
    /** 用户数据访问 */
    private final SysUserMapper userMapper;
    /** 审计日志异步执行器 */
    @Resource(name = "auditExecutor")
    private Executor auditExecutor;

    /**
     * 记录当前请求的登录结果（IP/UA 必须在请求线程内读取）。
     */
    @Override
    public void recordCurrentRequest(Long userId, String username, boolean success, String message) {
        SysLoginLog entity = new SysLoginLog();
        entity.setUserId(userId);
        entity.setUsername(truncate(username, 64));
        entity.setSuccess(success ? 1 : 0);
        entity.setMessage(truncate(message, 256));
        HttpServletRequest request = currentRequest();
        if (request != null) {
            entity.setIp(truncate(clientIp(request), 64));
            entity.setUserAgent(truncate(request.getHeader("User-Agent"), 512));
        }
        try {
            auditExecutor.execute(() -> {
                try {
                    loginLogMapper.insert(entity);
                } catch (Exception e) {
                    log.warn("写入登录日志失败 username={}: {}", username, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("提交登录日志任务失败 username={}: {}", username, e.getMessage());
        }
    }
    /** {@inheritDoc} */
    @Override
    public IPage<SysLoginLog> page(DataScopeService.Scope scope, int page, int size,
                                   String username, Integer success) {
        QueryWrapper<SysLoginLog> wrapper = new QueryWrapper<>();
        if (username != null && !username.isBlank()) {
            wrapper.like("username", username);
        }
        if (success != null) {
            wrapper.eq("success", success);
        }
        applyScope(wrapper, scope);
        wrapper.orderByDesc("id");
        return loginLogMapper.selectPage(new Page<>((long) Math.max(page, 0) + 1, size), wrapper);
    }

    /**
     * 数据范围：管理员/全部范围可见全部；其他人只能看本人及范围内用户的登录日志。
     */
    private void applyScope(QueryWrapper<SysLoginLog> wrapper, DataScopeService.Scope scope) {
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

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
