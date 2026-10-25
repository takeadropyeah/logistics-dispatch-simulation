package com.muite.zongshe1.service.impl;

import com.muite.zongshe1.entity.Point;
import com.muite.zongshe1.mapper.PointMapper;
import com.muite.zongshe1.service.PointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PointServiceImpl implements PointService {
    @Autowired
    PointMapper pointMapper;

    @Override
    public List<Point> selectByName(String name) {
        return pointMapper.selectByName(name);
    }

    @Override
    public List<Point> selectAll() {
        return pointMapper.selectAll();
    }
}
