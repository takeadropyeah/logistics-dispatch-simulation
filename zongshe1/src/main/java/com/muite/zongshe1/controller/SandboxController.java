package com.muite.zongshe1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muite.zongshe1.utils.SandboxEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 数据实验沙箱接口（阶段III任务2）。
 * 实验方案 = 环境配置 + 算法配置（JSON 存档）；每运行一次追加一个批次（sandbox_run），
 * 固定 seed 即可精确复现；/verify 提供同种子双跑一致性校验。
 */
@Tag(name = "数据实验沙箱", description = "实验方案管理、批次运行、可复现校验、结果对比")
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/sandbox")
public class SandboxController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 实验方案管理 ====================

    @Operation(summary = "新增实验方案")
    @PostMapping("/experiment")
    public Map<String, Object> createExperiment(@RequestBody Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            String name = str(body.get("name"), "未命名实验");
            String desc = str(body.get("description"), "");
            String researcher = str(body.get("researcher"), "研究人员");
            Map<String, Object> config = defaultConfig();
            Object cfg = body.get("config");
            if (cfg instanceof Map) deepMerge(config, (Map<String, Object>) cfg);

            jdbcTemplate.update("INSERT INTO sandbox_experiment(name, description, researcher, config, status) VALUES (?,?,?,?,?)",
                    name, desc, researcher, objectMapper.writeValueAsString(config), "已创建");
            Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM sandbox_experiment", Long.class);
            out.put("status", "success");
            out.put("id", id);
        } catch (Exception e) {
            out.put("status", "error");
            out.put("message", e.getMessage());
        }
        return out;
    }

    @Operation(summary = "实验方案列表")
    @GetMapping("/experiments")
    public Map<String, Object> listExperiments() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT e.id, e.name, e.description, e.researcher, e.config, e.create_time, " +
                "  (SELECT COUNT(*) FROM sandbox_run r WHERE r.experiment_id = e.id) AS run_count " +
                "FROM sandbox_experiment e ORDER BY e.id DESC");
        for (Map<String, Object> row : rows) {
            row.put("config", parseJson(str(row.get("config"), "{}")));
        }
        out.put("status", "success");
        out.put("experiments", rows);
        return out;
    }

    @Operation(summary = "实验详情（含全部批次结果）")
    @GetMapping("/experiment/{id}")
    public Map<String, Object> experimentDetail(@PathVariable int id) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> exps = jdbcTemplate.queryForList(
                "SELECT * FROM sandbox_experiment WHERE id = ?", id);
        if (exps.isEmpty()) {
            out.put("status", "error");
            out.put("message", "实验不存在");
            return out;
        }
        Map<String, Object> exp = exps.get(0);
        exp.put("config", parseJson(str(exp.get("config"), "{}")));
        List<Map<String, Object>> runs = jdbcTemplate.queryForList(
                "SELECT id, run_no, seed, algorithm, status, compute_ms, result, create_time " +
                "FROM sandbox_run WHERE experiment_id = ? ORDER BY run_no ASC", id);
        for (Map<String, Object> run : runs) {
            run.put("result", parseJson(str(run.get("result"), "{}")));
        }
        out.put("status", "success");
        out.put("experiment", exp);
        out.put("runs", runs);
        return out;
    }

    @Operation(summary = "更新实验方案配置")
    @PutMapping("/experiment/{id}")
    public Map<String, Object> updateExperiment(@PathVariable int id, @RequestBody Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            if (body.containsKey("name")) {
                jdbcTemplate.update("UPDATE sandbox_experiment SET name=? WHERE id=?", str(body.get("name"), "未命名实验"), id);
            }
            if (body.containsKey("description")) {
                jdbcTemplate.update("UPDATE sandbox_experiment SET description=? WHERE id=?", str(body.get("description"), ""), id);
            }
            if (body.get("config") instanceof Map) {
                Map<String, Object> config = defaultConfig();
                deepMerge(config, (Map<String, Object>) body.get("config"));
                jdbcTemplate.update("UPDATE sandbox_experiment SET config=? WHERE id=?",
                        objectMapper.writeValueAsString(config), id);
            }
            out.put("status", "success");
        } catch (Exception e) {
            out.put("status", "error");
            out.put("message", e.getMessage());
        }
        return out;
    }

    @Operation(summary = "删除实验方案及其全部批次")
    @DeleteMapping("/experiment/{id}")
    public Map<String, Object> deleteExperiment(@PathVariable int id) {
        jdbcTemplate.update("DELETE FROM sandbox_run WHERE experiment_id = ?", id);
        jdbcTemplate.update("DELETE FROM sandbox_experiment WHERE id = ?", id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "success");
        return out;
    }

    // ==================== 运行批次 ====================

    @Operation(summary = "运行一个批次", description = "按实验方案执行确定性仿真；seed 为空则随机生成并记录")
    @PostMapping("/experiment/{id}/run")
    public Map<String, Object> runExperiment(@PathVariable int id) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> exps = jdbcTemplate.queryForList(
                    "SELECT config FROM sandbox_experiment WHERE id = ?", id);
            if (exps.isEmpty()) {
                out.put("status", "error");
                out.put("message", "实验不存在");
                return out;
            }
            Map<String, Object> config = defaultConfig();
            deepMerge(config, parseJson(str(exps.get(0).get("config"), "{}")));

            SandboxEngine.Config cfg = toEngineConfig(config, null);
            if (cfg.seed == null) cfg.seed = System.currentTimeMillis();

            long startMs = System.currentTimeMillis();
            Map<String, Object> result = SandboxEngine.run(cfg);
            long computeMs = System.currentTimeMillis() - startMs;
            Object snapshots = result.remove("snapshots");

            Integer runCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sandbox_run WHERE experiment_id = ?", Integer.class, id);
            int runNo = (runCount != null ? runCount : 0) + 1;

            jdbcTemplate.update(
                    "INSERT INTO sandbox_run(experiment_id, run_no, seed, algorithm, status, compute_ms, result, snapshots) VALUES (?,?,?,?,?,?,?,?)",
                    id, runNo, cfg.seed, cfg.algorithm, "已完成", computeMs,
                    objectMapper.writeValueAsString(result),
                    objectMapper.writeValueAsString(snapshots != null ? snapshots : Collections.emptyList()));
            Long runId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM sandbox_run", Long.class);

            out.put("status", "success");
            out.put("runId", runId);
            out.put("runNo", runNo);
            out.put("seed", cfg.seed);
            out.put("computeMs", computeMs);
            out.put("result", result);
        } catch (Exception e) {
            out.put("status", "error");
            out.put("message", String.valueOf(e.getMessage()));
        }
        return out;
    }

    @Operation(summary = "同种子复现校验", description = "按指定批次的种子与配置再跑一次，校验总成本是否逐位一致")
    @PostMapping("/run/{runId}/verify")
    public Map<String, Object> verify(@PathVariable int runId) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> runs = jdbcTemplate.queryForList(
                    "SELECT r.seed, r.algorithm, e.config FROM sandbox_run r JOIN sandbox_experiment e ON e.id = r.experiment_id WHERE r.id = ?",
                    runId);
            if (runs.isEmpty()) {
                out.put("status", "error");
                out.put("message", "批次不存在");
                return out;
            }
            Map<String, Object> config = defaultConfig();
            deepMerge(config, parseJson(str(runs.get(0).get("config"), "{}")));
            long seed = ((Number) runs.get(0).get("seed")).longValue();

            SandboxEngine.Config cfg = toEngineConfig(config, seed);
            cfg.algorithm = str(runs.get(0).get("algorithm"), "GREEDY");
            Map<String, Object> r1 = SandboxEngine.run(cfg);
            Map<String, Object> r2 = SandboxEngine.run(cfg);

            double c1 = num(r1.get("C") instanceof Map ? ((Map<?, ?>) r1.get("C")).get("totalCost") : null);
            double c2 = num(r2.get("C") instanceof Map ? ((Map<?, ?>) r2.get("C")).get("totalCost") : null);
            boolean reproducible = Double.doubleToLongBits(c1) == Double.doubleToLongBits(c2);

            out.put("status", "success");
            out.put("reproducible", reproducible);
            out.put("costRun1", c1);
            out.put("costRun2", c2);
            out.put("seed", seed);
            out.put("note", reproducible ? "同种子两次运行总成本逐位一致，实验可精确复现" : "两次结果不一致，请检查引擎确定性");
        } catch (Exception e) {
            out.put("status", "error");
            out.put("message", String.valueOf(e.getMessage()));
        }
        return out;
    }

    @Operation(summary = "批次指标快照曲线")
    @GetMapping("/run/{runId}/snapshots")
    public Map<String, Object> runSnapshots(@PathVariable int runId) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT snapshots FROM sandbox_run WHERE id = ?", runId);
        if (rows.isEmpty()) {
            out.put("status", "error");
            return out;
        }
        Object snaps = parseJson(str(rows.get(0).get("snapshots"), "[]"));
        out.put("status", "success");
        out.put("snapshots", snaps);
        return out;
    }

    @Operation(summary = "多批次对比", description = "按 runIds 返回各批次的八维关键指标，用于对照实验")
    @GetMapping("/compare")
    public Map<String, Object> compare(@RequestParam String runIds) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (String s : runIds.split(",")) {
            try {
                int rid = Integer.parseInt(s.trim());
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT r.id, r.run_no, r.seed, r.algorithm, r.compute_ms, r.result, e.name AS experiment_name " +
                        "FROM sandbox_run r JOIN sandbox_experiment e ON e.id = r.experiment_id WHERE r.id = ?", rid);
                if (rows.isEmpty()) continue;
                Map<String, Object> row = rows.get(0);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("runId", rid);
                item.put("runNo", row.get("run_no"));
                item.put("experimentName", row.get("experiment_name"));
                item.put("algorithm", row.get("algorithm"));
                item.put("seed", row.get("seed"));
                item.put("computeMs", row.get("compute_ms"));
                item.put("result", parseJson(str(row.get("result"), "{}")));
                items.add(item);
            } catch (NumberFormatException ignored) { }
        }
        out.put("status", "success");
        out.put("runs", items);
        return out;
    }

    // ==================== 工具 ====================

    @SuppressWarnings("unchecked")
    private SandboxEngine.Config toEngineConfig(Map<String, Object> config, Long seedOverride) {
        SandboxEngine.Config cfg = new SandboxEngine.Config();
        Map<String, Object> env = config.get("env") instanceof Map ? (Map<String, Object>) config.get("env") : Collections.emptyMap();
        Map<String, Object> algo = config.get("algo") instanceof Map ? (Map<String, Object>) config.get("algo") : Collections.emptyMap();

        cfg.vehicleCount = (int) toD(env.get("vehicleCount"), 12);
        cfg.taskCount = (int) toD(env.get("taskCount"), 60);
        cfg.ticks = (int) toD(env.get("ticks"), 720);
        cfg.jamRate = toD(env.get("jamRate"), 0.02);
        cfg.weatherRate = toD(env.get("weatherRate"), 0.01);
        cfg.algorithm = str(algo.get("algorithm"), "GREEDY");
        cfg.algoParams = algo.get("params") instanceof Map ? (Map<String, Object>) algo.get("params") : new LinkedHashMap<>();
        Object seed = config.get("seed");
        if (seedOverride != null) cfg.seed = seedOverride;
        else if (seed instanceof Number) cfg.seed = ((Number) seed).longValue();
        else cfg.seed = null;
        return cfg;
    }

    private Map<String, Object> defaultConfig() {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("vehicleCount", 12);
        env.put("taskCount", 60);
        env.put("ticks", 720);
        env.put("jamRate", 0.02);
        env.put("weatherRate", 0.01);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("initTemp", 1000);
        params.put("coolRate", 0.995);
        params.put("iterations", 4000);
        params.put("population", 40);
        params.put("generations", 80);
        params.put("crossoverRate", 0.8);
        params.put("mutationRate", 0.1);
        Map<String, Object> algo = new LinkedHashMap<>();
        algo.put("algorithm", "GREEDY");
        algo.put("params", params);
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("env", env);
        cfg.put("algo", algo);
        cfg.put("seed", null);
        return cfg;
    }

    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> base, Map<String, Object> override) {
        for (Map.Entry<String, Object> e : override.entrySet()) {
            if (e.getValue() instanceof Map && base.get(e.getKey()) instanceof Map) {
                deepMerge((Map<String, Object>) base.get(e.getKey()), (Map<String, Object>) e.getValue());
            } else {
                base.put(e.getKey(), e.getValue());
            }
        }
    }

    private Map<String, Object> parseJson(String s) {
        try {
            return objectMapper.readValue(s, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String str(Object o, String def) { return o != null ? String.valueOf(o) : def; }

    private static double toD(Object o, double def) { return o instanceof Number ? ((Number) o).doubleValue() : def; }

    private static double num(Object o) { return o instanceof Number ? ((Number) o).doubleValue() : 0; }
}
