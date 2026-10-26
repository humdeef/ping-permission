package com.ping.user.pc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ping.user.pc.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * SysDeptMapper - 部门 Mapper。
 *
 * @author humdeef
 */
@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /**
     * 查询全部部门，按排序值升序。
     *
     * @return 部门列表
     */
    @Select("SELECT * FROM sys_dept ORDER BY sort ASC, id ASC")
    List<SysDept> findAllOrdered();
}
