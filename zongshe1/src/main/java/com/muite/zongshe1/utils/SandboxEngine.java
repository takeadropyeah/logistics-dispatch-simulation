package com.muite.zongshe1.utils;

import java.util.*;

/**
 * 数据实验沙箱——确定性仿真引擎（阶段III任务2）。
 *
 * 设计要点（对应《数据实验沙箱》机制文档与胡杨小组实现思路）：
 *  1. 环境模拟器：钢铁产业链节点路网 + 车队 + 任务流，纯内存运行，不依赖高德API与线上数据库；
 *  2. 数据记录器：每 N tick 采样一次指标快照，终态输出八维指标；
 *  3. 分析器：同一种子下所有随机数取自唯一 Random(seed)，且取数顺序固定
 *     （任务按id序、车辆按id序、每 tick 固定阶段顺序），因此同种子两次运行结果逐位一致，可精确复现；
 *  4. 变量隔离：环境配置（车辆数/任务数/拥堵率/天气率）与算法配置（GREEDY/SA/GA）显式传参，
 *     对照实验只改想要研究的变量。
 */
public class SandboxEngine {

    // ==================== 常量（世界规则） ====================
    /** 每 tick 代表的虚拟小时数 */
    private static final double TICK_HOURS = 0.25;
    /** 车辆虚拟速度 km/h */
    private static final double SPEED_KMH = 80.0;
    /** 取/装货停留 tick 数 */
    private static final int LOAD_TICKS = 1;
    private static final int UNLOAD_TICKS = 1;
    /** 拥堵持续 tick 数 */
    private static final int JAM_TICKS = 2;
    /** 天气持续 tick 数 */
    private static final int WEATHER_TICKS = 3;
    /** 油耗 L/100km */
    private static final double FUEL_L_PER_100KM = 30.0;
    private static final double FUEL_PRICE = 7.5;
    private static final double MAINT_PER_KM = 0.5;
    private static final double DRIVER_WAGE_PER_H = 25.0;
    private static final double CARBON_KG_PER_L = 2.6;
    private static final double GINI_WARNING = 0.4;

    /** 产业链节点（名称/经度/纬度/类型）——世界静态背景，所有实验共享 */
    private static final double[][] NODES = {
            // 矿山
            {118.18, 39.63}, {113.30, 40.08}, {109.78, 39.61}, {122.99, 41.11},
            // 钢厂
            {118.17, 39.60}, {122.96, 41.08}, {114.38, 30.59}, {109.85, 40.65},
            // 物流中心
            {117.75, 38.98}, {120.38, 36.07}, {121.49, 31.23}, {113.26, 23.13},
            // 加工厂
            {120.58, 31.30}, {113.12, 23.02},
            // 终端客户
            {116.40, 39.90}, {121.47, 31.23}, {113.27, 23.11}
    };
    private static final String[] NODE_NAMES = {
            "唐山铁矿", "大同煤矿", "鄂尔多斯煤矿", "鞍山铁矿",
            "唐山钢厂", "鞍山钢厂", "武汉钢厂", "包头钢厂",
            "天津物流中心", "青岛物流中心", "上海物流中心", "广州物流中心",
            "苏州加工厂", "佛山加工厂",
            "北京建工", "上海建工", "广州城建"
    };
    private static final int NODE_MINE = 0, NODE_STEEL = 4, NODE_LOGISTICS = 8, NODE_FACTORY = 12, NODE_CUSTOMER = 14;
    private static final String[] TRUCK_TYPES = {"仓栅式车", "集装箱货车", "厢式车", "自卸车"};
    private static final double[] TRUCK_CAPS = {15.0, 25.0, 10.0, 30.0};
    /** 六种产业链环节：起点类型集合 → 终点类型集合 + 吨位范围 */
    private static final int[][] LINK_FROM = {
            {NODE_MINE, NODE_STEEL}, {NODE_STEEL, NODE_STEEL + 4}, {NODE_LOGISTICS, NODE_LOGISTICS + 4},
            {NODE_FACTORY, NODE_CUSTOMER}, {NODE_STEEL, NODE_CUSTOMER}, {NODE_LOGISTICS, NODE_CUSTOMER}
    };
    private static final int[][] LINK_TO = {
            {NODE_STEEL, NODE_LOGISTICS}, {NODE_LOGISTICS, NODE_LOGISTICS + 4}, {NODE_FACTORY, NODE_CUSTOMER},
            {NODE_CUSTOMER, NODE_CUSTOMER + 3}, {NODE_CUSTOMER, NODE_CUSTOMER + 3}, {NODE_CUSTOMER, NODE_CUSTOMER + 3}
    };
    private static final double[][] LINK_WEIGHT = {{20, 60}, {10, 40}, {3, 18}, {1, 11}, {20, 60}, {0.5, 8.5}};

