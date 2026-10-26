package com.ping.data.scope.core;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 请求级数据范围上下文。
 *
 * <p>每个请求只解析一次，后续所有 SQL 复用。解析期间使用 resolving 标记
 * 跳过数据权限，避免“解析数据范围”本身的查询又被数据权限拦截。</p>
 *
 * <p>Servlet 过滤器会在请求结束时清理；线程池/异步任务通过
 * {@link TransmittableThreadLocal} 传递已有上下文。</p>
 *
 * @author humdeef
 */
public final class DataScopeContext {

    private static final TransmittableThreadLocal<State> STATE = new TransmittableThreadLocal<>();

    private DataScopeContext() {
    }

    /** 当前请求已解析的范围；未解析返回 null。 */
    public static DataScopeInfo get() {
        State state = STATE.get();
        return state == null ? null : state.info();
    }

    public static void set(DataScopeInfo info) {
        STATE.set(new State(info, false));
    }

    /** 是否正在解析数据范围（此时拦截器必须放行解析自身的查询）。 */
    public static boolean isResolving() {
        State state = STATE.get();
        return state != null && state.resolving();
    }

    /**
     * 解析当前用户的数据范围，并缓存到当前线程。
     *
     * <p>如果已经处于解析过程中，返回 null，调用方应跳过过滤。</p>
     */
    public static DataScopeInfo resolve(DataScopeProvider provider) {
        State current = STATE.get();
        if (current != null && current.info() != null) {
            return current.info();
        }
        if (current != null && current.resolving()) {
            return null;
        }
        STATE.set(State.RESOLVING);
        try {
            DataScopeInfo info = provider.resolve();
            STATE.set(new State(info, false));
            return info;
        } catch (RuntimeException e) {
            STATE.remove();
            throw e;
        }
    }

    public static void clear() {
        STATE.remove();
    }

    private record State(DataScopeInfo info, boolean resolving) {
        private static final State RESOLVING = new State(null, true);

        private State {
            // record 紧凑构造器：保留解析标记
        }
    }
}
