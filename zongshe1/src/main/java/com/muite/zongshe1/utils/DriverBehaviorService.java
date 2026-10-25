package com.muite.zongshe1.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muite.zongshe1.constant.TaskStatusConstant;
import com.muite.zongshe1.constant.TruckStatusConstant;
import com.muite.zongshe1.entity.AlertLog;
import com.muite.zongshe1.entity.Task;
import com.muite.zongshe1.entity.Truck;
import com.muite.zongshe1.mapper.AlertLogMapper;
import com.muite.zongshe1.mapper.TaskMapper;
import com.muite.zongshe1.mapper.TaskRoutePointMapper;
import com.muite.zongshe1.mapper.TruckMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 司机行为状态引擎 + 线上随机事件（阶段III）。
 *
 * 司机行为状态共5种，均以数学概率为变化基准：
 *   空闲 / 驾驶中（既有）；
 *   拒单冷却：派单时按 4%×驾照系数 触发（A2 驾照更稳定），冷却 9 秒，由 SimulationService 派单插桩触发；
 *   疲劳休息：连续驾驶超过 900 tick（约3分钟）后每 tick 2% 概率触发，休息 12 秒；
 *   休假：空闲且未绑车司机每 10 秒 1% 概率触发，休假 20 秒。
 *
 * 线上随机事件共5种：
 *   交通拥堵（既有，2%/tick）、货物损耗（既有）；
 *   极端天气：区域滚动冻结（每10秒对区域内运输车辆续期3 tick 冻结）；
 *   车辆故障：0.05%/tick 触发，任务释放回派单池、写维保/告警，90 秒后恢复；
 *   缺货：待分配任务每 3 秒 0.2% 概率被取消。
 */
@Service
public class DriverBehaviorService {

    private static final Logger log = LoggerFactory.getLogger(DriverBehaviorService.class);

    @Autowired
    private TruckMapper truckMapper;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private TaskRoutePointMapper taskRoutePointMapper;
    @Autowired
    private AlertLogMapper alertLogMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SimulationService simulationService;

    private final Random random = new Random();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 疲劳驾驶累计 tick（车辆id → 连续运输 tick 数） */
    private final Map<Integer, Integer> drivingTicks = new ConcurrentHashMap<>();
    /** 疲劳休息恢复时刻（车辆id → 时间戳） */
    private final Map<Integer, Long> restUntil = new ConcurrentHashMap<>();
    /** 故障恢复时刻（车辆id → 时间戳），故障时长 90 秒 */
    private final Map<Integer, Long> faultUntil = new ConcurrentHashMap<>();
    /** 保养恢复时刻（车辆id → 时间戳），保养时长 30 秒 */
    private final Map<Integer, Long> maintUntil = new ConcurrentHashMap<>();
    /** 休假恢复时刻（司机id → 时间戳），休假 20 秒 */
    private final Map<Integer, Long> vacationUntil = new ConcurrentHashMap<>();

    /** 天气区域（经度min, 经度max, 纬度min, 纬度max） */
    private static final double[][] WEATHER_REGIONS = {
            {115.0, 118.0, 38.5, 41.5},   // 京津冀
            {119.5, 122.5, 30.0, 32.5},   // 长三角
            {112.5, 115.5, 21.8, 23.8},   // 珠三角
            {102.8, 105.5, 29.8, 31.5},   // 成渝
            {113.0, 116.0, 33.8, 36.0},   // 中原
    };
    private static final String[] WEATHER_REGION_NAMES = {"京津冀", "长三角", "珠三角", "成渝", "中原"};
    private volatile int weatherRegion = -1;
    private volatile long weatherUntil = 0;

    // ==================== 200ms 主行为循环：疲劳 + 故障 + 恢复 ====================

