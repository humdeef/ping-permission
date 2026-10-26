package com.ping.user.pc.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ping.user.pc.entity.SysRole;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.model.UserRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户管理。
 *
 * @author humdeef
 */
public interface UserService {

    /**
     * 按数据范围和部门分页查询用户；部门条件与数据范围取交集。
     *
     * <p>数据范围由 {@code @DataScope} + MyBatis 拦截器自动注入，
     * 业务层只处理显式传入的部门筛选。</p>
     *
     * @param page 页码，从 0 开始
     * @param size 每页条数
     * @param username 用户名，可为空
     * @param nickname 昵称，可为空
     * @param deptId 部门 ID
     * @param includeChildren 是否包含下级部门
     * @return 分页结果
     */
    IPage<SysUser> listScoped(int page, int size,
                              String username, String nickname, Long deptId, boolean includeChildren);

    /**
     * 按主键查询用户。
     *
     * @param id 主键 ID
     * @return 用户信息
     */
    SysUser get(Long id);

    /**
     * 新增或编辑用户，同时维护角色、部门、密码和强制改密标记。
     *
     * @param req 请求参数
     * @return 用户信息
     */
    SysUser save(UserRequest req);

    /**
     * 删除用户并清理角色关联；内置管理员不可删除。
     *
     * @param id 主键 ID
     */
    void delete(Long id);

    /**
     * 管理员重置用户密码，并踢出该用户会话。
     *
     * @param id 主键 ID
     * @param newPassword 新密码
     */
    void resetPassword(Long id, String newPassword);

    /**
     * 查询全部角色。
     * @return 列表
     */
    List<SysRole> listAllRoles();

    /**
     * 查询单个用户的角色 ID。
     *
     * @param userId 用户 ID
     * @return 集合
     */
    Set<Long> getRoleIds(Long userId);

    /**
     * 批量查询多个用户的角色 ID。
     *
     * @param userIds 用户 ID 集合
     * @return 映射结果
     */
    Map<Long, Set<Long>> getRoleIds(List<Long> userIds);
}
