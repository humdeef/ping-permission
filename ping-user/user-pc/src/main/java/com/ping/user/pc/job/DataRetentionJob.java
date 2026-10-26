package com.ping.user.pc.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ping.user.pc.entity.SysLoginLog;
import com.ping.user.pc.entity.SysOperationLog;
import com.ping.user.pc.mapper.SysLoginLogMapper;
import com.ping.user.pc.mapper.SysOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * DataRetentionJob - 审计/登录日志保留策略。
 *
 * <p>每天凌晨清理超过保留期的日志，避免日志表无限增长。
 * 保留天数通过 {@code ping.audit.retention-days} 配置。</p>
 *
 * @author humdeef
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionJob {

    private final SysOperationLogMapper operationLogMapper;
    private final SysLoginLogMapper loginLogMapper;

    @Value("${ping.audit.retention-days:180}")
    private int retentionDays;

    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanExpiredLogs() {
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        try {
            int operations = operationLogMapper.delete(
                    new QueryWrapper<SysOperationLog>().lt("create_time", before));
            int logins = loginLogMapper.delete(
                    new QueryWrapper<SysLoginLog>().lt("create_time", before));
            log.info("[DataRetention] 清理 {} 天前日志：操作日志 {} 条，登录日志 {} 条",
                    retentionDays, operations, logins);
        } catch (Exception e) {
            log.warn("[DataRetention] 清理过期日志失败: {}", e.getMessage());
        }
    }
}
