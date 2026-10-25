package com.muite.zongshe1.mapper;

import com.muite.zongshe1.entity.MaintenanceRecord;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface MaintenanceRecordMapper {
    int deleteByPrimaryKey(Integer id);

    int insert(MaintenanceRecord record);

    int insertSelective(MaintenanceRecord record);

    MaintenanceRecord selectByPrimaryKey(Integer id);

    List<MaintenanceRecord> selectAll();

    int updateByPrimaryKeySelective(MaintenanceRecord record);

    int updateByPrimaryKey(MaintenanceRecord record);
}
