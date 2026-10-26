package com.ping.user.pc.service.impl;

import com.ping.user.pc.service.PermissionService;
import com.ping.user.pc.service.PermissionCacheService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ping.user.pc.audit.AuditContext;
import com.ping.user.pc.model.MenuNode;
import com.ping.user.pc.model.PermissionRequest;
import com.ping.user.pc.entity.SysApiResource;
import com.ping.user.pc.entity.SysPermission;
import com.ping.user.pc.entity.SysRolePermission;
import com.ping.user.pc.entity.SysUserRole;
import com.ping.user.pc.mapper.SysApiResourceMapper;
import com.ping.user.pc.mapper.SysPermissionMapper;
import com.ping.user.pc.mapper.SysRolePermissionMapper;
import com.ping.user.pc.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PermissionServiceImpl - 权限管理（菜单/按钮定义）
 *
 * <p>权限定义变更后，主动失效受影响用户的 Sa-Token 会话缓存，
 * 保证鉴权数据与数据库一致（权限变更即时生效）。
 * 删除权限时会级联删除子权限及其挂接的接口资源。</p>
 *
 * @author humdeef
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {
    /** 权限数据访问 */
    private final SysPermissionMapper permissionMapper;
    /** 接口资源数据访问 */
    private final SysApiResourceMapper apiResourceMapper;
    /** 角色权限关系数据访问 */
    private final SysRolePermissionMapper rolePermissionMapper;
    /** 用户角色关系数据访问 */
    private final SysUserRoleMapper userRoleMapper;
    /** 权限缓存服务 */
    private final PermissionCacheService permissionCacheService;
    /** {@inheritDoc} */
    @Override
    public IPage<SysPermission> list(int page, int size, String name, String code) {
        QueryWrapper<SysPermission> wrapper = new QueryWrapper<>();
        if (name != null && !name.isBlank()) {
            wrapper.like("name", name);
        }
        if (code != null && !code.isBlank()) {
            wrapper.like("code", code);
        }
        wrapper.orderByAsc("sort", "id");
        return permissionMapper.selectPage(new Page<>((long) Math.max(page, 0) + 1, size), wrapper);
    }

    /**
     * 限定角色范围的分页查询（非管理员只能看到自己角色拥有的权限）。
     */
    @Override
    public IPage<SysPermission> list(Collection<Long> roleIds, int page, int size, String name, String code) {
        Page<SysPermission> p = new Page<>((long) Math.max(page, 0) + 1, size);
        Set<Long> permissionIds = permissionIdsOfRoles(roleIds);
        if (permissionIds.isEmpty()) {
            return p;
        }
        QueryWrapper<SysPermission> wrapper = new QueryWrapper<SysPermission>().in("id", permissionIds);
        if (name != null && !name.isBlank()) {
            wrapper.like("name", name);
        }
        if (code != null && !code.isBlank()) {
            wrapper.like("code", code);
        }
        wrapper.orderByAsc("sort", "id");
        return permissionMapper.selectPage(p, wrapper);
    }

    /**
     * 返回完整的权限列表（按 sort 排序，平铺结构，由前端组装树）
     */
    @Override
    public List<SysPermission> listAllTree() {
        return permissionMapper.findAllOrdered();
    }

    /**
     * 当前用户角色拥有的权限 ID 集合。
     */
    @Override
    public Set<Long> permissionIdsOfRoles(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> ids = rolePermissionMapper.findPermissionIdsByRoleIds(new ArrayList<>(roleIds));
        return ids == null ? Set.of() : ids;
    }

    /**
     * 当前用户可查看的权限树：自己角色拥有的权限，并补全其祖先节点（保证前端能组装成树）。
     */
    @Override
    public List<SysPermission> listTreeByRoleIds(Collection<Long> roleIds) {
        Set<Long> ownedIds = permissionIdsOfRoles(roleIds);
        if (ownedIds.isEmpty()) {
            return List.of();
        }
        List<SysPermission> all = permissionMapper.findAllOrdered();
        Map<Long, SysPermission> byId = all.stream()
                .collect(Collectors.toMap(SysPermission::getId, p -> p));

        Set<Long> visibleIds = new HashSet<>(ownedIds);
        for (Long ownedId : ownedIds) {
            SysPermission owned = byId.get(ownedId);
            Long parentId = owned == null ? null : owned.getParentId();
            while (parentId != null && parentId != 0 && visibleIds.add(parentId)) {
                SysPermission parent = byId.get(parentId);
                parentId = parent == null ? null : parent.getParentId();
            }
        }
        return all.stream().filter(p -> visibleIds.contains(p.getId())).collect(Collectors.toList());
    }

    /**
     * 当前用户可见的菜单树：自己角色拥有的目录/菜单，并补全祖先目录。
     */
    @Override
    public List<MenuNode> menuTree(Long userId, boolean admin) {
        List<SysPermission> all = permissionMapper.findAllOrdered();
        Map<Long, SysPermission> byId = all.stream()
                .collect(Collectors.toMap(SysPermission::getId, p -> p));

        Set<Long> visibleIds = new HashSet<>();
        if (admin) {
            all.forEach(p -> visibleIds.add(p.getId()));
        } else {
            Set<Long> owned = permissionIdsOfRoles(userRoleMapper.findRoleIdsByUserId(userId));
            for (Long ownedId : owned) {
                Long cursor = ownedId;
                // 补全祖先目录，保证前端能组装成完整树
                while (cursor != null && cursor != 0L && visibleIds.add(cursor)) {
                    SysPermission current = byId.get(cursor);
                    cursor = current == null ? null : current.getParentId();
                }
            }
        }

        List<MenuNode> nodes = all.stream()
                .filter(p -> visibleIds.contains(p.getId()))
                .filter(p -> p.getType() != null && (p.getType() == 1 || p.getType() == 2))
                .map(this::toMenuNode)
                .collect(Collectors.toList());

        Map<Long, MenuNode> nodeById = nodes.stream()
                .collect(Collectors.toMap(MenuNode::getId, n -> n));
        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode node : nodes) {
            MenuNode parent = node.getParentId() == null ? null : nodeById.get(node.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    private MenuNode toMenuNode(SysPermission permission) {
        MenuNode node = new MenuNode();
        node.setId(permission.getId());
        node.setParentId(permission.getParentId());
        node.setName(permission.getName());
        node.setCode(permission.getCode());
        node.setType(permission.getType());
        node.setPath(permission.getPath());
        node.setComponent(permission.getComponent());
        node.setIcon(permission.getIcon());
        node.setSort(permission.getSort());
        node.setVisible(permission.getVisible() == null ? 1 : permission.getVisible());
        return node;
    }

    /**
     * 按角色批量查询权限，返回 角色ID -> 权限列表（按 sort、id 排序）。
     */
    @Override
    public Map<Long, List<SysPermission>> permissionsByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Map.of();
        }
        List<SysRolePermission> relations = rolePermissionMapper.selectList(
                new QueryWrapper<SysRolePermission>().in("role_id", roleIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<Long> permissionIds = relations.stream()
                .map(SysRolePermission::getPermissionId)
                .collect(Collectors.toSet());
        Map<Long, SysPermission> permissionById = permissionMapper.selectBatchIds(permissionIds).stream()
                .collect(Collectors.toMap(SysPermission::getId, p -> p));

        Map<Long, List<SysPermission>> result = new HashMap<>(roleIds.size());
        for (SysRolePermission relation : relations) {
            SysPermission permission = permissionById.get(relation.getPermissionId());
            if (permission != null) {
                result.computeIfAbsent(relation.getRoleId(), k -> new ArrayList<>()).add(permission);
            }
        }
        result.values().forEach(list -> list.sort(PERMISSION_ORDER));
        return result;
    }

    /** 多个角色的权限去重合并（按 sort、id 排序）。 */
    @Override
    public List<SysPermission> permissionsOfRoles(Collection<Long> roleIds) {
        Map<Long, SysPermission> merged = new LinkedHashMap<>();
        permissionsByRoleIds(roleIds).values().stream()
                .flatMap(List::stream)
                .sorted(PERMISSION_ORDER)
                .forEach(p -> merged.putIfAbsent(p.getId(), p));
        return new ArrayList<>(merged.values());
    }

    /** 权限类型：菜单。 */
    private static final int TYPE_MENU = 2;

    /** 路由地址分隔符。 */
    private static final String PATH_SEPARATOR = "/";

    private static final Comparator<SysPermission> PERMISSION_ORDER =
            Comparator.comparing((SysPermission p) -> p.getSort() == null ? Integer.MAX_VALUE : p.getSort())
                    .thenComparing(SysPermission::getId);
    /** {@inheritDoc} 权限编码创建后不可修改；变更后踢出持有该权限的用户。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysPermission save(PermissionRequest req) {
        SysPermission perm;
        if (req.getId() != null) {
            perm = permissionMapper.selectById(req.getId());
            if (perm == null) {
                throw new RuntimeException("权限不存在");
            }
            // 权限编码创建后不可修改（接口资源通过 code 关联）
            if (!perm.getCode().equals(req.getCode())) {
                throw new RuntimeException("权限编码不可修改");
            }
        } else {
            Long count = permissionMapper.selectCount(new QueryWrapper<SysPermission>().eq("code", req.getCode()));
            if (count > 0) {
                throw new RuntimeException("权限编码已存在");
            }
            perm = new SysPermission();
            perm.setCode(req.getCode());
        }
        // 权限编码一经创建不可修改（接口资源通过 code 关联）
        boolean button = Objects.equals(req.getType(), 3);
        if (Objects.equals(req.getType(), TYPE_MENU)) {
            if (req.getPath() == null || req.getPath().isBlank()) {
                throw new RuntimeException("菜单类型必须填写路由地址");
            }
            if (!req.getPath().startsWith(PATH_SEPARATOR)) {
                throw new RuntimeException("路由地址必须以 / 开头");
            }
            if (req.getComponent() == null || req.getComponent().isBlank()) {
                throw new RuntimeException("菜单类型必须填写组件名");
            }
        }
        perm.setName(req.getName());
        perm.setType(req.getType());
        perm.setParentId(req.getParentId());
        perm.setPath(button ? "" : trimToEmpty(req.getPath()));
        perm.setComponent(button ? "" : trimToEmpty(req.getComponent()));
        perm.setSort(req.getSort());
        perm.setIcon(req.getIcon());
        perm.setVisible(req.getVisible() == null ? 1 : req.getVisible());
        if (req.getId() != null) {
            permissionMapper.updateById(perm);
        } else {
            try {
                permissionMapper.insert(perm);
            } catch (DuplicateKeyException e) {
                throw new RuntimeException("权限编码已存在");
            }
        }
        // 权限定义变更：失效所有持有该权限（通过任意角色）的用户会话
        permissionCacheService.invalidateCache(userIdsOfPermission(perm.getId()));
        AuditContext.setDetail((req.getId() == null ? "新增权限 code=" : "编辑权限 code=") + perm.getCode());
        return perm;
    }

    /**
     * 删除权限：级联删除所有子权限、角色绑定，以及这些权限挂接的接口资源。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysPermission perm = permissionMapper.selectById(id);
        if (perm == null) {
            throw new RuntimeException("权限不存在");
        }

        List<SysPermission> all = permissionMapper.findAllOrdered();
        List<Long> ids = new ArrayList<>();
        collectDescendants(all, id, ids);

        List<String> codes = all.stream()
                .filter(p -> ids.contains(p.getId()))
                .map(SysPermission::getCode)
                .collect(Collectors.toList());

        // 踢出受影响用户（合并去重）
        List<Long> affectedUsers = new ArrayList<>();
        for (Long pid : ids) {
            affectedUsers.addAll(userIdsOfPermission(pid));
        }
        permissionCacheService.invalidateCache(
                affectedUsers.stream().distinct().collect(Collectors.toList()));

        // 先删关联与接口资源，再删权限本身
        rolePermissionMapper.delete(new QueryWrapper<SysRolePermission>().in("permission_id", ids));
        if (!codes.isEmpty()) {
            apiResourceMapper.delete(new QueryWrapper<SysApiResource>().in("permission_code", codes));
        }
        permissionMapper.deleteBatchIds(ids);
        AuditContext.setDetail("删除权限 code=" + perm.getCode());
        log.info("[Permission] 已删除权限及其子权限 {} 个: {}，关联接口资源 {} 个",
                ids.size(), codes, codes.size());
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /** 递归收集自身及所有子权限 ID */
    private void collectDescendants(List<SysPermission> all, Long id, List<Long> out) {
        if (out.contains(id)) {
            return;
        }
        out.add(id);
        for (SysPermission p : all) {
            if (id.equals(p.getParentId())) {
                collectDescendants(all, p.getId(), out);
            }
        }
    }

    /**
     * 找出所有持有该权限（通过任意角色）的用户。
     */
    private List<Long> userIdsOfPermission(Long permissionId) {
        List<Long> roleIds = rolePermissionMapper.selectList(
                new QueryWrapper<SysRolePermission>().eq("permission_id", permissionId))
                .stream().map(SysRolePermission::getRoleId).distinct().collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return userRoleMapper.selectList(
                new QueryWrapper<SysUserRole>().in("role_id", roleIds))
                .stream().map(SysUserRole::getUserId).distinct().collect(Collectors.toList());
    }
}
