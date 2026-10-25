package com.muite.zongshe1.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class GoodsDemand {
    private Integer id;
    private Integer goodsId;
    private String demandType;
    private LocalDateTime demandTime;
    private String region;
    private String urgency;
    private String status;

    public GoodsDemand() {
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

    public String getDemandType() {
        return demandType;
    }

    public void setDemandType(String demandType) {
        this.demandType = demandType;
    }

    public LocalDateTime getDemandTime() {
        return demandTime;
    }

    public void setDemandTime(LocalDateTime demandTime) {
        this.demandTime = demandTime;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "GoodsDemand{" +
                "id=" + id +
                ", goodsId=" + goodsId +
                ", demandType='" + demandType + '\'' +
                ", demandTime=" + demandTime +
                ", region='" + region + '\'' +
                ", urgency='" + urgency + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
