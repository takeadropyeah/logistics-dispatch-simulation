package com.muite.zongshe1.service.impl;

import com.muite.zongshe1.entity.Truck;
import com.muite.zongshe1.mapper.TruckMapper;
import com.muite.zongshe1.service.TruckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TruckServiceImpl implements TruckService {
    @Autowired
    TruckMapper truckMapper;
    @Override
    public List<Truck> selectAll() {
        return truckMapper.selectAll();
    }
}
