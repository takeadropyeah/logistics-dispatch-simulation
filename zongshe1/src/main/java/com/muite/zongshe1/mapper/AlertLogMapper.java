package com.muite.zongshe1.mapper;

import com.muite.zongshe1.entity.AlertLog;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface AlertLogMapper {
    int deleteByPrimaryKey(Integer id);

    int insert(AlertLog record);

    int insertSelective(AlertLog record);

    AlertLog selectByPrimaryKey(Integer id);

    List<AlertLog> selectAll();

    int updateByPrimaryKeySelective(AlertLog record);

    int updateByPrimaryKey(AlertLog record);
}
