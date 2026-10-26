package com.ping.user.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ping.user.app.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * AppUserMapper - App 用户 Mapper。
 *
 * @author humdeef
 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
}
