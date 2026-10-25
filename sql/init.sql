-- ============================================================
-- 物流运输调度仿真系统 · 数据库初始化脚本
-- 说明：driver/customer/goods 等其余表由后端启动时自动创建，
--       只有下面 5 张核心表必须手工执行本脚本创建。
-- 使用：mysql -uroot -p < sql/init.sql   或在 Workbench 中执行。
-- ============================================================

CREATE DATABASE IF NOT EXISTS zongshe DEFAULT CHARACTER SET utf8mb4;
USE zongshe;

CREATE TABLE IF NOT EXISTS truck (
  id INT AUTO_INCREMENT PRIMARY KEY,
  max_weight DECIMAL(10,2), max_vol DECIMAL(10,2),
  status VARCHAR(50), type VARCHAR(20),
  location VARCHAR(100), task_id INT, current_point_sequence INT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS task (
  id INT AUTO_INCREMENT PRIMARY KEY,
  start VARCHAR(100), destination VARCHAR(100),
  goods_type VARCHAR(50), truck_id INT,
  status VARCHAR(20) DEFAULT '待分配',
  sender_id INT, receiver_id INT,
  sender_name VARCHAR(100), receiver_name VARCHAR(100),
  goods_id INT,
  weight DECIMAL(10,2), volume DECIMAL(10,2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS point (
  id INT AUTO_INCREMENT PRIMARY KEY,
  location VARCHAR(100), name VARCHAR(100), type VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 代码中同时使用 point 与 poi 两张同构表（爬虫与按名查询使用 poi）
CREATE TABLE IF NOT EXISTS poi LIKE point;

CREATE TABLE IF NOT EXISTS task_route_point (
  id INT AUTO_INCREMENT PRIMARY KEY,
  task_id INT, point_location VARCHAR(100),
  point_name VARCHAR(100), sequence INT,
  INDEX idx_trp_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
