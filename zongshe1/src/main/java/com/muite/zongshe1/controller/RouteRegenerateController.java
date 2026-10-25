package com.muite.zongshe1.controller;

import com.muite.zongshe1.entity.Point;
import com.muite.zongshe1.entity.Task;
import com.muite.zongshe1.entity.TaskRoutePoint;
import com.muite.zongshe1.entity.Truck;
import com.muite.zongshe1.entity.TruckRoute;
import com.muite.zongshe1.mapper.TaskMapper;
import com.muite.zongshe1.mapper.TaskRoutePointMapper;
import com.muite.zongshe1.mapper.TruckMapper;
import com.muite.zongshe1.utils.AmapTruckRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@Tag(name="路径重新生成接口")
public class RouteRegenerateController {

    private static final Logger log = LoggerFactory.getLogger(RouteRegenerateController.class);

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TruckMapper truckMapper;

    @Autowired
    private TaskRoutePointMapper taskRoutePointMapper;

    @Autowired
    private AmapTruckRouteService amapTruckRouteService;

    @GetMapping("/route/regenerate")
    @Operation(summary = "重新生成所有活跃任务的路径点（使用新的点数量限制）")
    public String regenerateAllActiveTaskRoutes() {
        log.info("开始重新生成所有活跃任务的路径点...");

        List<Task> activeTasks = taskMapper.selectActiveTasks();
        log.info("找到 {} 个活跃任务", activeTasks.size());

        int successCount = 0;
        int failCount = 0;

        for (Task task : activeTasks) {
            try {
                Truck truck = truckMapper.selectByPrimaryKey(task.getTruckId().longValue());
                if (truck == null) {
                    log.warn("任务 {} 的车辆 {} 不存在", task.getId(), task.getTruckId());
                    failCount++;
                    continue;
                }

                regenerateTaskRoute(task, truck);
                successCount++;

                Thread.sleep(500);
            } catch (Exception e) {
                log.error("重新生成任务 {} 的路径点失败: {}", task.getId(), e.getMessage());
                failCount++;
            }
        }

        String result = String.format("路径点重新生成完成！成功: %d, 失败: %d", successCount, failCount);
        log.info(result);
        return result;
    }

    private void regenerateTaskRoute(Task task, Truck truck) {
        String taskStart = task.getStart();
        String taskDestination = task.getDestination();
        String truckLocation = truck.getLocation();

        List<TaskRoutePoint> allRoutePoints = new ArrayList<>();
        int maxTotalPoints = 15000;

        log.info("重新规划任务{}路线：车辆位置={}, 任务起点={}, 任务终点={}", 
            task.getId(), truckLocation, taskStart, taskDestination);

        // 1. 规划运输路线（正数sequence）
        int deliverySequence = 0;
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
            log.warn("任务{}运输路线规划失败: {}", task.getId(), deliveryRoute != null ? deliveryRoute.getErrorMsg() : "null");
        }

        if (deliverySequence < maxTotalPoints - 1000) {
            allRoutePoints.add(new TaskRoutePoint(
                    task.getId(),
                    taskDestination,
                    "卸货完成-" + task.getId(),
                    deliverySequence++
            ));
        }

        // 2. 规划取货路线（负数sequence，从车辆当前位置到任务起点）
        int pickupSequence = -1;
        if (truckLocation != null && !truckLocation.equals(taskStart)) {
            TruckRoute pickupRoute = amapTruckRouteService.getTruckRoute(truckLocation, taskStart, truck);
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
                log.warn("任务{}取货路线规划失败: {}", task.getId(), pickupRoute != null ? pickupRoute.getErrorMsg() : "null");
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

        log.info("任务{}路线规划完成，运输路线{}个点，取货路线{}个点，总计{}个点", 
            task.getId(), deliverySequence, Math.abs(pickupSequence), allRoutePoints.size());

        taskRoutePointMapper.deleteByTaskId(task.getId());
        if (!allRoutePoints.isEmpty()) {
            taskRoutePointMapper.batchInsert(allRoutePoints);
        }
    }
}
