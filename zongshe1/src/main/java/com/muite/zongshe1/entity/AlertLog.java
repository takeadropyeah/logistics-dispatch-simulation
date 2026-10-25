package com.muite.zongshe1.entity;

import java.io.Serializable;
import java.util.Date;

/**
 * 告警日志实体类
 */
public class AlertLog implements Serializable {
    private Integer id;
    private Integer truckId;
    private Integer driverId; // 新增：关联司机ID
    private Integer taskId;
    private String type; // 拥堵, 故障, 超速

    // ... getters and setters ...
    
    public Integer getDriverId() {
        return driverId;
    }

    public void setDriverId(Integer driverId) {
        this.driverId = driverId;
    }
    private String level; // 一般, 严重
    private String location;
    private Date createTime;
    private Boolean isHandled;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getTruckId() {
        return truckId;
    }

    public void setTruckId(Integer truckId) {
        this.truckId = truckId;
    }

    public Integer getTaskId() {
        return taskId;
    }

    public void setTaskId(Integer taskId) {
        this.taskId = taskId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Boolean getIsHandled() {
        return isHandled;
    }

    public void setIsHandled(Boolean handled) {
        isHandled = handled;
    }
}
