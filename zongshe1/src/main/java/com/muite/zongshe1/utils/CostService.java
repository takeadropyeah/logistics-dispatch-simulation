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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CostService {

    private static final Logger log = LoggerFactory.getLogger(CostService.class);

    @Autowired
    private TruckMapper truckMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private SimulationService simulationService;

    // 成本计算参数（可配置）
    // A模型：k1 + k2 = 1，考虑等待时间(小时)和空驶里程(公里)的单位比例
    private double k1 = 0.0099; // 等待时间权重 (1:100比例)
    private double k2 = 0.9901; // 空驶里程权重 (1:100比例)
    // B模型：b1 + b2 = 1，考虑吨*等待时间和等待时间的单位比例
    private double b1 = 0.9; // 吨*等待时间权重
    private double b2 = 0.1; // 最慢货物等待时间权重
    // C模型：c1 + c2 + c3 = 1，所有项都是无单位比例
    private double c1 = 0.4; // 空驶率权重
    private double c2 = 0.4; // 等待时间占比权重
    private double c3 = 0.2; // 最长等待时间权重
    // 保留k3以兼容现有代码
    private double k3 = 0.2;
    // D模型：d1 + d2 = 1，两项都是运能差异(吨)
    private double d1 = 0.8; // 总运能差异权重
    private double d2 = 0.2; // 最低运能差异权重
    // E模型：r1 + r2 + r3 = 1，考虑单位差异
    private double r1 = 0.4; // 等待时间标准差权重 (小时)
    private double r2 = 0.4; // 拿货时间标准差权重 (小时)
    private double r3 = 0.2; // 运距平方权重 (公里²)

    // 大综合权重
    private double lambda1 = 0.2; // costA权重
    private double lambda2 = 0.2; // costB权重
    private double lambda3 = 0.2; // costC权重
    private double lambda4 = 0.2; // costD权重
    private double lambda5 = 0.2; // costE权重

    /**
     * 计算直接成本 (A)
     * cost = k₁ * 所有车辆的等待时间 + k₂ * 所有车空驶里程
     */
    public double calculateCostA() {
        // 获取所有车辆的等待时间（小时）
        double totalWaitingTime = getTotalWaitingTimeHours();
        
        // 获取所有车辆的空驶里程（公里）
        double totalEmptyMileage = getTotalEmptyMileage();
        
        double cost = k1 * totalWaitingTime + k2 * totalEmptyMileage;
        log.info("Cost A: 总等待时间={}小时, 总空驶里程={}公里, 成本={}", totalWaitingTime, totalEmptyMileage, cost);
        return cost;
    }

    /**
     * 计算运货快成本 (B)
     * cost = k₁ * 所有货物（吨*等待时间） + k₂ * 最慢货物等待时间
     */
    public double calculateCostB() {
        // 获取所有货物的吨*等待时间
        double totalWeightTime = getTotalWeightTime();
        
        // 获取最慢货物等待时间（小时）
        double maxGoodsWaitingTime = getMaxGoodsWaitingTime();
        
        // 计算基础成本
        double baseCost = b1 * totalWeightTime + b2 * maxGoodsWaitingTime;
        // 缩小成本B的结果，使其与其他成本模型数值相当
        double cost = baseCost * 0.0001; // 缩小10000倍
        log.info("Cost B: 总吨*等待时间={}, 最慢货物等待时间={}小时, 成本={}", totalWeightTime, maxGoodsWaitingTime, cost);
        return cost;
    }

    /**
     * 计算效率+公平成本 (C)
     * cost = k₁ * (总空驶里程/总里程) + k₂ * (总等待时间/总运输时间) + k₃ * 等最长车的时间
     */
    public double calculateCostC() {
        // 总空驶里程
        double totalEmptyMileage = getTotalEmptyMileage();
        // 总里程
        double totalMileage = getTotalMileage();
        // 空驶率（如果总里程为0，使用默认空驶率0.1）
        double emptyRate = totalMileage > 0 ? totalEmptyMileage / totalMileage : 0.1;
        
        // 总等待时间
        double totalWaitingTime = getTotalWaitingTimeHours();
        // 总运输时间（使用实际运输时间或默认100小时）
        double totalTransportTime = Math.max(getTotalTransportTime(), 100);
        // 等待时间占比（如果等待时间为0，使用默认值0.05）
        double waitingRate = totalTransportTime > 0 ? totalWaitingTime / totalTransportTime : 0.05;
        if (waitingRate == 0 && totalWaitingTime == 0) {
            waitingRate = 0.05; // 默认等待时间占比
        }
        
        // 最长等待时间（如果没有等待，使用默认值0.5小时）
        double maxWaitingTime = getMaxWaitingTime();
        if (maxWaitingTime == 0) {
            maxWaitingTime = 0.5; // 默认最长等待时间
        }
        
        // 计算基础成本
        double baseCost = c1 * emptyRate + c2 * waitingRate + c3 * maxWaitingTime;
        // 放大成本C的结果，使其与其他成本模型数值相当
        double cost = baseCost * 500; // 放大500倍
        log.info("Cost C: 空驶率={}, 等待时间占比={}, 最长等待时间={}小时, 成本={}", emptyRate, waitingRate, maxWaitingTime, cost);
        return cost;
    }

    /**
     * 计算平台成本 (D)
     * cost = k₁ * (总运能 - 总实际运能) + k₂ * 最低的(运能-实际运能)
     */
    public double calculateCostD() {
        // 总运能（吨）
        double totalCapacity = getTotalCapacity();
        // 总实际运能（吨）
        double totalActualCapacity = getTotalActualCapacity();
        // 运能差异（取正值，表示未利用的运能）
        double capacityDifference = Math.max(0, totalCapacity - totalActualCapacity);
        
        // 最低的(运能-实际运能)
        double minCapacityDifference = getMinCapacityDifference();
        
        // 计算基础成本
        double baseCost = d1 * capacityDifference + d2 * minCapacityDifference;
        // 缩小成本D的结果，使其与其他成本模型数值相当
        double cost = baseCost * 0.5; // 缩小2倍
        log.info("Cost D: 总运能差异={}吨, 最低运能差异={}吨, 成本={}", capacityDifference, minCapacityDifference, cost);
        return cost;
    }

    /**
     * 计算风险成本 (E)
     * cost = 基础成本(A/B/C/D) + r₁ * 车辆等待时间标准差 + r₂ * 拿货时间标准差 + r₃*(运距)²
     */
    public double calculateCostE(String baseCostType) {
        // 基础成本
        double baseCost = 0;
        switch (baseCostType) {
            case "A":
                baseCost = calculateCostA();
                break;
            case "B":
                baseCost = calculateCostB();
                break;
            case "C":
                baseCost = calculateCostC();
                break;
            case "D":
                baseCost = calculateCostD();
                break;
            default:
                baseCost = calculateCostA();
        }
        
        // 车辆等待时间标准差
        double waitingTimeStd = getWaitingTimeStd();
        
        // 拿货时间标准差（需要缩小，因为数值太大）
        double pickupTimeStd = getPickupTimeStd() * 0.01; // 缩小100倍
        
        // 平均运距的平方（需要缩小）
        double avgDistance = getAverageDistance();
        double distanceSquared = avgDistance * avgDistance * 0.0001; // 缩小10000倍
        
        // 计算成本
        double cost = baseCost + r1 * waitingTimeStd + r2 * pickupTimeStd + r3 * distanceSquared;
        log.info("Cost E: 基础成本={}, 等待时间标准差={}, 拿货时间标准差={}, 运距平方={}, 成本={}", 
                baseCost, waitingTimeStd, pickupTimeStd, distanceSquared, cost);
        return cost;
    }

    /**
     * 计算大综合成本 (F)
     * cost = λ₁*costₐ + λ₂*costᵦ + λ₃*cost_c + λ₄*cost_d + λ₅*cost_e
     */
    public double calculateCostF() {
        double costA = calculateCostA();
        double costB = calculateCostB();
        double costC = calculateCostC();
        double costD = calculateCostD();
        double costE = calculateCostE("A"); // 使用A作为基础成本
        
        double cost = lambda1 * costA + lambda2 * costB + lambda3 * costC + lambda4 * costD + lambda5 * costE;
        log.info("Cost F: A={}, B={}, C={}, D={}, E={}, 总成本={}", costA, costB, costC, costD, costE, cost);
        return cost;
    }

    /**
     * 获取所有成本计算结果
     */
    public Map<String, Object> getAllCosts() {
        Map<String, Object> result = new HashMap<>();
        
        result.put("costA", calculateCostA());
        result.put("costB", calculateCostB());
        result.put("costC", calculateCostC());
        result.put("costD", calculateCostD());
        result.put("costE", calculateCostE("A"));
        result.put("costF", calculateCostF());
        
        // 添加详细数据
        result.put("details", getCostDetails());
        
        return result;
    }

    /**
     * 获取成本计算的详细数据
     */
    private Map<String, Object> getCostDetails() {
        Map<String, Object> details = new HashMap<>();
        
        details.put("totalWaitingTime", getTotalWaitingTimeHours());
        details.put("totalEmptyMileage", getTotalEmptyMileage());
        details.put("totalWeightTime", getTotalWeightTime());
        details.put("maxGoodsWaitingTime", getMaxGoodsWaitingTime());
        details.put("totalMileage", getTotalMileage());
        details.put("totalTransportTime", getTotalTransportTime());
        details.put("maxWaitingTime", getMaxWaitingTime());
        details.put("totalCapacity", getTotalCapacity());
        details.put("totalActualCapacity", getTotalActualCapacity());
        details.put("minCapacityDifference", getMinCapacityDifference());
        details.put("waitingTimeStd", getWaitingTimeStd());
        details.put("pickupTimeStd", getPickupTimeStd());
        details.put("averageDistance", getAverageDistance());
        
        return details;
    }

    // ===== 辅助方法 =====

    /**
     * 获取所有车辆的等待时间（小时）
     */
    private double getTotalWaitingTimeHours() {
        // 从SimulationService获取等待计数器
        Map<Integer, Integer> waitCounters = simulationService.getTruckWaitCounters();
        int totalCycles = waitCounters.values().stream().mapToInt(Integer::intValue).sum();
        // 转换为小时（每个周期30分钟，即0.5小时）
        return totalCycles * 0.5;
    }

    /**
     * 获取所有车辆的空驶里程（公里）
     */
    private double getTotalEmptyMileage() {
        // 这里简化计算，实际应该从历史数据中获取
        // 暂时返回一个估算值
        return 100.0;
    }

    /**
     * 获取所有货物的吨*等待时间
     */
    private double getTotalWeightTime() {
        List<Goods> goodsList = goodsService.selectAll();
        double total = 0;
        
        for (Goods goods : goodsList) {
            if (goods.getWeight() != null && goods.getCreateTime() != null && goods.getLoadTime() != null) {
                Duration duration = Duration.between(goods.getCreateTime(), goods.getLoadTime());
                double hours = duration.toMinutes() / 60.0;
                double weight = goods.getWeight().doubleValue();
                total += weight * hours;
            }
        }
        
        return total;
    }

    /**
     * 获取最慢货物等待时间（小时）
     */
    private double getMaxGoodsWaitingTime() {
        List<Goods> goodsList = goodsService.selectAll();
        double maxHours = 0;
        
        for (Goods goods : goodsList) {
            if (goods.getCreateTime() != null && goods.getLoadTime() != null) {
                Duration duration = Duration.between(goods.getCreateTime(), goods.getLoadTime());
                double hours = duration.toMinutes() / 60.0;
                if (hours > maxHours) {
                    maxHours = hours;
                }
            }
        }
        
        return maxHours;
    }

    /**
     * 获取总里程（公里）
     */
    private double getTotalMileage() {
        // 从SimulationService获取总里程
        Map<Integer, Double> totalDistanceMap = simulationService.getTruckTotalDistance();
        return totalDistanceMap.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /**
     * 获取总运输时间（小时）
     */
    private double getTotalTransportTime() {
        // 简化计算，实际应该从历史数据中获取
        return 100.0;
    }

    /**
     * 获取最长等待时间（小时）
     */
    private double getMaxWaitingTime() {
        Map<Integer, Integer> waitCounters = simulationService.getTruckWaitCounters();
        if (waitCounters.isEmpty()) {
            return 0;
        }
        int maxCycles = waitCounters.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return maxCycles * 0.5; // 每个周期30分钟
    }

    /**
     * 获取总运能（吨）
     */
    private double getTotalCapacity() {
        List<Truck> trucks = truckMapper.selectAll();
        return trucks.stream()
                .filter(truck -> truck.getMaxWeight() != null)
                .mapToDouble(truck -> truck.getMaxWeight().doubleValue())
                .sum();
    }

    /**
     * 获取总实际运能（吨）
     * 只计算当前运输中的货物，避免历史数据影响
     */
    private double getTotalActualCapacity() {
        List<Goods> goodsList = goodsService.selectAll();
        // 只计算运输中的货物（状态为运输中或取货中）
        return goodsList.stream()
                .filter(goods -> goods.getWeight() != null)
                .filter(goods -> "运输中".equals(goods.getStatus()) || "取货中".equals(goods.getStatus()))
                .mapToDouble(goods -> goods.getWeight().doubleValue())
                .sum();
    }

    /**
     * 获取最低的(运能-实际运能)
     */
    private double getMinCapacityDifference() {
        List<Truck> trucks = truckMapper.selectAll();
        if (trucks.isEmpty()) {
            return 0;
        }
        
        double minDifference = Double.MAX_VALUE;
        for (Truck truck : trucks) {
            if (truck.getMaxWeight() != null) {
                double capacity = truck.getMaxWeight().doubleValue();
                // 简化计算，实际应该根据车辆当前运输的货物计算
                double actual = capacity * 0.7; // 假设平均利用率70%
                double difference = capacity - actual;
                if (difference < minDifference) {
                    minDifference = difference;
                }
            }
        }
        
        return minDifference;
    }

    /**
     * 获取车辆等待时间标准差
     */
    private double getWaitingTimeStd() {
        Map<Integer, Integer> waitCounters = simulationService.getTruckWaitCounters();
        if (waitCounters.isEmpty()) {
            return 0;
        }
        
        List<Double> waitingTimes = waitCounters.values().stream()
                .map(cycles -> cycles * 0.5) // 转换为小时
                .collect(Collectors.toList());
        
        return calculateStandardDeviation(waitingTimes);
    }

    /**
     * 获取拿货时间标准差
     */
    private double getPickupTimeStd() {
        List<Goods> goodsList = goodsService.selectAll();
        List<Double> pickupTimes = new ArrayList<>();
        
        for (Goods goods : goodsList) {
            if (goods.getAssignTime() != null && goods.getLoadTime() != null) {
                Duration duration = Duration.between(goods.getAssignTime(), goods.getLoadTime());
                double hours = duration.toMinutes() / 60.0;
                pickupTimes.add(hours);
            }
        }
        
        return calculateStandardDeviation(pickupTimes);
    }

    /**
     * 获取平均运距（公里）
     */
    private double getAverageDistance() {
        List<Goods> goodsList = goodsService.selectAll();
        if (goodsList.isEmpty()) {
            return 0;
        }
        
        double totalDistance = goodsList.stream()
                .filter(goods -> goods.getDistance() != null)
                .mapToDouble(goods -> goods.getDistance().doubleValue())
                .sum();
        
        return totalDistance / goodsList.size();
    }

    /**
     * 计算标准差
     */
    private double calculateStandardDeviation(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0);
        
        return Math.sqrt(variance);
    }

    // ===== Getters and Setters =====

    public double getK1() {
        return k1;
    }

    public void setK1(double k1) {
        this.k1 = k1;
    }

    public double getK2() {
        return k2;
    }

    public void setK2(double k2) {
        this.k2 = k2;
    }

    public double getK3() {
        return k3;
    }

    public void setK3(double k3) {
        this.k3 = k3;
    }

    public double getR1() {
        return r1;
    }

    public void setR1(double r1) {
        this.r1 = r1;
    }

    public double getR2() {
        return r2;
    }

    public void setR2(double r2) {
        this.r2 = r2;
    }

    public double getR3() {
        return r3;
    }

    public void setR3(double r3) {
        this.r3 = r3;
    }

    public double getLambda1() {
        return lambda1;
    }

    public void setLambda1(double lambda1) {
        this.lambda1 = lambda1;
    }

    public double getLambda2() {
        return lambda2;
    }

    public void setLambda2(double lambda2) {
        this.lambda2 = lambda2;
    }

    public double getLambda3() {
        return lambda3;
    }

    public void setLambda3(double lambda3) {
        this.lambda3 = lambda3;
    }

    public double getLambda4() {
        return lambda4;
    }

    public void setLambda4(double lambda4) {
        this.lambda4 = lambda4;
    }

    public double getLambda5() {
        return lambda5;
    }

    public void setLambda5(double lambda5) {
        this.lambda5 = lambda5;
    }
}
