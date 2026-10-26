package com.ping.user.pc.service.impl;

import com.ping.user.common.constant.UserConstants;

import com.ping.user.pc.service.AuditLogService;

import cn.dev33.satoken.stp.StpUtil;
import com.ping.user.pc.entity.SysOperationLog;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.mapper.SysOperationLogMapper;
import com.ping.user.pc.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.Executor;

/**
 * AuditLogServiceImpl - 审计日志写入。
 *
 * <p>写日志失败不影响业务主流程；落库异步执行，并带上链路追踪 ID。</p>
 *
 * @author humdeef
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {
    /** 操作日志数据访问 */
    private final SysOperationLogMapper operationLogMapper;
    /** 用户数据访问 */
    private final SysUserMapper userMapper;
    /** 审计日志异步执行器 */
    @Resource(name = "auditExecutor")
    private Executor auditExecutor;

    /**
     * 记录当前登录人的操作。
     */
    @Override
    public void record(String action, String targetType, Object targetId, String detail, boolean success) {
        Long userId = null;
        String username = null;
        try {
            if (StpUtil.isLogin()) {
                userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
                SysUser user = userMapper.selectById(userId);
                username = user == null ? null : user.getUsername();
            }
        } catch (Exception e) {
            log.debug("获取审计操作人失败: {}", e.getMessage());
        }
        record(userId, username, action, targetType, targetId, detail, success);
    }

    /**
     * 记录指定用户的操作（如登录成功/失败）。
     */
    @Override
    public void record(Long userId, String username, String action, String targetType,
                       Object targetId, String detail, boolean success) {
        SysOperationLog entity = new SysOperationLog();
        entity.setUserId(userId);
        entity.setUsername(truncate(username, 64));
        entity.setAction(truncate(action, 64));
        entity.setTargetType(truncate(targetType, 64));
        entity.setTargetId(targetId == null ? "" : truncate(String.valueOf(targetId), 64));
        entity.setTraceId(truncate(MDC.get("bizTid"), 64));
        entity.setDetail(truncate(detail, UserConstants.DETAIL_MAX_LENGTH));
        entity.setIp(currentIp());
        entity.setSuccess(success ? 1 : 0);

        try {
            auditExecutor.execute(() -> {
                try {
                    operationLogMapper.insert(entity);
                } catch (Exception e) {
                    // 审计失败不影响业务
                    log.warn("写入审计日志失败 action={}: {}", action, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("提交审计日志任务失败 action={}: {}", action, e.getMessage());
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String currentIp() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                HttpServletRequest request = servletAttrs.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("获取请求 IP 失败: {}", e.getMessage());
        }
        return "";
    }
}
