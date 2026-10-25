package com.muite.zongshe1.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muite.zongshe1.constant.GoodsTypeConstant;
import com.muite.zongshe1.constant.TaskStatusConstant;
import com.muite.zongshe1.constant.TruckStatusConstant;
import com.muite.zongshe1.constant.TruckTypeConstant;
import com.muite.zongshe1.entity.*;
import com.muite.zongshe1.mapper.RouteMapper;
import com.muite.zongshe1.mapper.TaskMapper;
import com.muite.zongshe1.mapper.TaskRoutePointMapper;
import com.muite.zongshe1.mapper.TruckMapper;
import com.muite.zongshe1.mapper.AlertLogMapper;
import com.muite.zongshe1.service.GoodsService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.Stack;

@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    @Autowired
    TruckMapper truckMapper;
    @Autowired
    TaskMapper taskMapper;
    @Autowired
    RouteMapper routeMapper;

    @Autowired
    AmapTruckRouteService amapTruckRouteService;

    @Autowired
    private TaskRoutePointMapper taskRoutePointMapper;
    @Autowired
    private AlertLogMapper alertLogMapper;
    
    @Autowired
    private RouteSimplifier routeSimplifier;
    
    @Autowired
    private GoodsService goodsService;
    
    @Autowired
    @Lazy
    private OptimizationService optimizationService;
    
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate metricsJdbcTemplate;

    /** 最近一轮派单求解耗时（毫秒），供指标体系（A2 算法效率）读取 */
    private volatile long lastAssignCostMs = 0;

    /** 司机拒单冷却（司机id → 冷却截止时间戳） */
    private final Map<Integer, Long> driverRejectCooldown = new java.util.concurrent.ConcurrentHashMap<>();

    public long getLastAssignCostMs() {
        return lastAssignCostMs;
    }

    /**
     * 司机行为状态：按数学概率拒单。候选司机为第一名空闲未绑车的司机；
     * 基准概率 15%，A2 驾照修正系数 0.5（更稳定）；拒单后写告警日志
     * （公平性指标 F2 拒单数的数据源），司机进入 9 秒拒单冷却（冷却期间不参与派单绑定）。
     */
    private boolean driverRejects(Truck truck, Task task) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, license_type FROM driver WHERE status='空闲' AND truck_id IS NULL LIMIT 1");
            if (rows.isEmpty()) {
                return false; // 没有可指派的候选司机
            }
            Integer driverId = ((Number) rows.get(0).get("id")).intValue();
            String license = String.valueOf(rows.get(0).get("license_type"));
            double rate = "A2".equals(license) ? 0.08 : 0.15;
            if (random.nextDouble() >= rate) {
                return false;
            }
            driverRejectCooldown.put(driverId, System.currentTimeMillis() + 9000);
            jdbcTemplate.update("UPDATE driver SET status='拒单冷却' WHERE id=?", driverId);
            AlertLog alertLog = new AlertLog();
            alertLog.setTruckId(truck.getId());
            alertLog.setTaskId(task.getId());
            alertLog.setType("拒单");
            alertLog.setLevel("一般");
            alertLog.setLocation(truck.getLocation());
            alertLog.setCreateTime(new Date());
            alertLog.setIsHandled(false);
            alertLogMapper.insert(alertLog);
            broadcastTruckEvent(truck, "DRIVER_REJECT", null);
            log.info("司机行为：司机{} 拒单（概率{}），车辆{} 本轮跳过任务{}", driverId, rate, truck.getId(), task.getId());
            return true;
        } catch (Exception e) {
            log.warn("拒单判定失败: {}", e.getMessage());
            return false;
        }
    }

    private final Map<Integer, Integer> truckWaitCounters = new java.util.concurrent.ConcurrentHashMap();

    // 获取车辆等待计数器
    public Map<Integer, Integer> getTruckWaitCounters() {
        return truckWaitCounters;
    }

    // 获取车辆总里程
    public Map<Integer, Double> getTruckTotalDistance() {
        return truckTotalDistance;
    }

    private final Random random = new Random();

    private final Map<Integer, Long> lastLossCheckTime = new HashMap<>();

    private volatile boolean isSimulationRunning = true;
    
    // 仿真速度倍数（默认1倍）
    private volatile int simulationSpeed = 1;

    public void startSimulation() {
        this.isSimulationRunning = true;
        log.info("仿真已启动/恢复");
    }

    public void pauseSimulation() {
        this.isSimulationRunning = false;
        log.info("仿真已暂停");
    }

    public boolean isSimulationRunning() {
        return isSimulationRunning;
    }
    
    public void setSimulationSpeed(int speed) {
        int oldSpeed = this.simulationSpeed;
        
        if (oldSpeed != speed) {
            // 先更新速度，再同步车辆（resyncAllMovingTrucks需要使用新速度计算）
            this.simulationSpeed = speed;
            resyncAllMovingTrucks(oldSpeed, speed);
            
            // 广播速度变更事件给所有前端
            broadcastSpeedChangeEvent(oldSpeed, speed);
            
            log.info("仿真速度已调整为{}倍", speed);
        }
    }
    
    private void broadcastSpeedChangeEvent(int oldSpeed, int newSpeed) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("eventType", "SPEED_CHANGED");
            message.put("oldSpeed", oldSpeed);
            message.put("newSpeed", newSpeed);
            message.put("timestamp", System.currentTimeMillis());
            
            VehicleSimulationSocket.broadcast(new ObjectMapper().writeValueAsString(message));
            log.info("广播速度变更事件: {}x -> {}x", oldSpeed, newSpeed);
        } catch (Exception e) {
            log.error("广播速度变更事件失败", e);
        }
    }
    
    private void resyncAllMovingTrucks(int oldSpeed, int newSpeed) {
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<Integer, Long> entry : truckMovementStartTime.entrySet()) {
            Integer truckId = entry.getKey();
            Long startTime = entry.getValue();
            Double totalDistance = truckTotalDistance.get(truckId);
            List<TaskRoutePoint> routePoints = truckRouteCache.get(truckId);
            
            if (startTime != null && totalDistance != null && routePoints != null && totalDistance > 0) {
                long elapsed = currentTime - startTime;
                double speedKmh = 600.0 * oldSpeed;
                double speedMs = speedKmh * 1000 / 3600;
                double traveledDistance = speedMs * (elapsed / 1000.0);
                double progress = Math.min(traveledDistance / (totalDistance * 1000), 1.0);
                
                if (progress < 1.0) {
                    String currentLocation = interpolatePosition(routePoints, progress);
                    
                    // 关键修复：根据已行驶的进度，倒推一个新的startTime
                    // 使得在新速度下，从新的startTime开始计算，车辆会处于相同的位置
                    double newSpeedKmh = 600.0 * newSpeed;
                    double newSpeedMs = newSpeedKmh * 1000 / 3600;
                    // 已行驶距离 / 新速度 = 需要的时间（秒）
                    double timeNeededSeconds = traveledDistance / newSpeedMs;
                    // 新的startTime = 当前时间 - 需要的时间
                    long newStartTime = currentTime - (long)(timeNeededSeconds * 1000);
                    
                    truckMovementStartTime.put(truckId, newStartTime);
                    
                    Truck truck = truckMapper.selectByPrimaryKey(truckId.longValue());
                    if (truck != null) {
                        truck.setLocation(currentLocation);
                        truckMapper.updateByPrimaryKey(truck);
                        
                        log.info("车辆 {} 速度从{}x调整到{}x，已行驶{:.2f}%，重新锚定位置: {}", 
                            truckId, oldSpeed, newSpeed, progress * 100, currentLocation);
                    }
                }
            }
        }
    }
    
    public int getSimulationSpeed() {
        return simulationSpeed;
    }

    // 服务启动时恢复车辆进度
    @PostConstruct
    public void restoreTruckProgress() {
        log.info("开始恢复车辆进度...");
        
        // 先修复异常位置数据（确保所有车辆在中国境内）
        fixAbnormalLocations();
        
        List<Truck> trucks = truckMapper.selectAll();
        for (Truck truck : trucks) {
            Integer truckId = truck.getId();
            Integer currentSequence = truck.getCurrentPointSequence();
            String status = truck.getStatus().toString();
            
            // 如果车辆状态不是空闲，但没有路径点，则重置为空闲
            if (!TruckStatusConstant.IDLE.equals(status) && !TruckStatusConstant.LOADING.equals(status) 
                && !TruckStatusConstant.UNLOADING.equals(status)) {
                Task task = taskMapper.selectByTruckId(truckId);
                if (task != null) {
                    List<TaskRoutePoint> routePoints = taskRoutePointMapper.selectByTaskId(task.getId());
                    if (routePoints.isEmpty()) {
                        // 没有路径点，重置车辆状态
                        truck.setStatus(TruckStatusConstant.IDLE);
                        truck.setCurrentPointSequence(0);
                        truckMapper.updateByPrimaryKey(truck);
                        log.warn("车辆 {} 状态为{}但无路径点，重置为空闲", truckId, status);
                        continue;
                    }
                }
            }
            
            // 只恢复有进度记录且正在运输或取货的车辆
            if (currentSequence != null && currentSequence != 0) {
                Task task = taskMapper.selectByTruckId(truckId);
                if (task == null) {
                    continue;
                }
                
                if (TruckStatusConstant.IN_TRANSIT.equals(status)) {
                    // 恢复运输状态
                    List<TaskRoutePoint> routePoints = taskRoutePointMapper.selectByTaskId(task.getId());
                    if (!routePoints.isEmpty()) {
                        routePoints.sort(Comparator.comparingInt(TaskRoutePoint::getSequence));
                        
                        // 根据保存的进度计算新的startTime
                        int savedIndex = Math.min(currentSequence, routePoints.size() - 1);
                        double progress = (double) savedIndex / routePoints.size();
                        
                        truckRouteCache.put(truckId, routePoints);
                        truckTotalDistance.put(truckId, calculateTotalRouteDistance(routePoints));
                        
                        // 倒推startTime，使车辆从保存的位置继续
                        double totalDist = truckTotalDistance.get(truckId);
                        double traveledDistance = progress * totalDist * 1000; // 米
                        double speedMs = 600.0 * simulationSpeed * 1000 / 3600;
                        long timeNeededMs = (long) (traveledDistance / speedMs * 1000);
                        long newStartTime = System.currentTimeMillis() - timeNeededMs;
                        
                        truckMovementStartTime.put(truckId, newStartTime);
                        
                        // 更新车辆位置
                        String location = interpolatePosition(routePoints, progress);
                        truck.setLocation(location);
                        truckMapper.updateByPrimaryKey(truck);
                        
                        log.info("车辆 {} 运输进度已恢复，当前位置: {}，进度: {:.2f}%", 
                            truckId, location, progress * 100);
                    }
                } else if (TruckStatusConstant.PICKUP.equals(status)) {
                    // 恢复取货状态
                    List<TaskRoutePoint> routePoints = taskRoutePointMapper.selectByTaskId(task.getId()).stream()
                            .filter(p -> p.getSequence() < 0)
                            .sorted(Comparator.comparingInt(TaskRoutePoint::getSequence))
                            .collect(Collectors.toList());
                    
                    if (!routePoints.isEmpty()) {
                        // 取货路径使用负数索引
                        int savedIndex = Math.min(-currentSequence, routePoints.size() - 1);
                        double progress = (double) savedIndex / routePoints.size();
                        
                        truckRouteCache.put(truckId, routePoints);
                        truckTotalDistance.put(truckId, calculateTotalRouteDistance(routePoints));
                        
                        double totalDist = truckTotalDistance.get(truckId);
                        double traveledDistance = progress * totalDist * 1000;
                        double speedMs = 600.0 * simulationSpeed * 1000 / 3600;
                        long timeNeededMs = (long) (traveledDistance / speedMs * 1000);
                        long newStartTime = System.currentTimeMillis() - timeNeededMs;
                        
                        truckMovementStartTime.put(truckId, newStartTime);
                        
                        String location = interpolatePosition(routePoints, progress);
                        truck.setLocation(location);
                        truckMapper.updateByPrimaryKey(truck);
                        
                        log.info("车辆 {} 取货进度已恢复，当前位置: {}，进度: {:.2f}%", 
                            truckId, location, progress * 100);
                    }
                } else if (TruckStatusConstant.LOADING.equals(status)) {
                    // 恢复装货状态：初始化等待计数器
                    truckWaitCounters.put(truckId, 3);
                    log.info("车辆 {} 装货状态已恢复，等待计数器已初始化", truckId);
                } else if (TruckStatusConstant.UNLOADING.equals(status)) {
                    // 恢复卸货状态：初始化等待计数器
                    truckWaitCounters.put(truckId, 3);
                    log.info("车辆 {} 卸货状态已恢复，等待计数器已初始化", truckId);
                }
            }
        }
        
        log.info("车辆进度恢复完成，共恢复 {} 辆车", truckMovementStartTime.size());
    }

    private final Queue<Task> pendingTasks = new ConcurrentLinkedQueue<>();

    public void addPendingTask(Task task) {
        pendingTasks.add(task);
    }


    @Scheduled(fixedRate = 3000, initialDelay = 3000)
    public void syncUnassignedTasksFromDb() {
        try {
            // 直接分配，不使用队列
            directAssignTasks();
        } catch (Exception e) {
            log.error("任务分配失败", e);
        }
    }
    
    /**
     * 直接从数据库获取任务和空闲车辆进行分配
     */
    private void directAssignTasks() {
        synchronized (assignLock) {
            long startNs = System.nanoTime();
            // 1. 直接从数据库获取未分配任务
            List<Task> unassignedTasks = taskMapper.findByTruckIdIsNull();
            if (unassignedTasks.isEmpty()) {
                log.debug("没有待分配任务");
                return;
            }

            // 2. 直接从数据库获取空闲车辆
            List<Truck> idleTrucks = truckMapper.selectByStatus(TruckStatusConstant.IDLE);
            if (idleTrucks.isEmpty()) {
                log.debug("没有空闲车辆");
                return;
            }

            log.info("开始任务分配：待分配任务={}, 空闲车辆={}", unassignedTasks.size(), idleTrucks.size());

            // 司机行为状态：拒单冷却期满的司机恢复空闲（冷却期间不参与派单绑定）
            driverRejectCooldown.entrySet().removeIf(e -> {
                if (System.currentTimeMillis() >= e.getValue()) {
                    try {
                        jdbcTemplate.update("UPDATE driver SET status='空闲' WHERE id=? AND status='拒单冷却'", e.getKey());
                    } catch (Exception ignored) { }
                    return true;
                }
                return false;
            });

            int assignedCount = 0;
            Set<Integer> assignedTaskIds = new HashSet<>();
            Set<Integer> usedTruckIds = new HashSet<>();

            // 3. 为每个空闲车辆分配任务
            for (Truck truck : idleTrucks) {
                Task matchedTask = null;

                for (Task task : unassignedTasks) {
                    if (assignedTaskIds.contains(task.getId())) {
                        continue;
                    }

                    // 检查车辆能否装载此任务
                    BigDecimal taskWeight = task.getWeight() != null ? task.getWeight() : BigDecimal.ZERO;
                    BigDecimal taskVolume = task.getVolume() != null ? task.getVolume() : BigDecimal.ZERO;

                    if (truck.canLoad(taskWeight, taskVolume)) {
                        matchedTask = task;
                        break;
                    }
                }

                if (matchedTask != null) {
                    // 司机行为状态：候选司机按概率拒单（基准 8%，A2 驾照减半更稳定），拒单后进入 9 秒冷却，任务留待下一轮
                    if (driverRejects(truck, matchedTask)) {
                        continue;
                    }
                    assignedTaskIds.add(matchedTask.getId());
                    try {
                        processSingleTaskAssignment(truck, matchedTask);
                        assignedCount++;
                        usedTruckIds.add(truck.getId());
                        // 记录匹配质量（以条件最优车为基准答案），供匹配精准度指标使用
                        logMatchQuality(matchedTask, truck, idleTrucks, usedTruckIds);
                        // 司机行为状态：为本次运输绑定一名空闲司机（空闲 → 驾驶中）；
                        // 先检查该车辆是否已有"驾驶中"司机，避免一车多绑定的累积
                        try {
                            Integer bound = jdbcTemplate.queryForObject(
                                    "SELECT COUNT(*) FROM driver WHERE truck_id=? AND status='驾驶中'",
                                    Integer.class, truck.getId());
                            if (bound == null || bound == 0) {
                                jdbcTemplate.update(
                                        "UPDATE driver SET status='驾驶中', truck_id=? WHERE status='空闲' AND truck_id IS NULL LIMIT 1",
                                        truck.getId());
                            }
                        } catch (Exception e) {
                            log.warn("绑定驾驶司机失败: {}", e.getMessage());
                        }
                        log.info("车辆{}分配任务{}成功", truck.getId(), matchedTask.getId());
                    } catch (Exception e) {
                        log.error("车辆{}分配任务{}失败: {}", truck.getId(), matchedTask.getId(), e.getMessage());
                        assignedTaskIds.remove(matchedTask.getId());
                    }
                }
            }

            lastAssignCostMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("本轮分配完成：成功分配{}个任务", assignedCount);
        }
    }

    /**
     * 匹配质量插桩：以"条件最优车"（本轮尚未被占用的空闲车中，可装载且空驶里程最小者）为基准答案，
     * 记录本次派单的空驶里程、最优空驶、候选排名与是否最优（M 匹配精准度指标数据源）。
     */
    private void logMatchQuality(Task task, Truck chosen, List<Truck> idleTrucks, Set<Integer> usedTruckIds) {
        try {
            if (task.getStart() == null || !task.getStart().contains(",")) return;
            double taskLat = DistanceUtils.parseLatitude(task.getStart());
            double taskLon = DistanceUtils.parseLongitude(task.getStart());

            double chosenKm = Double.MAX_VALUE;
            double bestKm = Double.MAX_VALUE;
            int bestTruckId = -1;
            List<double[]> ranked = new ArrayList<>(); // {truckId, emptyKm}
            for (Truck t : idleTrucks) {
                if (t.getLocation() == null || !t.getLocation().contains(",")) continue;
                BigDecimal taskWeight = task.getWeight() != null ? task.getWeight() : BigDecimal.ZERO;
                BigDecimal taskVolume = task.getVolume() != null ? task.getVolume() : BigDecimal.ZERO;
                if (!t.canLoad(taskWeight, taskVolume)) continue;
                double km = DistanceUtils.calculateDistance(
                        DistanceUtils.parseLatitude(t.getLocation()), DistanceUtils.parseLongitude(t.getLocation()),
                        taskLat, taskLon);
                ranked.add(new double[]{t.getId(), km});
                if (t.getId().equals(chosen.getId())) chosenKm = km;
                if (km < bestKm) { bestKm = km; bestTruckId = t.getId(); }
            }
            if (ranked.isEmpty() || chosenKm == Double.MAX_VALUE) return;

            ranked.sort(Comparator.comparingDouble(a -> a[1]));
            int rank = 1;
            for (double[] r : ranked) {
                if ((int) r[0] == chosen.getId()) break;
                rank++;
            }
            boolean isOptimal = chosen.getId() == bestTruckId;

            metricsJdbcTemplate.update(
                    "INSERT INTO match_log(task_id, truck_id, chosen_empty_km, optimal_empty_km, candidate_count, rank_no, is_optimal) VALUES (?,?,?,?,?,?,?)",
                    task.getId(), chosen.getId(), chosenKm, bestKm, ranked.size(), rank, isOptimal);
        } catch (Exception e) {
            log.warn("记录匹配质量失败(任务{}): {}", task.getId(), e.getMessage());
        }
    }

    /**
     * 任务完成时按路径点拆分空驶/载重里程（sequence<0 为取货空驶段，其余为载重段），
     * 并写入完成时间（U 资源利用 / C 成本 / G 碳排指标数据源）。
     */
    private void recordTaskDistance(Integer taskId) {
        try {
            if (taskId == null) return;
            List<TaskRoutePoint> pts = taskRoutePointMapper.selectByTaskId(taskId);
            if (pts == null || pts.isEmpty()) return;
            pts.sort(Comparator.comparingInt(TaskRoutePoint::getSequence));
            double emptyKm = 0, loadedKm = 0;
            for (int i = 0; i < pts.size() - 1; i++) {
                TaskRoutePoint a = pts.get(i), b = pts.get(i + 1);
                if (a.getPointLocation() == null || b.getPointLocation() == null) continue;
                double d = DistanceUtils.calculateDistance(
                        DistanceUtils.parseLatitude(a.getPointLocation()), DistanceUtils.parseLongitude(a.getPointLocation()),
                        DistanceUtils.parseLatitude(b.getPointLocation()), DistanceUtils.parseLongitude(b.getPointLocation()));
                if (b.getSequence() != null && b.getSequence() <= 0) emptyKm += d;
                else loadedKm += d;
            }
            final double e = emptyKm, l = loadedKm;
            metricsJdbcTemplate.update("UPDATE task SET empty_km = ?, loaded_km = ?, complete_time = NOW() WHERE id = ?", e, l, taskId);
        } catch (Exception e) {
            log.warn("记录任务{}里程数据失败: {}", taskId, e.getMessage());
        }
    }

    // 可选：服务启动时立即同步一次（避免等待第一个定时周期）
    // @PostConstruct
    // public void initSync() {
    //     try {
    //         syncUnassignedTasksFromDb();
    //     } catch (Exception e) {
    //         log.error("启动时同步任务失败", e);
    //     }
    // }


    /**
     * 判断任务是否为蝇头小利（货少里程长）
     * 价值密度 = 货物重量 / 运输里程（公斤/公里）
     * 如果价值密度低于阈值，则认为是蝇头小利
     */
    private boolean isNotWorthwhile(Task task) {
        // 获取任务重量
        double weight = 0;
        if (task.getWeight() != null) {
            weight = task.getWeight().doubleValue();
            log.debug("任务{} weight对象: {}, double值: {}", task.getId(), task.getWeight(), weight);
        } else {
            log.debug("任务{} weight字段为null", task.getId());
        }
        
        // 计算运输里程（从起点到终点的距离）
        String start = task.getStart();
        String destination = task.getDestination();
        double distance = 0;
        if (start != null && destination != null && !start.equals(destination)) {
            double startLat = DistanceUtils.parseLatitude(start);
            double startLon = DistanceUtils.parseLongitude(start);
            double destLat = DistanceUtils.parseLatitude(destination);
            double destLon = DistanceUtils.parseLongitude(destination);
            distance = DistanceUtils.calculateDistance(startLat, startLon, destLat, destLon);
        }
        
        // 如果距离为0，不算蝇头小利
        if (distance <= 0) {
            return false;
        }
        
        // 重量单位是吨，转换为公斤
        double weightInKg = weight * 1000;
        
        // 计算重量密度（公斤/公里）
        double weightDensity = weightInKg / distance;
        
        // 设置阈值：每公里至少运输0.1公斤才值得（可配置）
        double threshold = 0.1;
        
        boolean notWorthwhile = weightDensity < threshold;
        if (notWorthwhile) {
            log.info("任务{}被判定为蝇头小利：重量={}吨(={}kg), 里程={}km, 密度={}kg/km（阈值={}kg/km）", 
                task.getId(), weight, weightInKg, distance, String.format("%.2f", weightDensity), threshold);
        }
        
        return notWorthwhile;
    }

    /**
     * 一车多货任务分配方法
     * 实现逻辑：
     * 1. 过滤蝇头小利任务
     * 2. 为每个车辆分配多个任务（基于剩余容量）
     * 3. 实现先装后卸的装载顺序（使用栈结构）
     */
    private final Object assignLock = new Object();
    
    public void assignTasks() {
        try {
            synchronized (assignLock) {
                // 使用临时列表避免死循环
                List<Task> tasksToProcess = new ArrayList<>(pendingTasks);
                pendingTasks.clear();

                // 1. 过滤蝇头小利任务
                List<Task> worthwhileTasks = tasksToProcess.stream()
                        .filter(task -> !isNotWorthwhile(task))
                        .collect(Collectors.toList());
                
                List<Task> rejectedTasks = tasksToProcess.stream()
                        .filter(this::isNotWorthwhile)
                        .collect(Collectors.toList());
                
                log.info("本轮任务分配：总数={}, 值得运输={}, 蝇头小利={}", 
                    tasksToProcess.size(), worthwhileTasks.size(), rejectedTasks.size());

                // 2. 获取所有空闲车辆
                List<Truck> idleTrucks = truckMapper.selectByStatus(TruckStatusConstant.IDLE);
                log.info("可用空闲车辆数: {}", idleTrucks.size());

                // 3. 为所有车辆分配任务
                int assignedTruckCount = 0;
                Set<Integer> globalAssignedTaskIds = new HashSet<>();
                for (Truck truck : idleTrucks) {
                    Task assignedTask = null;
                    for (Task task : worthwhileTasks) {
                        if (globalAssignedTaskIds.contains(task.getId())) {
                            continue;
                        }
                        
                        if (!isTruckMatchTask(truck, task)) {
                            continue;
                        }
                        
                        BigDecimal taskWeight = task.getWeight() != null ? task.getWeight() : BigDecimal.ZERO;
                        BigDecimal taskVolume = task.getVolume() != null ? task.getVolume() : BigDecimal.ZERO;
                        
                        if (truck.canLoad(taskWeight, taskVolume)) {
                            assignedTask = task;
                            globalAssignedTaskIds.add(task.getId());
                            truck.load(taskWeight, taskVolume);
                            break;
                        }
                    }
                    
                    if (assignedTask != null) {
                        log.info("车辆{}装载任务{}", truck.getId(), assignedTask.getId());
                        truck.getTaskStack().push(assignedTask.getId());
                        truck.setTaskId(assignedTask.getId());
                        
                        try {
                            processSingleTaskAssignment(truck, assignedTask);
                            assignedTruckCount++;
                        } catch (Exception e) {
                            log.error("处理车辆{}任务分配失败: {}", truck.getId(), e.getMessage(), e);
                            truck.unload(assignedTask.getWeight() != null ? assignedTask.getWeight() : BigDecimal.ZERO, 
                                      assignedTask.getVolume() != null ? assignedTask.getVolume() : BigDecimal.ZERO);
                            globalAssignedTaskIds.remove(assignedTask.getId());
                            truck.setTaskId(null);
                        }
                    }
                }

                // 4. 处理未分配的任务（放回队列）
                for (Task task : worthwhileTasks) {
                    if (!task.getStatus().equals(TaskStatusConstant.IN_TRANSIT) && task.getTruckId() == null) {
                        pendingTasks.offer(task);
                    }
                }
                
                // 5. 蝇头小利任务也放回队列
                for (Task task : rejectedTasks) {
                    pendingTasks.offer(task);
                }
                
                if (!pendingTasks.isEmpty()) {
                    log.info("本轮分配结束，剩余{}个任务等待分配车辆", pendingTasks.size());
                }
            }
        } catch (Exception e) {
            log.error("任务分配失败", e);
        }
    }

    /**
     * 处理单任务分配：规划路线并更新状态（简化版）
     */
    private void processSingleTaskAssignment(Truck truck, Task task) {
        String truckLocation = truck.getLocation();
        String taskStart = task.getStart();
        String taskDestination = task.getDestination();
        
        List<TaskRoutePoint> allRoutePoints = new ArrayList<>();
        int maxTotalPoints = 15000;

        log.info("开始规划车辆{}任务{}路线：起点={}, 终点={}", truck.getId(), task.getId(), taskStart, taskDestination);

        String currentLocation = truckLocation;
        int pickupSequence = -1;
        
        // 1. 规划运输路线（使用正数sequence，优先分配更多路径点）
        int deliverySequence = 0;
        if (!taskStart.equals(taskDestination)) {
            TruckRoute deliveryRoute = amapTruckRouteService.getTruckRoute(taskStart, taskDestination, truck);
            if (deliveryRoute != null && deliveryRoute.isSuccess()) {
                for (Point p : deliveryRoute.getPathPoints()) {
                    if (deliverySequence >= maxTotalPoints - 1000) break;
                    allRoutePoints.add(new TaskRoutePoint(
                            task.getId(),
                            p.getLocation(),
                            "运输-" + task.getId(),
                            deliverySequence++
                    ));
                }
            } else {
                log.warn("运输路线规划失败: {}", deliveryRoute != null ? deliveryRoute.getErrorMsg() : "null");
            }
        }
        
        if (deliverySequence < maxTotalPoints - 1000) {
            allRoutePoints.add(new TaskRoutePoint(
                    task.getId(),
                    taskDestination,
                    "卸货完成-" + task.getId(),
                    deliverySequence++
            ));
        }

        // 2. 规划取货路线（使用负数sequence，使用剩余路径点）
        if (!currentLocation.equals(taskStart)) {
            TruckRoute pickupRoute = amapTruckRouteService.getTruckRoute(currentLocation, taskStart, truck);
            if (pickupRoute != null && pickupRoute.isSuccess()) {
                for (Point p : pickupRoute.getPathPoints()) {
                    if (Math.abs(pickupSequence) >= 1000) break;
                    allRoutePoints.add(new TaskRoutePoint(
                            task.getId(),
                            p.getLocation(),
                            "取货-" + task.getId(),
                            pickupSequence--
                    ));
                }
            } else {
                log.warn("取货路线规划失败: {}", pickupRoute != null ? pickupRoute.getErrorMsg() : "null");
            }
        }
        
        if (Math.abs(pickupSequence) < 1000) {
            allRoutePoints.add(new TaskRoutePoint(
                    task.getId(),
                    taskStart,
                    "取货完成-" + task.getId(),
                    pickupSequence--
            ));
        }

        log.info("车辆{}任务{}路线规划完成，路径点数量: {}", truck.getId(), task.getId(), allRoutePoints.size());

        // 保存当前sequence值供lambda使用（必须是最终变量）
        final int finalPickupSequence = Math.abs(pickupSequence) - 1;

        // 3. 使用编程式事务提交所有数据库操作
        transactionTemplate.execute(status -> {
            // 删除该车辆之前所有任务的路径点
            List<Task> existingTasks = taskMapper.selectTasksByTruckId(truck.getId());
            for (Task existingTask : existingTasks) {
                taskRoutePointMapper.deleteByTaskId(existingTask.getId());
            }
            
            // 更新任务状态
            task.setTruckId(truck.getId());
            task.setStatus(TaskStatusConstant.IN_TRANSIT);
            taskMapper.updateTaskTruckId(task);
            taskMapper.updateStatus(task);
            
            if (task.getGoodsId() != null) {
                try {
                    goodsService.assignGoodsToTask(task.getGoodsId(), task.getId(), truck.getId());
                } catch (Exception e) {
                    log.error("货物分配记录失败: {}", e.getMessage());
                }
            }
            
            // 保存路径点
            if (!allRoutePoints.isEmpty()) {
                taskRoutePointMapper.batchInsert(allRoutePoints);
            }
            
            // 更新车辆状态
            truck.setStatus(TruckStatusConstant.PICKUP);
            truck.setCurrentPointSequence(finalPickupSequence);
            truckMapper.updateByPrimaryKey(truck);
            
            log.info("车辆{}任务{}数据库操作完成", truck.getId(), task.getId());
            return null;
        });
    }

    /**
     * 处理多任务分配：规划路线并更新状态
     */
    private void processMultiTaskAssignment(Truck truck, Stack<Task> loadStack, List<Task> assignedTasks) {
        String truckLocation = truck.getLocation();
        
        List<TaskRoutePoint> allRoutePoints = new ArrayList<>();
        int maxTotalPoints = 5000;

        // 1. 规划取货路线（使用负数sequence）
        String currentLocation = truckLocation;
        List<Task> loadingOrder = new ArrayList<>(loadStack);
        int pickupSequence = -1;
        
        for (int i = 0; i < loadingOrder.size(); i++) {
            if (Math.abs(pickupSequence) >= maxTotalPoints / 2) break;
            
            Task task = loadingOrder.get(i);
            String taskStart = task.getStart();
            
            if (!currentLocation.equals(taskStart)) {
                TruckRoute pickupRoute = amapTruckRouteService.getTruckRoute(currentLocation, taskStart, truck);
                if (pickupRoute != null && pickupRoute.isSuccess()) {
                    for (Point p : pickupRoute.getPathPoints()) {
                        if (Math.abs(pickupSequence) >= maxTotalPoints / 2) break;
                        allRoutePoints.add(new TaskRoutePoint(
                                task.getId(),
                                p.getLocation(),
                                "取货-" + task.getId() + "-" + (i + 1),
                                pickupSequence--
                        ));
                    }
                    currentLocation = taskStart;
                }
            }
            
            if (Math.abs(pickupSequence) < maxTotalPoints / 2) {
                allRoutePoints.add(new TaskRoutePoint(
                        task.getId(),
                        taskStart,
                        "取货完成-" + task.getId(),
                        pickupSequence--
                ));
            }
        }

        // 2. 规划运输路线（使用正数sequence，从0开始）
        Stack<Task> unloadStack = (Stack<Task>) loadStack.clone();
        int deliverySequence = 0;
        while (!unloadStack.isEmpty()) {
            if (deliverySequence >= maxTotalPoints / 2) break;
            
            Task task = unloadStack.pop();
            String taskDestination = task.getDestination();
            
            if (!currentLocation.equals(taskDestination)) {
                TruckRoute deliveryRoute = amapTruckRouteService.getTruckRoute(currentLocation, taskDestination, truck);
                if (deliveryRoute != null && deliveryRoute.isSuccess()) {
                    for (Point p : deliveryRoute.getPathPoints()) {
                        if (deliverySequence >= maxTotalPoints / 2) break;
                        allRoutePoints.add(new TaskRoutePoint(
                                task.getId(),
                                p.getLocation(),
                                "运输-" + task.getId(),
                                deliverySequence++
                        ));
                    }
                    currentLocation = taskDestination;
                }
            }
            
            if (deliverySequence < maxTotalPoints / 2) {
                allRoutePoints.add(new TaskRoutePoint(
                        task.getId(),
                        taskDestination,
                        "卸货完成-" + task.getId(),
                        deliverySequence++
                ));
            }
        }

        // 3. 使用编程式事务提交所有数据库操作
        transactionTemplate.execute(status -> {
            // 删除该车辆之前所有任务的路径点，防止重复插入
            List<Task> existingTasks = taskMapper.selectTasksByTruckId(truck.getId());
            for (Task existingTask : existingTasks) {
                taskRoutePointMapper.deleteByTaskId(existingTask.getId());
            }
            
            // 更新所有任务状态
            for (Task task : assignedTasks) {
                task.setTruckId(truck.getId());
                task.setStatus(TaskStatusConstant.IN_TRANSIT);
                taskMapper.updateTaskTruckId(task);
                taskMapper.updateStatus(task);
                
                if (task.getGoodsId() != null) {
                    try {
                        goodsService.assignGoodsToTask(task.getGoodsId(), task.getId(), truck.getId());
                    } catch (Exception e) {
                        log.error("货物分配记录失败: {}", e.getMessage());
                    }
                }
            }
            
            // 批量保存路径点
            if (!allRoutePoints.isEmpty()) {
                taskRoutePointMapper.batchInsert(allRoutePoints);
            }
            
            // 更新车辆状态
            truck.setStatus(TruckStatusConstant.PICKUP);
            truckMapper.updateByPrimaryKey(truck);
            
            return null;
        });

        // 4. 初始化车辆移动状态（非数据库操作）
        truckRouteCache.put(truck.getId(), allRoutePoints);
        truckMovementStartTime.put(truck.getId(), System.currentTimeMillis());
        truckTotalDistance.put(truck.getId(), calculateTotalRouteDistance(allRoutePoints));

        // 5. 推送事件给前端
        broadcastTruckEvent(truck, "MULTI_TASK_ASSIGNED", allRoutePoints);
        log.info("车辆{}多任务分配完成，共{}个任务，路径点{}个", 
            truck.getId(), assignedTasks.size(), allRoutePoints.size());
    }

    // 检验车辆与任务是否匹配（支持一车多货）
    private boolean isTruckMatchTask(Truck truck, Task task) {
        String truckType = truck.getType() != null ? truck.getType().toString() : "";
        String goodsType = task.getGoodsType() != null ? task.getGoodsType().toString() : "";
        
        log.debug("车辆{}类型: '{}', 任务{}货物类型: '{}'", truck.getId(), truckType, task.getId(), goodsType);
        
        // 1. 类型匹配检查（放宽规则，优先保证任务能分配出去）
        boolean typeMatch = 
                // 特殊货物需要专用车辆
                (truckType.equals(TruckTypeConstant.REFRIGERATED_TRUCK) && goodsType.equals(GoodsTypeConstant.PERISHABLE_GOODS)) ||
                (truckType.equals(TruckTypeConstant.DANGEROUS_GOODS_TRUCK) && goodsType.equals(GoodsTypeConstant.DANGEROUS_GOODS)) ||
                (truckType.equals(TruckTypeConstant.TANK_TRUCK) && goodsType.equals(GoodsTypeConstant.LIQUID_POWDERS)) ||
                // 普通货物：几乎所有车辆都可以运输
                (goodsType.equals(GoodsTypeConstant.GENERAL_GOODS) && 
                 !truckType.equals(TruckTypeConstant.DANGEROUS_GOODS_TRUCK) && 
                 !truckType.equals(TruckTypeConstant.TANK_TRUCK)) ||
                // 大宗货物：板车、厢式车、自卸车、集装箱货车
                (truckType.equals(TruckTypeConstant.PALLET_TRUCK) && (goodsType.equals(GoodsTypeConstant.BULK_HEAVY_GOODS) || goodsType.equals(GoodsTypeConstant.BULK_GOODS))) ||
                (truckType.equals(TruckTypeConstant.VAN) && (goodsType.equals(GoodsTypeConstant.GOODS_REQUIREING_VENTILATION) || goodsType.equals(GoodsTypeConstant.BULK_GOODS))) ||
                (truckType.equals(TruckTypeConstant.WAREHOUSE_TRUCK) && (goodsType.equals(GoodsTypeConstant.GOODS_REQUIREING_VENTILATION) || goodsType.equals(GoodsTypeConstant.BULK_GOODS))) ||
                (truckType.equals(TruckTypeConstant.DUMP_TRUCK) && (goodsType.equals(GoodsTypeConstant.BULK_HEAVY_GOODS) || goodsType.equals(GoodsTypeConstant.BULK_GOODS))) ||
                (truckType.equals(TruckTypeConstant.CONTAINER_TRUCK) && (goodsType.equals(GoodsTypeConstant.BULK_GOODS) || goodsType.equals(GoodsTypeConstant.BULK_HEAVY_GOODS))) ||
                // 默认允许匹配（如果类型不匹配以上规则，允许装载）
                true;

        // 2. 重量和体积匹配（一车多货场景：检查剩余容量是否足够）
        BigDecimal taskWeight = task.getWeight() != null ? task.getWeight() : BigDecimal.ZERO;
        BigDecimal taskVolume = task.getVolume() != null ? task.getVolume() : BigDecimal.ZERO;
        
        // 首先检查单个任务是否超过车辆最大容量（防止单个任务超重/超体积）
        boolean singleTaskWeightMatch = truck.getMaxWeight().compareTo(taskWeight) >= 0;
        boolean singleTaskVolumeMatch = truck.getMaxVol().compareTo(taskVolume) >= 0;
        
        // 然后检查车辆剩余容量是否足够装载此任务
        boolean weightMatch = singleTaskWeightMatch && truck.canLoad(taskWeight, BigDecimal.ZERO);
        boolean volumeMatch = singleTaskVolumeMatch && truck.canLoad(BigDecimal.ZERO, taskVolume);

        // 3. 所有条件满足才匹配
        boolean match = typeMatch && weightMatch && volumeMatch;
        if (!match) {
            log.debug("车辆{}类型{}与任务{}货物类型{}不匹配，剩余载重{} vs 重量{}，剩余容积{} vs 体积{}", 
                truck.getId(), truckType, task.getId(), goodsType, 
                truck.getRemainingWeight(), taskWeight, truck.getRemainingVolume(), taskVolume);
        }
        return match;
    }


    // 每30秒尝试分配一次任务，已在syncUnassignedTasksFromDb中执行
