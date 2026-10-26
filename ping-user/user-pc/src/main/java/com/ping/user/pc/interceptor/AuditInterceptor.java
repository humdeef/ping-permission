package com.ping.user.pc.interceptor;

import com.ping.user.pc.annotation.Audit;
import com.ping.user.pc.audit.AuditContext;
import com.ping.user.pc.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * AuditInterceptor - 为标注 {@link Audit} 的 Controller 方法写审计日志。
 *
 * <p>使用 Spring MVC 拦截器实现，避免引入额外 AOP 依赖。
 * 只记录动作、目标类型、目标 ID 和方法签名，不记录请求参数。</p>
 *
 * @author humdeef
 */
@Component
@RequiredArgsConstructor
public class AuditInterceptor implements HandlerInterceptor {

    /** 路径变量名：主键 ID。 */
    private static final String PATH_VARIABLE_ID = "id";

    private final AuditLogService auditLogService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return;
        }
        // 无论是否命中 @Audit 都要清理，避免线程复用串数据
        String diff = AuditContext.getDetailAndClear();
        Audit audit = handlerMethod.getMethodAnnotation(Audit.class);
        if (audit == null) {
            return;
        }
        String targetId = "";
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars instanceof Map<?, ?> varsMap && varsMap.get(PATH_VARIABLE_ID) != null) {
            targetId = String.valueOf(varsMap.get(PATH_VARIABLE_ID));
        }
        String detail = handlerMethod.getShortLogMessage();
        if (diff != null && !diff.isBlank()) {
            detail = detail + " | " + diff;
        }
        auditLogService.record(audit.action(), audit.targetType(), targetId, detail, ex == null);
    }
}