    // ==================== 运行时对象 ====================

    private static class Truck {
        int id;
        int type;
        double lng, lat;
        String status = "空闲"; // 空闲/取货中/装货/运输中/卸货/拥堵/天气等待
        int taskId = -1;
        double emptyRemainKm, loadedRemainKm;
        int freezeTicks = 0;
        double emptyKm = 0, loadedKm = 0;   // 累计空驶/载重里程
        double workTonKm = 0;               // 完成工作量（公平性指标）
        int idleTicks = 0;
    }

    private static class STask {
        int id;
        int fromNode, toNode;
        double weight;
        int releaseTick;
        int deadlineTick;
        String status = "待释放"; // 待释放/待分配/取货中/运输中/已完成
        int truckId = -1;
        int assignTick = -1, completeTick = -1;
    }

    /** 单次实验配置（全部显式传参 → 变量隔离） */
    public static class Config {
        public int vehicleCount = 12;
        public int taskCount = 60;
        public int ticks = 720;
        public double jamRate = 0.02;
        public double weatherRate = 0.01;
        public String algorithm = "GREEDY"; // GREEDY / SA / GA
        public Map<String, Object> algoParams = new LinkedHashMap<>();
        public Long seed;
    }

    // ==================== 引擎状态 ====================
    private final Config cfg;
    private final Random rng;
    private final List<Truck> trucks = new ArrayList<>();
    private final List<STask> tasks = new ArrayList<>();
    private final List<Map<String, Object>> snapshots = new ArrayList<>();
    // 匹配精准度计数（M）
    private int matchTotal = 0, matchOptimal = 0;
    private double matchReciprocalRank = 0;
    // 事件计数（R）
    private int jamCount = 0, weatherCount = 0, frozenTruckTicks = 0;
    // 决策耗时（A）
    private long solveNanos = 0;
    private boolean weatherActive = false;
    private int weatherRemainTicks = 0;

    private SandboxEngine(Config cfg) {
        this.cfg = cfg;
        this.rng = new Random(cfg.seed != null ? cfg.seed : 0L);
    }

    // ==================== 入口 ====================

    public static Map<String, Object> run(Config cfg) {
        SandboxEngine e = new SandboxEngine(cfg);
        return e.execute();
    }

    private Map<String, Object> execute() {
        generateWorld();
        int snapshotEvery = Math.max(1, cfg.ticks / 72); // 最多约72个快照点

        for (int tick = 0; tick < cfg.ticks; tick++) {
            // 固定阶段顺序（复现性关键）：01释放任务 → 02天气摇号 → 03算法派单 → 04随机事件 → 05移动/恢复 → 06快照
            releaseTasks(tick);
            stepWeather(tick);
            assign(tick);
            stepEvents();
            stepMovement(tick);
            if (tick % snapshotEvery == 0) snapshot(tick);
        }

        Map<String, Object> result = buildResult();
        result.put("snapshots", snapshots);
        return result;
    }

    // ==================== 世界生成（固定顺序取随机数） ====================

