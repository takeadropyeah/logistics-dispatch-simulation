package com.muite.zongshe1.service;

import com.muite.zongshe1.entity.Point;

import java.util.List;

public interface PointService {
    List<Point> selectByName(String name);

    List<Point> selectAll();
}
