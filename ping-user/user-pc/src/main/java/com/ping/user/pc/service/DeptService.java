package com.ping.user.pc.service;

import com.ping.user.pc.entity.SysDept;
import com.ping.user.pc.model.DeptRequest;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 部门管理。
 *
 * @author humdeef
 */
public interface DeptService {

    /**
     * 查询当前用户可见的部门，并补全祖先节点。
     *
     * @param userId 用户 ID
     * @return 列表
     */
    List<SysDept> listVisible(Long userId);

    /**
     * 按主键查询部门。
     *
     * @param id 主键 ID
     * @return 部门信息
     */
    SysDept get(Long id);

    /**
     * 新增或编辑部门，自动维护 ancestors 并校验层级成环。
     *
     * @param req 请求参数
     * @return 部门信息
     */
    SysDept save(DeptRequest req);

    /**
     * 删除部门；存在子部门或已分配用户时拒绝。
     *
     * @param id 主键 ID
     */
    void delete(Long id);

    /**
     * 批量查询部门 ID 到名称的映射。
     *
     * @param deptIds 部门 ID 集合
     * @return 映射结果
     */
    Map<Long, String> nameMap(Collection<Long> deptIds);
}