    private void generateWorld() {
        for (int i = 0; i < cfg.vehicleCount; i++) {
            Truck t = new Truck();
            t.id = i + 1;
            t.type = rng.nextInt(TRUCK_TYPES.length);
            int node = rng.nextInt(NODES.length);
            t.lng = NODES[node][0];
            t.lat = NODES[node][1];
            trucks.add(t);
        }
        for (int i = 0; i < cfg.taskCount; i++) {
            STask task = new STask();
            task.id = i + 1;
            int link = rng.nextInt(LINK_FROM.length);
            task.fromNode = LINK_FROM[link][0] + rng.nextInt(LINK_FROM[link][1] - LINK_FROM[link][0]);
            task.toNode = LINK_TO[link][0] + rng.nextInt(LINK_TO[link][1] - LINK_TO[link][0]);
            double lo = LINK_WEIGHT[link][0], hi = LINK_WEIGHT[link][1];
            task.weight = Math.round((lo + rng.nextDouble() * (hi - lo)) * 10) / 10.0;
            task.releaseTick = rng.nextInt(Math.max(1, cfg.ticks / 2));
            int legTicks = (int) Math.ceil(haversineKm(task.fromNode, task.toNode) / (SPEED_KMH * TICK_HOURS));
            task.deadlineTick = task.releaseTick + (int) Math.ceil(legTicks * 1.8) + 24;
            task.status = "待释放";
            tasks.add(task);
        }
        tasks.sort(Comparator.comparingInt(t -> t.releaseTick));
    }

    private void releaseTasks(int tick) {
        for (STask t : tasks) {
            if ("待释放".equals(t.status) && t.releaseTick <= tick) t.status = "待分配";
        }
    }

    // ==================== 天气（交通环境变量） ====================

    private void stepWeather(int tick) {
        if (weatherActive) {
            weatherRemainTicks--;
            if (weatherRemainTicks <= 0) weatherActive = false;
        } else if (rng.nextDouble() < cfg.weatherRate) {
            weatherActive = true;
            weatherRemainTicks = WEATHER_TICKS;
            weatherCount++;
        }
    }

    // ==================== 算法派单（环境隔离 + 种子RNG） ====================

    private void assign(int tick) {
        List<STask> pending = new ArrayList<>();
        for (STask t : tasks) if ("待分配".equals(t.status)) pending.add(t);
        List<Truck> idle = new ArrayList<>();
        for (Truck t : trucks) if ("空闲".equals(t.status)) idle.add(t);
        if (pending.isEmpty() || idle.isEmpty()) return;

        long start = System.nanoTime();
        int[] assignment; // pending 索引 → idle 索引，-1 表示本批不分配
        switch (cfg.algorithm == null ? "GREEDY" : cfg.algorithm.toUpperCase()) {
            case "SA": assignment = saAssign(pending, idle); break;
            case "GA": assignment = gaAssign(pending, idle); break;
            default: assignment = greedyAssign(pending, idle); break;
        }
        solveNanos += System.nanoTime() - start;

        for (int i = 0; i < pending.size(); i++) {
            int j = assignment[i];
            if (j < 0) continue;
            STask task = pending.get(i);
            Truck truck = idle.get(j);
            task.status = "取货中";
            task.truckId = truck.id;
            task.assignTick = tick;
            truck.status = "取货中";
            truck.taskId = task.id;
            truck.emptyRemainKm = haversine(nodeLng(truck), nodeLat(truck), NODES[task.fromNode][0], NODES[task.fromNode][1]);
            truck.loadedRemainKm = haversineKm(task.fromNode, task.toNode);
            recordMatchQuality(task, truck, idle);
        }
    }

    /** 贪心：任务按id序，选剩余空驶最小的可装载空闲车 */
    private int[] greedyAssign(List<STask> pending, List<Truck> idle) {
        int[] out = new int[pending.size()];
        Arrays.fill(out, -1);
        boolean[] used = new boolean[idle.size()];
        for (int i = 0; i < pending.size(); i++) {
            STask task = pending.get(i);
            int best = -1;
            double bestD = Double.MAX_VALUE;
            for (int j = 0; j < idle.size(); j++) {
                if (used[j] || !canCarry(idle.get(j), task)) continue;
                Truck t = idle.get(j);
                double d = haversine(nodeLng(t), nodeLat(t), NODES[task.fromNode][0], NODES[task.fromNode][1]);
                if (d < bestD) { bestD = d; best = j; }
            }
            if (best >= 0) { out[i] = best; used[best] = true; }
        }
        return out;
    }

