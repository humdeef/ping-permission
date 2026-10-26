-- 种子账号 lisa 的密码统一为 123456，与 admin / tom / jerry 一致。
--
-- V1 已发布、按约定只读，因此用新的版本文件修正数据。
-- WHERE 带上 V1 里的原始哈希：只有密码仍是初始值时才会重置，
-- 避免把使用者已经改过的密码覆盖掉。
UPDATE `sys_user`
SET `password` = '$2a$10$fCVbO/D4RljKW1n4SonrAuzJ6FyKEwxS96b6dRvhrfS2oXwFedKxu',
    `password_updated_at` = NOW()
WHERE `username` = 'lisa'
  AND `password` = '$2a$10$l5eYlhqLHRan2cfXGCh.FODRO5ZefY/DVHmlXhvr/DvKU13A4p8S.';
