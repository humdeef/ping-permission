package com.ping.dubbo;

import com.ping.core.context.UserContext;
import com.ping.core.context.UserContextHolder;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;

/**
 * UserContextProviderFilter - Dubbo 提供端：从 RPC attachment 恢复用户、角色、权限。
 *
 * <p>提供方业务代码可直接使用：</p>
 * <pre>{@code
 * Long callerId = UserContextHolder.getUserId();
 * boolean canEdit = UserContextHolder.hasPermission("system:user:edit");
 * }</pre>
 *
 * @author humdeef
 */
@Activate(group = CommonConstants.PROVIDER, order = -10000)
public class UserContextProviderFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String userId = RpcContext.getServerAttachment().getAttachment(UserContextConsumerFilter.KEY);
        if (userId != null && !userId.isBlank()) {
            String roles = RpcContext.getServerAttachment().getAttachment(UserContextConsumerFilter.ROLES_KEY);
            String permissions = RpcContext.getServerAttachment().getAttachment(UserContextConsumerFilter.PERMS_KEY);
            String operatorType = RpcContext.getServerAttachment()
                    .getAttachment(UserContextConsumerFilter.OPERATOR_TYPE_KEY);
            String operatorName = RpcContext.getServerAttachment()
                    .getAttachment(UserContextConsumerFilter.OPERATOR_NAME_KEY);
            String deptId = RpcContext.getServerAttachment()
                    .getAttachment(UserContextConsumerFilter.DEPT_ID_KEY);
            try {
                Integer typeCode = operatorType == null || operatorType.isBlank()
                        ? null : Integer.valueOf(operatorType);
                Long dept = deptId == null || deptId.isBlank() ? null : Long.valueOf(deptId);
                UserContextHolder.set(UserContext.of(Long.valueOf(userId), roles, permissions,
                        typeCode, operatorName, dept));
            } catch (NumberFormatException ignored) {
                // 非法上下文直接忽略
            }
        }
        try {
            return invoker.invoke(invocation);
        } finally {
            UserContextHolder.clear();
        }
    }
}
