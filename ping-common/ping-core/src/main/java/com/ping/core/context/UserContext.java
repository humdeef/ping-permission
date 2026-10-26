package com.ping.core.context;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * UserContext - 当前登录用户上下文（用户 ID + 角色 + 权限 + 操作人类型）。
 *
 * <p>不可变对象，可安全跨线程传递。</p>
 *
 * <p>注意：{@code userId} 可能来自后台 {@code sys_user}，也可能来自 App {@code app_user}。
 * 仅看 ID 无法区分两者，因此额外保存 {@link OperatorType}：
 * 审计字段写入的是「类型 + ID + 名称快照」，而不是裸的 {@code update_id}。</p>
 *
 * @author humdeef
 */
public final class UserContext implements Serializable {

    private static final long serialVersionUID = 2L;

    /** CSV 分隔符。 */
    private static final String CSV_SEPARATOR = ",";

    private final Long userId;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final OperatorType operatorType;
    private final String userName;
    /** 后台用户部门 ID；App 用户为 null。用于数据权限与业务表部门快照。 */
    private final Long deptId;

    /**
     * 兼容旧调用：根据 {@code app} 角色推断操作人类型。
     */
    public UserContext(Long userId, Set<String> roles, Set<String> permissions) {
        this(userId, roles, permissions, OperatorType.fromRoles(roles, userId), null);
    }

    public UserContext(Long userId, Set<String> roles, Set<String> permissions, OperatorType operatorType) {
        this(userId, roles, permissions, operatorType, null);
    }

    public UserContext(Long userId, Set<String> roles, Set<String> permissions,
                       OperatorType operatorType, String userName) {
        this(userId, roles, permissions, operatorType, userName, null);
    }

    public UserContext(Long userId, Set<String> roles, Set<String> permissions,
                       OperatorType operatorType, String userName, Long deptId) {
        this.userId = userId;
        this.roles = roles == null ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
        this.permissions = permissions == null ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
        this.operatorType = operatorType == null
                ? OperatorType.fromRoles(this.roles, userId) : operatorType;
        this.userName = userName;
        this.deptId = deptId;
    }

    public Long getUserId() {
        return userId;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public OperatorType getOperatorType() {
        return operatorType;
    }

    /**
     * 登录时缓存的展示名；旧 token 或未写入时可能为 {@code null}。
     */
    public String getUserName() {
        return userName;
    }

    /**
     * 后台用户部门 ID；App 用户或旧 token 可能为 {@code null}。
     */
    public Long getDeptId() {
        return deptId;
    }

    public boolean hasRole(String role) {
        return role != null && roles.contains(role);
    }

    /**
     * 权限匹配：精确匹配 + {@code xxx:*} 通配。
     */
    public boolean hasPermission(String permission) {
        if (permission == null) {
            return false;
        }
        if (permissions.contains(permission)) {
            return true;
        }
        for (String granted : permissions) {
            if (granted.endsWith(":*") && permission.startsWith(granted.substring(0, granted.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    public static UserContext of(Long userId, String rolesCsv, String permissionsCsv) {
        return new UserContext(userId, split(rolesCsv), split(permissionsCsv));
    }

    public static UserContext of(Long userId, String rolesCsv, String permissionsCsv,
                                 Integer operatorTypeCode, String userName) {
        return of(userId, rolesCsv, permissionsCsv, operatorTypeCode, userName, null);
    }

    public static UserContext of(Long userId, String rolesCsv, String permissionsCsv,
                                 Integer operatorTypeCode, String userName, Long deptId) {
        return new UserContext(userId, split(rolesCsv), split(permissionsCsv),
                OperatorType.fromCode(operatorTypeCode), userName, deptId);
    }

    private static Set<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String item : csv.split(CSV_SEPARATOR)) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }
        return result;
    }
}
