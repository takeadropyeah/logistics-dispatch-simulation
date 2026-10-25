package com.muite.zongshe1.utils;

import com.muite.zongshe1.entity.Goods;
import com.muite.zongshe1.entity.Task;
import com.muite.zongshe1.entity.Truck;
import com.muite.zongshe1.mapper.TaskMapper;
import com.muite.zongshe1.mapper.TruckMapper;
import com.muite.zongshe1.service.GoodsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 优化算法服务
 * 包含：
 * 1. 模拟退火算法（类似吃鸡缩圈淘汰算法）
 * 2. 遗传算法（用于路径优化）
 */
@Service
public class OptimizationService {

    private static final Logger log = LoggerFactory.getLogger(OptimizationService.class);

    @Autowired
    private TruckMapper truckMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private CostService costService;

    // ==================== 模拟退火算法（类似吃鸡缩圈淘汰算法）====================

    /**
     * 模拟退火算法优化任务分配
     * 类似吃鸡缩圈机制：随着迭代进行，搜索范围逐渐缩小，温度降低
     *
     * @param trucks 车辆列表
     * @param tasks  任务列表
     * @return 优化后的任务分配方案
     */
    public Map<Truck, List<Task>> simulatedAnnealingOptimization(List<Truck> trucks, List<Task> tasks) {
        // 初始化当前解：随机分配任务
        Map<Truck, List<Task>> currentSolution = randomAssignment(trucks, tasks);
        Map<Truck, List<Task>> bestSolution = deepCopySolution(currentSolution);

        // 模拟退火参数
        double temperature = 1000.0; // 初始温度
        double coolingRate = 0.95;   // 冷却速率
        double minTemperature = 0.1; // 最低温度

        int iteration = 0;
        while (temperature > minTemperature) {
            // 生成邻居解（随机交换两个车辆的任务）
            Map<Truck, List<Task>> newSolution = generateNeighborSolution(currentSolution);

            // 计算成本差
            double currentCost = calculateTotalCost(currentSolution);
            double newCost = calculateTotalCost(newSolution);
            double costDiff = newCost - currentCost;

            // 决定是否接受新解
            if (costDiff < 0 || Math.random() < Math.exp(-costDiff / temperature)) {
                currentSolution = newSolution;

                // 更新最优解
                if (newCost < calculateTotalCost(bestSolution)) {
                    bestSolution = deepCopySolution(newSolution);
                }
            }

            // 降低温度（缩圈）
            temperature *= coolingRate;
            iteration++;

            if (iteration % 10 == 0) {
                log.info("模拟退火迭代 {}: 温度={}, 当前成本={}, 最优成本={}",
                        iteration, String.format("%.2f", temperature), 
                        String.format("%.2f", currentCost), 
                        String.format("%.2f", calculateTotalCost(bestSolution)));
            }
        }

        log.info("模拟退火优化完成，迭代次数={}, 最优成本={}", iteration, 
                String.format("%.2f", calculateTotalCost(bestSolution)));
        return bestSolution;
    }

    /**
     * 模拟退火算法优化（支持初始解）
     * 用于在已有较好解的基础上进行精细优化
     *
     * @param trucks       车辆列表
     * @param tasks        任务列表
     * @param initialSolution 初始解（由遗传算法提供）
     * @return 优化后的解
     */
    public Map<Truck, List<Task>> simulatedAnnealingOptimization(List<Truck> trucks, List<Task> tasks, 
                                                                 Map<Truck, List<Task>> initialSolution) {
        // 使用传入的初始解
        Map<Truck, List<Task>> currentSolution = deepCopySolution(initialSolution);
        Map<Truck, List<Task>> bestSolution = deepCopySolution(currentSolution);

        // 使用较低的初始温度进行精细优化
        double temperature = 100.0;  // 初始温度较低，直接进入精细搜索阶段
        double coolingRate = 0.98;   // 冷却速率较慢，搜索更精细
        double minTemperature = 0.01;

        int iteration = 0;
        while (temperature > minTemperature) {
            Map<Truck, List<Task>> newSolution = generateNeighborSolution(currentSolution);

            double currentCost = calculateTotalCost(currentSolution);
            double newCost = calculateTotalCost(newSolution);
            double costDiff = newCost - currentCost;

            if (costDiff < 0 || Math.random() < Math.exp(-costDiff / temperature)) {
                currentSolution = newSolution;
                if (newCost < calculateTotalCost(bestSolution)) {
                    bestSolution = deepCopySolution(newSolution);
                }
            }

            temperature *= coolingRate;
            iteration++;
        }

        log.info("模拟退火精细优化完成，迭代次数={}, 最优成本={}", iteration, 
                String.format("%.2f", calculateTotalCost(bestSolution)));
        return bestSolution;
    }

