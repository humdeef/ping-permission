package com.ping.order.model;

/**
 * OrderOperation - OrderOperation。
 *
 * @author humdeef
 */
public final class OrderOperation {

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String PAY = "PAY";
    public static final String CANCEL = "CANCEL";
    public static final String COMPLETE = "COMPLETE";
    public static final String DELETE = "DELETE";

    private OrderOperation() {
    }
}
