package com.muite.zongshe1.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始检查并初始化数据库表结构...");

        try {
            // 1. 创建 driver 表
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS driver (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(50)," +
                    "phone VARCHAR(20)," +
                    "license_type VARCHAR(10)," +
                    "status VARCHAR(20)," +
                    "truck_id INT" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            
            // 2. 创建 customer 表
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS customer (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(100)," +
                    "address VARCHAR(200)," +
                    "contact_person VARCHAR(50)," +
                    "phone VARCHAR(20)," +
                    "type VARCHAR(20)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

            // 3. 创建 maintenance_record 表
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS maintenance_record (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "truck_id INT," +
                    "start_date DATETIME," +
                    "end_date DATETIME," +
                    "type VARCHAR(20)," +
                    "description TEXT," +
                    "cost DECIMAL(10, 2)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

            // 4. 创建 alert_log 表
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS alert_log (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "truck_id INT," +
                    "driver_id INT," +
                    "task_id INT," +
                    "type VARCHAR(20)," +
                    "level VARCHAR(10)," +
                    "location VARCHAR(50)," +
                    "create_time DATETIME," +
                    "is_handled BOOLEAN" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            
            // 5. 创建或修改 goods 表
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS goods (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY," +
                        "name VARCHAR(100) DEFAULT NULL," +
                        "type VARCHAR(50) DEFAULT NULL," +
                        "weight DECIMAL(10,2) DEFAULT NULL," +
                        "volume DECIMAL(10,2) DEFAULT NULL," +
                        "value DECIMAL(10,2) DEFAULT NULL," +
                        "status VARCHAR(20) DEFAULT '待分配'," +
                        "priority VARCHAR(20) DEFAULT NULL," +
                        "sender_id INT DEFAULT NULL," +
                        "receiver_id INT DEFAULT NULL," +
                        "origin_location VARCHAR(100) DEFAULT NULL," +
                        "dest_location VARCHAR(100) DEFAULT NULL," +
                        "distance DECIMAL(10,2) DEFAULT NULL," +
                        "task_id INT DEFAULT NULL," +
                        "create_time DATETIME DEFAULT NULL," +
                        "assign_time DATETIME DEFAULT NULL," +
                        "load_time DATETIME DEFAULT NULL," +
                        "deliver_time DATETIME DEFAULT NULL," +
                        "complete_time DATETIME DEFAULT NULL," +
                        "location VARCHAR(100) DEFAULT NULL" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            } catch (Exception e) { }
            
            // 添加缺失的列（如果表已存在）
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN name VARCHAR(100) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN type VARCHAR(50) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN weight DECIMAL(10,2) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN volume DECIMAL(10,2) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN value DECIMAL(10,2) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN status VARCHAR(20) DEFAULT '待分配'");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN priority VARCHAR(20) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN sender_id INT DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN receiver_id INT DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN origin_location VARCHAR(100) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN dest_location VARCHAR(100) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN distance DECIMAL(10,2) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN task_id INT DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN create_time DATETIME DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN assign_time DATETIME DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN load_time DATETIME DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN deliver_time DATETIME DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN complete_time DATETIME DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods ADD COLUMN location VARCHAR(100) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN location VARCHAR(100) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN name VARCHAR(100) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods CHANGE COLUMN goods_type type VARCHAR(50) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN type VARCHAR(50) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN weight DECIMAL(10,2) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN volume DECIMAL(10,2) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN value DECIMAL(10,2) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN status VARCHAR(20) DEFAULT '待分配'");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN priority VARCHAR(20) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN sender_id INT DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN receiver_id INT DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN origin_location VARCHAR(100) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN dest_location VARCHAR(100) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN distance DECIMAL(10,2) DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN task_id INT DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN create_time DATETIME DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN assign_time DATETIME DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN load_time DATETIME DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN deliver_time DATETIME DEFAULT NULL");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE goods MODIFY COLUMN complete_time DATETIME DEFAULT NULL");
            } catch (Exception e) { }
            log.info("已检查并更新 goods 表结构");
            
            // 6. 创建 goods_demand 表（货物需求记录）
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS goods_demand (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "goods_id INT," +
                    "demand_type VARCHAR(20)," +
                    "demand_time DATETIME," +
                    "region VARCHAR(50)," +
                    "urgency VARCHAR(20)," +
                    "status VARCHAR(20)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            
            // 7. 创建 goods_transport 表（货物运输记录）
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS goods_transport (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "goods_id INT," +
                    "task_id INT," +
                    "truck_id INT," +
                    "transport_type VARCHAR(20)," +
                    "start_time DATETIME," +
                    "end_time DATETIME," +
                    "actual_distance DECIMAL(10,2)," +
                    "transport_region VARCHAR(50)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            
            // 8. 创建 goods_waiting 表（货物等待记录）
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS goods_waiting (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "goods_id INT," +
                    "waiting_type VARCHAR(20)," +
                    "start_time DATETIME," +
                    "end_time DATETIME," +
                    "waiting_reason VARCHAR(100)," +
                    "waiting_location VARCHAR(100)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            
            // 9. 创建 goods_loss 表（货物损耗记录）
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS goods_loss (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "goods_id INT," +
                    "loss_type VARCHAR(20)," +
                    "loss_weight DECIMAL(10,2)," +
                    "loss_value DECIMAL(10,2)," +
                    "loss_time DATETIME," +
                    "factory_id INT" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            
            // 10. 创建索引优化查询性能
            try {
                jdbcTemplate.execute("CREATE INDEX idx_goods_create_time ON goods(create_time)");
                jdbcTemplate.execute("CREATE INDEX idx_goods_type ON goods(type)");
                jdbcTemplate.execute("CREATE INDEX idx_goods_status ON goods(status)");
                jdbcTemplate.execute("CREATE INDEX idx_goods_sender ON goods(sender_id)");
                jdbcTemplate.execute("CREATE INDEX idx_goods_receiver ON goods(receiver_id)");
                log.info("已创建货物表索引");
            } catch (Exception e) {
                // 忽略已存在索引的错误
            }
            
            // 尝试添加新列（如果表已存在）
            try {
                jdbcTemplate.execute("ALTER TABLE task ADD COLUMN sender_id INT");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE task ADD COLUMN receiver_id INT");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE task ADD COLUMN sender_name VARCHAR(100)");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE task ADD COLUMN receiver_name VARCHAR(100)");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE task ADD COLUMN goods_id INT");
                log.info("已添加 task.goods_id 列");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE task ADD COLUMN weight DECIMAL(10,2)");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE task ADD COLUMN volume DECIMAL(10,2)");
            } catch (Exception e) { }
            try {
                jdbcTemplate.execute("ALTER TABLE task ADD COLUMN status VARCHAR(20)");
            } catch (Exception e) { }
            log.info("已检查 task 表结构");
            try {
                jdbcTemplate.execute("ALTER TABLE alert_log ADD COLUMN driver_id INT");
            } catch (Exception e) {
                // 忽略
            }
            
            // 扩展 truck 表 status 列长度以支持中文状态
            try {
                jdbcTemplate.execute("ALTER TABLE truck MODIFY COLUMN status VARCHAR(50)");
                log.info("已扩展 truck.status 列长度");
            } catch (Exception e) {
                // 忽略
            }
            
            // 添加 current_point_sequence 字段用于保存车辆进度
            try {
                jdbcTemplate.execute("ALTER TABLE truck ADD COLUMN current_point_sequence INT");
                log.info("已添加 truck.current_point_sequence 列");
            } catch (Exception e) {
                // 忽略已存在错误
            }

            log.info("数据库表结构初始化完成");

            // 只在表为空时初始化模拟数据
            Integer goodsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM goods", Integer.class);
            if (goodsCount == null || goodsCount == 0) {
                log.info("goods表为空，初始化模拟数据...");
                initMockData();
            } else {
                log.info("goods表已有 {} 条数据，跳过初始化", goodsCount);
            }
            
            // 6. 修复可能存在的经纬度格式错误 (lat,lng -> lng,lat)
            fixLocationData();
            
            // 7. (已移除) 强制重置所有车辆和任务
            // log.warn("执行深度清理：重置所有车辆位置至北京，并清空任务队列...");
            // ... (此前代码已注释或删除，保留现场)
            log.info("数据库检查与修复完成");

        } catch (Exception e) {
            log.error("数据库初始化失败", e);
        }
    }

    private void fixLocationData() {
        try {
            // 中国境内经纬度范围：经度 73-135，纬度 3-54
            double minLng = 73.0;
            double maxLng = 135.0;
            double minLat = 3.0;
            double maxLat = 54.0;
            
            // 查出所有车辆
            java.util.List<java.util.Map<String, Object>> trucks = jdbcTemplate.queryForList("SELECT id, location FROM truck");
            for (java.util.Map<String, Object> truck : trucks) {
                Long id = ((Number) truck.get("id")).longValue();
                String location = (String) truck.get("location");
                
                if (location != null && location.contains(",")) {
                    String[] parts = location.split(",");
                    if (parts.length == 2) {
                        try {
                            double part1 = Double.parseDouble(parts[0].trim());
                            double part2 = Double.parseDouble(parts[1].trim());
                            
                            // 判断依据：中国纬度约 3-54，经度约 73-135
                            // 正常的 lng,lat 格式应该是 part1(70-140), part2(3-54)
                            boolean isLngLat = (part1 >= minLng && part1 <= maxLng) && (part2 >= minLat && part2 <= maxLat);
                            // lat,lng 格式是 part1(3-54), part2(70-140)
                            boolean isLatLng = (part1 >= minLat && part1 <= maxLat) && (part2 >= minLng && part2 <= maxLng);
                            
                            String newLocation = null;
                            
                            if (isLatLng && !isLngLat) {
                                // 需要翻转：lat,lng -> lng,lat
                                newLocation = String.format("%.6f,%.6f", part2, part1);
                            } else if (!isLngLat && !isLatLng) {
                                // 两种格式都不符合，说明位置在国外或数据错误，重置到北京
                                newLocation = "116.4074,39.9042";
                                log.warn("车辆 {} 位置数据异常（在国外），重置到北京: {}", id, location);
                            } else if (isLngLat) {
                                // 已经是正确的 lng,lat 格式且在中国境内，不需要处理
                            } else if (isLatLng) {
                                // 已经是正确的 lat,lng 格式且在中国境内，但需要翻转
                                newLocation = String.format("%.6f,%.6f", part2, part1);
                            }
                            
                            // 额外检查：即使格式正确，如果坐标不在中国境内，也重置
                            if (newLocation == null) {
                                double lng = isLngLat ? part1 : part2;
                                double lat = isLngLat ? part2 : part1;
                                if (lng < minLng || lng > maxLng || lat < minLat || lat > maxLat) {
                                    newLocation = "116.4074,39.9042";
                                    log.warn("车辆 {} 位置在中国境外，重置到北京: {}", id, location);
                                }
                            }
                            
                            if (newLocation != null && !newLocation.equals(location)) {
                                jdbcTemplate.update("UPDATE truck SET location = ?, current_point_sequence = 0 WHERE id = ?", newLocation, id);
                                log.info("修复车辆 {} 位置数据: {} -> {}", id, location, newLocation);
                            }
                        } catch (NumberFormatException e) {
                            // 忽略解析错误
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("修复车辆位置数据失败", e);
        }
    }

    private void initMockData() {
        // 初始化客户数据
        Integer customerCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer", Integer.class);
        if (customerCount != null && customerCount == 0) {
            log.info("初始化客户模拟数据...");
            jdbcTemplate.execute("INSERT INTO customer (name, address, contact_person, phone, type) VALUES " +
                    "('京东物流北京分拣中心', '北京市大兴区经海路', '张经理', '13800138001', '发货方')," +
                    "('顺丰速运通州集散点', '北京市通州区马驹桥', '李主管', '13900139002', '收货方')," +
                    "('盒马鲜生朝阳店', '北京市朝阳区十里堡', '王店长', '13700137003', '收货方')," +
                    "('中石化燕山石化工厂', '北京市房山区燕山', '赵工', '13600136004', '发货方')," +
                    "('北京新发地批发市场', '北京市丰台区新发地', '孙老板', '13500135005', '发货方')");
        }

        // 初始化司机数据
        // 判断司机是否少于车辆数，如果是则补充生成
        Integer driverCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver", Integer.class);
        Integer truckCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM truck", Integer.class);
        
        // 目标司机数量：至少比车辆多5人，或者固定50人
        int targetDriverCount = Math.max(truckCount != null ? truckCount + 5 : 50, 50);
        
        if (driverCount != null && driverCount < targetDriverCount) {
            log.info("检测到司机数量不足（当前{}，目标{}），开始补充生成...", driverCount, targetDriverCount);
            
            // 常见姓氏
            String[] surnames = {"赵", "钱", "孙", "李", "周", "吴", "郑", "王", "冯", "陈", "褚", "卫", "蒋", "沈", "韩", "杨", "朱", "秦", "尤", "许"};
            // 常见名字字符
            String[] namesChar = {"建", "国", "华", "强", "军", "平", "安", "康", "顺", "利", "明", "辉", "伟", "刚", "勇", "超", "杰", "涛", "斌", "波"};
            
            // 批量插入
            int needToCreate = targetDriverCount - driverCount;
            // 分批处理，避免SQL过长
            int batchSize = 20;
            for (int i = 0; i < needToCreate; i += batchSize) {
                 StringBuilder sql = new StringBuilder("INSERT INTO driver (name, phone, license_type, status) VALUES ");
                 int currentBatch = Math.min(batchSize, needToCreate - i);
                 
                 for (int j = 0; j < currentBatch; j++) {
                     if (j > 0) sql.append(",");
                     
                     // 随机生成姓名
                     String name = surnames[(int)(Math.random() * surnames.length)] + namesChar[(int)(Math.random() * namesChar.length)];
                     // 随机生成手机号
                     String phone = "13" + (int)(Math.random() * 9 + 1) + String.format("%08d", (int)(Math.random() * 100000000));
                     // 随机驾照 (A2占比40%, B2占比60%)
                     String license = Math.random() < 0.4 ? "A2" : "B2";
                     
                     sql.append(String.format("('%s', '%s', '%s', '空闲')", name, phone, license));
                 }
                 jdbcTemplate.execute(sql.toString());
            }
            log.info("成功补充生成 {} 位司机", needToCreate);
        }
        
        // 维保记录和告警日志通常随业务运行产生，暂不预置过多数据
    }
}
