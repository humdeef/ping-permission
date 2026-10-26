package com.ping.user.pc.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ping.data.scope.annotation.DataScope;
import com.ping.user.pc.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SysUserMapper - 后台用户 Mapper。
 *
 * @author humdeef
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按数据范围分页查询用户。
     *
     * <p>数据权限条件由 DataPermissionInterceptor 自动注入，业务代码不再手写
     * {@code dept_id in (...) or id = ?}。</p>
     *
     * @param page    分页参数
     * @param wrapper 查询条件
     * @return 用户分页结果
     */
    @DataScope(table = "sys_user", deptColumn = "dept_id", ownerColumn = "id")
    IPage<SysUser> selectScopedPage(Page<SysUser> page,
                                    @Param(Constants.WRAPPER) Wrapper<SysUser> wrapper);

    /*
     * 写方法显式重声明并标注 @DataScope。
     *
     * 这里只覆盖「按主键/条件」的四个写方法，而不是给整个接口加注解：
     * 登录、权限缓存、数据范围解析等内部查询需要能读到任意用户，
     * 类型级注解会把它们一并过滤掉。重声明必须保留 BaseMapper 的 @Param，
     * 否则 updateById 的 #{et.xxx}、update 的 #{ew.xxx} 会绑定失败。
     */

    /**
     * 按主键更新用户；数据范围之外的更新影响 0 行。
     *
     * @param entity 用户实体
     * @return 影响行数
     */
    @Override
    @DataScope(table = "sys_user", deptColumn = "dept_id", ownerColumn = "id")
    int updateById(@Param(Constants.ENTITY) SysUser entity);

    /**
     * 按条件更新用户；数据范围之外的更新影响 0 行。
     *
     * @param entity        用户实体
     * @param updateWrapper 更新条件
     * @return 影响行数
     */
    @Override
    @DataScope(table = "sys_user", deptColumn = "dept_id", ownerColumn = "id")
    int update(@Param(Constants.ENTITY) SysUser entity,
               @Param(Constants.WRAPPER) Wrapper<SysUser> updateWrapper);

    /**
     * 按主键删除用户；数据范围之外的删除影响 0 行。
     *
     * @param entity 用户实体
     * @return 影响行数
     */
    @Override
    @DataScope(table = "sys_user", deptColumn = "dept_id", ownerColumn = "id")
    int deleteById(SysUser entity);

    /**
     * 按条件删除用户；数据范围之外的删除影响 0 行。
     *
     * @param queryWrapper 删除条件
     * @return 影响行数
     */
    @Override
    @DataScope(table = "sys_user", deptColumn = "dept_id", ownerColumn = "id")
    int delete(@Param(Constants.WRAPPER) Wrapper<SysUser> queryWrapper);
}