    /**
     * 混合优化算法（真实实现，替换原占位版本）：
     * 阶段1 遗传算法全局搜索 → 阶段2 以GA结果为初始解的模拟退火精修 → 阶段3 超载贪心修复。
     *
     * @param trucks 车辆列表
     * @param tasks  任务列表
     * @return 优化后的任务分配方案
     */
    public Map<Truck, List<Task>> hybridOptimization(List<Truck> trucks, List<Task> tasks) {
        long start = System.currentTimeMillis();
        if (trucks == null || trucks.isEmpty() || tasks == null || tasks.isEmpty()) {
            return new HashMap<>();
        }
        log.info("=== 开始混合优化（真实执行：GA全局搜索 + SA精修 + 超载修复） ===");

        // 阶段1：遗传算法全局搜索
        Map<Truck, List<Task>> gaResult = geneticAlgorithmOptimization(trucks, tasks);
        double gaCost = calculateTotalCost(gaResult);
        log.info("阶段1 遗传算法完成，成本={}", String.format("%.2f", gaCost));

        // 阶段2：以GA结果为初始解的模拟退火精细优化
        Map<Truck, List<Task>> saResult = simulatedAnnealingOptimization(trucks, tasks, gaResult);
        double saCost = calculateTotalCost(saResult);
        log.info("阶段2 模拟退火完成，成本={}", String.format("%.2f", saCost));

        // 阶段3：超载贪心修复（取两阶段中较优者做可行性修正）
        Map<Truck, List<Task>> finalResult = repairOverload(saCost <= gaCost ? saResult : gaResult);
        double finalCost = calculateTotalCost(finalResult);

        log.info("混合优化完成：最终成本={}, 总耗时={}ms",
                String.format("%.2f", finalCost), System.currentTimeMillis() - start);
        return finalResult;
    }

