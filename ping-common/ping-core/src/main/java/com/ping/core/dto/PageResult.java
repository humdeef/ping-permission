package com.ping.core.dto;

import lombok.Data;

import java.util.List;

/**
 * PageResult - 统一分页响应。
 *
 * <p>JSON 结构保持 {@code {list, total}}，与现有前端兼容。</p>
 *
 * @param <T> 列表元素类型
 *
 * @author humdeef
 */
@Data
public class PageResult<T> {

    private List<T> list;
    private long total;

    public PageResult() {
    }

    public PageResult(List<T> list, long total) {
        this.list = list;
        this.total = total;
    }

    public static <T> PageResult<T> of(List<T> list, long total) {
        return new PageResult<>(list, total);
    }
}
