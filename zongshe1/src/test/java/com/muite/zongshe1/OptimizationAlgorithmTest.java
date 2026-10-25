package com.muite.zongshe1;

import com.muite.zongshe1.entity.Task;
import com.muite.zongshe1.entity.Truck;
import com.muite.zongshe1.utils.OptimizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 优化算法单元测试
 * 验证模拟退火算法和遗传算法的核心功能
 */
public class OptimizationAlgorithmTest {

    private OptimizationService optimizationService;
    private List<Truck> trucks;
    private List<Task> tasks;

    @BeforeEach
    public void setUp() {
        optimizationService = new OptimizationService();
        
        // 初始化测试数据
        trucks = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Truck truck = new Truck();
            truck.setId(i);
            truck.setMaxWeight(new BigDecimal(100)); // 每辆车最大载重100
            trucks.add(truck);
        }

        tasks = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Task task = new Task();
            task.setId(i);
            task.setWeight(new BigDecimal(20 + (i % 5) * 10)); // 任务重量在20-60之间
            tasks.add(task);
        }
    }

    /**
     * 测试模拟退火算法
     */
    @Test
    public void testSimulatedAnnealingOptimization() {
        System.out.println("=== 测试模拟退火算法 ===");
        
        long startTime = System.currentTimeMillis();
        Map<Truck, List<Task>> result = optimizationService.simulatedAnnealingOptimization(trucks, tasks);
        long endTime = System.currentTimeMillis();

        System.out.println("优化耗时: " + (endTime - startTime) + "ms");
        printResult(result);
    }

    /**
     * 测试遗传算法
     */
    @Test
    public void testGeneticAlgorithmOptimization() {
        System.out.println("\n=== 测试遗传算法 ===");
        
        long startTime = System.currentTimeMillis();
        Map<Truck, List<Task>> result = optimizationService.geneticAlgorithmOptimization(trucks, tasks);
        long endTime = System.currentTimeMillis();

        System.out.println("优化耗时: " + (endTime - startTime) + "ms");
        printResult(result);
    }

    /**
     * 打印优化结果
     */
    private void printResult(Map<Truck, List<Task>> result) {
        int totalTasks = 0;
        for (Map.Entry<Truck, List<Task>> entry : result.entrySet()) {
            Truck truck = entry.getKey();
            List<Task> truckTasks = entry.getValue();
            
            double totalWeight = truckTasks.stream()
                    .mapToDouble(t -> t.getWeight() != null ? t.getWeight().doubleValue() : 0)
                    .sum();

            System.out.println(String.format("车辆ID: %d, 任务数量: %d, 总重量: %.2f, 最大载重: %.2f",
                    truck.getId(),
                    truckTasks.size(),
                    totalWeight,
                    truck.getMaxWeight().doubleValue()));
            
            System.out.print("  任务ID列表: ");
            for (Task task : truckTasks) {
                System.out.print(task.getId() + "(" + task.getWeight() + "t) ");
            }
            System.out.println();
            totalTasks += truckTasks.size();
        }
        System.out.println("总任务数: " + totalTasks);
    }
}
