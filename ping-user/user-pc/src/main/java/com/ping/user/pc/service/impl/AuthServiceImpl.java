package com.ping.user.pc.service.impl;

import com.ping.user.pc.service.AuthService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ping.user.auth.config.PingSecurityProperties;
import com.ping.user.pc.entity.SysRole;
import com.ping.user.pc.entity.SysUser;
import com.ping.user.pc.entity.SysUserRole;
import com.ping.user.pc.mapper.SysRoleMapper;
import com.ping.user.pc.mapper.SysUserMapper;
import com.ping.user.pc.mapper.SysUserRoleMapper;
import com.ping.user.auth.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AuthServiceImpl - 认证服务实现。
 *
 * @author humdeef
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    /** 用户数据访问 */
    private final SysUserMapper userMapper;
    /** 用户角色关系数据访问 */
    private final SysUserRoleMapper userRoleMapper;
    /** 角色数据访问 */
    private final SysRoleMapper roleMapper;
    /** 密码编码器 */
    private final PasswordEncoder passwordEncoder;
    /** 密码策略 */
    private final PasswordPolicy passwordPolicy;
    /** 登录安全配置 */
    private final PingSecurityProperties securityProperties;
    /** {@inheritDoc} 注册成功绑定默认角色，默认角色不存在时不绑定。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser register(String username, String password, String nickname, String email, String phone) {
        if (!securityProperties.isRegistrationEnabled()) {
            throw new AccessDeniedException("系统未开放自助注册");
        }
        username = username == null ? null : username.trim();
        passwordPolicy.validate(password);
        Long count = userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", username));
        if (count > 0) {
            throw new RuntimeException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setEmail(email);
        user.setPhone(phone);
        user.setStatus(1);
        user.setForceChangePassword(0);
        user.setPasswordUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        // 绑定默认角色，避免注册后登录「什么都看不到」
        SysRole defaultRole = roleMapper.selectOne(new QueryWrapper<SysRole>()
                .eq("code", securityProperties.getDefaultRoleCode()).last("LIMIT 1"));
        if (defaultRole != null) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(user.getId());
            relation.setRoleId(defaultRole.getId());
            userRoleMapper.insert(relation);
        }
        return user;
    }

    /** 查询用户，不存在返回 null（登录场景使用，避免把认证失败变成 500） */
    @Override
    public SysUser findByUsernameOrNull(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username));
    }
    /** {@inheritDoc} */
    @Override
    public SysUser findById(Long id) {
        return userMapper.selectById(id);
    }

    /**
     * 当前用户修改密码：校验旧密码 + 新密码策略，成功后清除强制改密标记。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new RuntimeException("新密码不能与原密码相同");
        }
        passwordPolicy.validate(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setForceChangePassword(0);
        user.setPasswordUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /**
     * 管理员重置密码：设置强制改密标记。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        passwordPolicy.validate(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setForceChangePassword(1);
        user.setPasswordUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }
}
