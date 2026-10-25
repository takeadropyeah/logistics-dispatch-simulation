package com.muite.zongshe1.controller;

import com.muite.zongshe1.entity.Point;
import com.muite.zongshe1.mapper.PointMapper;
import com.muite.zongshe1.service.PointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")

@Tag(name="Poi相关接口")
public class PointController {
    @Autowired
    PointService pointService;

    @Operation(summary = "根据名称查询Poi")
    @GetMapping("/point/{name}")
    public List<Point> select(@PathVariable String name) {
        List<Point> location= pointService.selectByName(name);
        return location;
    }

    @Operation(summary = "查询所有Poi")
    @GetMapping("/point")
    public List<Point> selectAll(){
        List<Point> locations=pointService.selectAll();
        return locations;
    }

}
