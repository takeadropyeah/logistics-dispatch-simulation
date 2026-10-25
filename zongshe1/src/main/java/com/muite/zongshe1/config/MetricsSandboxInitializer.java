package com.muite.zongshe1.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 指标体系与数据实验沙箱的表结构初始化。
 * 与 DatabaseInitializer 相互独立，负责阶段III新增的 4 张表和 task 表的时间戳/里程列。
 */
@Component
@Order(2)
public class MetricsSandboxInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MetricsSandboxInitializer.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            // 1. 匹配日志表：每次任务-车辆分配时记录"条件最优车"对照信息（匹配精准度指标数据源）
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS match_log (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "task_id INT," +
                    "truck_id INT," +
                    "chosen_empty_km DOUBLE," +
                    "optimal_empty_km DOUBLE," +
                    "candidate_count INT," +
                    "rank_no INT," +
                    "is_optimal BOOLEAN," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // 2. 指标快照表：定时采集的八维指标历史（趋势曲线数据源）
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS metric_snapshot (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "metrics LONGTEXT," +
                    "create_time DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // 3. 沙箱实验表：实验方案（环境配置 + 算法配置，JSON）
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sandbox_experiment (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(100)," +
                    "description VARCHAR(500)," +
                    "researcher VARCHAR(64) DEFAULT '研究人员'," +
                    "config LONGTEXT," +
                    "status VARCHAR(20) DEFAULT '已创建'," +
                    "create_time DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // 4. 沙箱批次运行表：每运行一次追加一行，seed 保证可复现
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sandbox_run (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "experiment_id INT," +
                    "run_no INT," +
                    "seed BIGINT," +
                    "algorithm VARCHAR(32)," +
                    "status VARCHAR(20) DEFAULT '已完成'," +
                    "compute_ms BIGINT," +
                    "result LONGTEXT," +
                    "snapshots LONGTEXT," +
                    "create_time DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // 5. 全局优化派单批次表：每轮多目标派单记录"贪心基线 vs 优化解"成本对比（A 维优化收益数据源）
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dispatch_batch_log (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "task_count INT," +
                    "truck_count INT," +
                    "greedy_cost DOUBLE," +
                    "optimized_cost DOUBLE," +
                    "improvement_pct DOUBLE," +
                    "algorithm VARCHAR(32)," +
                    "create_time DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            try {
                jdbcTemplate.execute("ALTER TABLE dispatch_batch_log ADD COLUMN carbon_kg DOUBLE");
            } catch (Exception ignored) { }

            // task 表补时间戳与里程列（响应时间 / 空驶里程指标的口径基础）
            try { jdbcTemplate.execute("ALTER TABLE task ADD COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP"); } catch (Exception ignored) { }
            try { jdbcTemplate.execute("ALTER TABLE task ADD COLUMN assign_time DATETIME"); } catch (Exception ignored) { }
            try { jdbcTemplate.execute("ALTER TABLE task ADD COLUMN complete_time DATETIME"); } catch (Exception ignored) { }
            try { jdbcTemplate.execute("ALTER TABLE task ADD COLUMN empty_km DOUBLE"); } catch (Exception ignored) { }
            try { jdbcTemplate.execute("ALTER TABLE task ADD COLUMN loaded_km DOUBLE"); } catch (Exception ignored) { }

            // 已有任务用 goods 的创建时间回填 create_time，避免响应时间口径失真
            try {
                jdbcTemplate.update("UPDATE task t JOIN goods g ON g.task_id = t.id SET t.create_time = g.create_time WHERE t.create_time IS NULL AND g.create_time IS NOT NULL");
                jdbcTemplate.update("UPDATE task SET create_time = NOW() WHERE create_time IS NULL");
            } catch (Exception ignored) { }

            log.info("指标体系与沙箱表结构初始化完成（match_log / metric_snapshot / sandbox_experiment / sandbox_run）");
        } catch (Exception e) {
            log.error("指标体系与沙箱表初始化失败", e);
        }
    }
}