    /**
     * 贪心局部修正：把超载车辆上的任务迁移到有剩余载重能力的车辆，直到无迁移可能。
     */
    private Map<Truck, List<Task>> repairOverload(Map<Truck, List<Task>> solution) {
        for (int pass = 0; pass < 3; pass++) {
            boolean moved = false;
            for (Map.Entry<Truck, List<Task>> entry : solution.entrySet()) {
                Truck truck = entry.getKey();
                List<Task> list = entry.getValue();
                BigDecimal load = list.stream()
                        .map(t -> t.getWeight() != null ? t.getWeight() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (truck.getMaxWeight() == null || load.compareTo(truck.getMaxWeight()) <= 0) {
                    continue;
                }
                for (Task task : new ArrayList<>(list)) {
                    BigDecimal w = task.getWeight() != null ? task.getWeight() : BigDecimal.ZERO;
                    for (Map.Entry<Truck, List<Task>> other : solution.entrySet()) {
                        if (other.getKey().getId().equals(truck.getId()) || other.getKey().getMaxWeight() == null) {
                            continue;
                        }
                        List<Task> oList = other.getValue();
                        BigDecimal oLoad = oList.stream()
                                .map(t -> t.getWeight() != null ? t.getWeight() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (oLoad.add(w).compareTo(other.getKey().getMaxWeight()) <= 0) {
                            oList.add(task);
                            list.remove(task);
                            moved = true;
                            break;
                        }
                    }
                    if (moved) break;
                }
                if (moved) break;
            }
            if (!moved) break;
        }
        return solution;
    }

    /**
     * 模拟吃鸡缩圈淘汰机制
     * 在每次迭代中，淘汰表现最差的部分解
     *
     * @param solutions      候选解列表
     * @param eliminationRate 淘汰率（0-1）
     * @return 淘汰后的解列表
     */
    public List<Map<Truck, List<Task>>> zoneShrinkElimination(List<Map<Truck, List<Task>>> solutions, double eliminationRate) {
        // 按成本排序
        solutions.sort(Comparator.comparingDouble(this::calculateTotalCost));

        // 计算淘汰数量
        int keepCount = (int) (solutions.size() * (1 - eliminationRate));
        if (keepCount < 1) keepCount = 1;

        log.info("缩圈淘汰: 原始解数量={}, 淘汰率={}, 保留数量={}", solutions.size(), eliminationRate, keepCount);

        // 保留前keepCount个最优解
        return solutions.subList(0, keepCount);
    }

    // ==================== 遗传算法 ====================

    /**
     * 遗传算法优化路径规划
     *
     * @param trucks 车辆列表
     * @param tasks  任务列表
     * @return 优化后的任务分配方案
     */
    public Map<Truck, List<Task>> geneticAlgorithmOptimization(List<Truck> trucks, List<Task> tasks) {
        // 参数设置
        int populationSize = 50;      // 种群大小
        int maxGenerations = 100;     // 最大迭代次数
        double crossoverRate = 0.8;   // 交叉概率
        double mutationRate = 0.1;    // 变异概率

        // 1. 初始化种群
        List<Map<Truck, List<Task>>> population = initializePopulation(populationSize, trucks, tasks);

        Map<Truck, List<Task>> bestSolution = null;
        double bestCost = Double.MAX_VALUE;

        for (int generation = 0; generation < maxGenerations; generation++) {
            // 2. 适应度评估
            List<Double> fitnessScores = population.stream()
                    .map(this::calculateTotalCost)
                    .collect(Collectors.toList());

            // 3. 选择（轮盘赌选择）
            List<Map<Truck, List<Task>>> selected = selection(population, fitnessScores);

            // 4. 交叉
            List<Map<Truck, List<Task>>> offspring = crossover(selected, crossoverRate);

            // 5. 变异
            mutate(offspring, mutationRate);

            // 6. 替换种群
            population = elitistReplacement(population, offspring);

            // 记录最优解
            Map<Truck, List<Task>> currentBest = population.stream()
                    .min(Comparator.comparingDouble(this::calculateTotalCost))
                    .orElse(null);

            if (currentBest != null) {
                double currentCost = calculateTotalCost(currentBest);
                if (currentCost < bestCost) {
                    bestCost = currentCost;
                    bestSolution = deepCopySolution(currentBest);
                }
            }

            if (generation % 10 == 0) {
                log.info("遗传算法代次 {}: 最优成本={}", generation, String.format("%.2f", bestCost));
            }
        }

        log.info("遗传算法优化完成，最优成本={}", String.format("%.2f", bestCost));
        return bestSolution;
    }

    /**
     * 初始化种群
     */
    private List<Map<Truck, List<Task>>> initializePopulation(int size, List<Truck> trucks, List<Task> tasks) {
        List<Map<Truck, List<Task>>> population = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            population.add(randomAssignment(trucks, tasks));
        }
        return population;
    }

    /**
     * 选择操作（轮盘赌）
     */
    private List<Map<Truck, List<Task>>> selection(List<Map<Truck, List<Task>>> population, List<Double> fitnessScores) {
        // 转换为最大化问题（成本越小越好，转换为适应度越大越好）
        double minCost = fitnessScores.stream().mapToDouble(Double::doubleValue).min().orElse(1);
        List<Double> adjustedFitness = fitnessScores.stream()
                .map(cost -> 1 / (cost + minCost + 1)) // 转换为适应度
                .collect(Collectors.toList());

        double totalFitness = adjustedFitness.stream().mapToDouble(Double::doubleValue).sum();

        List<Map<Truck, List<Task>>> selected = new ArrayList<>();
        for (int i = 0; i < population.size(); i++) {
            double r = Math.random() * totalFitness;
            double cumulative = 0;
            for (int j = 0; j < adjustedFitness.size(); j++) {
                cumulative += adjustedFitness.get(j);
                if (cumulative >= r) {
                    selected.add(deepCopySolution(population.get(j)));
                    break;
                }
            }
        }
        return selected;
    }

    /**
     * 交叉操作
     */
    private List<Map<Truck, List<Task>>> crossover(List<Map<Truck, List<Task>>> parents, double crossoverRate) {
        List<Map<Truck, List<Task>>> offspring = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < parents.size(); i += 2) {
            if (i + 1 < parents.size() && Math.random() < crossoverRate) {
                Map<Truck, List<Task>> parent1 = parents.get(i);
                Map<Truck, List<Task>> parent2 = parents.get(i + 1);

                // 获取所有任务
                List<Task> allTasks = new ArrayList<>();
                for (List<Task> tasks : parent1.values()) {
                    allTasks.addAll(tasks);
                }

                // 创建子解
                Map<Truck, List<Task>> child1 = createEmptyAssignment(new ArrayList<>(parent1.keySet()));
                Map<Truck, List<Task>> child2 = createEmptyAssignment(new ArrayList<>(parent2.keySet()));

                // 使用单点交叉：随机选择一个分割点
                int crossPoint = random.nextInt(allTasks.size());

                // 收集已经分配的任务
                Set<Task> assignedToChild1 = new HashSet<>();
                Set<Task> assignedToChild2 = new HashSet<>();

                // 前crossPoint个任务从parent1分配
                int taskIdx = 0;
                for (Truck truck : parent1.keySet()) {
                    for (Task task : parent1.get(truck)) {
                        if (taskIdx < crossPoint) {
                            child1.get(truck).add(task);
                            assignedToChild1.add(task);
                        }
                        taskIdx++;
                    }
                }

                // 后部分任务从parent2分配，确保不重复
                for (Truck truck : parent2.keySet()) {
                    for (Task task : parent2.get(truck)) {
                        if (!assignedToChild1.contains(task)) {
                            // 找到第一个有空间的车辆
                            for (Truck targetTruck : child1.keySet()) {
                                if (!child1.get(targetTruck).contains(task)) {
                                    child1.get(targetTruck).add(task);
                                    break;
                                }
                            }
                        }
                    }
                }

                // child2相反
                taskIdx = 0;
                for (Truck truck : parent2.keySet()) {
                    for (Task task : parent2.get(truck)) {
                        if (taskIdx < crossPoint) {
                            child2.get(truck).add(task);
                            assignedToChild2.add(task);
                        }
                        taskIdx++;
                    }
                }

                for (Truck truck : parent1.keySet()) {
                    for (Task task : parent1.get(truck)) {
                        if (!assignedToChild2.contains(task)) {
                            for (Truck targetTruck : child2.keySet()) {
                                if (!child2.get(targetTruck).contains(task)) {
                                    child2.get(targetTruck).add(task);
                                    break;
                                }
                            }
                        }
                    }
                }

                offspring.add(child1);
                offspring.add(child2);
            } else {
                offspring.add(deepCopySolution(parents.get(i)));
                if (i + 1 < parents.size()) {
                    offspring.add(deepCopySolution(parents.get(i + 1)));
                }
            }
        }
        return offspring;
    }

