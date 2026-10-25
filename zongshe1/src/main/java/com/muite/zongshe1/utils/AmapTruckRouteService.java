package com.muite.zongshe1.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muite.zongshe1.entity.Point;
import com.muite.zongshe1.entity.Truck;
import com.muite.zongshe1.entity.TruckRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class AmapTruckRouteService {
    @Value("${amap.key}")
    private String amapKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // 引入日志，打印原始响应方便排查
    private static final Logger log = LoggerFactory.getLogger(AmapTruckRouteService.class);

    public AmapTruckRouteService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用高德货车路径规划API
     * @param start 起点经纬度（格式："经度,纬度"，注意高德坐标系为GCJ02）
     * @param destination 终点经纬度（格式同上）
     * @param truck 货车信息（用于限制车型、重量等参数）
     * @return 路径规划结果（封装为项目中的TruckRoute实体）
     */
    public TruckRoute getTruckRoute(String start, String destination, Truck truck) {
        // 1. 数据库中的位置已经是"经度,纬度"格式，直接使用
        
        if (!start.contains(",") || !destination.contains(",")) {
            log.error("经纬度格式错误：start={}, destination={}", start, destination);
            return new TruckRoute(null, null, null, false, "经纬度格式错误");
        }
        
        // 2. 构建API请求参数（使用普通驾车路径规划API，货车API需要特殊权限）
        String url = String.format(
                "https://restapi.amap.com/v3/direction/driving?key=%s&origin=%s&destination=%s&strategy=0&extensions=all",
                amapKey,
                start,  // 直接使用"经度,纬度"格式
                destination
        );

        try {
            // 2. 调用API
            log.info("调用高德驾车路径规划API，URL：{}", url);
            String response = restTemplate.getForObject(url, String.class);

            log.info("高德API返回原始响应长度：{}", response != null ? response.length() : 0);
            JsonNode root = objectMapper.readTree(response);
            if (root == null || !root.has("status")) {
                return new TruckRoute(null, null, null, false, "API返回无status字段，响应：" + response);
            }

            // 3. 解析响应（根据高德API返回格式调整）
            String status = root.get("status").asText();
            if ("1".equals(status)) {
                if (root.has("route") && root.get("route").has("paths") && root.get("route").get("paths").size() > 0) {
                    JsonNode path = root.get("route").get("paths").get(0);
                    int distance = path.get("distance").asInt();
                    int duration = path.get("duration").asInt();

                    // 解析路径点（从 steps 提取，这是高德API驾车路径规划的标准格式）
                    List<Point> pathPoints = new ArrayList<>();

                    // 使用 LinkedHashSet 去重并保持顺序
                    java.util.Set<String> uniquePoints = new java.util.LinkedHashSet<>();

                    // 从 steps 提取路径点（高德API驾车路径规划没有 paths[0].polyline，只有 steps 中每个step有 polyline）
                    if (path.has("steps") && path.get("steps").isArray()) {
                        for (JsonNode step : path.get("steps")) {
                            if (step.has("polyline")) {
                                String polyline = step.get("polyline").asText();
                                if (!polyline.isEmpty()) {
                                    // 高德API返回的polyline是分号分隔的经纬度字符串（格式：lng1,lat1;lng2,lat2;...）
                                    // 直接分割，不需要解码
                                    String[] points = polyline.split(";");
                                    for (String p : points) {
                                        if (p.trim().isEmpty()) continue;
                                        uniquePoints.add(p.trim());
                                    }
                                }
                            }
                        }
                    }

                    log.info("从steps提取到的原始路径点数量：{}", uniquePoints.size());

                    // 转换为 Point 对象（不限制数量，确保路线精度）
                    for (String p : uniquePoints) {
                        String[] lonLat = p.split(",");
                        if (lonLat.length == 2) {
                            String location = lonLat[0].trim() + "," + lonLat[1].trim();
                            Point point = new Point();
                            point.setLocation(location);
                            point.setName("");
                            point.setType("route_point");
                            pathPoints.add(point);
                        }
                    }

                    // 日志打印路径点数量，便于调试
                    log.info("解析到的路径点数量：{}", pathPoints.size());
                    return new TruckRoute(distance, duration, pathPoints, true, null);
                } else {
                    return new TruckRoute(null, null, null, false, "无可用路径");
                }
            } else {
                String errorMsg = root.has("info") ? root.get("info").asText() : "规划失败";
                return new TruckRoute(null, null, null, false, errorMsg);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new TruckRoute(null, null, null, false, "API调用异常：" + e.getMessage());
        }
    }

    /**
     * 解码高德地图polyline编码
     * 高德使用类似Google Polyline的编码，但精度为6位小数
     * @param polyline 编码字符串
     * @return 解码后的经纬度点列表（格式："经度,纬度"）
     */
    private List<String> decodeAmapPolyline(String polyline) {
        List<String> points = new ArrayList<>();
        if (polyline == null || polyline.isEmpty()) {
            return points;
        }
        
        // 如果包含逗号但不包含分号，可能已经是解码格式，直接分割
        if (polyline.contains(",") && !polyline.contains(";")) {
            // 尝试判断是否是编码格式（编码格式通常只包含字母和数字）
            if (polyline.matches("^[A-Za-z0-9]+$")) {
                // 编码格式，继续解码
            } else {
                // 已经是解码格式，尝试分割
                String[] coords = polyline.split(",");
                if (coords.length >= 2) {
                    for (int i = 0; i < coords.length - 1; i += 2) {
                        points.add(coords[i].trim() + "," + coords[i + 1].trim());
                    }
                    return points;
                }
            }
        }
        
        // 如果包含分号，尝试直接分割（未编码格式）
        if (polyline.contains(";")) {
            String[] rawPoints = polyline.split(";");
            for (String p : rawPoints) {
                if (p.trim().isEmpty()) continue;
                points.add(p.trim());
            }
            return points;
        }
        
        try {
            // 高德polyline解码算法
            int index = 0;
            int len = polyline.length();
            int lat = 0, lng = 0;
            
            while (index < len) {
                // 解码纬度
                int b, shift = 0, result = 0;
                do {
                    b = polyline.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lat += dlat;
                
                // 解码经度
                shift = 0;
                result = 0;
                do {
                    b = polyline.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lng += dlng;
                
                // 转换为经纬度（精度1e-6）
                double longitude = lng / 1000000.0;
                double latitude = lat / 1000000.0;
                
                points.add(String.format("%.6f,%.6f", longitude, latitude));
            }
            
            log.debug("Polyline解码完成，共{}个点", points.size());
        } catch (Exception e) {
            log.warn("Polyline解码失败，将尝试其他方式: {}", e.getMessage());
            points.clear();
        }
        
        return points;
    }
}

