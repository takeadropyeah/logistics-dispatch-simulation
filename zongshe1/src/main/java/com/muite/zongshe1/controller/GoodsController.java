package com.muite.zongshe1.controller;

import com.muite.zongshe1.entity.*;
import com.muite.zongshe1.mapper.*;
import com.muite.zongshe1.service.GoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
@CrossOrigin(origins = "*")
@Tag(name = "货物相关接口")
public class GoodsController {
    
    private static final Logger log = LoggerFactory.getLogger(GoodsController.class);

    @Autowired
    private GoodsService goodsService;
    
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
    
    @Autowired
    private TaskMapper taskMapper;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Operation(summary = "查询所有货物")
    @GetMapping("/goods")
    public List<Goods> selectAll() {
        return goodsService.selectAll();
    }

    @Operation(summary = "根据ID查询货物")
    @GetMapping("/goods/{id}")
    public Goods selectById(@PathVariable Integer id) {
        return goodsService.selectById(id);
    }

    @Operation(summary = "添加货物")
    @PostMapping("/goods")
    public void createGoods(@RequestBody Goods goods) {
        goodsService.insert(goods);
    }

    @Operation(summary = "分配货物到任务")
    @PostMapping("/goods/{goodsId}/assign")
    public void assignGoods(@PathVariable Integer goodsId, 
                           @RequestParam Integer taskId, 
                           @RequestParam Integer truckId) {
        goodsService.assignGoodsToTask(goodsId, taskId, truckId);
    }

    @Operation(summary = "装载货物")
    @PostMapping("/goods/{goodsId}/load")
    public void loadGoods(@PathVariable Integer goodsId) {
        goodsService.loadGoods(goodsId);
    }

    @Operation(summary = "送达货物")
    @PostMapping("/goods/{goodsId}/deliver")
    public void deliverGoods(@PathVariable Integer goodsId) {
        goodsService.deliverGoods(goodsId);
    }

    @Operation(summary = "完成货物")
    @PostMapping("/goods/{goodsId}/complete")
    public void completeGoods(@PathVariable Integer goodsId) {
        goodsService.completeGoods(goodsId);
    }

    @Operation(summary = "获取货物统计数据")
    @GetMapping("/goods/statistics")
    public Map<String, Object> getStatistics() {
        return goodsService.getGoodsStatistics();
    }

    @Operation(summary = "查询待分配货物")
    @GetMapping("/goods/pending")
    public List<Goods> selectPendingGoods() {
        return goodsMapper.selectPendingGoods();
    }

    @Operation(summary = "查询等待中货物")
    @GetMapping("/goods/waiting")
    public List<Goods> selectWaitingGoods() {
        return goodsMapper.selectWaitingGoods();
    }

    @Operation(summary = "查询货物需求记录")
    @GetMapping("/goods/{goodsId}/demand")
    public List<GoodsDemand> getGoodsDemand(@PathVariable Integer goodsId) {
        return goodsDemandMapper.selectByGoodsId(goodsId);
    }

    @Operation(summary = "查询所有需求记录")
    @GetMapping("/goods/demand/all")
    public List<GoodsDemand> getAllDemand() {
        return goodsDemandMapper.selectAll();
    }

    @Operation(summary = "查询货物运输记录")
    @GetMapping("/goods/{goodsId}/transport")
    public List<GoodsTransport> getGoodsTransport(@PathVariable Integer goodsId) {
        return goodsTransportMapper.selectByGoodsId(goodsId);
    }

    @Operation(summary = "查询所有运输记录")
    @GetMapping("/goods/transport/all")
    public List<GoodsTransport> getAllTransport() {
        return goodsTransportMapper.selectAll();
    }

    @Operation(summary = "查询货物等待记录")
    @GetMapping("/goods/{goodsId}/waiting")
    public List<GoodsWaiting> getGoodsWaiting(@PathVariable Integer goodsId) {
        return goodsWaitingMapper.selectByGoodsId(goodsId);
    }

    @Operation(summary = "查询所有等待记录")
    @GetMapping("/goods/waiting/all")
    public List<GoodsWaiting> getAllWaiting() {
        return goodsWaitingMapper.selectAll();
    }

    @Operation(summary = "查询货物损耗记录")
    @GetMapping("/goods/{goodsId}/loss")
    public List<GoodsLoss> getGoodsLoss(@PathVariable Integer goodsId) {
        return goodsLossMapper.selectByGoodsId(goodsId);
    }

    @Operation(summary = "查询所有损耗记录")
    @GetMapping("/goods/loss/all")
    public List<GoodsLoss> getAllLoss() {
        return goodsLossMapper.selectAll();
    }

