package com.muite.zongshe1.controller;

import com.muite.zongshe1.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "评价指标体系", description = "八维专业评价指标：匹配精准度/资源利用/吞吐响应/算法效率/成本效益/可靠性/公平性/绿色低碳")
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/metrics")
public class MetricsController {

    @Autowired
    private MetricsService metricsService;

    @Operation(summary = "八维指标总览", description = "实时计算 M/U/T/A/C/R/F/G 八维指标")
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return metricsService.getOverview();
    }

    @Operation(summary = "指标历史快照", description = "返回定时快照序列（默认最长240个点，用于趋势曲线）")
    @GetMapping("/history")
    public Map<String, Object> history(@RequestParam(defaultValue = "240") int limit) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("points", metricsService.getHistory(limit));
        return out;
    }

    @Operation(summary = "优化gap精确对比", description = "小规模(≤8任务×≤8车)精确枚举 vs 贪心 vs 模拟退火的优化差距")
    @GetMapping("/gap")
    public Map<String, Object> gap() {
        return metricsService.optimizationGap();
    }
}