    /**
     * 变异操作 - 随机将一个任务从一辆车移动到另一辆车
     */
    private void mutate(List<Map<Truck, List<Task>>> population, double mutationRate) {
        for (Map<Truck, List<Task>> solution : population) {
            if (Math.random() < mutationRate) {
                List<Truck> truckList = new ArrayList<>(solution.keySet());
                if (truckList.size() >= 2) {
                    // 随机选择两个不同的车辆
                    int idx1 = new Random().nextInt(truckList.size());
                    int idx2 = new Random().nextInt(truckList.size());
                    while (idx1 == idx2 && truckList.size() > 1) {
                        idx2 = new Random().nextInt(truckList.size());
                    }

                    Truck truck1 = truckList.get(idx1);
                    Truck truck2 = truckList.get(idx2);

                    List<Task> tasks1 = solution.get(truck1);
                    List<Task> tasks2 = solution.get(truck2);

                    if (!tasks1.isEmpty()) {
                        // 随机选择一个任务从truck1移动到truck2
                        int taskIdx = new Random().nextInt(tasks1.size());
                        Task taskToMove = tasks1.remove(taskIdx);
                        tasks2.add(taskToMove);
                    }
                }
            }
        }
    }

    /**
     * 创建空的任务分配方案
     */
    private Map<Truck, List<Task>> createEmptyAssignment(List<Truck> trucks) {
        Map<Truck, List<Task>> assignment = new HashMap<>();
        for (Truck truck : trucks) {
            assignment.put(truck, new ArrayList<>());
        }
        return assignment;
    }

