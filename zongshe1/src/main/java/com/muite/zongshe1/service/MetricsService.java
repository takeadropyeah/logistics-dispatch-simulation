package com.muite.zongshe1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 八维专业评价指标计算引擎。
 *
 * 指标口径（对应小组PPT《建立更加专业完备的评价指标》）：
 *  M 匹配精准度：以"条件最优车"（当前空闲且可装载的车辆中空驶里程最小者）为基准答案，
 *    统计精确率/召回率/F1/MRR，数据来源 match_log（SimulationService 分配时插桩）。
 *  U 资源利用：里程利用率=载重里程/总里程；重量装载率=任务重量/车辆额定载重（均值+标准差）；空驶率=空驶里程/总里程。
 *  T 吞吐响应：响应时间=任务 create_time→assign_time，报 P50/P95/P99；吞吐=近1小时完成任务数。
 *  A 算法效率：最近一轮派单求解耗时（SimulationService 上报）+ 按需精确枚举的优化 gap（/metrics/gap）。
 *  C 成本效益：成本模型=燃油(30L/100km×7.5元/L)+维保(0.5元/km)+司机(25元/h，按60km/h折算)；单位成本=总成本/吨公里。
 *  R 可靠性：基于 alert_log 事件估算可用性（每次拥堵按60仿真分钟计）与 MTBF。
 *  F 公平性：按车辆完成的吨公里工作量计算基尼系数（>0.4 触发失衡警示）。
 *  G 绿色低碳：碳排放=油耗(L)×2.6kg/L；单位吨公里碳排=碳排/吨公里。
 */
