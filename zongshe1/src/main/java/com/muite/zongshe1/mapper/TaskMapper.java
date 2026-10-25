package com.muite.zongshe1.mapper;

import com.muite.zongshe1.entity.Task;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskMapper {


    public List<Task> selectAll();

    /**
     * 更新任务的truckId
     * @param task
     */
    void updateTaskTruckId(Task task);

    /**
     * 获取车辆当前任务
     * @param truckId
     * @return
     */
    Task selectByTruckId(Integer truckId);

    /**
     * 获取车辆所有任务（包括已完成）
     * @param truckId
     * @return
     */
    @Select("SELECT * FROM task WHERE truck_id = #{truckId}")
    List<Task> selectTasksByTruckId(Integer truckId);

    /**
     * 插入任务
     * @param task
     */
    void insert(Task task);

    /**
     * 批量插入任务
     * @param taskList
     */
    void batchInsert(List<Task> taskList);


    /**
     * 查询所有未分配车辆的任务（truck_id 为 null 且状态不为运输中）
     * @return 未分配任务列表
     */
     @Select("SELECT * FROM task WHERE truck_id IS NULL AND (status IS NULL OR (status != '已完成' AND status != '已取消' AND status != '运输中'))")
    List<Task> findByTruckIdIsNull();


    /**
     * 统计未完成的任务数量
     * @return 数量
     */
    @Select("SELECT COUNT(*) FROM task WHERE status != '已完成'")
    int countIncompleteTasks();

    /**
     * 根据ID查询任务
     * @param id
     * @return
     */
    Task selectByPrimaryKey(Integer id);

    /**
     * 更新任务的status
     * @param task
     */
    void updateStatus(Task task);

    /**
     * 查询所有活跃任务（运输中或取货中）
     * @return 活跃任务列表
     */
    @Select("SELECT * FROM task WHERE status IN ('运输中', '取货中')")
    List<Task> selectActiveTasks();


}
