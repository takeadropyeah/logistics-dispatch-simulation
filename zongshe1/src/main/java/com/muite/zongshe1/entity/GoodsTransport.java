package com.muite.zongshe1.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class GoodsTransport {
    private Integer id;
    private Integer goodsId;
    private Integer taskId;
    private Integer truckId;
    private String transportType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal actualDistance;
    private String transportRegion;

    public GoodsTransport() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Integer goodsId) {
        this.goodsId = goodsId;
    }

    public Integer getTaskId() {
        return taskId;
    }

    public void setTaskId(Integer taskId) {
        this.taskId = taskId;
    }

    public Integer getTruckId() {
        return truckId;
    }

    public void setTruckId(Integer truckId) {
        this.truckId = truckId;
    }

    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public BigDecimal getActualDistance() {
        return actualDistance;
    }

    public void setActualDistance(BigDecimal actualDistance) {
        this.actualDistance = actualDistance;
    }

    public String getTransportRegion() {
        return transportRegion;
    }

    public void setTransportRegion(String transportRegion) {
        this.transportRegion = transportRegion;
    }

    @Override
    public String toString() {
        return "GoodsTransport{" +
                "id=" + id +
                ", goodsId=" + goodsId +
                ", taskId=" + taskId +
                ", truckId=" + truckId +
                ", transportType='" + transportType + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", actualDistance=" + actualDistance +
                ", transportRegion='" + transportRegion + '\'' +
                '}';
    }
}
