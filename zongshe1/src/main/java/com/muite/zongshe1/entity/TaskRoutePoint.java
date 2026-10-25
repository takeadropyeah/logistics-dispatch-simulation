package com.muite.zongshe1.entity;

public class TaskRoutePoint {
    private Integer id;
    private Integer taskId; // 关联的任务ID
    private String pointLocation; // 路径点经纬度
    private String pointName; // 路径点名称
    private Integer sequence; // 顺序

    // 构造方法
    public TaskRoutePoint() {}

    public TaskRoutePoint(Integer taskId, String pointLocation, String pointName, Integer sequence) {
        this.taskId = taskId;
        this.pointLocation = pointLocation;
        this.pointName = pointName;
        this.sequence = sequence;
    }

    // getter和setter
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getTaskId() { return taskId; }
    public void setTaskId(Integer taskId) { this.taskId = taskId; }
    public String getPointLocation() { return pointLocation; }
    public void setPointLocation(String pointLocation) { this.pointLocation = pointLocation; }
    public String getPointName() { return pointName; }
    public void setPointName(String pointName) { this.pointName = pointName; }
    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }
}