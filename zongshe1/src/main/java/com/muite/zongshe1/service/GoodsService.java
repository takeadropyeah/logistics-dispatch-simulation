package com.muite.zongshe1.service;

import com.muite.zongshe1.entity.*;
import com.muite.zongshe1.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class GoodsService {
    
    private static final Logger log = LoggerFactory.getLogger(GoodsService.class);

    @Autowired
    private GoodsMapper goodsMapper;
    
    @Autowired
    private GoodsDemandMapper goodsDemandMapper;
    
    @Autowired
    private GoodsTransportMapper goodsTransportMapper;
    
    @Autowired
    private GoodsWaitingMapper goodsWaitingMapper;
    
    @Autowired
    private GoodsLossMapper goodsLossMapper;

    public List<Goods> selectAll() {
        return goodsMapper.selectAll();
    }

    public Goods selectById(Integer id) {
        return goodsMapper.selectByPrimaryKey(id);
    }

    @Transactional
    public void insert(Goods goods) {
        goods.setCreateTime(LocalDateTime.now());
        if (goods.getStatus() == null) {
            goods.setStatus("待分配");
        }
        goodsMapper.insertSelective(goods);
        
        GoodsDemand demand = new GoodsDemand();
        demand.setGoodsId(goods.getId());
        demand.setDemandType("新订单");
        demand.setDemandTime(LocalDateTime.now());
        demand.setRegion(determineRegion(goods.getOriginLocation()));
        demand.setUrgency(goods.getPriority() != null ? goods.getPriority() : "常规");
        demand.setStatus("待处理");
        goodsDemandMapper.insertSelective(demand);
        
        log.info("创建货物 {} 并生成需求记录", goods.getId());
    }

    @Transactional
    public void assignGoodsToTask(Integer goodsId, Integer taskId, Integer truckId) {
        Goods goods = goodsMapper.selectByPrimaryKey(goodsId);
        if (goods == null) {
            log.error("货物 {} 不存在", goodsId);
            return;
        }
        
        goods.setTaskId(taskId);
        goods.setAssignTime(LocalDateTime.now());
        goods.setStatus("待运输");
        goodsMapper.updateByPrimaryKeySelective(goods);
        
        GoodsTransport transport = new GoodsTransport();
        transport.setGoodsId(goodsId);
        transport.setTaskId(taskId);
        transport.setTruckId(truckId);
        transport.setTransportType(determineTransportType(goods.getDistance()));
        transport.setStartTime(LocalDateTime.now());
        transport.setTransportRegion(determineRegion(goods.getOriginLocation()));
        goodsTransportMapper.insertSelective(transport);
        
        log.info("货物 {} 已分配给任务 {}", goodsId, taskId);
    }

    @Transactional
    public void loadGoods(Integer goodsId) {
        Goods goods = goodsMapper.selectByPrimaryKey(goodsId);
        if (goods == null) {
            log.error("货物 {} 不存在", goodsId);
            return;
        }
        
        goods.setLoadTime(LocalDateTime.now());
        goods.setStatus("运输中");
        goodsMapper.updateByPrimaryKeySelective(goods);
        
        endWaitingRecord(goodsId, "装货等待");
        
        log.info("货物 {} 已装载", goodsId);
    }

    @Transactional
    public void deliverGoods(Integer goodsId) {
        Goods goods = goodsMapper.selectByPrimaryKey(goodsId);
        if (goods == null) {
            log.error("货物 {} 不存在", goodsId);
            return;
        }
        
        goods.setDeliverTime(LocalDateTime.now());
        goods.setStatus("已送达");
        goodsMapper.updateByPrimaryKeySelective(goods);
        
        endWaitingRecord(goodsId, "卸货等待");
        
        log.info("货物 {} 已送达", goodsId);
    }

    @Transactional
    public void completeGoods(Integer goodsId) {
        Goods goods = goodsMapper.selectByPrimaryKey(goodsId);
        if (goods == null) {
            log.error("货物 {} 不存在", goodsId);
            return;
        }
        
        goods.setCompleteTime(LocalDateTime.now());
        goods.setStatus("已完成");
        goodsMapper.updateByPrimaryKeySelective(goods);
        
        List<GoodsTransport> transports = goodsTransportMapper.selectByGoodsId(goodsId);
        if (!transports.isEmpty()) {
            GoodsTransport transport = transports.get(0);
            transport.setEndTime(LocalDateTime.now());
            goodsTransportMapper.updateByPrimaryKeySelective(transport);
        }
        
        log.info("货物 {} 已完成", goodsId);
    }

    @Transactional
    public void startWaiting(Integer goodsId, String waitingType, String reason, String location) {
        GoodsWaiting waiting = new GoodsWaiting();
        waiting.setGoodsId(goodsId);
        waiting.setWaitingType(waitingType);
        waiting.setStartTime(LocalDateTime.now());
        waiting.setWaitingReason(reason);
        waiting.setWaitingLocation(location);
        goodsWaitingMapper.insertSelective(waiting);
        
        log.info("货物 {} 开始等待: {}", goodsId, waitingType);
    }

    @Transactional
    public void endWaitingRecord(Integer goodsId, String waitingType) {
        List<GoodsWaiting> waitings = goodsWaitingMapper.selectByGoodsId(goodsId);
        for (GoodsWaiting waiting : waitings) {
            if (waiting.getEndTime() == null && waiting.getWaitingType().equals(waitingType)) {
                waiting.setEndTime(LocalDateTime.now());
                goodsWaitingMapper.updateByPrimaryKeySelective(waiting);
                log.info("货物 {} 结束等待: {}", goodsId, waitingType);
            }
        }
    }

    @Transactional
    public void recordLoss(Integer goodsId, String lossType, BigDecimal lossWeight, BigDecimal lossValue, Integer factoryId) {
        GoodsLoss loss = new GoodsLoss();
        loss.setGoodsId(goodsId);
        loss.setLossType(lossType);
        loss.setLossWeight(lossWeight);
        loss.setLossValue(lossValue);
        loss.setLossTime(LocalDateTime.now());
        loss.setFactoryId(factoryId);
        goodsLossMapper.insertSelective(loss);
        
        log.info("记录货物 {} 损耗: {}", goodsId, lossType);
    }

    public Map<String, Object> getGoodsStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        List<Goods> allGoods = goodsMapper.selectAll();
        stats.put("total", allGoods.size());
        
        Map<String, Long> statusCount = new HashMap<>();
        Map<String, Long> typeCount = new HashMap<>();
        
        for (Goods goods : allGoods) {
            statusCount.merge(goods.getStatus(), 1L, Long::sum);
            typeCount.merge(goods.getType(), 1L, Long::sum);
        }
        
        stats.put("statusCount", statusCount);
        stats.put("typeCount", typeCount);
        
        return stats;
    }

    private String determineRegion(String location) {
        if (location == null) return "未知";
        return "市区";
    }

    private String determineTransportType(BigDecimal distance) {
        if (distance == null) return "同城配送";
        if (distance.compareTo(new BigDecimal("50")) < 0) return "同城配送";
        if (distance.compareTo(new BigDecimal("200")) < 0) return "跨城运输";
        return "干线运输";
    }
}
