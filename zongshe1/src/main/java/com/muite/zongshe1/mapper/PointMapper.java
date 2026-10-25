package com.muite.zongshe1.mapper;


import com.muite.zongshe1.entity.Point;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
* @author 14693
* @description 针对表【point】的数据库操作Mapper
* @createDate 2025-09-02 10:37:07
* @Entity generator.com/muite/zongshe1/entity.Point
*/
@Mapper
public interface PointMapper {

    int deleteByPrimaryKey(Long id);

    int insert(Point record);

    int insertSelective(Point record);

    Point selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(Point record);

    int updateByPrimaryKey(Point record);
    List<Point> selectByName(String name);
    List<Point> selectAll();

}
