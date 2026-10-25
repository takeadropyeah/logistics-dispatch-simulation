package com.muite.zongshe1.controller;

import com.muite.zongshe1.entity.Task;
import com.muite.zongshe1.entity.Truck;
import com.muite.zongshe1.mapper.TaskMapper;
import com.muite.zongshe1.mapper.TruckMapper;
import com.muite.zongshe1.utils.OptimizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 优化算法控制器
 * 提供模拟退火算法和遗传算法的API接口
 */
@RestController
@RequestMapping("/optimization")
public class OptimizationController {

    @Autowired
    private OptimizationService optimizationService;

    @Autowired
    private TruckMapper truckMapper;

    @Autowired
    private TaskMapper taskMapper;

    /**
     * 使用模拟退火算法优化任务分配
     */
    @PostMapping("/simulated-annealing")
    public ResponseEntity<Map<String, Object>> optimizeWithSA() {
        try {
            List<Truck> trucks = truckMapper.selectAll();
            List<Task> tasks = taskMapper.selectAll();

            if (trucks.isEmpty() || tasks.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "没有足够的车辆或任务数据"
                ));
            }

            Map<Truck, List<Task>> result = optimizationService.simulatedAnnealingOptimization(trucks, tasks);

            // 转换结果格式
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "模拟退火算法优化完成");
            response.put("result", formatResult(result));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "优化失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 使用遗传算法优化任务分配
     */
    @PostMapping("/genetic-algorithm")
    public ResponseEntity<Map<String, Object>> optimizeWithGA() {
        try {
            List<Truck> trucks = truckMapper.selectAll();
            List<Task> tasks = taskMapper.selectAll();

            if (trucks.isEmpty() || tasks.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "没有足够的车辆或任务数据"
                ));
            }

            Map<Truck, List<Task>> result = optimizationService.geneticAlgorithmOptimization(trucks, tasks);

            // 转换结果格式
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "遗传算法优化完成");
            response.put("result", formatResult(result));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "优化失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 使用混合优化算法（遗传算法 + 模拟退火）
     * 先用遗传算法快速找到较好解，再用模拟退火精细优化
     */
    @PostMapping("/hybrid")
    public ResponseEntity<Map<String, Object>> optimizeWithHybrid() {
        try {
            List<Truck> trucks = truckMapper.selectAll();
            List<Task> tasks = taskMapper.selectAll();

            if (trucks.isEmpty() || tasks.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "没有足够的车辆或任务数据"
                ));
            }

            Map<Truck, List<Task>> result = optimizationService.hybridOptimization(trucks, tasks);

            // 转换结果格式
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "混合优化算法（遗传算法 + 模拟退火）优化完成");
            response.put("result", formatResult(result));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "优化失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取算法说明
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getAlgorithmInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("simulated_annealing", Map.of(
                "name", "模拟退火算法（类似吃鸡缩圈淘汰）",
                "description", "模拟退火算法是一种基于蒙特卡洛迭代求解策略的随机寻优算法，其来源于固体退火原理。算法以一定的概率来接受一个比当前解要差的解，因此有可能会跳出局部最优解。类似吃鸡游戏中的缩圈机制，随着迭代进行，搜索范围逐渐缩小（温度降低），最终收敛到全局最优解。",
                "parameters", Map.of(
                        "temperature", "初始温度，控制初始接受较差解的概率",
                        "cooling_rate", "冷却速率，控制温度下降的快慢",
                        "min_temperature", "最低温度，算法终止条件"
                )
        ));

        info.put("genetic_algorithm", Map.of(
                "name", "遗传算法",
                "description", "遗传算法是一种基于自然选择和遗传变异的启发式搜索算法。通过模拟生物进化过程，在解空间中进行高效搜索。主要步骤包括：编码、初始种群生成、适应度评估、选择、交叉和变异。",
                "parameters", Map.of(
                        "population_size", "种群大小",
                        "max_generations", "最大迭代次数",
                        "crossover_rate", "交叉概率",
                        "mutation_rate", "变异概率"
                ),
                "steps", List.of(
                        "编码：将路径编码为染色体（如列表）",
                        "初始种群：随机生成多组路径",
                        "适应度评估：计算路径总代价",
                        "选择：保留适应度高的个体",
                        "交叉：交换部分路径片段",
                        "变异：随机改变某个节点的分配"
                )
        ));

        return ResponseEntity.ok(info);
    }

    /**
     * 格式化结果
     */
    private Map<String, Object> formatResult(Map<Truck, List<Task>> result) {
        Map<String, Object> formatted = new HashMap<>();

        int totalTasks = 0;

        for (Map.Entry<Truck, List<Task>> entry : result.entrySet()) {
            Truck truck = entry.getKey();
            List<Task> tasks = entry.getValue();

            Map<String, Object> truckInfo = new HashMap<>();
            truckInfo.put("truckId", truck.getId());
            truckInfo.put("taskCount", tasks.size());
            truckInfo.put("taskIds", tasks.stream().map(Task::getId).toList());

            // 计算车辆负载
            double totalWeight = tasks.stream()
                    .filter(t -> t.getWeight() != null)
                    .mapToDouble(t -> t.getWeight().doubleValue())
                    .sum();
            truckInfo.put("totalWeight", totalWeight);
            truckInfo.put("maxWeight", truck.getMaxWeight() != null ? truck.getMaxWeight().doubleValue() : 0);

            formatted.put("truck_" + truck.getId(), truckInfo);
            totalTasks += tasks.size();
        }

        formatted.put("summary", Map.of(
                "totalTrucks", result.size(),
                "totalTasks", totalTasks
        ));

        return formatted;
    }
}
