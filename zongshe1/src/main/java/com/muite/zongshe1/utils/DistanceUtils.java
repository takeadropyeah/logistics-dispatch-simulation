package com.muite.zongshe1.utils;


public class DistanceUtils {
    /**
     * 从"经度,纬度"格式的字符串中解析出纬度
     */
    public static double parseLatitude(String locationStr) {
        if (locationStr == null || !locationStr.contains(",")) {
            throw new IllegalArgumentException("无效的经纬度格式：" + locationStr);
        }
        // 高德格式：经度,纬度 -> split[1]是纬度
        return Double.parseDouble(locationStr.split(",")[1].trim());
    }


    /**
     * 从"经度,纬度"格式的字符串中解析出经度
     */
    public static double parseLongitude(String locationStr) {
        if (locationStr == null || !locationStr.contains(",")) {
            throw new IllegalArgumentException("无效的经纬度格式：" + locationStr);
        }
        // 高德格式：经度,纬度 -> split[0]是经度
        return Double.parseDouble(locationStr.split(",")[0].trim());
    }


    /**
     * 计算两点经纬度之间的距离（公里）
     * @param lat1 车辆纬度
     * @param lon1 车辆经度
     * @param lat2 任务起点纬度
     * @param lon2 任务起点经度
     * @return 距离（公里）
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371; // 地球半径（公里）
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; // 距离（公里）
    }


}