    @Scheduled(fixedRate = 200)
    public void behaviorTick() {
        if (!simulationService.isSimulationRunning()) {
            return;
        }
        long now = System.currentTimeMillis();
        recoverFinished(now);

        List<Truck> trucks = truckMapper.selectAll();
        for (Truck truck : trucks) {
            Object statusObj = truck.getStatus();
            if (statusObj == null) continue;
            String status = statusObj.toString();
            int id = truck.getId();

            if (TruckStatusConstant.IN_TRANSIT.equals(status) || TruckStatusConstant.PICKUP.equals(status)) {
                if (restUntil.containsKey(id) || faultUntil.containsKey(id)) continue;
                // 疲劳：连续驾驶 900 tick 后每 tick 2% 概率触发
                int ticks = drivingTicks.merge(id, 1, Integer::sum);
                if (ticks > 900 && random.nextDouble() < 0.02) {
                    triggerRest(truck, now);
                    continue;
                }
                // 车辆故障：0.01%/tick（约每小时 5-10 次，避免停机时间占比过高）
                if (random.nextDouble() < 0.0001) {
                    triggerFault(truck, now);
                }
            } else if (TruckStatusConstant.IDLE.equals(status)) {
                drivingTicks.remove(id);
                // 保养：空闲车辆 0.05%/tick
                if (!maintUntil.containsKey(id) && random.nextDouble() < 0.0005) {
                    triggerMaintenance(truck, now);
                }
            }
        }
    }

    /** 疲劳休息：车辆停靠（借用等待计数器冻结移动），司机进入"疲劳休息"，12 秒后恢复 */
    private void triggerRest(Truck truck, long now) {
        int ticks = 60; // 12 秒真实时间
        simulationService.getTruckWaitCounters().put(truck.getId(), ticks);
        truck.setStatus(TruckStatusConstant.RESTING);
        truckMapper.updateByPrimaryKey(truck);
        restUntil.put(truck.getId(), now + ticks * 200L);
        jdbcTemplate.update("UPDATE driver SET status='疲劳休息' WHERE truck_id=? AND status='驾驶中'", truck.getId());
        insertAlert(truck.getId(), truck.getTaskId(), "疲劳休息", "一般", truck.getLocation());
        broadcast(truck, "DRIVER_REST");
        log.info("司机行为：车辆{} 疲劳休息 {} 秒", truck.getId(), ticks * 200 / 1000);
    }

    /** 车辆故障：任务释放回派单池、货物回待分配、司机解除绑定，90 秒后恢复空闲 */
    private void triggerFault(Truck truck, long now) {
        Integer taskId = truck.getTaskId();
        if (taskId != null) {
            try {
                Task up = new Task();
                up.setId(taskId);
                up.setStatus(TaskStatusConstant.TO_BE_ASSIGNED);
                taskMapper.updateStatus(up);
                jdbcTemplate.update("UPDATE task SET truck_id = NULL WHERE id = ?", taskId);
                taskRoutePointMapper.deleteByTaskId(taskId);
                jdbcTemplate.update("UPDATE goods SET status='待分配' WHERE task_id=? AND status='运输中'", taskId);
            } catch (Exception e) {
                log.warn("故障释放任务{}失败: {}", taskId, e.getMessage());
            }
        }
        truck.setStatus(TruckStatusConstant.MALFUNCTION);
        truck.setTaskId(null);
        truckMapper.updateByPrimaryKey(truck);
        faultUntil.put(truck.getId(), now + 450 * 200L); // 90 秒
        jdbcTemplate.update("UPDATE driver SET status='空闲', truck_id=NULL WHERE truck_id=?", truck.getId());
        insertAlert(truck.getId(), taskId, "故障", "严重", truck.getLocation());
        broadcast(truck, "VEHICLE_FAULT");
        log.info("随机事件：车辆{} 故障，任务{}已回池，90 秒后修复", truck.getId(), taskId);
    }

    /** 保养：空闲车辆进入保养，写维保记录（maintenance_record 首次产生真实数据），30 秒后恢复 */
    private void triggerMaintenance(Truck truck, long now) {
        int ticks = 150; // 30 秒
        simulationService.getTruckWaitCounters().put(truck.getId(), ticks);
        truck.setStatus(TruckStatusConstant.MAINTENANCE);
        truckMapper.updateByPrimaryKey(truck);
        maintUntil.put(truck.getId(), now + ticks * 200L);
        try {
            jdbcTemplate.update("INSERT INTO maintenance_record(truck_id, start_date, type, description, cost) " +
                            "VALUES (?, NOW(), '定期保养', '仿真触发：按概率进入定期保养', ?)",
                    truck.getId(), 200 + random.nextInt(800));
            jdbcTemplate.update("UPDATE driver SET status='保养中' WHERE truck_id=?", truck.getId());
        } catch (Exception e) {
            log.warn("写维保记录失败: {}", e.getMessage());
        }
        broadcast(truck, "MAINT_START");
        log.info("随机事件：车辆{} 进入保养 30 秒", truck.getId());
    }

