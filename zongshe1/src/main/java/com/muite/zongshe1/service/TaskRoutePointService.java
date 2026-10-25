package com.muite.zongshe1.service;

import com.muite.zongshe1.entity.TaskRoutePoint;
import com.muite.zongshe1.mapper.TaskRoutePointMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public interface TaskRoutePointService {

    List<TaskRoutePoint> selectAll();


    List<TaskRoutePoint> selectByTaskId(int taskId);
}