    /** 模拟退火：邻域=随机改派一辆车/取消分配，Metropolis 准则 */
    private int[] saAssign(List<STask> pending, List<Truck> idle) {
        int iters = intParam("iterations", 4000);
        double initTemp = doubleParam("initTemp", 1000);
        double coolRate = doubleParam("coolRate", 0.995);

        int[] cur = greedyAssign(pending, idle);
        double curCost = solutionCost(cur, pending, idle);
        int[] best = cur.clone();
        double bestCost = curCost;
        double temp = initTemp;

        for (int it = 0; it < iters && temp > 0.1; it++) {
            int[] cand = cur.clone();
            int i = rng.nextInt(pending.size());
            int j = rng.nextInt(idle.size() + 1) - 1;
            cand[i] = j;
            double c2 = solutionCost(cand, pending, idle);
            if (c2 <= curCost || rng.nextDouble() < Math.exp(-(c2 - curCost) / temp)) {
                cur = cand;
                curCost = c2;
                if (curCost < bestCost) { bestCost = curCost; best = cur.clone(); }
            }
            temp *= coolRate;
        }
        return best;
    }

    /** 遗传算法：轮盘赌+单点交叉+变异+精英保留 */
    private int[] gaAssign(List<STask> pending, List<Truck> idle) {
        int popSize = intParam("population", 40);
        int generations = intParam("generations", 80);
        double crossoverRate = doubleParam("crossoverRate", 0.8);
        double mutationRate = doubleParam("mutationRate", 0.1);

        List<int[]> pop = new ArrayList<>();
        pop.add(greedyAssign(pending, idle)); // 精英种子
        for (int p = 1; p < popSize; p++) {
            int[] g = new int[pending.size()];
            Arrays.fill(g, -1);
            boolean[] used = new boolean[idle.size()];
            for (int i = 0; i < pending.size(); i++) {
                int j = rng.nextInt(idle.size());
                if (!used[j] && canCarry(idle.get(j), pending.get(i))) { g[i] = j; used[j] = true; }
            }
            pop.add(g);
        }
        double[] fitness = new double[popSize];
        for (int p = 0; p < popSize; p++) fitness[p] = solutionCost(pop.get(p), pending, idle);

        for (int gen = 0; gen < generations; gen++) {
            List<int[]> next = new ArrayList<>();
            // 精英保留1个
            int elite = 0;
            for (int p = 1; p < popSize; p++) if (fitness[p] < fitness[elite]) elite = p;
            next.add(pop.get(elite).clone());
            while (next.size() < popSize) {
                int[] a = tournament(pop, fitness), b = tournament(pop, fitness);
                int[] child = rng.nextDouble() < crossoverRate ? crossover(a, b, idle.size()) : a.clone();
                if (rng.nextDouble() < mutationRate) {
                    int i = rng.nextInt(pending.size());
                    child[i] = rng.nextInt(idle.size() + 1) - 1;
                }
                next.add(child);
            }
            pop = next;
            for (int p = 0; p < popSize; p++) fitness[p] = solutionCost(pop.get(p), pending, idle);
        }
        int best = 0;
        for (int p = 1; p < popSize; p++) if (fitness[p] < fitness[best]) best = p;
        return pop.get(best);
    }

    private int[] tournament(List<int[]> pop, double[] fitness) {
        int a = rng.nextInt(pop.size()), b = rng.nextInt(pop.size());
        return fitness[a] <= fitness[b] ? pop.get(a) : pop.get(b);
    }

    private int[] crossover(int[] a, int[] b, int truckCount) {
        int[] child = new int[a.length];
        Arrays.fill(child, -1);
        boolean[] used = new boolean[truckCount];
        int cut = rng.nextInt(a.length);
        for (int i = 0; i < cut; i++) { child[i] = a[i]; if (a[i] >= 0) used[a[i]] = true; }
        for (int i = cut; i < a.length; i++) { child[i] = (b[i] >= 0 && !used[b[i]]) ? b[i] : -1; if (child[i] >= 0) used[child[i]] = true; }
        return child;
    }

