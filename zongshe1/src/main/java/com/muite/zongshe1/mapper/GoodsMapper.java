package com.muite.zongshe1.mapper;

import com.muite.zongshe1.entity.Goods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GoodsMapper {
    int insert(Goods goods);
    int insertSelective(Goods goods);
    int deleteByPrimaryKey(Integer id);
    int updateByPrimaryKeySelective(Goods goods);
    int updateByPrimaryKey(Goods goods);
    Goods selectByPrimaryKey(Integer id);
    List<Goods> selectAll();
    List<Goods> selectByStatus(@Param("status") String status);
    List<Goods> selectByType(@Param("type") String type);
    List<Goods> selectBySenderId(@Param("senderId") Integer senderId);
    List<Goods> selectByReceiverId(@Param("receiverId") Integer receiverId);
    List<Goods> selectPendingGoods();
    List<Goods> selectWaitingGoods();
}
