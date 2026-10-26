package com.ping.user.pc.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ping.core.dto.ApiResponse;
import com.ping.core.dto.PageResult;
import com.ping.user.pc.config.CurrentUser;
import com.ping.user.pc.entity.SysOperationLog;
import com.ping.user.pc.service.DataScopeService;
import com.ping.user.pc.service.OperationLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * OperationLogController - 操作审计日志查询与导出。
 *
 * @author humdeef
 */
@Tag(name = "操作日志")
@RestController
@RequestMapping("/user/pc/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** CSV 字段分隔符。 */
    private static final String CSV_SEPARATOR = ",";

    /** CSV 引号。 */
    private static final String CSV_QUOTE = "\"";

    /** CSV 引号转义。 */
    private static final String CSV_ESCAPED_QUOTE = "\"\"";

    private final OperationLogService operationLogService;
    private final DataScopeService dataScopeService;
    private final CurrentUser currentUser;
    /** 分页查询操作日志。 */
    @GetMapping
    @PreAuthorize("@authz.has('system:auditlog:list')")
    public ResponseEntity<ApiResponse<PageResult<SysOperationLog>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer success) {
        DataScopeService.Scope scope = dataScopeService.resolve(currentUser.id());
        IPage<SysOperationLog> p = operationLogService.page(scope, page, size, username, action, success);
        return ResponseEntity.ok(ApiResponse.ok(PageResult.of(p.getRecords(), p.getTotal())));
    }

    /**
     * 导出 CSV（最多 1 万条）。
     *
     * <p>文件下载接口，直接返回二进制内容，不使用 ApiResponse 包装。</p>
     */
    @GetMapping("/export")
    @PreAuthorize("@authz.has('system:auditlog:list')")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer success) {
        DataScopeService.Scope scope = dataScopeService.resolve(currentUser.id());
        List<SysOperationLog> logs = operationLogService.listForExport(scope, username, action, success);
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("ID,操作人,动作,目标类型,目标ID,链路ID,详情,IP,结果,时间\n");
        for (SysOperationLog log : logs) {
            sb.append(csv(log.getId()))
                    .append(',').append(csv(log.getUsername()))
                    .append(',').append(csv(log.getAction()))
                    .append(',').append(csv(log.getTargetType()))
                    .append(',').append(csv(log.getTargetId()))
                    .append(',').append(csv(log.getTraceId()))
                    .append(',').append(csv(log.getDetail()))
                    .append(',').append(csv(log.getIp()))
                    .append(',').append(log.getSuccess() != null && log.getSuccess() == 1 ? "成功" : "失败")
                    .append(',').append(log.getCreateTime() == null ? "" : TIME_FORMAT.format(log.getCreateTime()))
                    .append('\n');
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=operation-logs.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        text = text.replace("\r", " ").replace("\n", " ");
        if (text.contains(CSV_SEPARATOR) || text.contains(CSV_QUOTE)) {
            return CSV_QUOTE + text.replace(CSV_QUOTE, CSV_ESCAPED_QUOTE) + CSV_QUOTE;
        }
        return text;
    }
}
