package com.muite.zongshe1.controller;

import com.muite.zongshe1.entity.TaskRoutePoint;
import com.muite.zongshe1.service.TaskRoutePointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@Tag(name="路径相关接口")
public class TaskRoutePointController {

    @Autowired
    TaskRoutePointService taskRoutePointService;

    @GetMapping("/route")
    @Operation(summary = "查询所有路径")
    public List<TaskRoutePoint> selectAll() {
        return taskRoutePointService.selectAll();
    }

    @GetMapping("/route/{taskId}")
    @Operation(summary = "根据任务id查询路径上所有点")
    public List<TaskRoutePoint> selectByTaskId(@PathVariable int taskId) {
        return taskRoutePointService.selectByTaskId(taskId);
    }
}
