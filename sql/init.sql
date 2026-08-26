-- ============================================================
-- MyBatis 示例项目 - 数据库初始化脚本
-- 数据库名: mybatis
-- 说明: 包含 user、id_card、role、user_role 四张表的完整建表语句
-- ============================================================

-- 如果数据库不存在则创建
CREATE DATABASE IF NOT EXISTS `mybatis` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `mybatis`;

-- ============================================================
-- 1. 用户表 (user)
-- ============================================================
DROP TABLE IF EXISTS `user_role`;
DROP TABLE IF EXISTS `role`;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `id_card`;

CREATE TABLE `user` (
    `id`   INT          NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `name` VARCHAR(50)  NOT NULL                COMMENT '用户名',
    `pwd`  VARCHAR(100) NOT NULL                COMMENT '密码',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 2. 身份证表 (id_card)
-- ============================================================
CREATE TABLE `id_card` (
    `id`      INT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `card_no` VARCHAR(18)  NOT NULL UNIQUE         COMMENT '身份证号',
    `user_id` INT          NOT NULL                COMMENT '用户ID',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    CONSTRAINT `fk_id_card_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='身份证表';

-- ============================================================
-- 3. 角色表 (role)  —— 新增表
-- ============================================================
CREATE TABLE `role` (
    `id`          INT          NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_name`   VARCHAR(50)  NOT NULL UNIQUE         COMMENT '角色名称',
    `description` VARCHAR(200) DEFAULT NULL            COMMENT '角色描述',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ============================================================
-- 4. 用户角色关联表 (user_role)  —— 新增表
--    一个用户可以拥有多个角色，一个角色可以分配给多个用户（多对多）
-- ============================================================
CREATE TABLE `user_role` (
    `id`          INT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     INT      NOT NULL                COMMENT '用户ID',
    `role_id`     INT      NOT NULL                COMMENT '角色ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`),
    CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ============================================================
-- 5. 插入测试数据
-- ============================================================

-- 用户数据
INSERT INTO `user` (`name`, `pwd`) VALUES
('张三', '123456'),
('李四', 'abcdef'),
('王五', '789012');

-- 身份证数据
INSERT INTO `id_card` (`card_no`, `user_id`) VALUES
('110101199001011234', 1),
('110101199002022345', 2),
('110101199003033456', 3);

-- 角色数据
INSERT INTO `role` (`role_name`, `description`) VALUES
('ADMIN',  '系统管理员，拥有全部权限'),
('USER',   '普通用户，拥有基本操作权限'),
('EDITOR', '编辑者，拥有内容编辑权限');

-- 用户角色关联数据（张三=ADMIN+USER，李四=USER，王五=EDITOR）
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES
(1, 1),
(1, 2),
(2, 2),
(3, 3);