    @Operation(summary = "获取货物分析数据")
    @GetMapping("/goods/analysis")
    public Map<String, Object> getGoodsAnalysis() {
        Map<String, Object> analysis = new HashMap<>();
        
        List<Goods> allGoods = goodsMapper.selectAll();
        List<GoodsDemand> allDemand = goodsDemandMapper.selectAll();
        List<GoodsTransport> allTransport = goodsTransportMapper.selectAll();
        List<GoodsWaiting> allWaiting = goodsWaitingMapper.selectAll();
        List<GoodsLoss> allLoss = goodsLossMapper.selectAll();
        
        analysis.put("totalGoods", allGoods.size());
        analysis.put("totalDemand", allDemand.size());
        analysis.put("totalTransport", allTransport.size());
        analysis.put("totalWaiting", allWaiting.size());
        analysis.put("totalLoss", allLoss.size());
        
        Map<String, Long> statusCount = new HashMap<>();
        Map<String, Long> typeCount = new HashMap<>();
        Map<String, Long> priorityCount = new HashMap<>();
        
        for (Goods goods : allGoods) {
            if (goods.getStatus() != null) {
                statusCount.merge(goods.getStatus(), 1L, Long::sum);
            }
            if (goods.getType() != null) {
                typeCount.merge(goods.getType(), 1L, Long::sum);
            }
            if (goods.getPriority() != null) {
                priorityCount.merge(goods.getPriority(), 1L, Long::sum);
            }
        }
        
        analysis.put("statusDistribution", statusCount);
        analysis.put("typeDistribution", typeCount);
        analysis.put("priorityDistribution", priorityCount);
        
        Map<String, Long> demandStatusCount = new HashMap<>();
        Map<String, Long> demandRegionCount = new HashMap<>();
        for (GoodsDemand demand : allDemand) {
            demandStatusCount.merge(demand.getStatus(), 1L, Long::sum);
            if (demand.getRegion() != null) {
                demandRegionCount.merge(demand.getRegion(), 1L, Long::sum);
            }
        }
        analysis.put("demandStatusDistribution", demandStatusCount);
        analysis.put("demandRegionDistribution", demandRegionCount);
        
        Map<String, Long> transportTypeCount = new HashMap<>();
        Map<String, Long> transportRegionCount = new HashMap<>();
        for (GoodsTransport transport : allTransport) {
            if (transport.getTransportType() != null) {
                transportTypeCount.merge(transport.getTransportType(), 1L, Long::sum);
            }
            if (transport.getTransportRegion() != null) {
                transportRegionCount.merge(transport.getTransportRegion(), 1L, Long::sum);
            }
        }
        analysis.put("transportTypeDistribution", transportTypeCount);
        analysis.put("transportRegionDistribution", transportRegionCount);
        
        Map<String, Long> waitingTypeCount = new HashMap<>();
        for (GoodsWaiting waiting : allWaiting) {
            if (waiting.getWaitingType() != null) {
                waitingTypeCount.merge(waiting.getWaitingType(), 1L, Long::sum);
            }
        }
        analysis.put("waitingTypeDistribution", waitingTypeCount);
        
        Map<String, Long> lossTypeCount = new HashMap<>();
        double totalLossValue = 0;
        for (GoodsLoss loss : allLoss) {
            if (loss.getLossType() != null) {
                lossTypeCount.merge(loss.getLossType(), 1L, Long::sum);
            }
            if (loss.getLossValue() != null) {
                totalLossValue += loss.getLossValue().doubleValue();
            }
        }
        analysis.put("lossTypeDistribution", lossTypeCount);
        analysis.put("totalLossValue", totalLossValue);
        
        return analysis;
    }
    
    @Operation(summary = "为现有任务补全货物数据")
    @PostMapping("/goods/sync-from-tasks")
    public Map<String, Object> syncGoodsFromTasks() {
        Map<String, Object> result = new HashMap<>();
        int created = 0;
        int skipped = 0;
        Random random = new Random();
        
        List<Task> allTasks = taskMapper.selectAll();
        
        for (Task task : allTasks) {
            if (task.getGoodsId() == null) {
                Goods goods = new Goods();
                goods.setName("货物-" + task.getId());
                goods.setType(task.getGoodsType() != null ? task.getGoodsType().toString() : "通用货物");
                goods.setWeight(task.getWeight());
                goods.setVolume(task.getVolume());
                goods.setSenderId(task.getSenderId());
                goods.setReceiverId(task.getReceiverId());
                goods.setOriginLocation(task.getStart());
                goods.setDestLocation(task.getDestination());
                goods.setTaskId(task.getId());
                goods.setPriority(random.nextBoolean() ? "加急" : "常规");
                
                goodsService.insert(goods);
                
                jdbcTemplate.update("UPDATE task SET goods_id = ? WHERE id = ?", goods.getId(), task.getId());
                
                created++;
                log.info("为任务 {} 创建货物 {}", task.getId(), goods.getId());
            } else {
                skipped++;
            }
        }
        
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("total", allTasks.size());
        
        return result;
    }
}
