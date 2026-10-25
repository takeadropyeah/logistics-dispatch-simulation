package com.muite.zongshe1.mapper;

import com.muite.zongshe1.entity.TaskRoutePoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskRoutePointMapper {
    // 批量插入任务的路径点
    void batchInsert(List<TaskRoutePoint> points);

    // 根据任务ID查询路径点（按顺序排序）
    List<TaskRoutePoint> selectByTaskId(Integer taskId);

    // 根据任务ID删除路径点
    void deleteByTaskId(Integer taskId);

    // 删除已完成任务的路径点
    void deleteCompletedTaskRoutePoints();

    // 删除所有路径点
    void deleteAll();

    @Select("select * from task_route_point")
    List<TaskRoutePoint> selectAll();
}