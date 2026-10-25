package com.muite.zongshe1.mapper;

import com.muite.zongshe1.entity.GoodsLoss;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GoodsLossMapper {
    int insert(GoodsLoss goodsLoss);
    int insertSelective(GoodsLoss goodsLoss);
    int deleteByPrimaryKey(Integer id);
    int updateByPrimaryKeySelective(GoodsLoss goodsLoss);
    GoodsLoss selectByPrimaryKey(Integer id);
    List<GoodsLoss> selectAll();
    List<GoodsLoss> selectByGoodsId(@Param("goodsId") Integer goodsId);
    List<GoodsLoss> selectByFactoryId(@Param("factoryId") Integer factoryId);
}
