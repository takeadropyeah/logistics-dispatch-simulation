package com.muite.zongshe1.utils;

import com.muite.zongshe1.constant.GoodsTypeConstant;
import com.muite.zongshe1.constant.PoiTypeConstant;
import com.muite.zongshe1.entity.Point;
import com.muite.zongshe1.entity.Task;
import com.muite.zongshe1.mapper.PointMapper;
import com.muite.zongshe1.mapper.TaskMapper;
import com.muite.zongshe1.service.GoodsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Component
public class SteelIndustryChainTaskGenerator {

    private static final Logger log = LoggerFactory.getLogger(SteelIndustryChainTaskGenerator.class);

    private final Random random = new Random(System.currentTimeMillis());

    private final PointMapper pointMapper;
    private final TaskMapper taskMapper;
    private final GoodsService goodsService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public SteelIndustryChainTaskGenerator(PointMapper pointMapper, TaskMapper taskMapper, 
                                           GoodsService goodsService, 
                                           org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.pointMapper = pointMapper;
        this.taskMapper = taskMapper;
        this.goodsService = goodsService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // 钢材产业链节点定义
    // 钢铁生产基地（钢铁厂）
    private static final List<IndustryNode> STEEL_PLANTS = List.of(
            new IndustryNode("河北唐山钢铁基地", "118.029722,39.6375", "钢铁厂"),
            new IndustryNode("辽宁鞍山钢铁基地", "122.994444,41.119444", "钢铁厂"),
            new IndustryNode("江苏张家港钢铁基地", "120.556667,31.881667", "钢铁厂"),
            new IndustryNode("湖北武汉钢铁基地", "114.358889,30.627778", "钢铁厂"),
            new IndustryNode("四川攀枝花钢铁基地", "101.723333,26.586667", "钢铁厂"),
            new IndustryNode("山东日照钢铁基地", "119.465,35.418333", "钢铁厂"),
            new IndustryNode("广东湛江钢铁基地", "110.365,21.258333", "钢铁厂"),
            new IndustryNode("内蒙古包头钢铁基地", "109.846111,40.651667", "钢铁厂")
    );

    // 铁矿石/煤炭产地（矿山）
    private static final List<IndustryNode> MINES = List.of(
            new IndustryNode("河北唐山铁矿", "118.1,39.7", "矿山"),
            new IndustryNode("山西大同煤矿", "113.35,40.12", "矿山"),
            new IndustryNode("内蒙古鄂尔多斯煤矿", "109.75,39.62", "矿山"),
            new IndustryNode("辽宁本溪铁矿", "123.78,41.32", "矿山"),
            new IndustryNode("四川攀枝花铁矿", "101.71,26.58", "矿山"),
            new IndustryNode("安徽马鞍山铁矿", "118.55,31.68", "矿山"),
            new IndustryNode("山东莱芜铁矿", "117.67,36.21", "矿山"),
            new IndustryNode("云南昆明铁矿", "102.73,24.88", "矿山")
    );

    // 钢材加工制造业集中地（加工厂）
    private static final List<IndustryNode> PROCESSING_CENTERS = List.of(
            new IndustryNode("上海钢材加工中心", "121.473611,31.230416", "加工厂"),
            new IndustryNode("苏州工业园区", "120.62,31.32", "加工厂"),
            new IndustryNode("无锡钢材市场", "120.3,31.49", "加工厂"),
            new IndustryNode("广州番禺工业区", "113.42,22.95", "加工厂"),
            new IndustryNode("佛山顺德制造业基地", "113.21,22.83", "加工厂"),
            new IndustryNode("深圳龙岗工业区", "114.21,22.72", "加工厂"),
            new IndustryNode("宁波北仑工业区", "121.88,29.87", "加工厂"),
            new IndustryNode("杭州萧山工业区", "120.25,30.16", "加工厂"),
            new IndustryNode("天津滨海新区", "117.62,39.14", "加工厂"),
            new IndustryNode("重庆巴南工业区", "106.55,29.41", "加工厂")
    );

    // 物流仓储中心
    private static final List<IndustryNode> LOGISTICS_CENTERS = List.of(
            new IndustryNode("天津港物流中心", "117.72,38.96", "物流中心"),
            new IndustryNode("上海港物流中心", "121.5,31.2", "物流中心"),
            new IndustryNode("宁波港物流中心", "121.55,29.85", "物流中心"),
            new IndustryNode("广州港物流中心", "113.26,23.13", "物流中心"),
            new IndustryNode("青岛港物流中心", "120.33,36.07", "物流中心"),
            new IndustryNode("唐山港物流中心", "118.4,39.3", "物流中心"),
            new IndustryNode("武汉物流枢纽", "114.31,30.52", "物流中心"),
            new IndustryNode("成都物流中心", "104.06,30.57", "物流中心")
    );

    // 终端客户（建筑公司、机械制造等）
    private static final List<IndustryNode> END_CUSTOMERS = List.of(
            new IndustryNode("北京建筑集团", "116.4,39.9", "客户"),
            new IndustryNode("上海建工集团", "121.47,31.23", "客户"),
            new IndustryNode("广州建筑集团", "113.26,23.13", "客户"),
            new IndustryNode("深圳建设集团", "114.05,22.55", "客户"),
            new IndustryNode("南京建工集团", "118.78,32.04", "客户"),
            new IndustryNode("杭州建设集团", "120.15,30.28", "客户"),
            new IndustryNode("西安建筑集团", "108.95,34.27", "客户"),
            new IndustryNode("郑州建设集团", "113.65,34.76", "客户"),
            new IndustryNode("长沙建设集团", "112.94,28.23", "客户"),
            new IndustryNode("大连重工集团", "121.62,38.92", "客户"),
            new IndustryNode("徐州工程机械", "117.22,34.22", "客户")
    );

    // 产业链环节定义
    // 环节1: 矿山 → 钢铁厂（原材料运输）
    // 环节2: 钢铁厂 → 物流中心（钢材成品运输）
    // 环节3: 物流中心 → 加工厂（钢材原材料运输）
    // 环节4: 加工厂 → 终端客户（成品运输）
    // 环节5: 钢铁厂 → 终端客户（直达运输）
    // 环节6: 物流中心 → 终端客户（分销运输）

    /**
     * 基于钢材产业链规律生成任务
     * @param count 生成任务数量
     * @return 生成的任务列表
     */
    public List<Task> generateSteelChainTasks(int count) {
        List<Task> taskList = new ArrayList<>(count);
        
        for (int i = 0; i < count; i++) {
            Task task = generateSteelChainTask();
            if (task != null) {
                taskList.add(task);
            }
        }

        if (!taskList.isEmpty()) {
            for (Task task : taskList) {
                taskMapper.insert(task);
                
                jdbcTemplate.update(
                    "INSERT INTO goods (name, type, weight, volume, status, sender_id, receiver_id, origin_location, dest_location, task_id, priority, create_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "钢材-" + task.getId(),
                    task.getGoodsType(),
                    task.getWeight(),
                    task.getVolume(),
                    "待分配",
                    task.getSenderId(),
                    task.getReceiverId(),
                    task.getStart(),
                    task.getDestination(),
                    task.getId(),
                    random.nextBoolean() ? "加急" : "常规",
                    java.time.LocalDateTime.now()
                );
                
                Integer goodsId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
                task.setGoodsId(goodsId);
                jdbcTemplate.update("UPDATE task SET goods_id = ? WHERE id = ?", goodsId, task.getId());
                
                log.info("钢材产业链任务 {} 生成：{} → {}，货物类型：{}", 
                    task.getId(), task.getStart(), task.getDestination(), task.getGoodsType());
            }
            
            log.info("成功生成 {} 条钢材产业链任务", taskList.size());
        }

        return taskList;
    }

    /**
     * 生成单个钢材产业链任务
     */
    private Task generateSteelChainTask() {
        // 根据产业链环节权重选择环节
        int chainType = random.nextInt(100);
        
        IndustryNode startNode;
        IndustryNode endNode;
        String goodsType;
        double weight;
        double volume;
        String priority;

        if (chainType < 20) {
            // 环节1: 矿山 → 钢铁厂（20%概率）
            startNode = MINES.get(random.nextInt(MINES.size()));
            endNode = STEEL_PLANTS.get(random.nextInt(STEEL_PLANTS.size()));
            
            // 选择最近的钢铁厂（增加合理性）
            endNode = findNearestNode(startNode, STEEL_PLANTS);
            
            if (random.nextBoolean()) {
                goodsType = GoodsTypeConstant.BULK_HEAVY_GOODS; // 铁矿石
                weight = random.nextDouble() * 40 + 20; // 20-60吨
                volume = random.nextDouble() * 20 + 5; // 5-25立方米
            } else {
                goodsType = GoodsTypeConstant.BULK_GOODS; // 煤炭
                weight = random.nextDouble() * 30 + 15; // 15-45吨
                volume = random.nextDouble() * 40 + 20; // 20-60立方米
            }
            priority = "常规";

        } else if (chainType < 35) {
            // 环节2: 钢铁厂 → 物流中心（15%概率）
            startNode = STEEL_PLANTS.get(random.nextInt(STEEL_PLANTS.size()));
            endNode = LOGISTICS_CENTERS.get(random.nextInt(LOGISTICS_CENTERS.size()));
            endNode = findNearestNode(startNode, LOGISTICS_CENTERS);
            
            if (random.nextDouble() < 0.6) {
                goodsType = GoodsTypeConstant.BULK_HEAVY_GOODS; // 钢板、型钢
                weight = random.nextDouble() * 30 + 10; // 10-40吨
                volume = random.nextDouble() * 25 + 5; // 5-30立方米
            } else {
                goodsType = GoodsTypeConstant.BULK_GOODS; // 钢筋、线材
                weight = random.nextDouble() * 25 + 8; // 8-33吨
                volume = random.nextDouble() * 35 + 10; // 10-45立方米
            }
            priority = random.nextDouble() < 0.3 ? "加急" : "常规";

        } else if (chainType < 50) {
            // 环节3: 物流中心 → 加工厂（15%概率）
            startNode = LOGISTICS_CENTERS.get(random.nextInt(LOGISTICS_CENTERS.size()));
            endNode = PROCESSING_CENTERS.get(random.nextInt(PROCESSING_CENTERS.size()));
            endNode = findNearestNode(startNode, PROCESSING_CENTERS);
            
            if (random.nextDouble() < 0.5) {
                goodsType = GoodsTypeConstant.GENERAL_GOODS; // 加工用钢板
                weight = random.nextDouble() * 15 + 3; // 3-18吨
                volume = random.nextDouble() * 20 + 3; // 3-23立方米
            } else {
                goodsType = GoodsTypeConstant.BULK_HEAVY_GOODS; // 型钢
                weight = random.nextDouble() * 20 + 5; // 5-25吨
                volume = random.nextDouble() * 15 + 3; // 3-18立方米
            }
            priority = random.nextDouble() < 0.2 ? "加急" : "常规";

        } else if (chainType < 70) {
            // 环节4: 加工厂 → 终端客户（20%概率）
            startNode = PROCESSING_CENTERS.get(random.nextInt(PROCESSING_CENTERS.size()));
            endNode = END_CUSTOMERS.get(random.nextInt(END_CUSTOMERS.size()));
            endNode = findNearestNode(startNode, END_CUSTOMERS);
            
            goodsType = GoodsTypeConstant.GENERAL_GOODS; // 钢结构件、管材
            weight = random.nextDouble() * 10 + 1; // 1-11吨
            volume = random.nextDouble() * 15 + 2; // 2-17立方米
            priority = random.nextDouble() < 0.4 ? "加急" : "常规";

        } else if (chainType < 85) {
            // 环节5: 钢铁厂 → 终端客户（15%概率，大型项目直达）
            startNode = STEEL_PLANTS.get(random.nextInt(STEEL_PLANTS.size()));
            endNode = END_CUSTOMERS.get(random.nextInt(END_CUSTOMERS.size()));
            
            goodsType = GoodsTypeConstant.BULK_HEAVY_GOODS; // 大型钢材
            weight = random.nextDouble() * 40 + 20; // 20-60吨
            volume = random.nextDouble() * 25 + 5; // 5-30立方米
            priority = random.nextDouble() < 0.5 ? "加急" : "常规";

        } else {
            // 环节6: 物流中心 → 终端客户（15%概率，分销）
            startNode = LOGISTICS_CENTERS.get(random.nextInt(LOGISTICS_CENTERS.size()));
            endNode = END_CUSTOMERS.get(random.nextInt(END_CUSTOMERS.size()));
            endNode = findNearestNode(startNode, END_CUSTOMERS);
            
            goodsType = GoodsTypeConstant.GENERAL_GOODS; // 小型钢材、配件
            weight = random.nextDouble() * 8 + 0.5; // 0.5-8.5吨
            volume = random.nextDouble() * 10 + 1; // 1-11立方米
            priority = "常规";
        }

        BigDecimal weightBD = BigDecimal.valueOf(weight).setScale(2, RoundingMode.HALF_UP);
        BigDecimal volumeBD = BigDecimal.valueOf(volume).setScale(2, RoundingMode.HALF_UP);

        Task task = new Task();
        task.setStart(startNode.location);
        task.setDestination(endNode.location);
        task.setGoodsType(goodsType);
        task.setStatus("待分配");
        task.setTruckId(null);
        task.setWeight(weightBD);
        task.setVolume(volumeBD);
        

        // 创建发货方和收货方信息
        String senderName = startNode.name + "-发货";
        String receiverName = endNode.name + "-收货";
        
        String senderPhone = "138" + String.format("%08d", random.nextInt(100000000));
        String receiverPhone = "139" + String.format("%08d", random.nextInt(100000000));
        
        String senderContact = generateContactName();
        String receiverContact = generateContactName();
        
        String senderAddress = startNode.name + "厂区";
        String receiverAddress = endNode.name + "厂区";
        
        jdbcTemplate.execute(String.format("INSERT INTO customer (name, address, contact_person, phone, type) VALUES ('%s', '%s', '%s', '%s', '%s')", 
            senderName, senderAddress, senderContact, senderPhone, "发货方"));
        Integer senderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        
        jdbcTemplate.execute(String.format("INSERT INTO customer (name, address, contact_person, phone, type) VALUES ('%s', '%s', '%s', '%s', '%s')", 
            receiverName, receiverAddress, receiverContact, receiverPhone, "收货方"));
        Integer receiverId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        
        task.setSenderId(senderId);
        task.setSenderName(senderName);
        task.setReceiverId(receiverId);
        task.setReceiverName(receiverName);

        return task;
    }

    /**
     * 找到最近的节点（增加地理合理性）
     */
    private IndustryNode findNearestNode(IndustryNode from, List<IndustryNode> candidates) {
        if (random.nextDouble() < 0.3) {
            // 30%概率随机选择，增加多样性
            return candidates.get(random.nextInt(candidates.size()));
        }
        
        IndustryNode nearest = candidates.get(0);
        double minDistance = calculateDistance(from, nearest);
        
        for (IndustryNode candidate : candidates) {
            double distance = calculateDistance(from, candidate);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = candidate;
            }
        }
        
        return nearest;
    }

