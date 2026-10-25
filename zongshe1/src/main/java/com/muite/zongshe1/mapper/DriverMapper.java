package com.muite.zongshe1.mapper;

import com.muite.zongshe1.entity.Driver;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface DriverMapper {
    int deleteByPrimaryKey(Integer id);

    int insert(Driver record);

    int insertSelective(Driver record);

    Driver selectByPrimaryKey(Integer id);

    List<Driver> selectAll();

    int updateByPrimaryKeySelective(Driver record);

    int updateByPrimaryKey(Driver record);
}
