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
 * UserContextConsumerFilter - UserContextConsumerFilter。
 *
 * @author humdeef
 */
@Activate(group = CommonConstants.CONSUMER, order = -10000)
public class UserContextConsumerFilter implements Filter {

    public static final String KEY = "ping-user-id";
    public static final String ROLES_KEY = "ping-user-roles";
    public static final String PERMS_KEY = "ping-user-perms";
    public static final String OPERATOR_TYPE_KEY = "ping-operator-type";
    public static final String OPERATOR_NAME_KEY = "ping-operator-name";
    public static final String DEPT_ID_KEY = "ping-user-dept-id";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        UserContext context = UserContextHolder.get();
        if (context != null) {
            RpcContext.getClientAttachment().setAttachment(KEY, String.valueOf(context.getUserId()));
            RpcContext.getClientAttachment().setAttachment(ROLES_KEY, String.join(",", context.getRoles()));
            RpcContext.getClientAttachment().setAttachment(PERMS_KEY, String.join(",", context.getPermissions()));
            RpcContext.getClientAttachment().setAttachment(OPERATOR_TYPE_KEY,
                    String.valueOf(context.getOperatorType().getCode()));
            if (context.getUserName() != null) {
                RpcContext.getClientAttachment().setAttachment(OPERATOR_NAME_KEY, context.getUserName());
            }
            if (context.getDeptId() != null) {
                RpcContext.getClientAttachment().setAttachment(DEPT_ID_KEY, String.valueOf(context.getDeptId()));
            }
        }
        try {
            return invoker.invoke(invocation);
        } finally {
            RpcContext.getClientAttachment().removeAttachment(KEY);
            RpcContext.getClientAttachment().removeAttachment(ROLES_KEY);
            RpcContext.getClientAttachment().removeAttachment(PERMS_KEY);
            RpcContext.getClientAttachment().removeAttachment(OPERATOR_TYPE_KEY);
            RpcContext.getClientAttachment().removeAttachment(OPERATOR_NAME_KEY);
            RpcContext.getClientAttachment().removeAttachment(DEPT_ID_KEY);
        }
    }
}
