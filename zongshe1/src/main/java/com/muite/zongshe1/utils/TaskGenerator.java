package com.muite.zongshe1.utils;

import com.muite.zongshe1.constant.GoodsTypeConstant;
import com.muite.zongshe1.constant.PoiTypeConstant;
import com.muite.zongshe1.entity.Point;
import com.muite.zongshe1.entity.Task;
import com.muite.zongshe1.entity.Goods;
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
public class TaskGenerator {
    private static final Logger log = LoggerFactory.getLogger(TaskGenerator.class);

    private final Random random = new Random(System.currentTimeMillis());

    private static final List<String> GOODS_TYPES = List.of(
            GoodsTypeConstant.DANGEROUS_GOODS,
            GoodsTypeConstant.GENERAL_GOODS,
            GoodsTypeConstant.GOODS_REQUIREING_VENTILATION,
            GoodsTypeConstant.PERISHABLE_GOODS,
            GoodsTypeConstant.LIQUID_POWDERS,
            GoodsTypeConstant.BULK_HEAVY_GOODS,
            GoodsTypeConstant.BULK_GOODS
    );

    private final PointMapper pointMapper;
    private final TaskMapper taskMapper;
    private final GoodsService goodsService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public TaskGenerator(PointMapper pointMapper, TaskMapper taskMapper, GoodsService goodsService, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.pointMapper = pointMapper;
        this.taskMapper = taskMapper;
        this.goodsService = goodsService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 随机生成任务
     * @param count 生成任务数量
     * @return 生成的任务列表
     */
    public List<Task> generateRandomTasks(int count) {
        // 1. 从数据库查询所有有效POI（作为起点/终点候选）
        List<Point> poiList = pointMapper.selectAll(); // 需实现PointMapper的selectAll方法
        if (poiList.size() < 2) {
            throw new RuntimeException("POI数据不足2条，无法生成任务（需不同起点和终点）");
        }

        // 先生成一批额外的客户信息（100条）
        generateExtraCustomers(100);

        List<Task> taskList = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // ... (省略部分代码) ...
            
            // 筛选起点和终点
            List<Point> startCandidates = poiList.stream()
                    .filter(poi -> PoiTypeConstant.WAREHOUSE.equals(poi.getType()) || 
                                   PoiTypeConstant.FACTORY.equals(poi.getType()) || 
                                   PoiTypeConstant.LOGISTICS_CENTER.equals(poi.getType()))
                    .collect(Collectors.toList());

            List<Point> endCandidates = poiList.stream()
                    .filter(poi -> PoiTypeConstant.SHOPPING_MALL.equals(poi.getType()) || 
                                   PoiTypeConstant.SUPERMARKET.equals(poi.getType()) ||
                                   PoiTypeConstant.GAS_STATION.equals(poi.getType()))
                    .collect(Collectors.toList());
            
            if (startCandidates.isEmpty()) startCandidates = poiList;
            if (endCandidates.isEmpty()) endCandidates = poiList;

            Point startPoi = startCandidates.get(random.nextInt(startCandidates.size()));
            Point endPoi = endCandidates.get(random.nextInt(endCandidates.size()));


            // 3. 随机选择货物类型
            String goodsType = GOODS_TYPES.get(random.nextInt(GOODS_TYPES.size()));


            // 4.按货物类型生成重量和体积（单位：吨、立方米）
            double weightDouble;
            double volumeDouble;
            switch (goodsType) {
                case GoodsTypeConstant.DANGEROUS_GOODS:
                    // 危险品通常单次运输量较小，且重量/体积适中
                    weightDouble = random.nextDouble() * 3 + 0.1; // 0.1-3.1吨
                    volumeDouble = random.nextDouble() * 5 + 0.5; // 0.5-5.5立方米
                    break;
                case GoodsTypeConstant.GENERAL_GOODS:
                    // 通用货物，重量和体积范围广
                    weightDouble = random.nextDouble() * 15 + 1; // 1-16吨
                    volumeDouble = random.nextDouble() * 30 + 5; // 5-35立方米
                    break;
                case GoodsTypeConstant.GOODS_REQUIREING_VENTILATION:
                    // 如农产品、纺织品，体积较大但重量较轻
                    weightDouble = random.nextDouble() * 8 + 0.5; // 0.5-8.5吨
                    volumeDouble = random.nextDouble() * 40 + 10; // 10-50立方米
                    break;
                case GoodsTypeConstant.PERISHABLE_GOODS:
                    // 如生鲜、食品，需冷藏，通常批量不大
                    weightDouble = random.nextDouble() * 6 + 0.3; // 0.3-6.3吨
                    volumeDouble = random.nextDouble() * 15 + 2; // 2-17立方米
                    break;
                case GoodsTypeConstant.LIQUID_POWDERS:
                    // 如化工液体、面粉，重量大但体积紧凑（罐式车装载）
                    weightDouble = random.nextDouble() * 20 + 5; // 5-25吨
                    volumeDouble = random.nextDouble() * 10 + 3; // 3-13立方米
                    break;
                case GoodsTypeConstant.BULK_HEAVY_GOODS:
                    // 如钢材、石材，重量大、体积相对小
                    weightDouble = random.nextDouble() * 40 + 20; // 20-60吨
                    volumeDouble = random.nextDouble() * 20 + 5; // 5-25立方米
                    break;
                case GoodsTypeConstant.BULK_GOODS:
                    // 如粮食、煤炭，体积大、重量中等
                    weightDouble = random.nextDouble() * 30 + 10; // 10-40吨
                    volumeDouble = random.nextDouble() * 50 + 30; // 30-80立方米
                    break;
                default:
                    weightDouble = 0.0;
                    volumeDouble = 0.0;
            }

            // 转换为BigDecimal，保留2位小数（四舍五入）
            BigDecimal weight = BigDecimal.valueOf(weightDouble)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal volume = BigDecimal.valueOf(volumeDouble)
                    .setScale(2, RoundingMode.HALF_UP);



            // 5. 构建Task对象（根据你的Task实体字段调整）
            Task task = new Task();
            task.setStart(startPoi.getLocation()); // 起点经纬度（复用POI的location字段）
            task.setDestination(endPoi.getLocation()); // 终点经纬度
            task.setGoodsType(goodsType);
            task.setStatus("待分配");
            task.setTruckId(null);
            task.setWeight(weight);
            task.setVolume(volume);
            
            String senderName = "发货方" + (i + 1);
            String receiverName = "收货方" + (i + 1);
            
            String senderPhone = "138" + String.format("%08d", random.nextInt(100000000));
            String receiverPhone = "139" + String.format("%08d", random.nextInt(100000000));
            
            String senderContact = "张" + random.nextInt(100);
            String receiverContact = "李" + random.nextInt(100);
            
            String senderAddress = "北京市朝阳区" + random.nextInt(100) + "号";
            String receiverAddress = "北京市海淀区" + random.nextInt(100) + "号";
            
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

            taskList.add(task);
        }

        if (!taskList.isEmpty()) {
            for (Task task : taskList) {
                taskMapper.insert(task);
                
                // 使用jdbcTemplate直接插入，确保能获取到自增ID
                jdbcTemplate.update(
                    "INSERT INTO goods (name, type, weight, volume, status, sender_id, receiver_id, origin_location, dest_location, task_id, priority, create_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "货物-" + task.getId(),
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
                
                log.info("任务 {} 关联货物 {}", task.getId(), goodsId);
            }
            
            log.info("成功随机生成 {} 条任务并关联货物", taskList.size());
        }

        return taskList;
    }
    
    /**
     * 生成额外的客户信息
     * @param count 生成客户数量
     */
    private void generateExtraCustomers(int count) {
        String[] lastNames = {"张", "李", "王", "赵", "刘", "陈", "杨", "黄", "周", "吴", "徐", "孙", "马", "朱", "胡", "林", "郭", "何", "高", "罗", "郑", "梁", "谢", "宋", "唐", "许", "邓", "冯", "韩", "曹", "曾", "彭", "萧", "蔡", "潘", "田", "董", "袁", "于", "余", "叶", "蒋", "杜", "苏", "魏", "程", "吕", "丁", "沈", "任", "姚", "卢", "傅", "钟", "姜", "崔", "谭", "廖", "范", "汪", "陆", "金", "石", "戴", "贾", "韦", "夏", "邱", "方", "侯", "邹", "熊", "孟", "秦", "白", "江", "阎", "薛", "尹", "段", "雷", "黎", "史", "龙", "陶", "贺", "顾", "毛", "郝", "龚", "邵", "万", "钱", "严", "覃", "武", "戴", "莫", "孔", "向", "汤"};
        String[] firstNames = {"伟", "芳", "强", "磊", "军", "勇", "艳", "杰", "涛", "明", "华", "丽", "敏", "静", "文", "辉", "刚", "英", "宇", "佳", "浩", "婷", "秀", "健", "超", "萍", "波", "荣", "春", "平", "燕", "峰", "霞", "亮", "雪", "强", "军", "宁", "玲", "锋", "莉", "彬", "琴", "伟", "红", "兵", "兰", "青", "梅", "松", "桂", "芝", "菊", "竹", "莲", "荷", "桃", "杏", "梨", "枣", "栗", "李", "杨", "柳", "榆", "槐", "梧桐", "枫", "柏", "松", "杉", "竹", "菊", "兰", "梅", "荷", "莲", "桂", "芝", "蓉", "薇", "菲", "芳", "芬", "芸", "芹", "萍", "菠", "萝", "茄", "椒", "葱", "姜", "蒜", "韭", "菜", "瓜", "果", "桃", "李", "杏", "梨", "枣", "栗", "梅", "兰", "竹", "菊", "荷", "莲", "桂", "芝", "蓉", "薇", "菲", "芳", "芬", "芸", "芹", "萍", "菠", "萝", "茄", "椒", "葱", "姜", "蒜", "韭", "菜", "瓜", "果"};
        String[] addressPrefixes = {"北京市朝阳区", "北京市海淀区", "北京市西城区", "北京市东城区", "北京市丰台区", "北京市石景山区", "北京市通州区", "北京市顺义区", "北京市房山区", "北京市大兴区", "北京市昌平区", "北京市平谷区", "北京市怀柔区", "北京市密云区", "北京市延庆区"};
        String[] types = {"发货方", "收货方", "中转商", "经销商", "零售商", "供应商"};
        
        for (int i = 0; i < count; i++) {
            // 随机生成姓名
            String lastName = lastNames[random.nextInt(lastNames.length)];
            String firstName = firstNames[random.nextInt(firstNames.length)];
            String name = lastName + firstName;
            
            // 随机生成手机号
            String phone = "13" + (random.nextInt(8) + 5) + String.format("%08d", random.nextInt(100000000));
            
            // 随机生成地址
            String address = addressPrefixes[random.nextInt(addressPrefixes.length)] + random.nextInt(1000) + "号";
            
            // 随机生成类型
            String type = types[random.nextInt(types.length)];
            
            // 插入到customer表
            jdbcTemplate.execute(String.format("INSERT INTO customer (name, address, contact_person, phone, type) VALUES ('%s', '%s', '%s', '%s', '%s')", 
                name, address, name, phone, type));
        }
        
        log.info("成功生成 {} 条额外客户信息", count);
    }



    // 已禁用随机任务生成，改用 SteelIndustryChainTaskGenerator 生成基于钢材产业链的任务
    // /**
    //  * 定时自动生成任务（每30秒生成3条，确保任务充足且循环）
    //  */
    // @Scheduled(fixedRate = 30000) // 间隔时间：30000毫秒 = 30秒
    // public void autoGenerateTasks() {
    //     try {
    //         int incompleteTasks = taskMapper.countIncompleteTasks();
    //         if (incompleteTasks < 50) {
    //             generateRandomTasks(3);
    //             log.info("定时任务执行：自动生成3条任务");
    //         } else {
    //             log.info("当前任务充足 ({})，跳过本次生成", incompleteTasks);
    //         }
    //     } catch (Exception e) {
    //         log.error("定时任务生成任务失败", e);
    //     }
    // }
    
}