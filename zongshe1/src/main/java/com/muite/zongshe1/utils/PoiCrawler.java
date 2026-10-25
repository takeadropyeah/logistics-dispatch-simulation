package com.muite.zongshe1.utils;

import com.muite.zongshe1.entity.Point;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PoiCrawler {

    private static final Logger log = LoggerFactory.getLogger(PoiCrawler.class);

    @Value("${amap.key}")
    private String amapKey;

    @Value("${poi.crawler.auto-run}")
    private boolean autoRun;

    @Value("${poi.crawler.keywords}")
    private String keywords;

    @Value("${poi.crawler.city}")
    private String city;

    @Value("${poi.crawler.max-pages}")
    private int maxPages;
    
    // 目标城市列表：覆盖全国主要区域
    private static final String[] TARGET_CITIES = {
        "北京", "上海", "广州", "深圳", "成都", "武汉", "西安", "南京", "杭州", "郑州", "长沙"
    };

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PoiCrawler(JdbcTemplate jdbcTemplate, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (autoRun) {
            log.info(" 启动时自动爬取 POI 数据...");
            
            // 1. 检查数据库状态
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM poi", Integer.class);
            if (count != null && count > 0) {
                 log.info("检测到数据库已有 {} 条POI数据，跳过初始化爬取（保留现有数据）", count);
                 // 直接返回，不再执行清理或爬取
                 return;
            }

            // 2. 多城市爬取（仅在数据为空时执行）
            crawlMultiCityData();
        }
    }

    private void cleanupOldData() {
        log.warn("正在清理旧POI数据及相关任务...");
        try {
            // 临时禁用外键检查，以便直接Truncate
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
            
            // 清理任务关联表
            jdbcTemplate.execute("TRUNCATE TABLE task_route_point");
            // 清理任务表
            jdbcTemplate.execute("TRUNCATE TABLE task");
            // 清理POI表
            jdbcTemplate.execute("TRUNCATE TABLE poi");
            
            // 重置车辆状态（因为任务没了）
            jdbcTemplate.execute("UPDATE truck SET status='空闲', current_point_sequence=NULL");
            
            // 恢复外键检查
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
            
            log.info("清理完成");
        } catch (Exception e) {
            log.error("清理旧数据失败", e);
        }
    }

    private void crawlMultiCityData() {
        for (String targetCity : TARGET_CITIES) {
            log.info("开始爬取城市: {}", targetCity);
            // 临时修改实例变量
            this.city = targetCity;
            // 每个城市只爬取少量页面（例如1-2页），避免数据量过大且保证分布均匀
            int originalMaxPages = this.maxPages;
            this.maxPages = 5; // 增加页数以获取更多POI
            
            getData();
            
            this.maxPages = originalMaxPages; // 恢复
        }
    }

    public void getData() {
        List<Point> allPois = new ArrayList<>();

        try {
            for (int page = 1; page <= maxPages; page++) {
                String url = String.format(
                        "https://restapi.amap.com/v3/place/text?key=%s&keywords=%s&city=%s&offset=20&page=%d&extensions=all",
                        amapKey, keywords, city, page
                );

                String response = restTemplate.getForObject(url, String.class);
                List<Point> pois = parsePoiResponse(response);
                if (pois.isEmpty()) break;
                allPois.addAll(pois);
                log.info(" 第 " + page + " 页，获取 " + pois.size() + " 条 POI");

                Thread.sleep(1000); // 避免触发 API 限流
            }
            //System.out.println(allPois);
            saveToDatabase(allPois);

        } catch (Exception e) {
            log.error("爬取失败: {}", e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private List<Point> parsePoiResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        if (!"1".equals(root.get("status").asText())) {
            System.err.println("API 错误: " + json);
            return new ArrayList<>();
        }

        List<Point> list = new ArrayList<>();
        JsonNode pois = root.get("pois");
        if (pois != null && pois.isArray()) {
            for (JsonNode node : pois) {
                String name = node.get("name").asText("").trim();
                String location = node.get("location").asText("").trim();
                String type = node.get("type").asText("").split(";")[0];

                if (!name.isEmpty() && !location.isEmpty()) {
                    list.add(new Point(name, location, type));
                }
            }
        }
        return list;
    }

    private void saveToDatabase(List<Point> pois) {
        if (pois.isEmpty()) {
            System.out.println(" 无POI数据可保存");
            return;
        }

        // 1. 先过滤无效数据（原逻辑保留）
        List<Point> validPois = pois.stream()
                .filter(poi -> {
                    String loc = poi.getLocation();
                    if (loc == null || loc.isEmpty()) return false;
                    String[] parts = loc.split(",");
                    return parts.length == 2 && isNumeric(parts[0]) && isNumeric(parts[1]);
                })
                .collect(Collectors.toList());

        if (validPois.isEmpty()) {
            System.out.println(" 无有效POI数据可保存");
            return;
        }


        // 2. 动态生成去重SQL（关键修复：根据POI数量生成占位符）
        List<String> nameList = validPois.stream().map(Point::getName).collect(Collectors.toList());
        List<String> locationList = validPois.stream().map(Point::getLocation).collect(Collectors.toList());

        // 生成占位符（如nameList有3条数据，生成 "?, ?, ?"）
        String namePlaceholders = String.join(", ", Collections.nCopies(nameList.size(), "?"));
        String locationPlaceholders = String.join(", ", Collections.nCopies(locationList.size(), "?"));

        // 3. 查数据库中已存在的POI（动态占位符，避免参数不匹配）
        String sql = String.format(
                "SELECT CONCAT(name, '_', location) FROM poi WHERE name IN (%s) AND location IN (%s)",
                namePlaceholders, locationPlaceholders
        );

        // 合并参数（nameList在前，locationList在后，与占位符顺序一致）
        List<Object> params = new ArrayList<>();
        params.addAll(nameList);
        params.addAll(locationList);

        List<String> existingKeys = jdbcTemplate.queryForList(
                sql,
                String.class,
                params.toArray() // 动态传入参数，数量与占位符一致
        );


        // 4. 过滤掉已存在的POI
        List<Point> newPois = validPois.stream()
                .filter(poi -> !existingKeys.contains(poi.getName() + "_" + poi.getLocation()))
                .collect(Collectors.toList());

        if (newPois.isEmpty()) {
            log.info(" 所有POI数据已存在，无需重复保存");
            return;
        }


        // 5. 批量插入新数据
        String insertSql = "INSERT INTO poi (name, location, type) VALUES (?, ?, ?)";
        jdbcTemplate.batchUpdate(insertSql, newPois, newPois.size(), (ps, poi) -> {
            ps.setString(1, poi.getName());
            ps.setString(2, poi.getLocation());
            ps.setString(3, poi.getType());
        });

        log.info("新增保存 {} 条POI到MySQL", newPois.size());
    }

    private boolean isNumeric(String str) {
        if (str == null || str.trim().isEmpty()) return false;
        try {
            Double.parseDouble(str.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // 新增：带动态参数的爬取方法（供Controller调用）
    public void getData(String dynamicCity, String dynamicKeyword, Integer dynamicMaxPages) {
        // 1. 保存原始配置参数（用于后续恢复，避免影响自动爬取）
        String originalCity = this.city;
        String originalKeyword = this.keywords;
        int originalMaxPages = this.maxPages;

        try {
            // 2. 用动态参数覆盖当前实例的参数（若传入null则使用默认配置）
            if (dynamicCity != null && !dynamicCity.trim().isEmpty()) {
                this.city = dynamicCity.trim();
            }
            if (dynamicKeyword != null && !dynamicKeyword.trim().isEmpty()) {
                this.keywords = dynamicKeyword.trim();
            }
            if (dynamicMaxPages != null && dynamicMaxPages > 0) {
                this.maxPages = dynamicMaxPages;
            }

            // 3. 调用原有爬取逻辑（此时使用的是动态参数）
            getData();

        } finally {
            // 4. 无论成功失败，恢复原始参数（关键！避免影响后续自动爬取或其他调用）
            this.city = originalCity;
            this.keywords = originalKeyword;
            this.maxPages = originalMaxPages;
        }
    }

}