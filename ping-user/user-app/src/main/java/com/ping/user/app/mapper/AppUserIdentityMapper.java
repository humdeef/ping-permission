package com.ping.user.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ping.user.app.entity.AppUserIdentity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AppUserIdentityMapper - App 用户身份 Mapper。
 *
 * @author humdeef
 */
@Mapper
public interface AppUserIdentityMapper extends BaseMapper<AppUserIdentity> {
}