    /**
     * 计算两点之间的粗略距离（经纬度）
     */
    private double calculateDistance(IndustryNode a, IndustryNode b) {
        String[] coordsA = a.location.split(",");
        String[] coordsB = b.location.split(",");
        
        double lngA = Double.parseDouble(coordsA[0]);
        double latA = Double.parseDouble(coordsA[1]);
        double lngB = Double.parseDouble(coordsB[0]);
        double latB = Double.parseDouble(coordsB[1]);
        
        double dx = (lngB - lngA) * 111.32;
        double dy = (latB - latA) * 111.32;
        
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 生成联系人姓名
     */
    private String generateContactName() {
        String[] lastNames = {"张", "李", "王", "赵", "刘", "陈", "杨", "黄", "周", "吴", "徐", "孙", "马"};
        String[] firstNames = {"伟", "强", "勇", "军", "杰", "涛", "明", "辉", "刚", "健", "超", "峰", "亮"};
        return lastNames[random.nextInt(lastNames.length)] + firstNames[random.nextInt(firstNames.length)];
    }

    /**
     * 定时自动生成钢材产业链任务
     */
    @Scheduled(fixedRate = 30000)
    public void autoGenerateSteelChainTasks() {
        try {
            int incompleteTasks = taskMapper.countIncompleteTasks();
            if (incompleteTasks < 200) {
                generateSteelChainTasks(5);
                log.info("钢材产业链定时任务执行：自动生成5条任务");
            } else {
                log.info("当前任务充足 ({})，跳过钢材产业链任务生成", incompleteTasks);
            }
        } catch (Exception e) {
            log.error("钢材产业链任务生成失败", e);
        }
    }

    /**
     * 产业链节点定义
     */
    private static class IndustryNode {
        String name;
        String location;
        String type;

        IndustryNode(String name, String location, String type) {
            this.name = name;
            this.location = location;
            this.type = type;
        }
    }
}