@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    /** 燃油消耗：升/百公里 */
    private static final double FUEL_L_PER_100KM = 30.0;
    /** 燃油单价：元/升 */
    private static final double FUEL_PRICE = 7.5;
    /** 维保成本：元/公里 */
    private static final double MAINT_PER_KM = 0.5;
    /** 司机工资：元/小时 */
    private static final double DRIVER_WAGE_PER_H = 25.0;
    /** 折算工时的平均车速：公里/小时 */
    private static final double AVG_SPEED_KMH = 60.0;
    /** 柴油碳排放因子：kg CO2 / 升 */
    private static final double CARBON_KG_PER_L = 2.6;
    /** 每次拥堵的仿真时长（分钟）：2个计数周期×30仿真分钟 */
    private static final double JAM_MINUTES = 60.0;
    /** 公平性警示线 */
    private static final double GINI_WARNING = 0.4;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.muite.zongshe1.utils.SimulationService simulationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 对外主接口 ====================

    public Map<String, Object> getOverview() {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Map<String, Object> m = matchMetrics();
            Map<String, Object> u = utilizationMetrics();
            Map<String, Object> t = throughputMetrics();
            Map<String, Object> a = algorithmMetrics();
            Map<String, Object> c = costMetrics(u);
            Map<String, Object> r = reliabilityMetrics();
            Map<String, Object> f = fairnessMetrics();
            Map<String, Object> g = greenMetrics(u, c);

            out.put("M", m);
            out.put("U", u);
            out.put("T", t);
            out.put("A", a);
            out.put("C", c);
            out.put("R", r);
            out.put("F", f);
            out.put("G", g);
            out.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            log.error("指标计算失败", e);
            out.put("error", e.getMessage());
        }
        return out;
    }

    public List<Map<String, Object>> getHistory(int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT metrics, create_time FROM metric_snapshot ORDER BY id DESC LIMIT " + Math.min(Math.max(limit, 1), 720));
        Collections.reverse(rows);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            try {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("time", row.get("create_time"));
                item.put("metrics", objectMapper.readValue((String) row.get("metrics"), Map.class));
                out.add(item);
            } catch (Exception ignored) { }
        }
        return out;
    }

    // ==================== 定时快照 ====================

    @Scheduled(fixedRate = 30000, initialDelay = 20000)
    public void snapshot() {
        try {
            Map<String, Object> overview = getOverview();
            jdbcTemplate.update("INSERT INTO metric_snapshot(metrics) VALUES (?)",
                    objectMapper.writeValueAsString(overview));
            // 历史与明细数据保留策略：快照3天、匹配日志与告警7天，防止无限增长
            jdbcTemplate.update("DELETE FROM metric_snapshot WHERE create_time < NOW() - INTERVAL 3 DAY");
            jdbcTemplate.update("DELETE FROM match_log WHERE created_at < NOW() - INTERVAL 7 DAY");
            jdbcTemplate.update("DELETE FROM alert_log WHERE create_time < NOW() - INTERVAL 7 DAY");
        } catch (Exception e) {
            log.warn("指标快照失败: {}", e.getMessage());
        }
    }

    // ==================== M 匹配精准度 ====================

    private Map<String, Object> matchMetrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> agg = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS total, COALESCE(SUM(is_optimal),0) AS opt, COALESCE(AVG(1.0/rank_no),0) AS mrr FROM match_log");
        long total = ((Number) agg.get("total")).longValue();
        long optimal = ((Number) agg.get("opt")).longValue();
        double mrr = agg.get("mrr") != null ? ((Number) agg.get("mrr")).doubleValue() : 0;

        Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE truck_id IS NULL AND (status IS NULL OR (status != '已完成' AND status != '已取消' AND status != '运输中'))",
                Integer.class);
        long pendingCount = pending != null ? pending : 0;

        double precision = total > 0 ? (double) optimal / total : 0;
        double opportunities = total + pendingCount;
        double recall = opportunities > 0 ? (double) optimal / opportunities : 0;
        double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0;

        m.put("precision", round(precision));
        m.put("recall", round(recall));
        m.put("f1", round(f1));
        m.put("mrr", round(mrr));
        m.put("samples", total);
        m.put("pendingCount", pendingCount);
        m.put("desc", "以条件最优车（可装载空闲车中空驶最小）为基准答案");
        return m;
    }

    // ==================== U 资源利用 ====================

    private Map<String, Object> utilizationMetrics() {
        Map<String, Object> u = new LinkedHashMap<>();

        Map<String, Object> km = jdbcTemplate.queryForMap(
                "SELECT COALESCE(SUM(empty_km),0) AS e, COALESCE(SUM(loaded_km),0) AS l " +
                "FROM task WHERE status='已完成' AND (empty_km IS NOT NULL OR loaded_km IS NOT NULL)");
        double emptyKm = ((Number) km.get("e")).doubleValue();
        double loadedKm = ((Number) km.get("l")).doubleValue();
        double totalKm = emptyKm + loadedKm;

        u.put("mileageUtilization", round(totalKm > 0 ? loadedKm / totalKm : 0));
        u.put("emptyRate", round(totalKm > 0 ? emptyKm / totalKm : 0));
        u.put("emptyKm", round(emptyKm));
        u.put("loadedKm", round(loadedKm));

        // 重量装载率：任务重量 / 车辆额定载重，报均值+标准差
        List<Double> loadRates = jdbcTemplate.query(
                "SELECT t.weight / tr.max_weight AS rate FROM task t JOIN truck tr ON t.truck_id = tr.id " +
                "WHERE t.truck_id IS NOT NULL AND t.weight IS NOT NULL AND tr.max_weight > 0",
                (rs, i) -> rs.getDouble("rate"));
        double[] meanStd = meanStd(loadRates);
        u.put("loadRateMean", round(meanStd[0]));
        u.put("loadRateStd", round(meanStd[1]));
        u.put("loadRateSamples", loadRates.size());
        u.put("desc", "里程利用率与空驶率口径：已完成任务的取货(空驶)/运输(载重)路径里程");
        return u;
    }

    // ==================== T 吞吐响应 ====================

    private Map<String, Object> throughputMetrics() {
        Map<String, Object> t = new LinkedHashMap<>();

        List<Long> respSeconds = jdbcTemplate.query(
                "SELECT TIMESTAMPDIFF(SECOND, create_time, assign_time) AS s FROM task " +
                "WHERE create_time IS NOT NULL AND assign_time IS NOT NULL AND assign_time >= create_time",
                (rs, i) -> rs.getLong("s"));
        t.put("respP50", round(percentileHours(respSeconds, 0.5)));
        t.put("respP95", round(percentileHours(respSeconds, 0.95)));
        t.put("respP99", round(percentileHours(respSeconds, 0.99)));
        t.put("respSamples", respSeconds.size());

        Integer doneLastHour = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE status='已完成' AND complete_time >= NOW() - INTERVAL 1 HOUR", Integer.class);
        Integer doneTotal = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE status='已完成'", Integer.class);
        t.put("throughputPerHour", doneLastHour != null ? doneLastHour : 0);
        t.put("completedTotal", doneTotal != null ? doneTotal : 0);
        t.put("desc", "响应时间=任务创建到分配的时长（小时），P95代表长尾体验");
        return t;
    }

    // ==================== A 算法效率 ====================

    private Map<String, Object> algorithmMetrics() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("assignCostMs", simulationService != null ? simulationService.getLastAssignCostMs() : 0);
        a.put("desc", "assignCostMs=最近一轮全量派单求解耗时；优化gap见 /metrics/gap（小规模精确枚举对比）");
        return a;
    }

    // ==================== C 成本效益 ====================

    private Map<String, Object> costMetrics(Map<String, Object> u) {
        Map<String, Object> c = new LinkedHashMap<>();
        double totalKm = toD(u.get("emptyKm")) + toD(u.get("loadedKm"));
        double fuelCost = totalKm / 100 * FUEL_L_PER_100KM * FUEL_PRICE;
        double maintCost = totalKm * MAINT_PER_KM;
        double driverCost = totalKm / AVG_SPEED_KMH * DRIVER_WAGE_PER_H;
        double totalCost = fuelCost + maintCost + driverCost;

        c.put("fuelCost", round(fuelCost));
        c.put("maintCost", round(maintCost));
        c.put("driverCost", round(driverCost));
        c.put("totalCost", round(totalCost));

        double tonKm = tonKmCompleted();
        c.put("unitCost", round(tonKm > 0 ? totalCost / tonKm : 0));
        c.put("tonKm", round(tonKm));
        c.put("desc", "成本模型：燃油30L/100km×7.5元/L + 维保0.5元/km + 司机25元/h(60km/h折算)；单位成本=总成本/吨公里");
        return c;
    }

    // ==================== R 可靠性 ====================

    private Map<String, Object> reliabilityMetrics() {
        Map<String, Object> r = new LinkedHashMap<>();
        // 滑动窗口口径：只统计最近1小时（真实时钟）的事件，消除长时仿真的累积失真
        long jams = countRecentAlert("拥堵");
        long faults = countRecentAlert("故障");
        long weathers = countRecentAlert("天气");

        // 真实时钟停机估算：拥堵每次冻结 0.4 秒（2 tick），故障平均停机 90 秒
        double downtimeSec = jams * 0.4 + faults * 90.0;
        double availability = Math.max(0, 1 - downtimeSec / 3600.0);
        double mtbfHours = faults > 0 ? 1.0 / faults : 1.0;

        Integer restingDrivers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver WHERE status='疲劳休息'", Integer.class);
        Integer trucksInMaint = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM truck WHERE status='保养'", Integer.class);
        Integer trucksInFault = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM truck WHERE status='故障'", Integer.class);

        r.put("jamCount", jams);
        r.put("faultCount", faults);
        r.put("weatherCount", weathers);
        r.put("restingDrivers", restingDrivers != null ? restingDrivers : 0);
        r.put("trucksInMaint", trucksInMaint != null ? trucksInMaint : 0);
        r.put("trucksInFault", trucksInFault != null ? trucksInFault : 0);
        r.put("availability", round(availability));
        r.put("mtbfHours", round(mtbfHours));
        r.put("windowHours", 1);
        r.put("desc", "可用性按最近1小时滑动窗口（真实时钟）计算：拥堵0.4s/次、故障90s/次；MTBF基于故障事件");
        return r;
    }

    private long countRecentAlert(String type) {
        Long v = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alert_log WHERE type=? AND create_time >= NOW() - INTERVAL 1 HOUR",
                Long.class, type);
        return v != null ? v : 0;
    }

    // ==================== F 公平性 ====================

    private Map<String, Object> fairnessMetrics() {
        Map<String, Object> f = new LinkedHashMap<>();
        List<Double> workloads = jdbcTemplate.query(
                "SELECT t.truck_id AS tid, SUM(t.weight * t.loaded_km) AS wk FROM task t " +
                "WHERE t.status='已完成' AND t.loaded_km IS NOT NULL AND t.weight IS NOT NULL AND t.truck_id IS NOT NULL " +
                "GROUP BY t.truck_id",
                (rs, i) -> rs.getDouble("wk"));
        double gini = gini(workloads);
        Long rejections = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alert_log WHERE type='拒单'", Long.class);
        f.put("gini", round(gini));
        f.put("warning", gini > GINI_WARNING);
        f.put("truckCount", workloads.size());
        f.put("rejections", rejections != null ? rejections : 0);
        f.put("desc", "基尼系数：按车辆完成吨公里工作量计算，>0.4 表示负载失衡；拒单数为司机行为累计值");
        return f;
    }

    // ==================== G 绿色低碳 ====================

    private Map<String, Object> greenMetrics(Map<String, Object> u, Map<String, Object> c) {
        Map<String, Object> g = new LinkedHashMap<>();
        double totalKm = toD(u.get("emptyKm")) + toD(u.get("loadedKm"));
        double fuelL = totalKm / 100 * FUEL_L_PER_100KM;
        double carbonKg = fuelL * CARBON_KG_PER_L;
        double tonKm = tonKmCompleted();
        g.put("carbonKg", round(carbonKg));
        g.put("unitCarbon", round(tonKm > 0 ? carbonKg / tonKm : 0));
        g.put("tonKm", round(tonKm));
        g.put("desc", "碳排=油耗×2.6kg/L；单位吨公里碳排是运输环保效率核心指标");
        return g;
    }

    private double tonKmCompleted() {
        Double v = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(weight * loaded_km),0) FROM task " +
                "WHERE status='已完成' AND loaded_km IS NOT NULL AND weight IS NOT NULL", Double.class);
        return v != null ? v : 0;
    }

    // ==================== A3 优化 gap（按需精确对比） ====================

    /**
     * 小规模精确枚举：当前待分配任务(≤8)与空闲车辆(≤8)做 0-1 指派，
     * 精确最优 vs 贪心最近 vs 模拟退火，输出 gap 百分比。
     */
    public Map<String, Object> optimizationGap() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
                "SELECT id, start, weight FROM task WHERE truck_id IS NULL AND start IS NOT NULL ORDER BY id LIMIT 8");
        List<Map<String, Object>> trucks = jdbcTemplate.queryForList(
                "SELECT id, location, max_weight FROM truck WHERE status='空闲' AND location IS NOT NULL ORDER BY id LIMIT 8");
        if (tasks.isEmpty() || trucks.isEmpty()) {
            out.put("computable", false);
            out.put("reason", "待分配任务或空闲车辆不足，无法构造指派实例");
            return out;
        }

        int n = tasks.size(), m = trucks.size();
        double[][] d = new double[n][m];
        boolean[][] feasible = new boolean[n][m];
        for (int i = 0; i < n; i++) {
            double[] tp = parseLatLng((String) tasks.get(i).get("start"));
            double w = tasks.get(i).get("weight") instanceof Number ? ((Number) tasks.get(i).get("weight")).doubleValue() : 0;
            for (int j = 0; j < m; j++) {
                double[] lp = parseLatLng((String) trucks.get(j).get("location"));
                double cap = trucks.get(j).get("max_weight") instanceof Number ? ((Number) trucks.get(j).get("max_weight")).doubleValue() : 0;
                d[i][j] = (tp == null || lp == null) ? 1e9 : haversine(tp[0], tp[1], lp[0], lp[1]);
                feasible[i][j] = tp != null && lp != null && cap >= w;
            }
        }

        // 贪心最近（任务序优先，每车至多一单，与线上派单口径一致）
        boolean[] used = new boolean[m];
        double greedy = 0;
        for (int i = 0; i < n; i++) {
            int best = -1; double bestD = Double.MAX_VALUE;
            for (int j = 0; j < m; j++) if (!used[j] && feasible[i][j] && d[i][j] < bestD) { bestD = d[i][j]; best = j; }
            if (best >= 0) { used[best] = true; greedy += bestD; }
        }

        // 精确：DP over truck bitmask（每车至多一单）
        int full = 1 << m;
        double[] dp = new double[full];
        Arrays.fill(dp, Double.MAX_VALUE / 2);
        dp[0] = 0;
        for (int i = 0; i < n; i++) {
            double[] nd = new double[full];
            Arrays.fill(nd, Double.MAX_VALUE / 2);
            for (int mask = 0; mask < full; mask++) {
                if (dp[mask] >= Double.MAX_VALUE / 2) continue;
                if (dp[mask] < nd[mask]) nd[mask] = dp[mask]; // 任务i不分配
                for (int j = 0; j < m; j++) {
                    if ((mask & (1 << j)) == 0 && feasible[i][j]) {
                        double v = dp[mask] + d[i][j];
                        if (v < nd[mask | (1 << j)]) nd[mask | (1 << j)] = v;
                    }
                }
            }
            dp = nd;
        }
        double exact = Arrays.stream(dp).min().orElse(Double.MAX_VALUE / 2);
        boolean computable = exact < Double.MAX_VALUE / 2 && exact > 0;

        // 模拟退火（固定种子，保证结果可复现）
        double sa = saAssign(d, feasible, n, m, greedy);

        out.put("computable", computable);
        out.put("taskCount", n);
        out.put("truckCount", m);
        out.put("greedyCost", round(greedy));
        if (computable) {
            out.put("exactCost", round(exact));
            out.put("greedyGap", round(exact > 0 ? (greedy - exact) / exact * 100 : 0));
            out.put("saCost", round(sa));
            out.put("saGap", round(exact > 0 ? (sa - exact) / exact * 100 : 0));
        }
        return out;
    }

    private double saAssign(double[][] d, boolean[][] feasible, int n, int m, double greedyCost) {
        Random rng = new Random(42);
        int[] assign = new int[n];
        Arrays.fill(assign, -1);
        boolean[] used = new boolean[m];
        // 初始解：贪心
        for (int i = 0; i < n; i++) {
            int best = -1; double bestD = Double.MAX_VALUE;
            for (int j = 0; j < m; j++) if (!used[j] && feasible[i][j] && d[i][j] < bestD) { bestD = d[i][j]; best = j; }
            if (best >= 0) { assign[i] = best; used[best] = true; }
        }
        double cost = assignCost(assign, d, feasible);
        double temp = 1000;
        int[] bestAssign = assign.clone();
        double bestCost = cost;
        for (int it = 0; it < 5000 && temp > 0.1; it++) {
            int[] cand = assign.clone();
            int i = rng.nextInt(n);
            int j = rng.nextInt(m + 1) - 1; // -1 表示不分配
            boolean free = j < 0;
            if (!free && (!feasible[i][j] || contains(cand, j, i))) continue;
            cand[i] = j;
            double c2 = assignCost(cand, d, feasible);
            if (c2 < cost || rng.nextDouble() < Math.exp(-(c2 - cost) / temp)) {
                assign = cand; cost = c2;
                if (cost < bestCost) { bestCost = cost; bestAssign = assign.clone(); }
            }
            temp *= 0.995;
        }
        return bestCost;
    }

    private boolean contains(int[] arr, int v, int excludeIdx) {
        for (int i = 0; i < arr.length; i++) if (i != excludeIdx && arr[i] == v) return true;
        return false;
    }

    private double assignCost(int[] assign, double[][] d, boolean[][] feasible) {
        double c = 0;
        for (int i = 0; i < assign.length; i++) {
            if (assign[i] < 0) c += 3000; // 未分配惩罚（空驶里程口径）
            else if (!feasible[i][assign[i]]) c += 1e6;
            else c += d[i][assign[i]];
        }
        return c;
    }

    // ==================== 工具函数 ====================

    private static double gini(List<Double> values) {
        if (values == null || values.isEmpty()) return 0;
        List<Double> v = values.stream().filter(x -> x >= 0).sorted().collect(Collectors.toList());
        int n = v.size();
        if (n == 0) return 0;
        double sum = 0;
        for (double x : v) sum += x;
        if (sum <= 0) return 0;
        double cum = 0;
        for (int i = 0; i < n; i++) cum += (i + 1) * v.get(i);
        return (2.0 * cum) / (n * sum) - (n + 1.0) / n;
    }

    private static double percentileHours(List<Long> seconds, double q) {
        if (seconds == null || seconds.isEmpty()) return 0;
        List<Long> s = new ArrayList<>(seconds);
        Collections.sort(s);
        int idx = (int) Math.ceil(q * (s.size() - 1));
        return s.get(idx) / 3600.0;
    }

    private static double[] meanStd(List<Double> values) {
        if (values == null || values.isEmpty()) return new double[]{0, 0};
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = values.stream().mapToDouble(x -> (x - mean) * (x - mean)).average().orElse(0);
        return new double[]{mean, Math.sqrt(var)};
    }

    private static double[] parseLatLng(String s) {
        if (s == null || !s.contains(",")) return null;
        try {
            String[] p = s.split(",");
            return new double[]{Double.parseDouble(p[1].trim()), Double.parseDouble(p[0].trim())};
        } catch (Exception e) { return null; }
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double toD(Object o) { return o instanceof Number ? ((Number) o).doubleValue() : 0; }

    private static double round(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