    /** 恢复检查：休息/故障/保养期满的车辆回到工作状态 */
    private void recoverFinished(long now) {
        recoverMap(restUntil, now, true, "REST_DONE");
        recoverMap(faultUntil, now, false, "REPAIR_DONE");
        recoverMap(maintUntil, now, false, "MAINT_DONE");
    }

    private void recoverMap(Map<Integer, Long> map, long now, boolean hasTask, String event) {
        for (Map.Entry<Integer, Long> e : map.entrySet()) {
            if (now >= e.getValue()) {
                Integer truckId = e.getKey();
                map.remove(truckId);
                try {
                    Truck truck = truckMapper.selectByPrimaryKey(truckId.longValue());
                    if (truck == null) continue;
                    // 休息的车辆可能有任务在身：恢复为运输中；其余恢复空闲
                    truck.setStatus(hasTask && truck.getTaskId() != null ? TruckStatusConstant.IN_TRANSIT : TruckStatusConstant.IDLE);
                    truckMapper.updateByPrimaryKey(truck);
                    if (hasTask) {
                        jdbcTemplate.update("UPDATE driver SET status='驾驶中' WHERE truck_id=? AND status='疲劳休息'", truck.getId());
                    } else {
                        jdbcTemplate.update("UPDATE driver SET status='空闲' WHERE truck_id=? AND status IN ('疲劳休息','保养中')", truck.getId());
                    }
                    drivingTicks.remove(truckId);
                    broadcast(truck, event);
                    log.info("行为恢复：车辆{} -> {}", truckId, truck.getStatus());
                } catch (Exception ex) {
                    log.warn("恢复车辆{}失败: {}", truckId, ex.getMessage());
                }
            }
        }
    }

    // ==================== 10 秒：极端天气 + 司机休假 + 状态对账 ====================

    @Scheduled(fixedRate = 10000)
    public void weatherAndDriverTick() {
        if (!simulationService.isSimulationRunning()) {
            return;
        }
        long now = System.currentTimeMillis();

        // 极端天气：无天气时 10% 概率在某区域发生，持续 60 秒；期间对区域内运输车辆滚动冻结 3 tick（降速效果）
        if (weatherRegion >= 0) {
            if (now >= weatherUntil) {
                String name = WEATHER_REGION_NAMES[weatherRegion];
                weatherRegion = -1;
                log.info("随机事件：极端天气结束（{}）", name);
            } else {
                freezeTrucksInRegion();
            }
        } else if (random.nextDouble() < 0.10) {
            weatherRegion = random.nextInt(WEATHER_REGIONS.length);
            weatherUntil = now + 60_000;
            freezeTrucksInRegion();
            insertAlert(null, null, "天气", "一般", WEATHER_REGION_NAMES[weatherRegion] + "区域");
            log.info("随机事件：极端天气袭击 {} 区域，持续 60 秒", WEATHER_REGION_NAMES[weatherRegion]);
        }

        // 司机休假：空闲且未绑车的司机 1%/10秒 → 休假 20 秒（第5种司机状态）
        vacationRecover(now);
        try {
            List<Integer> idle = jdbcTemplate.queryForList(
                    "SELECT id FROM driver WHERE status='空闲' AND truck_id IS NULL", Integer.class);
            for (Integer driverId : idle) {
                if (!vacationUntil.containsKey(driverId) && random.nextDouble() < 0.01) {
                    jdbcTemplate.update("UPDATE driver SET status='休假' WHERE id=?", driverId);
                    vacationUntil.put(driverId, now + 20_000);
                    log.info("司机行为：司机{} 休假 20 秒", driverId);
                }
            }
        } catch (Exception e) {
            log.warn("司机休假检查失败: {}", e.getMessage());
        }

        // 状态对账（不变式校验）：司机绑定的车辆若已空闲/不存在/无任务，则解绑司机。
        // 旧数据里存在大量失效绑定（truck_id 指向早已不工作的车辆），此规则能自动清退，
        // 使"派单时绑定 / 拒单候选"始终能找到真正空闲（truck_id IS NULL）的司机。
        try {
            jdbcTemplate.update("UPDATE driver d LEFT JOIN truck t ON d.truck_id = t.id " +
                    "SET d.status='空闲', d.truck_id=NULL " +
                    "WHERE d.truck_id IS NOT NULL AND d.status IN ('空闲','驾驶中') " +
                    "AND (t.id IS NULL OR t.status IS NULL OR t.status IN ('空闲','保养') OR t.task_id IS NULL AND t.status NOT IN ('故障'))");
        } catch (Exception e) {
            log.warn("司机状态对账失败: {}", e.getMessage());
        }
    }

