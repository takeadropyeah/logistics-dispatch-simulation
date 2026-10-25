package com.muite.zongshe1.controller;

import com.muite.zongshe1.utils.PoiCrawler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@CrossOrigin(origins = "*")
@Tag(name = "POI爬取相关接口")
public class CrawlerController {

    @Autowired
    PoiCrawler poiCrawler;

    public CrawlerController(PoiCrawler poiCrawler) {
        this.poiCrawler = poiCrawler;
    }


    /**
     * 动态参数爬取POI接口
     * @param city 城市（如"上海"，不填则使用配置文件默认值）
     * @param keyword 关键词（如"加油站"，不填则使用配置文件默认值）
     * @param maxPages 最大页数（如10，不填则使用配置文件默认值）
     * @return 爬取结果
     */
    @GetMapping("/crawl")
    @Operation(summary = "动态参数爬取POI", description = "可指定城市、关键词、爬取页数，为空则使用配置文件默认值")
    public String crawlDynamic(
            @Parameter(description = "目标城市（如北京、上海）")
            @RequestParam(required = false) String city,

            @Parameter(description = "搜索关键词（如仓库、加油站）")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "最大爬取页数（如5，默认使用配置文件值）")
            @RequestParam(required = false) Integer maxPages) {

        try {
            // 调用带参数的爬取方法
            poiCrawler.getData(city, keyword, maxPages);
            return "爬取完成！城市：" + (city == null ? "默认" : city) +
                    "，关键词：" + (keyword == null ? "默认" : keyword);
        } catch (Exception e) {
            return "爬取失败：" + e.getMessage();
        }
    }

    @GetMapping("/crawl/multi-city")
    @Operation(summary = "重建多城市POI数据集", description = "清空 poi 表后按内置 11 城列表逐城爬取（每城 5 页）；任务起终点为坐标，不影响在途任务。跨城任务由此产生。")
    public java.util.Map<String, Object> crawlMultiCity(
            @Parameter(description = "是否清空现有 POI（默认 true）")
            @RequestParam(defaultValue = "true") boolean clear) {
        if (!clear) {
            java.util.Map<String, Object> skip = new java.util.LinkedHashMap<>();
            skip.put("status", "skipped");
            skip.put("message", "clear=false 时跳过重建");
            return skip;
        }
        return poiCrawler.rebuildMultiCity();
    }
}