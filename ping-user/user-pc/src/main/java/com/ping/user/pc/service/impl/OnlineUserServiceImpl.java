package com.ping.user.pc.service.impl;

import com.ping.user.common.constant.UserConstants;

import com.ping.user.pc.service.OnlineUserService;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.mapper.SysUserMapper;
import com.ping.user.pc.model.OnlineUserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OnlineUserServiceImpl - 在线会话查询与强制下线。
 *
 * <p>直接读取 Sa-Token 在 Redis 中的 token session，与网关/各服务共享同一份登录态。</p>
 *
 * @author humdeef
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUserServiceImpl implements OnlineUserService {

    /** 用户数据访问 */
    private final SysUserMapper userMapper;
    /** {@inheritDoc} 直接读取 Sa-Token token session，并兼容带前缀的 token。 */
    @Override
    public List<OnlineUserDTO> list(String username) {
        // searchTokenValue 返回的是 token 值（可能带 satoken:login:token: 前缀）
        List<String> tokens = StpUtil.searchTokenValue("", 0, -1, true);
        if (tokens == null || tokens.isEmpty()) {
            log.info("[Online] 未搜索到任何 token");
            return List.of();
        }
        List<OnlineUserDTO> result = new ArrayList<>();
        int skipped = 0;
        for (String rawToken : tokens) {
            String token = normalizeToken(rawToken);
            try {
                Object loginId = StpUtil.getLoginIdByToken(token);
                if (loginId == null) {
                    skipped++;
                    continue;
                }
                SaSession session = StpUtil.getTokenSessionByToken(token);
                if (session == null) {
                    skipped++;
                    continue;
                }
                OnlineUserDTO dto = new OnlineUserDTO();
                dto.setToken(token);
                dto.setUserId(Long.valueOf(String.valueOf(loginId)));
                dto.setIp(stringValue(session.get(UserConstants.SESSION_IP)));
                dto.setUserAgent(stringValue(session.get(UserConstants.SESSION_USER_AGENT)));
                dto.setLoginTime(toLocalDateTime(session.getCreateTime()));
                dto.setTimeoutSeconds(Math.max(session.timeout(), 0));
                result.add(dto);
            } catch (Exception e) {
                skipped++;
                log.debug("[Online] 解析 token 失败: {}", e.getMessage());
            }
        }
        fillUserInfo(result);
        result.removeIf(dto -> username != null && !username.isBlank()
                && (dto.getUsername() == null || !dto.getUsername().contains(username)));
        result.sort(Comparator.comparing(OnlineUserDTO::getLoginTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        log.info("[Online] tokens={}, listed={}, skipped={}", tokens.size(), result.size(), skipped);
        return result;
    }
    /** {@inheritDoc} */
    @Override
    public void kickout(String token) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("token 不能为空");
        }
        StpUtil.kickoutByTokenValue(token);
    }

    /**
     * Sa-Token 搜索接口可能返回完整 Redis key，这里统一取最后一段作为 token。
     * 正常 token 是 UUID，不含冒号，方法对两种形式都兼容。
     */
    private String normalizeToken(String token) {
        if (token == null) {
            return null;
        }
        int idx = token.lastIndexOf(':');
        return idx >= 0 ? token.substring(idx + 1) : token;
    }

    private void fillUserInfo(List<OnlineUserDTO> list) {
        Set<Long> userIds = list.stream().map(OnlineUserDTO::getUserId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, SysUser> userById = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
        for (OnlineUserDTO dto : list) {
            SysUser user = userById.get(dto.getUserId());
            if (user != null) {
                dto.setUsername(user.getUsername());
                dto.setNickname(user.getNickname());
            }
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        if (epochMillis <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }
}