    /**
     * 精英保留策略
     */
    private List<Map<Truck, List<Task>>> elitistReplacement(List<Map<Truck, List<Task>>> oldPopulation, List<Map<Truck, List<Task>>> offspring) {
        // 合并旧种群和新种群
        List<Map<Truck, List<Task>>> combined = new ArrayList<>();
        combined.addAll(oldPopulation);
        combined.addAll(offspring);

        // 按成本排序，保留最优的前N个
        combined.sort(Comparator.comparingDouble(this::calculateTotalCost));
        return combined.subList(0, oldPopulation.size());
    }

    // ==================== 辅助方法 ====================

    /**
     * 随机分配任务
     */
    private Map<Truck, List<Task>> randomAssignment(List<Truck> trucks, List<Task> tasks) {
        Map<Truck, List<Task>> assignment = new HashMap<>();
        for (Truck truck : trucks) {
            assignment.put(truck, new ArrayList<>());
        }

        List<Task> shuffledTasks = new ArrayList<>(tasks);
        Collections.shuffle(shuffledTasks);

        int idx = 0;
        for (Task task : shuffledTasks) {
            Truck truck = trucks.get(idx % trucks.size());
            assignment.get(truck).add(task);
            idx++;
        }

        return assignment;
    }

    /**
     * 生成邻居解（随机交换任务）
     */
    private Map<Truck, List<Task>> generateNeighborSolution(Map<Truck, List<Task>> solution) {
        Map<Truck, List<Task>> neighbor = deepCopySolution(solution);
        List<Truck> truckList = new ArrayList<>(neighbor.keySet());

        if (truckList.size() < 2) return neighbor;

        // 随机选择两个车辆
        Truck truck1 = truckList.get(new Random().nextInt(truckList.size()));
        Truck truck2 = truckList.get(new Random().nextInt(truckList.size()));

        List<Task> tasks1 = neighbor.get(truck1);
        List<Task> tasks2 = neighbor.get(truck2);

        if (!tasks1.isEmpty() && !tasks2.isEmpty()) {
            // 随机交换一个任务
            int idx1 = new Random().nextInt(tasks1.size());
            int idx2 = new Random().nextInt(tasks2.size());

            Task temp = tasks1.get(idx1);
            tasks1.set(idx1, tasks2.get(idx2));
            tasks2.set(idx2, temp);
        }

        return neighbor;
    }

