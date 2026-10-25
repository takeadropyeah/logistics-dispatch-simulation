package com.muite.zongshe1.mapper;

import com.muite.zongshe1.entity.Route;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RouteMapper {
    List<Route> selectByStartAndDestination(String start, String destination);
}
