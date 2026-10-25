package com.muite.zongshe1.entity;

import java.time.LocalDateTime;

public class GoodsWaiting {
    private Integer id;
    private Integer goodsId;
    private String waitingType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String waitingReason;
    private String waitingLocation;

    public GoodsWaiting() {
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

    public String getWaitingType() {
        return waitingType;
    }

    public void setWaitingType(String waitingType) {
        this.waitingType = waitingType;
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

    public String getWaitingReason() {
        return waitingReason;
    }

    public void setWaitingReason(String waitingReason) {
        this.waitingReason = waitingReason;
    }

    public String getWaitingLocation() {
        return waitingLocation;
    }

    public void setWaitingLocation(String waitingLocation) {
        this.waitingLocation = waitingLocation;
    }

    @Override
    public String toString() {
        return "GoodsWaiting{" +
                "id=" + id +
                ", goodsId=" + goodsId +
                ", waitingType='" + waitingType + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", waitingReason='" + waitingReason + '\'' +
                ", waitingLocation='" + waitingLocation + '\'' +
                '}';
    }
}