    /**
     * 深拷贝任务分配方案
     */
    private Map<Truck, List<Task>> deepCopySolution(Map<Truck, List<Task>> solution) {
        Map<Truck, List<Task>> copy = new HashMap<>();
        for (Map.Entry<Truck, List<Task>> entry : solution.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    /**
     * 计算总任务分配成本
     */
    private double calculateTotalCost(Map<Truck, List<Task>> solution) {
        double totalCost = 0;
        for (Map.Entry<Truck, List<Task>> entry : solution.entrySet()) {
            Truck truck = entry.getKey();
            List<Task> tasks = entry.getValue();

            // 计算车辆负载（使用任务的weight字段）
            double totalWeight = tasks.stream()
                    .mapToDouble(t -> t.getWeight() != null ? t.getWeight().doubleValue() : 0)
                    .sum();

            // 计算成本 = 负载惩罚 + 任务数量成本
            double capacity = truck.getMaxWeight() != null ? truck.getMaxWeight().doubleValue() : 100;
            double loadPenalty = totalWeight > capacity ? (totalWeight - capacity) * 10 : 0;
            double taskCost = tasks.size() * 10; // 每个任务基础成本

            totalCost += loadPenalty + taskCost;
        }

        return totalCost;
    }

    // ==================== 全局多目标派单（阶段III：接入线上主流程） ====================

    /** 全局派单结果：taskId → truckId 配对，附先到先得基线成本对比 */
    public static class AssignResult {
        private final Map<Integer, Integer> pairs;
        private final double planCost;
        private final double baselineCost;
        private final double planCarbonKg;

        public AssignResult(Map<Integer, Integer> pairs, double planCost, double baselineCost, double planCarbonKg) {
            this.pairs = pairs;
            this.planCost = planCost;
            this.baselineCost = baselineCost;
            this.planCarbonKg = planCarbonKg;
        }

        public double getPlanCarbonKg() {
            return planCarbonKg;
        }

        public Map<Integer, Integer> getPairs() {
            return pairs;
        }

        public double getPlanCost() {
            return planCost;
        }

        public double getBaselineCost() {
            return baselineCost;
        }

        /** 相对先到先得基线的成本降幅（%） */
        public double improvementPct() {
            return baselineCost > 0 ? Math.max(0, (baselineCost - planCost) / baselineCost * 100) : 0;
        }
    }

    // 多目标权重（标量化口径）：
    //   空驶公里 → C2 单位成本裁决（同批任务吨公里固定，成本最小化即单位成本最小化）+ U4 空驶 + G 碳排（与空驶同源）
    //   装载缺口² → U2 装载率过程目标（二次罚兼顾负载均衡）
    //   等待小时 → T2 响应 + 任务书"全局车辆总体等待时间"目标
    //   吨公里收益 → 任务书"总运输达成吨位"目标 + 吞吐激励（蝇头小利任务自然落选）
    private static final double W_EMPTY = 1.0;
    private static final double W_MISMATCH = 60.0;
    private static final double W_WAIT = 3.0;
    private static final double W_VALUE = 0.02;
    /** 碳排显式目标：柴油 0.78 kg/km × 碳价 0.1 元/kg（独立于空驶权重的第 5 个优化目标） */
    private static final double W_CARBON = 0.1;
    private static final double CARBON_KG_PER_KM = 0.78;
    private static final double INFEASIBLE_PENALTY = 1e6;

    /**
     * 全局多目标派单：为一批"空闲车辆 × 待分配任务"求一对一分配（每车本轮至多 1 单）。
     * 求解：固定种子(42)模拟退火——同批次状态相同则方案相同，派单决策可复现；
     * 基线：按原先到先得顺序（车辆 id 序 × 任务 id 序取首个可行任务）计算同一成本函数。
     */
    public AssignResult multiObjectiveAssign(List<Truck> idleTrucks, List<Task> tasks) {
        int n = Math.min(tasks.size(), 40);
        int m = Math.min(idleTrucks.size(), 20);
        List<Task> ts = new ArrayList<>(tasks.subList(0, n));
        List<Truck> vs = new ArrayList<>(idleTrucks.subList(0, m));
        long now = System.currentTimeMillis();

        // 预计算：空驶km / 可行性 / 等待小时 / 吨公里收益
        double[][] emptyKm = new double[n][m];
        boolean[][] feasible = new boolean[n][m];
        double[] waitH = new double[n];
        double[] value = new double[n];
        for (int i = 0; i < n; i++) {
            Task task = ts.get(i);
            double[] from = parseLatLon(task.getStart());
            double[] dest = parseLatLon(task.getDestination());
            double haul = (from != null && dest != null)
                    ? DistanceUtils.calculateDistance(from[0], from[1], dest[0], dest[1]) : 0;
            value[i] = nz(task.getWeight()).doubleValue() * haul;
            if (task.getCreateTime() != null) {
                waitH[i] = Math.min(24.0, (now - task.getCreateTime().getTime()) / 3600000.0);
            }
            for (int j = 0; j < m; j++) {
                Truck truck = vs.get(j);
                double[] tp = parseLatLon(truck.getLocation());
                emptyKm[i][j] = (from == null || tp == null)
                        ? 1e9
                        : DistanceUtils.calculateDistance(tp[0], tp[1], from[0], from[1]);
                feasible[i][j] = from != null && tp != null && truck.getMaxWeight() != null
                        && nz(task.getWeight()).compareTo(nz(truck.getMaxWeight())) <= 0
                        && nz(task.getVolume()).compareTo(nz(truck.getMaxVol())) <= 0;
            }
        }

        // 成本函数：assign[i] = 车辆下标或 -1（不分配），每车至多 1 单
        java.util.function.ToDoubleFunction<int[]> costFn = assign -> {
            double f = 0;
            for (int i = 0; i < n; i++) {
                int j = assign[i];
                if (j < 0) continue;
                if (!feasible[i][j]) {
                    f += INFEASIBLE_PENALTY;
                    continue;
                }
                double cap = Math.max(1e-9, nz(vs.get(j).getMaxWeight()).doubleValue());
                double gap = Math.max(0, 1.0 - nz(ts.get(i).getWeight()).doubleValue() / cap);
                f += W_EMPTY * emptyKm[i][j]
                        + W_MISMATCH * gap * gap
                        + W_WAIT * waitH[i]
                        + W_CARBON * CARBON_KG_PER_KM * emptyKm[i][j]
                        - W_VALUE * value[i];
            }
            return f;
        };

        // 初始解：任务按吨公里收益降序 → 各取空驶最小的可行空闲车
        int[] cur = new int[n];
        Arrays.fill(cur, -1);
        boolean[] used = new boolean[m];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(value[b], value[a]));
        for (int idx : order) {
            int bestJ = -1, bestKm = Integer.MAX_VALUE;
            for (int j = 0; j < m; j++) {
                if (used[j] || !feasible[idx][j]) continue;
                if (emptyKm[idx][j] < bestKm) { bestKm = (int) emptyKm[idx][j]; bestJ = j; }
            }
            if (bestJ >= 0) { cur[idx] = bestJ; used[bestJ] = true; }
        }
        double curCost = costFn.applyAsDouble(cur);
        int[] best = cur.clone();
        double bestCost = curCost;

        // 基线：先到先得（车辆 id 序 × 任务 id 序，首个可行任务）
        int[] base = new int[n];
        Arrays.fill(base, -1);
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < n; i++) {
                if (base[i] >= 0 || !feasible[i][j]) continue;
                base[i] = j;
                break;
            }
        }
        double baseCost = costFn.applyAsDouble(base);

