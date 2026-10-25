package com.muite.zongshe1.mapper;

import com.muite.zongshe1.entity.GoodsTransport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GoodsTransportMapper {
    int insert(GoodsTransport goodsTransport);
    int insertSelective(GoodsTransport goodsTransport);
    int deleteByPrimaryKey(Integer id);
    int updateByPrimaryKeySelective(GoodsTransport goodsTransport);
    GoodsTransport selectByPrimaryKey(Integer id);
    List<GoodsTransport> selectAll();
    List<GoodsTransport> selectByGoodsId(@Param("goodsId") Integer goodsId);
    List<GoodsTransport> selectByTaskId(@Param("taskId") Integer taskId);
    List<GoodsTransport> selectByTruckId(@Param("truckId") Integer truckId);
}
