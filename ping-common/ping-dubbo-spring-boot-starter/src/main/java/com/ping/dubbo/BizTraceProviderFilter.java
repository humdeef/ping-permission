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
 * BizTraceProviderFilter - Dubbo 提供端：把 attachment 中的 bizTid 恢复到 MDC。
 *
 * <p>恢复后，logback 的 {@code %X{bizTid}} 即可打印出与上游一致的业务链路 ID。</p>
 *
 * @author humdeef
 */
@Activate(group = CommonConstants.PROVIDER, order = -9999)
public class BizTraceProviderFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String bizTid = RpcContext.getServerAttachment().getAttachment(BizTraceConsumerFilter.KEY);
        if (bizTid != null && !bizTid.isBlank()) {
            MDC.put(BizTraceConsumerFilter.MDC_KEY, bizTid);
        }
        try {
            return invoker.invoke(invocation);
        } finally {
            MDC.remove(BizTraceConsumerFilter.MDC_KEY);
        }
    }
}