/*    @Scheduled(fixedRate = 30000)
    public void scheduleTaskAssignment() {
        assignTasks();
    }*/






    private final Map<Integer, Long> truckMovementStartTime = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Integer, Double> truckTotalDistance = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Integer, List<TaskRoutePoint>> truckRouteCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Scheduled(fixedRate = 200)
    public void simulateMovement() {
        if (!isSimulationRunning) {
            return;
        }

        List<Truck> trucks = truckMapper.selectAll();
        Random random = new Random();
        
        log.debug("开始执行车辆移动仿真，当前车辆总数：{}", trucks.size());

        for (Truck truck : trucks) {
            String status = truck.getStatus().toString();
            Integer truckId = truck.getId();
            
            Task currentTask = taskMapper.selectByTruckId(truckId);
            if (currentTask != null) {
                truck.setTaskId(currentTask.getId());
            } else {
                truck.setTaskId(null);
            }

            if (truckWaitCounters.containsKey(truckId)) {
                int remaining = truckWaitCounters.get(truckId);
                remaining--;
                
                if (remaining > 0) {
                    truckWaitCounters.put(truckId, remaining);
                    broadcastTruckEvent(truck, "WAITING", null);
                } else {
                    truckWaitCounters.remove(truckId);
                    
                    if (TruckStatusConstant.LOADING.equals(status)) {
                        truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                        truckMapper.updateByPrimaryKey(truck);
                        
                        if (currentTask != null && currentTask.getGoodsId() != null) {
                            try {
                                goodsService.loadGoods(currentTask.getGoodsId());
                            } catch (Exception e) {
                                log.error("货物装载记录失败: {}", e.getMessage());
                            }
                        }
                        
                        List<TaskRoutePoint> routePoints = taskRoutePointMapper.selectByTaskId(truck.getTaskId());
                        if (!routePoints.isEmpty()) {
                            routePoints.sort(Comparator.comparingInt(TaskRoutePoint::getSequence));
                            truckRouteCache.put(truckId, routePoints);
                            truckMovementStartTime.put(truckId, System.currentTimeMillis());
                            truckTotalDistance.put(truckId, calculateTotalRouteDistance(routePoints));
                            
                            // 找到运输路线的起点（sequence=0），而不是取货路线的起点
                            TaskRoutePoint startPoint = routePoints.stream()
                                    .filter(p -> p.getSequence() == 0)
                                    .findFirst()
                                    .orElse(routePoints.get(0));
                            truck.setLocation(startPoint.getPointLocation());
                            truckMapper.updateByPrimaryKey(truck);
                            
                            broadcastTruckEvent(truck, "DEPARTURE", routePoints);
                            log.info("车辆 {} 装货完成，开始运输，起点: {}", truckId, startPoint.getPointLocation());
                        } else {
                            log.warn("车辆 {} 装货完成但无路径点，跳过运输", truckId);
                        }
                    } else if (TruckStatusConstant.UNLOADING.equals(status)) {
                        if (truck.getTaskId() != null) {
                            Task task = new Task();
                            task.setId(truck.getTaskId());
                            task.setStatus(TaskStatusConstant.COMPLETED);
                            taskMapper.updateStatus(task);
                            log.info("任务{}已完成", truck.getTaskId());

                            // 完成时记录空驶/载重里程拆分与完成时间（指标体系数据源）
                            recordTaskDistance(truck.getTaskId());

                            // 删除已完成任务的路径点
                            try {
                                taskRoutePointMapper.deleteByTaskId(truck.getTaskId());
                                log.info("已删除任务{}的路径点数据", truck.getTaskId());
                            } catch (Exception e) {
                                log.error("删除任务路径点失败: {}", e.getMessage());
                            }
                            
                            if (currentTask != null && currentTask.getGoodsId() != null) {
                                try {
                                    goodsService.deliverGoods(currentTask.getGoodsId());
                                    goodsService.completeGoods(currentTask.getGoodsId());
                                } catch (Exception e) {
                                    log.error("货物送达记录失败: {}", e.getMessage());
                                }
                            }
                            
                            // 从车辆任务栈中移除已完成的任务
                            truck.completeCurrentTask();
                            
                            // 如果还有更多任务，继续处理下一个
                            if (truck.hasMoreTasks()) {
                                Integer nextTaskId = truck.peekNextTask();
                                Task nextTask = taskMapper.selectByPrimaryKey(nextTaskId);
                                if (nextTask != null) {
                                    // 更新车辆当前任务
                                    truck.setTaskId(nextTaskId);
                                    
                                    // 卸载当前货物重量和体积
                                    if (currentTask != null) {
                                        BigDecimal taskWeight = currentTask.getWeight() != null ? currentTask.getWeight() : BigDecimal.ZERO;
                                        BigDecimal taskVolume = currentTask.getVolume() != null ? currentTask.getVolume() : BigDecimal.ZERO;
                                        truck.unload(taskWeight, taskVolume);
                                    }
                                    
                                    log.info("车辆{}继续处理下一个任务{}，任务栈剩余{}个任务", 
                                        truckId, nextTaskId, truck.getTaskStack().size());
                                }
                            }
                        }
                        
                        // 检查是否还有更多任务
                        if (truck.hasMoreTasks()) {
                            // 还有任务，继续运输
                            // 重新获取路线点（可能需要重新规划）
                            List<TaskRoutePoint> remainingRoutePoints = truckRouteCache.get(truckId);
                            if (remainingRoutePoints != null && !remainingRoutePoints.isEmpty()) {
                                // 继续使用当前路线
                                truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                                truckMapper.updateByPrimaryKey(truck);
                                log.info("车辆{}继续运输，剩余{}个任务", truckId, truck.getTaskStack().size());
                            } else {
                                // 路线点已用完，需要重新规划
                                truck.setStatus(TruckStatusConstant.IDLE);
                                truckMapper.updateByPrimaryKey(truck);
                                log.warn("车辆{}任务栈非空但无路线点，重置为空闲状态", truckId);
                            }
                        } else {
                            // 没有更多任务，恢复空闲
                            truck.setStatus(TruckStatusConstant.IDLE);
                            truck.setCurrentPointSequence(null);
                            truck.setTaskId(null);
                            truckMapper.updateByPrimaryKey(truck);
                            
                            truckRouteCache.remove(truckId);
                            truckMovementStartTime.remove(truckId);
                            truckTotalDistance.remove(truckId);
                            
                            broadcastTruckEvent(truck, "ARRIVED", null);
                            log.info("车辆 {} 所有任务完成，恢复空闲", truckId);
                        }
                        continue;
                    } else if (TruckStatusConstant.TRAFFIC_JAM.equals(status)) {
                        truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                        truckMapper.updateByPrimaryKey(truck);
                        broadcastTruckEvent(truck, "JAM_CLEARED", null);
                        log.info("车辆 {} 拥堵结束，恢复运输", truckId);
                    }
                }
                continue;
            } else {
                if (TruckStatusConstant.TRAFFIC_JAM.equals(status)) {
                    truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                    truckMapper.updateByPrimaryKey(truck);
                } else if (TruckStatusConstant.LOADING.equals(status)) {
                    truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                    truckMapper.updateByPrimaryKey(truck);
                } else if (TruckStatusConstant.UNLOADING.equals(status)) {
                    Task task = taskMapper.selectByTruckId(truckId);
                    if (task != null) {
                        task.setStatus(TaskStatusConstant.COMPLETED);
                        taskMapper.updateStatus(task);
                    }
                    truck.setStatus(TruckStatusConstant.IDLE);
                    truck.setCurrentPointSequence(null);
                    truck.setTaskId(null);
                    truckMapper.updateByPrimaryKey(truck);
                    continue;
                }
            }

            if (TruckStatusConstant.IN_TRANSIT.equals(status)) {
                if (random.nextDouble() < 0.02) {
                    truck.setStatus(TruckStatusConstant.TRAFFIC_JAM);
                    truckWaitCounters.put(truckId, 2);
                    truckMapper.updateByPrimaryKey(truck);
                    
                    AlertLog alertLog = new AlertLog();
                    alertLog.setTruckId(truckId);
                    alertLog.setTaskId(truck.getTaskId());
                    alertLog.setType("拥堵");
                    alertLog.setLevel("一般");
                    alertLog.setLocation(truck.getLocation());
                    alertLog.setCreateTime(new Date());
                    alertLog.setIsHandled(false);
                    alertLogMapper.insert(alertLog);
                    
                    broadcastTruckEvent(truck, "TRAFFIC_JAM", null);
                    log.info("车辆 {} 遭遇交通拥堵", truckId);
                    continue;
                }

                Task task = taskMapper.selectByTruckId(truck.getId());
                if (task == null) {
                    truck.setStatus(TruckStatusConstant.IDLE);
                    truckMapper.updateByPrimaryKey(truck);
                    log.warn("车辆 {} 状态为运输中但无任务，重置为空闲", truckId);
                    continue;
                }

                List<TaskRoutePoint> routePoints = truckRouteCache.get(truckId);
                if (routePoints == null) {
                    routePoints = taskRoutePointMapper.selectByTaskId(task.getId());
                    if (routePoints.isEmpty()) {
                        handleEmptyRoutePoints(task, truck);
                        continue;
                    }
                    routePoints.sort(Comparator.comparingInt(TaskRoutePoint::getSequence));
                    truckRouteCache.put(truckId, routePoints);
                    truckMovementStartTime.put(truckId, System.currentTimeMillis());
                    truckTotalDistance.put(truckId, calculateTotalRouteDistance(routePoints));
                }

                Long startTime = truckMovementStartTime.get(truckId);
                Double totalDist = truckTotalDistance.get(truckId);
                
                if (startTime != null && totalDist != null && totalDist > 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    double speedKmh = 600.0 * simulationSpeed;
                    double speedMs = speedKmh * 1000 / 3600;
                    double traveledDistance = speedMs * (elapsed / 1000.0);
                    
                    double progress = Math.min(traveledDistance / (totalDist * 1000), 1.0);
                    
                    if (progress >= 1.0) {
                        TaskRoutePoint lastPoint = routePoints.get(routePoints.size() - 1);
                        truck.setLocation(lastPoint.getPointLocation());
                        truck.setStatus(TruckStatusConstant.UNLOADING);
                        truckWaitCounters.put(truckId, 3);
                        truckMapper.updateByPrimaryKey(truck);
                        
                        broadcastTruckEvent(truck, "UNLOADING_START", null);
                        log.info("车辆 {} 到达终点，开始卸货", truckId);
                    } else {
                        String interpolatedLocation = interpolatePosition(routePoints, progress);
                        truck.setLocation(interpolatedLocation);
                        
                        int currentIndex = (int) (progress * routePoints.size());
                        truck.setCurrentPointSequence(currentIndex);
                        
                        truckMapper.updateByPrimaryKey(truck);
                        
                        if (currentTask != null && currentTask.getGoodsId() != null) {
                            checkAndRecordGoodsLoss(currentTask.getGoodsId(), task, truck);
                        }
                        
                        broadcastTruckEvent(truck, "POSITION_UPDATE", null);
                    }
                }
            } else if (TruckStatusConstant.IDLE.equals(status)) {
                log.debug("车辆 {} 空闲中，等待分配任务", truckId);
            } else if (TruckStatusConstant.PICKUP.equals(status)) {
                // 处理前往取货点的车辆
                Task task = taskMapper.selectByTruckId(truck.getId());
                if (task == null) {
                    truck.setStatus(TruckStatusConstant.IDLE);
                    truckMapper.updateByPrimaryKey(truck);
                    log.warn("车辆 {} 状态为取货中但无任务，重置为空闲", truckId);
                    continue;
                }

                List<TaskRoutePoint> routePoints = truckRouteCache.get(truckId);
                if (routePoints == null) {
                    routePoints = taskRoutePointMapper.selectByTaskId(task.getId()).stream()
                            .filter(p -> p.getSequence() < 0)
                            .sorted(Comparator.comparingInt(TaskRoutePoint::getSequence))
                            .collect(Collectors.toList());
                    if (routePoints.isEmpty()) {
                        log.warn("车辆 {} 无取货路径点，直接开始装货", truckId);
                        truck.setStatus(TruckStatusConstant.LOADING);
                        truckWaitCounters.put(truckId, 3);
                        truckMapper.updateByPrimaryKey(truck);
                        continue;
                    }
                    truckRouteCache.put(truckId, routePoints);
                    truckMovementStartTime.put(truckId, System.currentTimeMillis());
                    truckTotalDistance.put(truckId, calculateTotalRouteDistance(routePoints));
                }

                Long startTime = truckMovementStartTime.get(truckId);
                Double totalDist = truckTotalDistance.get(truckId);
                
                if (startTime != null && totalDist != null && totalDist > 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    double speedKmh = 600.0 * simulationSpeed;
                    double speedMs = speedKmh * 1000 / 3600;
                    double traveledDistance = speedMs * (elapsed / 1000.0);
                    
                    double progress = Math.min(traveledDistance / (totalDist * 1000), 1.0);
                    
                    if (progress >= 1.0) {
                        // 到达取货点，开始装货
                        TaskRoutePoint lastPoint = routePoints.get(routePoints.size() - 1);
                        truck.setLocation(lastPoint.getPointLocation());
                        truck.setStatus(TruckStatusConstant.LOADING);
                        truckWaitCounters.put(truckId, 3);
                        
                        // 清除取货路径缓存，准备运输路径
                        truckRouteCache.remove(truckId);
                        truckMovementStartTime.remove(truckId);
                        truckTotalDistance.remove(truckId);
                        
                        truckMapper.updateByPrimaryKey(truck);
                        
                        broadcastTruckEvent(truck, "ARRIVED_PICKUP", null);
                        log.info("车辆 {} 到达取货点，开始装货", truckId);
                    } else {
                        String interpolatedLocation = interpolatePosition(routePoints, progress);
                        truck.setLocation(interpolatedLocation);
                        
                        // 计算当前路径点索引并保存（取货路径使用负数索引）
                        int currentIndex = (int) (progress * routePoints.size());
                        truck.setCurrentPointSequence(-currentIndex);
                        
                        truckMapper.updateByPrimaryKey(truck);
                        
                        broadcastTruckEvent(truck, "POSITION_UPDATE", null);
                    }
                }
            }
        }
    }
    
    private double calculateTotalRouteDistance(List<TaskRoutePoint> routePoints) {
        double totalDistance = 0;
        for (int i = 1; i < routePoints.size(); i++) {
            TaskRoutePoint p1 = routePoints.get(i - 1);
            TaskRoutePoint p2 = routePoints.get(i);
            
            // 验证路径点位置格式
            String loc1 = validateAndFixLocation(p1.getPointLocation());
            String loc2 = validateAndFixLocation(p2.getPointLocation());
            
            double dist = DistanceUtils.calculateDistance(
                DistanceUtils.parseLatitude(loc1), 
                DistanceUtils.parseLongitude(loc1),
                DistanceUtils.parseLatitude(loc2), 
                DistanceUtils.parseLongitude(loc2)
            );
            totalDistance += dist;
        }
        return totalDistance;
    }
    
    private String interpolatePosition(List<TaskRoutePoint> routePoints, double progress) {
        if (routePoints.isEmpty()) return "116.397428,39.90923";
        if (progress <= 0) return validateAndFixLocation(routePoints.get(0).getPointLocation());
        if (progress >= 1) return validateAndFixLocation(routePoints.get(routePoints.size() - 1).getPointLocation());
        
        double totalDistance = calculateTotalRouteDistance(routePoints);
        double targetDistance = totalDistance * progress;
        
        double accumulatedDistance = 0;
        for (int i = 1; i < routePoints.size(); i++) {
            TaskRoutePoint p1 = routePoints.get(i - 1);
            TaskRoutePoint p2 = routePoints.get(i);
            
            // 验证路径点位置格式
            String loc1 = validateAndFixLocation(p1.getPointLocation());
            String loc2 = validateAndFixLocation(p2.getPointLocation());
            
            double segmentDistance = DistanceUtils.calculateDistance(
                DistanceUtils.parseLatitude(loc1), 
                DistanceUtils.parseLongitude(loc1),
                DistanceUtils.parseLatitude(loc2), 
                DistanceUtils.parseLongitude(loc2)
            );
            
            if (accumulatedDistance + segmentDistance >= targetDistance) {
                double segmentProgress = (targetDistance - accumulatedDistance) / segmentDistance;
                
                double lat1 = DistanceUtils.parseLatitude(loc1);
                double lon1 = DistanceUtils.parseLongitude(loc1);
                double lat2 = DistanceUtils.parseLatitude(loc2);
                double lon2 = DistanceUtils.parseLongitude(loc2);
                
                double interpLat = lat1 + (lat2 - lat1) * segmentProgress;
                double interpLon = lon1 + (lon2 - lon1) * segmentProgress;
                
                return String.format("%.6f,%.6f", interpLon, interpLat);
            }
            
            accumulatedDistance += segmentDistance;
        }
        
        return validateAndFixLocation(routePoints.get(routePoints.size() - 1).getPointLocation());
    }
    
    private void broadcastTruckEvent(Truck truck, String eventType, List<TaskRoutePoint> routePoints) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("eventType", eventType);
            message.put("truckId", truck.getId());
            
            // 验证并修复位置格式（确保是 lng,lat 格式）
            String location = validateAndFixLocation(truck.getLocation());
            message.put("location", location);
            
            message.put("status", truck.getStatus().toString());
            message.put("taskId", truck.getTaskId());
            message.put("timestamp", System.currentTimeMillis());
            message.put("simulationSpeed", simulationSpeed);
            
            if (routePoints != null) {
                message.put("routePoints", routePoints);
                message.put("totalDistance", truckTotalDistance.get(truck.getId()));
                message.put("startTime", truckMovementStartTime.get(truck.getId()));
            }
            
            VehicleSimulationSocket.broadcast(new ObjectMapper().writeValueAsString(message));
        } catch (Exception e) {
            log.error("推送车辆事件失败", e);
        }
    }
    
    /**
     * 验证并修复位置格式，确保是 lng,lat 格式
     */
    private String validateAndFixLocation(String location) {
        if (location == null || !location.contains(",")) {
            return "116.4074,39.9042"; // 默认北京
        }
        
        double minLng = 73.0;
        double maxLng = 135.0;
        double minLat = 3.0;
        double maxLat = 54.0;
        
        try {
            String[] parts = location.split(",");
            if (parts.length == 2) {
                double part1 = Double.parseDouble(parts[0].trim());
                double part2 = Double.parseDouble(parts[1].trim());
                
                // 判断是否为 lng,lat 格式
                boolean isLngLat = (part1 >= minLng && part1 <= maxLng) && (part2 >= minLat && part2 <= maxLat);
                // 判断是否为 lat,lng 格式
                boolean isLatLng = (part1 >= minLat && part1 <= maxLat) && (part2 >= minLng && part2 <= maxLng);
                
                if (isLatLng && !isLngLat) {
                    // 需要翻转
                    return String.format("%.6f,%.6f", part2, part1);
                } else if (!isLngLat && !isLatLng) {
                    // 两种格式都不符合，重置到北京
                    log.warn("位置数据异常，重置到北京: {}", location);
                    return "116.4074,39.9042";
                }
                // 已经是正确格式，直接返回
                return location;
            }
        } catch (NumberFormatException e) {
            log.warn("位置数据解析失败，重置到北京: {}", location);
        }
        
        return "116.4074,39.9042";
    }

    //基于起始点实现路径选择
    private Route getOptimalRoute(String start, String destination,Truck truck) {
        TruckRoute amapRoute = amapTruckRouteService.getTruckRoute(start, destination, truck);
        if (amapRoute.isSuccess()) {
            // 3. 将高德返回的路径转换为项目中的Route实体
            Route route = new Route();
            route.setStart(start);
            route.setDestination(destination);
            route.setTimeCost(new BigDecimal(amapRoute.getDuration()));  // 时间成本（秒）
            route.setPassable("1");  // 标记为可通行
            // 可额外保存路径点到数据库（可选）
            return route;
        }else {
            // 4. API调用失败时的处理（仅依赖高德，不查数据库）
            log.error("高德货车路径规划失败：{}，起点：{}，终点：{}",
                    amapRoute.getErrorMsg(), start, destination);

            // 方案1：返回null，由调用方处理“无路径”场景
            return null;

            // 方案2：抛出异常，强制中断流程（适用于必须有路径才能继续的场景）
            // throw new RuntimeException("路径规划失败：" + amapRoute.getErrorMsg());
        }
    }

    // 处理路径点为空的异常情况
    private void handleEmptyRoutePoints(Task task, Truck truck) {
        log.error("任务{}路径点为空，标记为异常", task.getId());
        // 1. 标记任务为路线异常
        task.setStatus(TaskStatusConstant.TO_BE_ASSIGNED);
        taskMapper.updateStatus(task);

        // 2. 车辆恢复空闲
        truck.setStatus(TruckStatusConstant.IDLE);
        truck.setCurrentPointSequence(null);
        truckMapper.updateByPrimaryKey(truck);

        // 3. 解除任务与车辆关联
        task.setTruckId(null);
        taskMapper.updateTaskTruckId(task);
    }



    /**
     * 基于经纬度计算车辆的下一个位置（模拟向终点移动）
     * @param currentLoc 车辆当前位置（格式："纬度,经度"）
     * @param route 路径信息（包含终点经纬度）
     * @return 下一个位置（格式："纬度,经度"）
     */
    private String calculateNextLocation(String currentLoc, Route route) {
        // 1. 解析当前位置和终点的经纬度
        double currentLat = DistanceUtils.parseLatitude(currentLoc);
        double currentLon = DistanceUtils.parseLongitude(currentLoc);
        String destination = route.getDestination(); // 终点格式："纬度,经度"
        double destLat = DistanceUtils.parseLatitude(destination);
        double destLon = DistanceUtils.parseLongitude(destination);

        // 2. 计算当前位置到终点的经纬度总差值
        double latDiff = destLat - currentLat; // 纬度差值（正数表示向北移动，负数向南）
        double lonDiff = destLon - currentLon; // 经度差值（正数表示向东移动，负数向西）

        // 3. 定义移动步长
        // 计算当前位置到终点的总距离（公里）
        double totalDistance = DistanceUtils.calculateDistance(currentLat, currentLon, destLat, destLon);
        // 基础速度：60公里/小时，定时任务每1秒执行一次
        // 根据仿真速度倍数动态调整
        double step = calculateStep(60 * simulationSpeed, 1, totalDistance);

        // 4. 计算下一个位置的经纬度（当前位置 + 步长*总差值）
        double nextLat = currentLat + latDiff * step;
        double nextLon = currentLon + lonDiff * step;

        // 5. 边界处理：如果下一步超过终点，则直接到达终点
        // 判断是否已接近终点（步长内可到达）
        boolean isNearLat = Math.abs(nextLat - destLat) < Math.abs(latDiff * step);
        boolean isNearLon = Math.abs(nextLon - destLon) < Math.abs(lonDiff * step);
        if (isNearLat && isNearLon) {
            nextLat = destLat;
            nextLon = destLon;
        }

        // 6. 格式化经纬度为字符串（保留6位小数，精度约10厘米）
        // 修正：输出格式必须为 "经度,纬度" 以匹配高德API标准
        return String.format("%.6f,%.6f", nextLon, nextLat);
    }

    /**
     * 按速度和时间间隔计算步长（更真实的移动模拟）
     * @param speed 车辆速度（单位：公里/小时）
     * @param interval 模拟时间间隔（单位：秒，如定时任务每1秒执行一次）
     * @param totalDistance 总距离（公里，从当前位置到终点）
     * @return 步长（总距离的比例）
     */
    private double calculateStep(double speed, int interval, double totalDistance) {
        if (totalDistance <= 0) return 1.0; // 已到达终点
        // 计算在interval秒内车辆能行驶的距离（公里）
        double distancePerStep = speed * (interval / 3600.0);
        // 步长 = 每步行驶距离 / 总距离
        return Math.min(distancePerStep / totalDistance, 1.0); // 步长不超过1（避免超过终点）
    }

    private boolean isArrived(String currentLoc, String destination) {
        return currentLoc.contains(destination);
    }

    private void broadcastTruckStatus(List<Truck> trucks) {
        try {
            String json = new ObjectMapper().writeValueAsString(trucks);
            VehicleSimulationSocket.broadcast(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }






    // todo  暂时没用到
    // 车辆故障时重新分配任务
    public void handleTruckFailure(Integer truckId) {
        Truck truck = truckMapper.selectByPrimaryKey(truckId.longValue());
        truck.setStatus(TruckStatusConstant.MALFUNCTION);
        truckMapper.updateByPrimaryKey(truck);

        // 将原任务放回待分配队列
        Task task = taskMapper.selectByTruckId(truckId);
        if (task != null) {
            task.setTruckId(null);
            taskMapper.updateTaskTruckId(task);
            pendingTasks.offer(task);
        }
    }


    // todo 暂时没用到
    //更新车辆状态
    public void updateStatus(Integer truckId) {
        Truck truck = truckMapper.selectByPrimaryKey(truckId.longValue());
        if (truck == null) return;

        switch (truck.getStatus().toString()) {
            case TruckStatusConstant.LOADING:
                truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                break;
            case TruckStatusConstant.IN_TRANSIT:
                truck.setStatus(TruckStatusConstant.UNLOADING);
                break;
            case TruckStatusConstant.UNLOADING:
                truck.setStatus(TruckStatusConstant.IDLE);
                // 清除任务关联
                Task task = taskMapper.selectByTruckId(truckId);
                if (task != null) {
                    task.setTruckId(null);
                    taskMapper.updateTaskTruckId(task);
                }
                break;
            // TODO 其他状态转换...
        }
        truckMapper.updateByPrimaryKey(truck);
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate; // 注入JdbcTemplate

    /**
     * 随机生成车辆并保存到数据库
     * @param count 生成数量
     * @return 生成的车辆列表
     */
    public List<Truck> generateRandomVehicles(int count) {
        List<Truck> generatedTrucks = new ArrayList<>();
        Random random = new Random();
        
        // 车辆类型列表
        String[] truckTypes = {
            TruckTypeConstant.VAN,
            TruckTypeConstant.REFRIGERATED_TRUCK,
            TruckTypeConstant.DANGEROUS_GOODS_TRUCK,
            TruckTypeConstant.PALLET_TRUCK,
            TruckTypeConstant.WAREHOUSE_TRUCK,
            TruckTypeConstant.TANK_TRUCK,
            TruckTypeConstant.DUMP_TRUCK,
            TruckTypeConstant.CONTAINER_TRUCK
        };

        // 北京周边的经纬度范围
        double minLat = 39.80;
        double maxLat = 40.00;
        double minLon = 116.30;
        double maxLon = 116.50;

        for (int i = 0; i < count; i++) {
            Truck truck = new Truck();
            
            // 随机载重 (10-100吨)，覆盖更多任务需求
            truck.setMaxWeight(BigDecimal.valueOf(10 + random.nextInt(91)));
            
            // 随机体积 (20-150立方米)
            truck.setMaxVol(BigDecimal.valueOf(20 + random.nextInt(131)));
            
            // 状态默认为空闲
            truck.setStatus(TruckStatusConstant.IDLE);
            
            // 随机类型
            truck.setType(truckTypes[random.nextInt(truckTypes.length)]);
            
            // 随机位置
            double lat = minLat + (maxLat - minLat) * random.nextDouble();
            double lon = minLon + (maxLon - minLon) * random.nextDouble();
            // 修正：存储格式必须为 "经度,纬度"
            truck.setLocation(String.format("%.6f,%.6f", lon, lat));
            
            // 随机长度和自重 (简单模拟)
            truck.setLength(BigDecimal.valueOf(4.0 + random.nextDouble() * 10.0)); // 4-14米
            truck.setWeight(BigDecimal.valueOf(5.0 + random.nextDouble() * 10.0)); // 自重5-15吨
            
            // 插入数据库
            truckMapper.insertSelective(truck);
            generatedTrucks.add(truck);
            
            // 自动绑定空闲司机（简单策略：每生成一辆车，尝试绑定一个空闲司机）
            try {
                // 查询一个空闲司机
                String sql = "SELECT id FROM driver WHERE status = '空闲' AND truck_id IS NULL LIMIT 1";
                List<Integer> driverIds = jdbcTemplate.queryForList(sql, Integer.class);
                
                if (!driverIds.isEmpty()) {
                    Integer driverId = driverIds.get(0);
                    // 绑定司机和车辆
                    jdbcTemplate.update("UPDATE driver SET truck_id = ?, status = '驾驶中' WHERE id = ?", truck.getId(), driverId);
                    log.info("车辆 {} 已绑定司机 {}", truck.getId(), driverId);
                } else {
                    // 如果没有空闲司机，则自动创建一个新司机并绑定
                    String randomName = "司机" + System.currentTimeMillis() % 10000;
                    String randomPhone = "138" + String.format("%08d", random.nextInt(100000000));
                    jdbcTemplate.update("INSERT INTO driver (name, phone, license_type, status, truck_id) VALUES (?, ?, 'A2', '驾驶中', ?)", 
                            randomName, randomPhone, truck.getId());
                    log.info("车辆 {} 无空闲司机，已自动创建并绑定新司机", truck.getId());
                }
            } catch (Exception e) {
                log.error("自动绑定司机失败", e);
            }
        }
        
        log.info("已随机生成 {} 辆车", count);
        return generatedTrucks;
    }

    /**
     * 修复异常位置数据（确保所有车辆在中国境内）
     * 中国境内经纬度范围：经度 73-135，纬度 3-54
     */
    private void fixAbnormalLocations() {
        try {
            double minLng = 73.0;
            double maxLng = 135.0;
            double minLat = 3.0;
            double maxLat = 54.0;
            String defaultLocation = "116.4074,39.9042"; // 北京
            
            List<Truck> trucks = truckMapper.selectAll();
            int fixedCount = 0;
            
            for (Truck truck : trucks) {
                String location = truck.getLocation();
                if (location != null && location.contains(",")) {
                    String[] parts = location.split(",");
                    if (parts.length == 2) {
                        try {
                            double part1 = Double.parseDouble(parts[0].trim());
                            double part2 = Double.parseDouble(parts[1].trim());
                            
                            // 判断是否为 lng,lat 格式（经度在前）
                            boolean isLngLat = (part1 >= minLng && part1 <= maxLng) && (part2 >= minLat && part2 <= maxLat);
                            // 判断是否为 lat,lng 格式（纬度在前）
                            boolean isLatLng = (part1 >= minLat && part1 <= maxLat) && (part2 >= minLng && part2 <= maxLng);
                            
                            String newLocation = null;
                            
                            if (isLatLng && !isLngLat) {
                                // 需要翻转：lat,lng -> lng,lat
                                newLocation = String.format("%.6f,%.6f", part2, part1);
                            } else if (!isLngLat && !isLatLng) {
                                // 两种格式都不符合，说明位置在国外或数据错误，重置到北京
                                newLocation = defaultLocation;
                                log.warn("车辆 {} 位置数据异常（在国外），重置到北京: {}", truck.getId(), location);
                            }
                            
                            // 额外检查：即使格式正确，如果坐标不在中国境内，也重置
                            if (newLocation == null) {
                                double lng = isLngLat ? part1 : part2;
                                double lat = isLngLat ? part2 : part1;
                                if (lng < minLng || lng > maxLng || lat < minLat || lat > maxLat) {
                                    newLocation = defaultLocation;
                                    log.warn("车辆 {} 位置在中国境外，重置到北京: {}", truck.getId(), location);
                                }
                            }
                            
                            if (newLocation != null && !newLocation.equals(location)) {
                                truck.setLocation(newLocation);
                                truck.setCurrentPointSequence(0);
                                truckMapper.updateByPrimaryKey(truck);
                                log.info("修复车辆 {} 位置数据: {} -> {}", truck.getId(), location, newLocation);
                                fixedCount++;
                            }
                        } catch (NumberFormatException e) {
                            // 忽略解析错误
                        }
                    }
                }
            }
            
            if (fixedCount > 0) {
                log.info("共修复 {} 辆异常位置车辆", fixedCount);
            }
        } catch (Exception e) {
            log.error("修复车辆位置数据失败", e);
        }
    }

    @Autowired
    private com.muite.zongshe1.mapper.GoodsMapper goodsMapper;

    private void checkAndRecordGoodsLoss(Integer goodsId, Task task, Truck truck) {
        try {
            long currentTime = System.currentTimeMillis();
            Long lastCheck = lastLossCheckTime.get(goodsId);
            
            if (lastCheck != null && currentTime - lastCheck < 10000) {
                return;
            }
            
            lastLossCheckTime.put(goodsId, currentTime);
            
            double lossProbability = 0.15;
            if (random.nextDouble() < lossProbability) {
                Goods goods = goodsMapper.selectByPrimaryKey(goodsId);
                if (goods == null || goods.getWeight() == null) {
                    return;
                }
                
                String[] lossTypes = {"破损", "丢失", "变质", "受潮", "挤压"};
                String lossType = lossTypes[random.nextInt(lossTypes.length)];
                
                double lossPercentage = 1.0 + random.nextDouble() * 9.0;
                
                BigDecimal originalWeight = goods.getWeight();
                BigDecimal lossWeight = originalWeight.multiply(BigDecimal.valueOf(lossPercentage / 100.0));
                
                BigDecimal originalValue = goods.getValue();
                BigDecimal lossValue = originalValue != null 
                    ? originalValue.multiply(BigDecimal.valueOf(lossPercentage / 100.0)) 
                    : lossWeight.multiply(BigDecimal.valueOf(100));
                
                goodsService.recordLoss(goodsId, lossType, lossWeight, lossValue, truck.getId());
                
                log.info("货物 {} 发生{}损耗: {}% (重量: {}, 价值: {})", 
                    goodsId, lossType, String.format("%.2f", lossPercentage), 
                    lossWeight, lossValue);
            }
        } catch (Exception e) {
            log.error("记录货物损耗失败: {}", e.getMessage());
        }
    }

    public Map<String, Object> getTotalWaitingTime() {
        Map<String, Object> result = new HashMap<>();
        
        List<Truck> allTrucks = truckMapper.selectAll();
        long totalWaitingCycles = 0;
        int totalTrucks = allTrucks.size();
        
        for (Truck truck : allTrucks) {
            Integer waitCounter = truckWaitCounters.get(truck.getId());
            if (waitCounter != null && waitCounter > 0) {
                totalWaitingCycles += waitCounter;
            }
        }
        
        // 等待时间计算：乘以30，单位为分钟
        double waitingMinutes = totalWaitingCycles * 30.0;
        double waitingHours = waitingMinutes / 60.0;
        
        result.put("totalWaitingCycles", totalWaitingCycles);
        result.put("totalWaitingMinutes", waitingMinutes);
        result.put("totalWaitingHours", waitingHours);
        result.put("averageWaitingMinutesPerTruck", totalTrucks > 0 ? waitingMinutes / totalTrucks : 0);
        result.put("totalTrucks", totalTrucks);
        
        log.info("计算等待时间: 总周期={}, 总分钟={}, 总小时={}, 平均每车={}", 
            totalWaitingCycles, waitingMinutes, waitingHours, 
            totalTrucks > 0 ? waitingMinutes / totalTrucks : 0);
        
        return result;
    }

    /**
     * 定时优化任务分配
     * 每30分钟执行一次混合优化算法，对未分配任务进行重新优化
     */
    @Scheduled(fixedRate = 1800000, initialDelay = 60000) // 30分钟 = 1800000毫秒，初始延迟1分钟
    public void scheduledOptimization() {
        if (!isSimulationRunning) {
            return;
        }

        // 查询待分配的任务
        List<Task> unassignedTasks = taskMapper.findByTruckIdIsNull();
        if (unassignedTasks.isEmpty()) {
            log.debug("没有待分配任务，跳过优化");
            return;
        }

        // 查询所有空闲车辆
        List<Truck> idleTrucks = truckMapper.selectByStatus(TruckStatusConstant.IDLE);
        if (idleTrucks.isEmpty()) {
            log.debug("没有空闲车辆，跳过优化");
            return;
        }

        log.info("=== 开始定时优化任务分配 ===");
        log.info("待分配任务数: {}, 空闲车辆数: {}", unassignedTasks.size(), idleTrucks.size());

        try {
            // 使用混合优化算法（遗传算法 + 模拟退火）
            Map<Truck, List<Task>> optimizedResult = optimizationService.hybridOptimization(idleTrucks, unassignedTasks);

            // 应用优化结果
            applyOptimizationResult(optimizedResult);

            log.info("=== 定时优化完成 ===");
        } catch (Exception e) {
            log.error("定时优化失败: {}", e.getMessage());
        }
    }

    /**
     * 应用优化算法的分配结果
     * 将优化后的任务分配方案应用到实际调度中
     */
    private void applyOptimizationResult(Map<Truck, List<Task>> optimizedResult) {
        synchronized (assignLock) {
            Set<Integer> pendingTaskIds = new HashSet<>();
            for (Task t : pendingTasks) {
                if (t.getId() != null) {
                    pendingTaskIds.add(t.getId());
                }
            }

            for (Map.Entry<Truck, List<Task>> entry : optimizedResult.entrySet()) {
                Truck truck = entry.getKey();
                List<Task> tasks = entry.getValue();

                if (!tasks.isEmpty()) {
                    Task task = tasks.get(0);
                    
                    if (task.getId() != null && !pendingTaskIds.contains(task.getId())) {
                        Task currentTask = taskMapper.selectByPrimaryKey(task.getId());
                        if (currentTask != null && currentTask.getTruckId() == null) {
                            log.info("优化结果: 车辆{} 分配任务{}", truck.getId(), task.getId());
                            
                            // 直接处理分配，生成路径点
                            Stack<Task> loadStack = new Stack<>();
                            List<Task> assignedTasks = new ArrayList<>();
                            loadStack.push(task);
                            assignedTasks.add(task);
                            
                            processMultiTaskAssignment(truck, loadStack, assignedTasks);
                        }
                    }
                }
            }
        }
    }

}