    /** 解成本 = 总空驶 + 未分配惩罚 + 不可行大惩罚（与线上"减少空驶"目标一致） */
    private double solutionCost(int[] assignment, List<STask> pending, List<Truck> idle) {
        double cost = 0;
        for (int i = 0; i < assignment.length; i++) {
            int j = assignment[i];
            if (j < 0) { cost += 3000; continue; }
            STask task = pending.get(i);
            Truck truck = idle.get(j);
            if (!canCarry(truck, task)) cost += 1e6;
            else cost += haversine(nodeLng(truck), nodeLat(truck), NODES[task.fromNode][0], NODES[task.fromNode][1]);
        }
        return cost;
    }

    private boolean canCarry(Truck truck, STask task) {
        return task.weight <= TRUCK_CAPS[truck.type];
    }

    /** 匹配精准度记录：以"条件最优车"（当前空闲可装载集合中空驶最小者）为基准答案 */
    private void recordMatchQuality(STask task, Truck chosen, List<Truck> idlePool) {
        double bestD = Double.MAX_VALUE;
        int bestId = -1;
        List<double[]> ranked = new ArrayList<>();
        for (Truck t : idlePool) {
            if (!canCarry(t, task)) continue;
            double d = haversine(nodeLng(t), nodeLat(t), NODES[task.fromNode][0], NODES[task.fromNode][1]);
            ranked.add(new double[]{t.id, d});
            if (d < bestD) { bestD = d; bestId = t.id; }
        }
        ranked.sort(Comparator.comparingDouble(a -> a[1]));
        int rank = 1;
        for (double[] r : ranked) { if ((int) r[0] == chosen.id) break; rank++; }
        matchTotal++;
        if (chosen.id == bestId) matchOptimal++;
        matchReciprocalRank += 1.0 / rank;
    }

    // ==================== 随机事件（固定顺序摇号） ====================

    private void stepEvents() {
        for (Truck t : trucks) {
            if ("取货中".equals(t.status) || "运输中".equals(t.status)) {
                if (t.freezeTicks <= 0 && rng.nextDouble() < cfg.jamRate) {
                    t.freezeTicks = JAM_TICKS;
                    t.status = "拥堵";
                    jamCount++;
                }
            }
        }
    }

    // ==================== 移动/恢复（虚拟时钟推进） ====================

    private void stepMovement(int tick) {
        double kmPerTick = SPEED_KMH * TICK_HOURS * (weatherActive ? 0.5 : 1.0);
        for (Truck t : trucks) {
            if (t.freezeTicks > 0) {
                t.freezeTicks--;
                frozenTruckTicks++;
                if (t.freezeTicks == 0 && "拥堵".equals(t.status)) t.status = "运输中";
                continue;
            }
            switch (t.status) {
                case "取货中": {
                    double move = Math.min(kmPerTick, t.emptyRemainKm);
                    t.emptyRemainKm -= move;
                    t.emptyKm += move;
                    if (t.emptyRemainKm <= 1e-9) { t.status = "装货"; t.freezeTicks = LOAD_TICKS; }
                    break;
                }
                case "装货": {
                    if (t.freezeTicks <= 0) {
                        t.status = "运输中";
                        STask task = tasks.get(t.taskId - 1);
                        task.status = "运输中";
                    }
                    break;
                }
                case "运输中": {
                    double move = Math.min(kmPerTick, t.loadedRemainKm);
                    t.loadedRemainKm -= move;
                    t.loadedKm += move;
                    if (t.loadedRemainKm <= 1e-9) { t.status = "卸货"; t.freezeTicks = UNLOAD_TICKS; }
                    break;
                }
                case "卸货": {
                    if (t.freezeTicks <= 0) {
                        STask task = tasks.get(t.taskId - 1);
                        task.status = "已完成";
                        task.completeTick = tick;
                        t.workTonKm += task.weight * haversineKm(task.fromNode, task.toNode);
                        t.status = "空闲";
                        t.taskId = -1;
                    }
                    break;
                }
                default:
                    t.idleTicks++;
                    break;
            }
        }
    }

