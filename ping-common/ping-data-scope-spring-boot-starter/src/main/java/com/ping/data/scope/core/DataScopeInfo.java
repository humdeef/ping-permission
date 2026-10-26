package com.ping.data.scope.core;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 当前请求解析后的数据范围。
 *
 * @param all      是否拥有全部数据（管理员或 SCOPE_ALL）
 * @param deptIds  可见部门 ID 集合
 * @param selfOnly 是否只能看本人数据
 * @param userId   当前用户 ID
 */
public record DataScopeInfo(boolean all, Set<Long> deptIds, boolean selfOnly, Long userId)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public DataScopeInfo {
        deptIds = deptIds == null ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(deptIds));
    }

    /** 不受数据权限限制（App 自有逻辑、系统任务等场景显式使用）。 */
    public static DataScopeInfo all(Long userId) {
        return new DataScopeInfo(true, Collections.emptySet(), false, userId);
    }

    /** 仅本人。 */
    public static DataScopeInfo selfOnly(Long userId) {
        return new DataScopeInfo(false, Collections.emptySet(), true, userId);
    }
}
