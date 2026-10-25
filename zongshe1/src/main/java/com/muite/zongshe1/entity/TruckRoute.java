package com.muite.zongshe1.entity;
import java.util.List;


public class TruckRoute {
    // 路径总距离（米）
    private Integer distance;
    // 路径总时间（秒）
    private Integer duration;
    // 路径点坐标列表（经纬度）
    private List<Point> pathPoints;
    // 路径规划状态（成功/失败）
    private boolean success;
    // 错误信息（若失败）
    private String errorMsg;

    public TruckRoute() {
    }

    public TruckRoute(Integer distance, Integer duration, List<Point> pathPoints, boolean success, String errorMsg) {
        this.distance = distance;
        this.duration = duration;
        this.pathPoints = pathPoints;
        this.success = success;
        this.errorMsg = errorMsg;
    }

    /**
     * 获取
     * @return distance
     */
    public Integer getDistance() {
        return distance;
    }

    /**
     * 设置
     * @param distance
     */
    public void setDistance(Integer distance) {
        this.distance = distance;
    }

    /**
     * 获取
     * @return duration
     */
    public Integer getDuration() {
        return duration;
    }

    /**
     * 设置
     * @param duration
     */
    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    /**
     * 获取
     * @return pathPoints
     */
    public List<Point> getPathPoints() {
        return pathPoints;
    }

    /**
     * 设置
     * @param pathPoints
     */
    public void setPathPoints(List<Point> pathPoints) {
        this.pathPoints = pathPoints;
    }

    /**
     * 获取
     * @return success
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 设置
     * @param success
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * 获取
     * @return errorMsg
     */
    public String getErrorMsg() {
        return errorMsg;
    }

    /**
     * 设置
     * @param errorMsg
     */
    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public String toString() {
        return "TruckRoute{distance = " + distance + ", duration = " + duration + ", pathPoints = " + pathPoints + ", success = " + success + ", errorMsg = " + errorMsg + "}";
    }
}