    // ==================== 快照（数据记录器） ====================

    private void snapshot(int tick) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("tick", tick);
        s.put("completed", countStatus("已完成"));
        s.put("pending", countStatus("待分配"));
        s.put("inTransit", countStatus("运输中") + countStatus("取货中"));
        double totalKm = trucks.stream().mapToDouble(t -> t.emptyKm + t.loadedKm).sum();
        double emptyKm = trucks.stream().mapToDouble(t -> t.emptyKm).sum();
        s.put("emptyRate", totalKm > 0 ? round(emptyKm / totalKm) : 0);
        double totalCost = computeTotalCost();
        double tonKm = trucks.stream().mapToDouble(t -> t.workTonKm).sum();
        s.put("unitCost", tonKm > 0 ? round(totalCost / tonKm) : 0);
        s.put("totalCost", round(totalCost));
        snapshots.add(s);
    }

    private int countStatus(String status) {
        int c = 0;
        for (STask t : tasks) if (status.equals(t.status)) c++;
        return c;
    }

    private double computeTotalCost() {
        double totalKm = trucks.stream().mapToDouble(t -> t.emptyKm + t.loadedKm).sum();
        double fuel = totalKm / 100 * FUEL_L_PER_100KM * FUEL_PRICE;
        double maint = totalKm * MAINT_PER_KM;
        double driver = totalKm / SPEED_KMH * DRIVER_WAGE_PER_H;
        return fuel + maint + driver;
    }

    // ==================== 终态八维指标（分析器） ====================

    private Map<String, Object> buildResult() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 总量
        double emptyKm = trucks.stream().mapToDouble(t -> t.emptyKm).sum();
        double loadedKm = trucks.stream().mapToDouble(t -> t.loadedKm).sum();
        double totalKm = emptyKm + loadedKm;
        double tonKm = trucks.stream().mapToDouble(t -> t.workTonKm).sum();
        int completed = countStatus("已完成");
        int onTime = 0;
        for (STask t : tasks) if ("已完成".equals(t.status) && t.completeTick <= t.deadlineTick) onTime++;

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("ticks", cfg.ticks);
        totals.put("virtualHours", round(cfg.ticks * TICK_HOURS));
        totals.put("taskCount", cfg.taskCount);
        totals.put("vehicleCount", cfg.vehicleCount);
        totals.put("completed", completed);
        totals.put("onTimeRate", completed > 0 ? round((double) onTime / completed) : 0);
        totals.put("totalKm", round(totalKm));
        totals.put("emptyKm", round(emptyKm));
        totals.put("loadedKm", round(loadedKm));
        totals.put("tonKm", round(tonKm));
        result.put("totals", totals);

        // M 匹配精准度
        double precision = matchTotal > 0 ? (double) matchOptimal / matchTotal : 0;
        double recall = matchTotal > 0 ? (double) matchOptimal / matchTotal : 0;
        double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("precision", round(precision));
        m.put("recall", round(recall));
        m.put("f1", round(f1));
        m.put("mrr", matchTotal > 0 ? round(matchReciprocalRank / matchTotal) : 0);
        m.put("samples", matchTotal);
        result.put("M", m);

        // U 资源利用
        List<Double> loadRates = new ArrayList<>();
        for (STask t : tasks) {
            if (t.truckId >= 0 && t.weight > 0) {
                int ti = t.truckId - 1;
                if (ti < trucks.size() && TRUCK_CAPS[trucks.get(ti).type] > 0) {
                    loadRates.add(t.weight / TRUCK_CAPS[trucks.get(ti).type]);
                }
            }
        }
        double[] ms = meanStd(loadRates);
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("mileageUtilization", round(totalKm > 0 ? loadedKm / totalKm : 0));
        u.put("emptyRate", round(totalKm > 0 ? emptyKm / totalKm : 0));
        u.put("loadRateMean", round(ms[0]));
        u.put("loadRateStd", round(ms[1]));
        result.put("U", u);

        // T 吞吐响应（虚拟小时）
        List<Long> respTicks = new ArrayList<>();
        for (STask t : tasks) if (t.assignTick >= 0) respTicks.add((long) (t.assignTick - t.releaseTick));
        Map<String, Object> tm = new LinkedHashMap<>();
        tm.put("respP50", round(percentile(respTicks, 0.5) * TICK_HOURS));
        tm.put("respP95", round(percentile(respTicks, 0.95) * TICK_HOURS));
        tm.put("respP99", round(percentile(respTicks, 0.99) * TICK_HOURS));
        tm.put("throughputPerHour", round(completed / (cfg.ticks * TICK_HOURS)));
        result.put("T", tm);

        // A 算法效率
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("solveMs", round(solveNanos / 1e6));
        a.put("algorithm", cfg.algorithm);
        result.put("A", a);

        // C 成本效益
        double totalCost = computeTotalCost();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("totalCost", round(totalCost));
        c.put("unitCost", tonKm > 0 ? round(totalCost / tonKm) : 0);
        result.put("C", c);

        // R 可靠性
        double totalTruckTicks = (double) cfg.vehicleCount * cfg.ticks;
        double availability = totalTruckTicks > 0 ? 1 - (double) frozenTruckTicks / totalTruckTicks : 1;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("jamCount", jamCount);
        r.put("weatherCount", weatherCount);
        r.put("availability", round(availability));
        r.put("mtbfHours", jamCount > 0 ? round(cfg.ticks * TICK_HOURS / jamCount) : round(cfg.ticks * TICK_HOURS));
        result.put("R", r);

        // F 公平性
        List<Double> workloads = new ArrayList<>();
        for (Truck t : trucks) workloads.add(t.workTonKm);
        double gini = gini(workloads);
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("gini", round(gini));
        f.put("warning", gini > GINI_WARNING);
        result.put("F", f);

        // G 绿色低碳
        double fuelL = totalKm / 100 * FUEL_L_PER_100KM;
        double carbonKg = fuelL * CARBON_KG_PER_L;
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("carbonKg", round(carbonKg));
        g.put("unitCarbon", tonKm > 0 ? round(carbonKg / tonKm) : 0);
        result.put("G", g);

        return result;
    }

    // ==================== 工具 ====================

    private int intParam(String key, int def) {
        Object v = cfg.algoParams.get(key);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    private double doubleParam(String key, double def) {
        Object v = cfg.algoParams.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }

    private static double haversineKm(int fromNode, int toNode) {
        return haversineKm(NODES[fromNode][0], NODES[fromNode][1], NODES[toNode][0], NODES[toNode][1]);
    }

    private static double haversineKm(double lon1, double lat1, double lon2, double lat2) {
        return haversine(lat1, lon1, lat2, lon2);
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double nodeLng(Truck t) { return t.lng; }
    private static double nodeLat(Truck t) { return t.lat; }

    private static double percentile(List<Long> values, double q) {
        if (values.isEmpty()) return 0;
        List<Long> v = new ArrayList<>(values);
        Collections.sort(v);
        int idx = (int) Math.ceil(q * (v.size() - 1));
        return v.get(idx);
    }

    private static double[] meanStd(List<Double> values) {
        if (values.isEmpty()) return new double[]{0, 0};
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = values.stream().mapToDouble(x -> (x - mean) * (x - mean)).average().orElse(0);
        return new double[]{mean, Math.sqrt(var)};
    }

    private static double gini(List<Double> values) {
        List<Double> v = new ArrayList<>();
        for (double x : values) if (x >= 0) v.add(x);
        Collections.sort(v);
        int n = v.size();
        if (n == 0) return 0;
        double sum = 0;
        for (double x : v) sum += x;
        if (sum <= 0) return 0;
        double cum = 0;
        for (int i = 0; i < n; i++) cum += (i + 1) * v.get(i);
        return (2.0 * cum) / (n * sum) - (n + 1.0) / n;
    }

    private static double round(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
