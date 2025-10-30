-- ----------------------------
-- 脚本说明：创建 high_like 数据库及其相关表 (高兼容性幂等版本)
-- 作者：资深软件工程师
-- 特性：幂等性，可安全重复执行
-- 兼容性：兼容所有支持存储过程的MySQL版本 (5.0+)
-- ----------------------------

-- 1. 创建数据库（如果不存在）
CREATE SCHEMA IF NOT EXISTS `high_like` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2. 使用创建的数据库
USE `high_like`;

-- ----------------------------
-- 0. 辅助步骤：创建一个用于安全删除索引的存储过程
-- ----------------------------
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS `safely_drop_index` (
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64)
)
BEGIN
    DECLARE index_count INT;
    -- 查询information_schema来判断索引是否存在
    SELECT COUNT(*) INTO index_count
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name AND INDEX_NAME = p_index_name;

    -- 如果索引存在 (count > 0)，则执行删除
    IF index_count > 0 THEN
        SET @sql = CONCAT('DROP INDEX `', p_index_name, '` ON `', p_table_name, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ----------------------------
-- 3. 创建用户表 (user)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `user` (
                                      `id`       bigint auto_increment primary key comment '用户ID',
                                      `username` varchar(128) not null comment '用户名'
) comment '用户表';

-- ----------------------------
-- 4. 创建博客表 (blog)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `blog` (
                                      `id`         bigint auto_increment primary key comment '博客ID',
                                      `userId`     bigint                             not null comment '用户ID',
                                      `title`      varchar(512)                       null comment '标题',
                                      `coverImg`   varchar(1024)                      null comment '封面图片URL',
                                      `content`    text                               not null comment '博客内容',
                                      `thumbCount` int      default 0                 not null comment '点赞数',
                                      `createTime` datetime default CURRENT_TIMESTAMP not null comment '创建时间',
                                      `updateTime` datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
) comment '博客表';

-- 【修正点】调用存储过程，安全地创建用户ID索引
CALL `safely_drop_index`('blog', 'idx_blog_userId');
CREATE INDEX `idx_blog_userId` ON `blog` (`userId`);

-- ----------------------------
-- 5. 创建点赞记录表 (thumb)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `thumb` (
                                       `id`         bigint auto_increment primary key comment '点赞记录ID',
                                       `userId`     bigint                             not null comment '点赞用户ID',
                                       `blogId`     bigint                             not null comment '被点赞的博客ID',
                                       `createTime` datetime default CURRENT_TIMESTAMP not null comment '点赞时间'
) comment '点赞记录表';

-- 【修正点】调用存储过程，安全地创建唯一联合索引
CALL `safely_drop_index`('thumb', 'idx_thumb_userId_blogId');
CREATE UNIQUE INDEX `idx_thumb_userId_blogId` ON `thumb` (`userId`, `blogId`);

-- ----------------------------
-- (可选) 清理：删除辅助存储过程
-- 在确保所有表和索引都创建无误后，可以删除这个辅助存储过程
-- DROP PROCEDURE IF EXISTS `safely_drop_index`;
-- ----------------------------

-- ----------------------------
-- 脚本执行完毕
-- ----------------------------