        // 固定种子模拟退火：邻域 = 随机改派/取消一个任务（目标车已有任务则交换，保持每车≤1单）
        // 温度自适应：初温按初始解成本量级缩放，并保证迭代结束温度降到 1.0
        // （固定初温 200 对小批次成本(~10)过热，退火近似随机游走，解不出基线）
        Random rng = new Random(42);
        int iterations = Math.min(4000, 200 * Math.max(n, 1));
        double temp = Math.max(20.0, Math.abs(curCost) * 0.3);
        double coolRate = Math.pow(1.0 / temp, 1.0 / Math.max(iterations, 1));
        for (int it = 0; it < iterations; it++) {
            int[] cand = cur.clone();
            int i = rng.nextInt(n);
            int j = rng.nextInt(m + 1) - 1;
            if (j >= 0) {
                for (int k = 0; k < n; k++) {
                    if (k != i && cand[k] == j) { cand[k] = cand[i]; break; }
                }
            }
            cand[i] = j;
            double c2 = costFn.applyAsDouble(cand);
            if (c2 <= curCost || rng.nextDouble() < Math.exp(-(c2 - curCost) / temp)) {
                cur = cand;
                curCost = c2;
                if (curCost < bestCost) { bestCost = curCost; best = cur.clone(); }
            }
            temp *= coolRate;
        }

        Map<Integer, Integer> pairs = new LinkedHashMap<>();
        double carbonKg = 0;
        for (int i = 0; i < n; i++) {
            if (best[i] >= 0 && feasible[i][best[i]]) {
                pairs.put(ts.get(i).getId(), vs.get(best[i]).getId());
                carbonKg += emptyKm[i][best[i]] * CARBON_KG_PER_KM;
            }
        }
        log.info("全局多目标派单求解：任务{}×车{}，基线{}→优化{}（降幅{}%），预计空驶碳排{}kg，迭代{}次",
                n, m, String.format("%.1f", baseCost), String.format("%.1f", bestCost),
                String.format("%.1f", baseCost > 0 ? Math.max(0, (baseCost - bestCost) / baseCost * 100) : 0),
                String.format("%.1f", carbonKg), iterations);
        return new AssignResult(pairs, bestCost, baseCost, carbonKg);
    }

    private double[] parseLatLon(String s) {
        if (s == null || !s.contains(",")) return null;
        try {
            String[] p = s.split(",");
            return new double[]{Double.parseDouble(p[1].trim()), Double.parseDouble(p[0].trim())}; // {lat, lng}
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal nz(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }
}
