package com.ping.user.client.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * DataScopeDTO - 数据范围查询结果。
 *
 * <p>由 user-service 解析后返回给其他服务，供数据权限拦截器拼接 SQL 条件。</p>
 *
 * @author humdeef
 * @date 2026/10/24
 */
@Data
@NoArgsConstructor
public class DataScopeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否拥有全部数据 */
    private Boolean all;
    /** 可见部门 ID 集合（非 all 时生效） */
    private List<Long> deptIds;
    /** 是否仅能看本人数据 */
    private Boolean selfOnly;
    /** 当前用户 ID */
    private Long userId;

    public static DataScopeDTO all(Long userId) {
        return new DataScopeDTO(Boolean.TRUE, Set.of(), Boolean.FALSE, userId);
    }

    public static DataScopeDTO selfOnly(Long userId) {
        return new DataScopeDTO(Boolean.FALSE, Set.of(), Boolean.TRUE, userId);
    }

    /**
     * 以部门集合构造数据范围。
     *
     * @param all      是否拥有全部数据
     * @param deptIds  可见部门 ID 集合
     * @param selfOnly 是否仅能看本人数据
     * @param userId   当前用户 ID
     */
    public DataScopeDTO(Boolean all, Set<Long> deptIds, Boolean selfOnly, Long userId) {
        this.all = all;
        this.deptIds = deptIds == null ? new ArrayList<>() : new ArrayList<>(new LinkedHashSet<>(deptIds));
        this.selfOnly = selfOnly;
        this.userId = userId;
    }
}
