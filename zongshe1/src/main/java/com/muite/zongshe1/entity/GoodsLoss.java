package com.muite.zongshe1.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class GoodsLoss {
    private Integer id;
    private Integer goodsId;
    private String lossType;
    private BigDecimal lossWeight;
    private BigDecimal lossValue;
    private LocalDateTime lossTime;
    private Integer factoryId;

    public GoodsLoss() {
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

    public String getLossType() {
        return lossType;
    }

    public void setLossType(String lossType) {
        this.lossType = lossType;
    }

    public BigDecimal getLossWeight() {
        return lossWeight;
    }

    public void setLossWeight(BigDecimal lossWeight) {
        this.lossWeight = lossWeight;
    }

    public BigDecimal getLossValue() {
        return lossValue;
    }

    public void setLossValue(BigDecimal lossValue) {
        this.lossValue = lossValue;
    }

    public LocalDateTime getLossTime() {
        return lossTime;
    }

    public void setLossTime(LocalDateTime lossTime) {
        this.lossTime = lossTime;
    }

    public Integer getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Integer factoryId) {
        this.factoryId = factoryId;
    }

    @Override
    public String toString() {
        return "GoodsLoss{" +
                "id=" + id +
                ", goodsId=" + goodsId +
                ", lossType='" + lossType + '\'' +
                ", lossWeight=" + lossWeight +
                ", lossValue=" + lossValue +
                ", lossTime=" + lossTime +
                ", factoryId=" + factoryId +
                '}';
    }
}
