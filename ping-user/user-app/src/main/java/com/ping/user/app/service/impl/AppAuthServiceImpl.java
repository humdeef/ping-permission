package com.ping.user.app.service.impl;

import com.ping.user.common.constant.UserConstants;

import com.ping.user.app.service.AppAuthService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ping.core.id.DistributedIdGenerator;
import com.ping.user.app.entity.AppUser;
import com.ping.user.app.entity.AppUserIdentity;
import com.ping.user.app.mapper.AppUserIdentityMapper;
import com.ping.user.app.mapper.AppUserMapper;
import com.ping.user.auth.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AppAuthServiceImpl - C 端注册/登录。
 *
 * @author humdeef
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppAuthServiceImpl implements AppAuthService {
    /** App 用户数据访问 */
    private final AppUserMapper appUserMapper;
    /** App 用户身份数据访问 */
    private final AppUserIdentityMapper identityMapper;
    /** 密码编码器 */
    private final PasswordEncoder passwordEncoder;
    /** 密码策略 */
    private final PasswordPolicy passwordPolicy;
    /** 分布式 ID 生成器 */
    private final DistributedIdGenerator idGenerator;
    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser register(String phone, String password, String nickname) {
        String normalizedPhone = phone == null ? null : phone.trim();
        passwordPolicy.validate(password);
        Long count = appUserMapper.selectCount(
                new QueryWrapper<AppUser>().eq("phone", normalizedPhone));
        if (count != null && count > 0) {
            throw new RuntimeException("手机号已注册");
        }

        AppUser user = new AppUser();
        user.setId(Long.valueOf(idGenerator.nextId()));
        user.setPhone(normalizedPhone);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(nickname == null || nickname.isBlank() ? normalizedPhone : nickname);
        user.setStatus(UserConstants.APP_USER_STATUS_NORMAL);
        user.setRegisterSource(UserConstants.PROVIDER_PHONE);
        appUserMapper.insert(user);

        AppUserIdentity identity = new AppUserIdentity();
        identity.setId(Long.valueOf(idGenerator.nextId()));
        identity.setUserId(user.getId());
        identity.setProvider(UserConstants.PROVIDER_PHONE);
        identity.setIdentifier(normalizedPhone);
        identity.setCredential("");
        identityMapper.insert(identity);
        return user;
    }
    /** {@inheritDoc} */
    @Override
    public AppUser login(String phone, String password) {
        AppUser user = findByPhone(phone);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("手机号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == UserConstants.APP_USER_STATUS_DISABLED) {
            throw new AccessDeniedException("账号已被禁用");
        }
        if (user.getStatus() != null && user.getStatus() == UserConstants.APP_USER_STATUS_CANCELLING) {
            throw new AccessDeniedException("账号正在注销中");
        }
        user.setLastLoginAt(LocalDateTime.now());
        appUserMapper.updateById(user);
        return user;
    }
    /** {@inheritDoc} */
    @Override
    public AppUser findByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return appUserMapper.selectOne(
                new QueryWrapper<AppUser>().eq("phone", phone.trim()).last("LIMIT 1"));
    }
    /** {@inheritDoc} */
    @Override
    public AppUser findById(Long id) {
        return appUserMapper.selectById(id);
    }
}
