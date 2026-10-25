package com.muite.zongshe1.mapper;

import com.muite.zongshe1.entity.GoodsWaiting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GoodsWaitingMapper {
    int insert(GoodsWaiting goodsWaiting);
    int insertSelective(GoodsWaiting goodsWaiting);
    int deleteByPrimaryKey(Integer id);
    int updateByPrimaryKeySelective(GoodsWaiting goodsWaiting);
    GoodsWaiting selectByPrimaryKey(Integer id);
    List<GoodsWaiting> selectAll();
    List<GoodsWaiting> selectByGoodsId(@Param("goodsId") Integer goodsId);
    List<GoodsWaiting> selectActiveWaiting();
    List<GoodsWaiting> selectLongWaiting(@Param("hours") int hours);
}
