package com.ping.dubbo;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.MDC;

/**
 * BizTraceConsumerFilter - BizTraceConsumerFilter。
 *
 * @author humdeef
 */
@Activate(group = CommonConstants.CONSUMER, order = -9999)
public class BizTraceConsumerFilter implements Filter {

    public static final String KEY = "ping-biz-tid";
    public static final String MDC_KEY = "bizTid";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String bizTid = MDC.get(MDC_KEY);
        if (bizTid != null && !bizTid.isBlank()) {
            RpcContext.getClientAttachment().setAttachment(KEY, bizTid);
        }
        try {
            return invoker.invoke(invocation);
        } finally {
            RpcContext.getClientAttachment().removeAttachment(KEY);
        }
    }
}
