-- ============================================================
-- 结题数据导出脚本：每次运行会话结束后在 Workbench 执行，
-- 结果可直接截图/复制进结题报告。
-- ============================================================

USE zongshe;

-- 0. 【最核心证据】优化前后对比：按"全局优化上线时刻"切分两个时代聚合
--    （多目标优化于 2026-10-25 17:36 接入派单主流程）
--    预期：优化期空驶率显著低于基线期 → "全局优化性能提升"的直接证据
SELECT CASE WHEN complete_time < '2026-10-25 17:36:00'
            THEN '① 先到先得基线期'
            ELSE '② 多目标优化期' END AS 阶段,
       COUNT(*) AS 完成任务数,
       ROUND(SUM(loaded_km), 1) AS 载重里程km,
       ROUND(SUM(empty_km), 1) AS 空驶里程km,
       ROUND(SUM(empty_km) / NULLIF(SUM(empty_km) + SUM(loaded_km), 0) * 100, 1) AS 空驶率百分比,
       ROUND(AVG(TIMESTAMPDIFF(SECOND, create_time, complete_time)) / 60.0, 1) AS 平均流转分钟
FROM task
WHERE status = '已完成' AND complete_time IS NOT NULL AND empty_km IS NOT NULL
GROUP BY 阶段
ORDER BY 阶段;

-- 1. 【趋势曲线】按日期的空驶率趋势
SELECT DATE(complete_time) AS 日期,
       COUNT(*) AS 完成任务数,
       ROUND(SUM(loaded_km), 1) AS 载重里程km,
       ROUND(SUM(empty_km), 1) AS 空驶里程km,
       ROUND(SUM(empty_km) / NULLIF(SUM(empty_km) + SUM(loaded_km), 0) * 100, 1) AS 空驶率百分比
FROM task
WHERE status = '已完成' AND complete_time IS NOT NULL
GROUP BY DATE(complete_time)
ORDER BY 日期;

-- 2. 【核心证据】全局优化批次收益：每轮"先到先得基线 vs 优化解"
SELECT DATE(create_time) AS 日期,
       COUNT(*) AS 优化批次数,
       ROUND(AVG(improvement_pct), 1) AS 平均降幅百分比,
       ROUND(MAX(improvement_pct), 1) AS 最大降幅百分比,
       ROUND(SUM(carbon_kg), 1) AS 累计空驶碳排kg
FROM dispatch_batch_log
GROUP BY DATE(create_time)
ORDER BY 日期;

-- 3. 司机行为状态统计（五态证据）
SELECT status AS 司机状态, COUNT(*) AS 人数
FROM driver GROUP BY status ORDER BY 人数 DESC;

-- 4. 随机事件统计（五类事件证据）
SELECT type AS 事件类型, COUNT(*) AS 次数
FROM alert_log
WHERE create_time >= CURDATE() - INTERVAL 7 DAY
GROUP BY type ORDER BY 次数 DESC;

-- 5. 货物损耗与维保记录（随机事件的业务影响证据）
SELECT COUNT(*) AS 维保记录数, ROUND(SUM(cost), 0) AS 维保总成本 FROM maintenance_record;
SELECT COUNT(*) AS 损耗记录数 FROM goods_loss;
