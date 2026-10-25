package com.muite.zongshe1.utils;

import com.muite.zongshe1.entity.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RouteSimplifier {
    
    private static final Logger log = LoggerFactory.getLogger(RouteSimplifier.class);
    
    private static final double ANGLE_THRESHOLD = 15.0;
    private static final double MIN_DISTANCE_THRESHOLD = 0.1;
    
    public List<Point> simplifyRoute(List<Point> originalPoints) {
        if (originalPoints == null || originalPoints.size() <= 2) {
            return originalPoints;
        }
        
        List<Point> simplifiedPoints = new ArrayList<>();
        simplifiedPoints.add(originalPoints.get(0));
        
        for (int i = 1; i < originalPoints.size() - 1; i++) {
            Point prev = originalPoints.get(i - 1);
            Point current = originalPoints.get(i);
            Point next = originalPoints.get(i + 1);
            
            double angle = calculateAngle(prev, current, next);
            
            double distanceToPrev = calculateDistance(prev, current);
            double distanceToNext = calculateDistance(current, next);
            
            if (angle < (180 - ANGLE_THRESHOLD) || angle > (180 + ANGLE_THRESHOLD) ||
                distanceToPrev > MIN_DISTANCE_THRESHOLD || distanceToNext > MIN_DISTANCE_THRESHOLD) {
                simplifiedPoints.add(current);
            }
        }
        
        simplifiedPoints.add(originalPoints.get(originalPoints.size() - 1));
        
        log.info("路径简化完成：原始点数 {} -> 简化后点数 {}，压缩率 {:.2f}%",
                originalPoints.size(), simplifiedPoints.size(),
                (1 - (double) simplifiedPoints.size() / originalPoints.size()) * 100);
        
        return simplifiedPoints;
    }
    
    private double calculateAngle(Point p1, Point p2, Point p3) {
        double[] v1 = createVector(p1, p2);
        double[] v2 = createVector(p2, p3);
        
        double dotProduct = v1[0] * v2[0] + v1[1] * v2[1];
        double mag1 = Math.sqrt(v1[0] * v1[0] + v1[1] * v1[1]);
        double mag2 = Math.sqrt(v2[0] * v2[0] + v2[1] * v2[1]);
        
        if (mag1 == 0 || mag2 == 0) {
            return 180.0;
        }
        
        double cosAngle = dotProduct / (mag1 * mag2);
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));
        
        return Math.toDegrees(Math.acos(cosAngle));
    }
    
    private double[] createVector(Point from, Point to) {
        double lat1 = DistanceUtils.parseLatitude(from.getLocation());
        double lon1 = DistanceUtils.parseLongitude(from.getLocation());
        double lat2 = DistanceUtils.parseLatitude(to.getLocation());
        double lon2 = DistanceUtils.parseLongitude(to.getLocation());
        
        return new double[]{lat2 - lat1, lon2 - lon1};
    }
    
    private double calculateDistance(Point p1, Point p2) {
        double lat1 = DistanceUtils.parseLatitude(p1.getLocation());
        double lon1 = DistanceUtils.parseLongitude(p1.getLocation());
        double lat2 = DistanceUtils.parseLatitude(p2.getLocation());
        double lon2 = DistanceUtils.parseLongitude(p2.getLocation());
        
        return DistanceUtils.calculateDistance(lat1, lon1, lat2, lon2);
    }
    
    public List<Point> douglasPeucker(List<Point> points, double epsilon) {
        if (points == null || points.size() <= 2) {
            return points;
        }
        
        double maxDistance = 0;
        int index = 0;
        int end = points.size() - 1;
        
        for (int i = 1; i < end; i++) {
            double distance = perpendicularDistance(points.get(i), points.get(0), points.get(end));
            if (distance > maxDistance) {
                index = i;
                maxDistance = distance;
            }
        }
        
        List<Point> result = new ArrayList<>();
        
        if (maxDistance > epsilon) {
            List<Point> left = douglasPeucker(points.subList(0, index + 1), epsilon);
            List<Point> right = douglasPeucker(points.subList(index, points.size()), epsilon);
            
            result.addAll(left.subList(0, left.size() - 1));
            result.addAll(right);
        } else {
            result.add(points.get(0));
            result.add(points.get(end));
        }
        
        return result;
    }
    
    private double perpendicularDistance(Point point, Point lineStart, Point lineEnd) {
        double lat = DistanceUtils.parseLatitude(point.getLocation());
        double lon = DistanceUtils.parseLongitude(point.getLocation());
        double lat1 = DistanceUtils.parseLatitude(lineStart.getLocation());
        double lon1 = DistanceUtils.parseLongitude(lineStart.getLocation());
        double lat2 = DistanceUtils.parseLatitude(lineEnd.getLocation());
        double lon2 = DistanceUtils.parseLongitude(lineEnd.getLocation());
        
        double A = lon - lon1;
        double B = lat - lat1;
        double C = lon2 - lon1;
        double D = lat2 - lat1;
        
        double dot = A * C + B * D;
        double lenSq = C * C + D * D;
        double param = lenSq != 0 ? dot / lenSq : -1;
        
        double xx, yy;
        
        if (param < 0) {
            xx = lon1;
            yy = lat1;
        } else if (param > 1) {
            xx = lon2;
            yy = lat2;
        } else {
            xx = lon1 + param * C;
            yy = lat1 + param * D;
        }
        
        double dx = lon - xx;
        double dy = lat - yy;
        
        return Math.sqrt(dx * dx + dy * dy);
    }
}
