package com.muite.zongshe1.mapper;

import com.muite.zongshe1.entity.GoodsDemand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GoodsDemandMapper {
    int insert(GoodsDemand goodsDemand);
    int insertSelective(GoodsDemand goodsDemand);
    int deleteByPrimaryKey(Integer id);
    int updateByPrimaryKeySelective(GoodsDemand goodsDemand);
    GoodsDemand selectByPrimaryKey(Integer id);
    List<GoodsDemand> selectAll();
    List<GoodsDemand> selectByGoodsId(@Param("goodsId") Integer goodsId);
    List<GoodsDemand> selectByStatus(@Param("status") String status);
}
