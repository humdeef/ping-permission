package com.ping.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ping.order.entity.BizOrder;

/**
 * 订单 Mapper。
 *
 * <p>订单是纯 App 用户数据：App 端按 {@code user_id} 过滤，后台按
 * {@code system:order:*} 接口权限控制，不再套用部门/归属人行级数据权限。
 * 需要部门维度的后台业务表请参考 {@code SysUserMapper}。</p>
 *
 * @author humdeef
 */
public interface BizOrderMapper extends BaseMapper<BizOrder> {
}