    private void vacationRecover(long now) {
        for (Map.Entry<Integer, Long> e : vacationUntil.entrySet()) {
            if (now >= e.getValue()) {
                Integer driverId = e.getKey();
                vacationUntil.remove(driverId);
                try {
                    jdbcTemplate.update("UPDATE driver SET status='空闲' WHERE id=? AND status='休假'", driverId);
                } catch (Exception ignored) { }
            }
        }
    }

    private void freezeTrucksInRegion() {
        double[] box = WEATHER_REGIONS[weatherRegion];
        int frozen = 0;
        try {
            List<Truck> trucks = truckMapper.selectAll();
            for (Truck truck : trucks) {
                Object stObj = truck.getStatus();
                if (stObj == null) continue;
                String s = stObj.toString();
                if (!TruckStatusConstant.IN_TRANSIT.equals(s) && !TruckStatusConstant.PICKUP.equals(s)) continue;
                double[] pos = parseLngLat(truck.getLocation());
                if (pos == null) continue;
                if (pos[0] >= box[0] && pos[0] <= box[1] && pos[1] >= box[2] && pos[1] <= box[3]) {
                    Integer cur = simulationService.getTruckWaitCounters().get(truck.getId());
                    int target = Math.max(cur == null ? 0 : cur, 3);
                    simulationService.getTruckWaitCounters().put(truck.getId(), target);
                    frozen++;
                }
            }
        } catch (Exception e) {
            log.warn("天气冻结失败: {}", e.getMessage());
        }
        if (frozen > 0) {
            log.debug("极端天气（{}）：区域内 {} 辆车降速缓行", WEATHER_REGION_NAMES[weatherRegion], frozen);
        }
    }

    // ==================== 3 秒：缺货事件 ====================

    @Scheduled(fixedRate = 3000)
    public void stockoutCheck() {
        if (!simulationService.isSimulationRunning()) {
            return;
        }
        try {
            List<Task> pending = taskMapper.findByTruckIdIsNull();
            for (Task t : pending) {
                String status = String.valueOf(t.getStatus());
                if (!TaskStatusConstant.TO_BE_ASSIGNED.equals(status)) continue;
                // 缺货：0.2%/3秒 概率取消任务（与生成速率大致平衡，避免任务池无限膨胀或枯竭）
                if (random.nextDouble() < 0.002) {
                    Task up = new Task();
                    up.setId(t.getId());
                    up.setStatus(TaskStatusConstant.CANCELLED);
                    taskMapper.updateStatus(up);
                    jdbcTemplate.update("UPDATE goods SET status='已取消' WHERE task_id=?", t.getId());
                    insertAlert(null, t.getId(), "缺货", "一般", null);
                    log.info("随机事件：任务{} 缺货取消", t.getId());
                }
            }
        } catch (Exception e) {
            log.warn("缺货检查失败: {}", e.getMessage());
        }
    }

    // ==================== 工具 ====================

    private void insertAlert(Integer truckId, Integer taskId, String type, String level, String location) {
        try {
            AlertLog alertLog = new AlertLog();
            alertLog.setTruckId(truckId);
            alertLog.setTaskId(taskId);
            alertLog.setType(type);
            alertLog.setLevel(level);
            alertLog.setLocation(location);
            alertLog.setCreateTime(new Date());
            alertLog.setIsHandled(false);
            alertLogMapper.insert(alertLog);
        } catch (Exception e) {
            log.warn("写告警日志失败: {}", e.getMessage());
        }
    }

    private void broadcast(Truck truck, String eventType) {
        try {
            Map<String, Object> msg = new java.util.LinkedHashMap<>();
            msg.put("eventType", eventType);
            msg.put("truckId", truck.getId());
            msg.put("location", truck.getLocation());
            msg.put("status", truck.getStatus());
            msg.put("taskId", truck.getTaskId());
            msg.put("timestamp", System.currentTimeMillis());
            VehicleSimulationSocket.broadcast(objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.warn("广播{}事件失败: {}", eventType, e.getMessage());
        }
    }

    private double[] parseLngLat(String location) {
        if (location == null || !location.contains(",")) return null;
        try {
            String[] p = location.split(",");
            return new double[]{Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim())};
        } catch (Exception e) {
            return null;
        }
    }
}
