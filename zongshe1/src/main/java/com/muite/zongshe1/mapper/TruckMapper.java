package com.muite.zongshe1.mapper;


import com.muite.zongshe1.entity.Truck;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author 14693
* @description 针对表【truck】的数据库操作Mapper
* @createDate 2025-10-13 13:17:51
* @Entity .com/muite/zongshe1/entity.Truck
*/
@Mapper
public interface TruckMapper {

    int deleteByPrimaryKey(Long id);

    int insert(Truck record);

    int insertSelective(Truck record);

    Truck selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(Truck record);

    int updateByPrimaryKey(Truck record);

    List<Truck> selectAll();

    @Select("select * from truck where status=#{status}")
    List<Truck> selectByStatus(String status);
}
