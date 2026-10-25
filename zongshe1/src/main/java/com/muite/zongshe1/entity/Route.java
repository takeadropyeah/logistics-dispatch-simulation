package com.muite.zongshe1.entity;

import java.math.BigDecimal;

public class Route {
    private Integer id;
    private String start;
    private String destination;
    private BigDecimal timeCost;
    private String passable;


    public Route() {
    }

    public Route(Integer id, String start, String destination, BigDecimal timeCost, String passable) {
        this.id = id;
        this.start = start;
        this.destination = destination;
        this.timeCost = timeCost;
        this.passable = passable;
    }

    /**
     * 获取
     * @return id
     */
    public Integer getId() {
        return id;
    }

    /**
     * 设置
     * @param id
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * 获取
     * @return start
     */
    public String getStart() {
        return start;
    }

    /**
     * 设置
     * @param start
     */
    public void setStart(String start) {
        this.start = start;
    }

    /**
     * 获取
     * @return destination
     */
    public String getDestination() {
        return destination;
    }

    /**
     * 设置
     * @param destination
     */
    public void setDestination(String destination) {
        this.destination = destination;
    }

    /**
     * 获取
     * @return timeCost
     */
    public BigDecimal getTimeCost() {
        return timeCost;
    }

    /**
     * 设置
     * @param timeCost
     */
    public void setTimeCost(BigDecimal timeCost) {
        this.timeCost = timeCost;
    }

    /**
     * 获取
     * @return passable
     */
    public String getPassable() {
        return passable;
    }

    /**
     * 设置
     * @param passable
     */
    public void setPassable(String passable) {
        this.passable = passable;
    }

    public String toString() {
        return "Route{id = " + id + ", start = " + start + ", destination = " + destination + ", timeCost = " + timeCost + ", passable = " + passable + "}";
    }
}
