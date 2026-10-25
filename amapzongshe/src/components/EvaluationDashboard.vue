<template>
  <div class="eval-overlay">
    <div class="eval-panel">
      <!-- 顶栏 -->
      <div class="eval-header">
        <div>
          <h2>📈 综合评价指标看板</h2>
          <p class="eval-sub">八维指标体系 · 匹配精准度 M / 资源利用 U / 吞吐响应 T / 算法效率 A / 成本效益 C / 可靠性 R / 公平性 F / 绿色低碳 G · 每5秒自动刷新</p>
        </div>
        <div class="eval-header-right">
          <button class="gap-btn" @click="loadGap" :disabled="gapLoading">{{ gapLoading ? '计算中…' : '⚡ 优化gap检测' }}</button>
          <button class="close-btn" @click="$emit('close')">×</button>
        </div>
      </div>

      <div v-if="error" class="eval-error">⚠ 后端连接失败：{{ error }}（请确认 8080 端口服务已启动）</div>

      <div class="eval-body" v-if="data">
        <!-- 核心 KPI 大卡片：重要指标直观明显 -->
        <div class="kpi-row">
          <div class="kpi-card">
            <div class="kpi-label">C2 · 单位成本<span class="kpi-arrow">↓优</span></div>
            <div class="kpi-value">{{ fmt(data.C.unitCost) }}<span class="kpi-unit">元/吨公里</span></div>
            <div class="kpi-sub">总成本 {{ fmt(data.C.totalCost) }} 元 · 燃油{{ fmt(data.C.fuelCost) }}+维保{{ fmt(data.C.maintCost) }}+司机{{ fmt(data.C.driverCost) }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">U2 · 重量装载率<span class="kpi-arrow">↑优</span></div>
            <div class="kpi-value">{{ pct(data.U.loadRateMean) }}<span class="kpi-unit">%</span></div>
            <div class="kpi-sub">标准差 ±{{ fmt(data.U.loadRateStd) }}（低=负载均衡）· 样本{{ data.U.loadRateSamples }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">U4 · 空驶率<span class="kpi-arrow">↓优</span></div>
            <div class="kpi-value">{{ pct(data.U.emptyRate) }}<span class="kpi-unit">%</span></div>
            <div class="kpi-sub">里程利用率 {{ pct(data.U.mileageUtilization) }}% · 空驶 {{ fmt(data.U.emptyKm) }} km</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">T2 · 匹配响应 P95<span class="kpi-arrow">↓优</span></div>
            <div class="kpi-value">{{ fmt(data.T.respP95) }}<span class="kpi-unit">小时</span></div>
            <div class="kpi-sub">P50 {{ fmt(data.T.respP50) }} h · P99 {{ fmt(data.T.respP99) }} h · 吞吐 {{ data.T.throughputPerHour }}/h</div>
          </div>
          <div class="kpi-card" :class="{ 'kpi-warning': data.F.warning }">
            <div class="kpi-label">F1 · 基尼系数<span class="kpi-arrow">≤0.4</span></div>
            <div class="kpi-value">{{ fmt(data.F.gini) }}</div>
            <div class="kpi-sub">{{ data.F.warning ? '⚠ 负载失衡警示' : '负载分配公平' }} · 统计车辆 {{ data.F.truckCount }} 辆</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">G2 · 吨公里碳排<span class="kpi-arrow">↓优</span></div>
            <div class="kpi-value">{{ fmt(data.G.unitCarbon) }}<span class="kpi-unit">kg/吨公里</span></div>
            <div class="kpi-sub">总碳排 {{ fmt(data.G.carbonKg) }} kg · 总吨公里 {{ fmt(data.G.tonKm) }}</div>
          </div>
        </div>

        <!-- 图表区 -->
        <div class="chart-row">
          <div class="chart-card">
            <h3>六维能力雷达<span class="chart-note">（归一化，越大越优）</span></h3>
            <div ref="radarEl" class="chart-box"></div>
          </div>
          <div class="chart-card">
            <h3>M · 匹配精准度<span class="chart-note">（基准：条件最优车 = 空闲可装载车中空驶最小）</span></h3>
            <div ref="matchEl" class="chart-box"></div>
            <div class="match-note">累计派单样本 {{ data.M.samples }} 次 · 待分配任务 {{ data.M.pendingCount }} 个</div>
          </div>
          <div class="chart-card">
            <h3>R · 可靠性与系统状态</h3>
            <div ref="reliaEl" class="chart-box"></div>
          </div>
        </div>

        <!-- 趋势区 -->
        <div class="chart-row">
          <div class="chart-card wide">
            <h3>指标趋势<span class="chart-note">（每30秒快照，看长尾不看均值）</span></h3>
            <div ref="trendEl" class="chart-box-tall"></div>
          </div>
        </div>

        <!-- gap 检测结果 -->
        <div v-if="gap" class="gap-result">
          <h4>⚡ 优化 gap 检测（小规模实例：{{ gap.taskCount }} 任务 × {{ gap.truckCount }} 车，精确枚举为基准）</h4>
          <template v-if="gap.computable">
            <div class="gap-items">
              <div class="gap-item">精确最优：<b>{{ fmt(gap.exactCost) }}</b> km</div>
              <div class="gap-item">贪心最近：<b>{{ fmt(gap.greedyCost) }}</b> km（gap <b :class="{bad: gap.greedyGap > 5}">{{ fmt(gap.greedyGap) }}%</b>）</div>
              <div class="gap-item">模拟退火：<b>{{ fmt(gap.saCost) }}</b> km（gap <b :class="{bad: gap.saGap > 5}">{{ fmt(gap.saGap) }}%</b>）</div>
            </div>
          </template>
          <p v-else class="gap-none">{{ gap.reason }}</p>
        </div>
      </div>
      <div v-else-if="!error" class="eval-loading">指标加载中…</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from "vue";
import * as echarts from "echarts";

defineEmits(["close"]);

const API = "http://localhost:8080";
const data = ref(null);
const error = ref("");
const gap = ref(null);
const gapLoading = ref(false);

const radarEl = ref(null);
const matchEl = ref(null);
const reliaEl = ref(null);
const trendEl = ref(null);
let charts = {};
let timer = null;

const fmt = (v) => (typeof v === "number" ? (Math.round(v * 100) / 100).toLocaleString() : "-");
const pct = (v) => (typeof v === "number" ? Math.round(v * 10000) / 100 : "-");

async function fetchOverview() {
  try {
    const res = await fetch(`${API}/metrics/overview`);
    if (!res.ok) throw new Error("HTTP " + res.status);
    data.value = await res.json();
    error.value = "";
    await nextTick();
    renderCharts();
  } catch (e) {
    error.value = e.message;
  }
}

async function fetchTrend() {
  try {
    const res = await fetch(`${API}/metrics/history?limit=240`);
    const json = await res.json();
    const points = json.points || [];
    renderTrend(points);
  } catch (e) { /* 趋势失败不打断主视图 */ }
}

async function loadGap() {
  gapLoading.value = true;
  try {
    const res = await fetch(`${API}/metrics/gap`);
    gap.value = await res.json();
  } catch (e) {
    gap.value = { computable: false, reason: "请求失败：" + e.message };
  }
  gapLoading.value = false;
}

function setChart(key, el, option) {
  if (!el) return;
  if (!charts[key]) charts[key] = echarts.init(el);
  charts[key].setOption(option, true);
}

function renderCharts() {
  const d = data.value;
  if (!d) return;

  // 六维雷达（归一化，方向统一为越大越优）
  setChart("radar", radarEl.value, {
    radar: {
      indicator: [
        { name: "匹配F1", max: 100 },
        { name: "装载率", max: 100 },
        { name: "里程利用", max: 100 },
        { name: "响应效率", max: 100 },
        { name: "成本效率", max: 100 },
        { name: "低碳效率", max: 100 },
      ],
      radius: "62%",
      axisName: { color: "#475569", fontSize: 12 },
    },
    series: [{
      type: "radar",
      areaStyle: { opacity: 0.25, color: "#3b82f6" },
      lineStyle: { color: "#2563eb", width: 2 },
      itemStyle: { color: "#2563eb" },
      data: [{
        value: [
          pct(d.M.f1),
          pct(d.U.loadRateMean),
          pct(d.U.mileageUtilization),
          Math.max(0, 100 - d.T.respP95 * 15),
          Math.max(0, 100 - d.C.unitCost * 10),
          Math.max(0, 100 - d.G.unitCarbon * 8),
        ].map((v) => Math.max(0, Math.round(v * 10) / 10)),
        name: "当前系统能力",
      }],
    }],
  });

  // 匹配精准度横向条形
  setChart("match", matchEl.value, {
    grid: { left: 80, right: 40, top: 12, bottom: 24 },
    xAxis: { type: "value", max: 1, axisLabel: { formatter: (v) => Math.round(v * 100) + "%" } },
    yAxis: { type: "category", data: ["精确率", "召回率", "F1", "MRR"], axisLabel: { fontSize: 12 } },
    series: [{
      type: "bar",
      barWidth: 16,
      label: { show: true, position: "right", formatter: (p) => pct(p.value) + "%" },
      itemStyle: { color: "#10b981", borderRadius: [0, 8, 8, 0] },
      data: [d.M.precision, d.M.recall, d.M.f1, d.M.mrr],
    }],
  });

  // 可靠性仪表
  setChart("relia", reliaEl.value, {
    series: [
      {
        type: "gauge",
        center: ["50%", "58%"],
        radius: "92%",
        startAngle: 210,
        endAngle: -30,
        min: 0,
        max: 100,
        axisLine: { lineStyle: { width: 14, color: [[0.9, "#fca5a5"], [0.99, "#fbbf24"], [1, "#34d399"]] } },
        pointer: { itemStyle: { color: "#334155" } },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        title: { show: true, offsetCenter: [0, "62%"], fontSize: 13, color: "#475569" },
        detail: {
          formatter: (v) => v.toFixed(2) + "%",
          offsetCenter: [0, "32%"],
          fontSize: 22,
          fontWeight: "bold",
          color: "#0f172a",
        },
        data: [{ value: pct(d.R.availability), name: `服务可用性（拥堵${d.R.jamCount}次·MTBF ${fmt(d.R.mtbfHours)}h）` }],
      },
    ],
  });
}

function renderTrend(points) {
  if (!points.length) return;
  const times = points.map((p) => {
    const t = new Date(p.time);
    return `${String(t.getHours()).padStart(2, "0")}:${String(t.getMinutes()).padStart(2, "0")}:${String(t.getSeconds()).padStart(2, "0")}`;
  });
  const unitCost = points.map((p) => p.metrics?.C?.unitCost ?? 0);
  const emptyRate = points.map((p) => pct(p.metrics?.U?.emptyRate ?? 0));
  const loadRate = points.map((p) => pct(p.metrics?.U?.loadRateMean ?? 0));
  const f1 = points.map((p) => pct(p.metrics?.M?.f1 ?? 0));

  setChart("trend", trendEl.value, {
    grid: { left: 50, right: 50, top: 36, bottom: 28 },
    tooltip: { trigger: "axis" },
    legend: { data: ["单位成本(元/吨公里)", "空驶率(%)", "装载率(%)", "匹配F1(%)"], top: 0, textStyle: { fontSize: 12 } },
    xAxis: { type: "category", data: times, axisLabel: { fontSize: 11 } },
    yAxis: [
      { type: "value", name: "元/吨公里", axisLabel: { fontSize: 11 } },
      { type: "value", name: "%", max: 100, axisLabel: { fontSize: 11 } },
    ],
    series: [
      { name: "单位成本(元/吨公里)", type: "line", smooth: true, showSymbol: false, data: unitCost, itemStyle: { color: "#f59e0b" } },
      { name: "空驶率(%)", type: "line", smooth: true, showSymbol: false, yAxisIndex: 1, data: emptyRate, itemStyle: { color: "#ef4444" } },
      { name: "装载率(%)", type: "line", smooth: true, showSymbol: false, yAxisIndex: 1, data: loadRate, itemStyle: { color: "#3b82f6" } },
      { name: "匹配F1(%)", type: "line", smooth: true, showSymbol: false, yAxisIndex: 1, data: f1, itemStyle: { color: "#10b981" } },
    ],
  });
}

onMounted(() => {
  fetchOverview();
  fetchTrend();
  timer = setInterval(() => {
    fetchOverview();
    fetchTrend();
  }, 5000);
  window.addEventListener("resize", handleResize);
});

function handleResize() {
  Object.values(charts).forEach((c) => c && c.resize());
}

onUnmounted(() => {
  if (timer) clearInterval(timer);
  window.removeEventListener("resize", handleResize);
  Object.values(charts).forEach((c) => c && c.dispose());
  charts = {};
});
</script>

<style scoped>
.eval-overlay {
  position: fixed;
  inset: 0;
  background: #eef2f7;
  z-index: 3000;
  overflow-y: auto;
}
.eval-panel {
  max-width: 1440px;
  margin: 0 auto;
  padding: 20px 28px 40px;
}
.eval-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 4px 0 14px;
}
.eval-header h2 { margin: 0 0 4px; color: #0f172a; font-size: 22px; }
.eval-sub { margin: 0; color: #64748b; font-size: 12px; }
.eval-header-right { display: flex; gap: 10px; align-items: center; }
.gap-btn {
  border: none; border-radius: 8px; padding: 8px 14px; cursor: pointer;
  background: #0f172a; color: #fff; font-size: 13px;
}
.gap-btn:disabled { opacity: 0.5; cursor: default; }
.close-btn {
  border: none; background: #fff; color: #334155;
  width: 38px; height: 38px; border-radius: 50%; cursor: pointer;
  box-shadow: 0 1px 4px rgba(15,23,42,.15);
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; line-height: 1; padding: 0; flex: none;
}
.eval-error {
  background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca;
  padding: 12px 16px; border-radius: 10px; margin-bottom: 16px; font-size: 13px;
}
.eval-loading { text-align: center; color: #64748b; padding: 80px 0; font-size: 14px; }

.kpi-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}
.kpi-card {
  background: #fff; border-radius: 14px; padding: 14px 16px;
  box-shadow: 0 1px 4px rgba(15,23,42,.06);
  border-top: 3px solid #2563eb;
}
.kpi-card:nth-child(2) { border-top-color: #10b981; }
.kpi-card:nth-child(3) { border-top-color: #ef4444; }
.kpi-card:nth-child(4) { border-top-color: #8b5cf6; }
.kpi-card:nth-child(5) { border-top-color: #f59e0b; }
.kpi-card:nth-child(6) { border-top-color: #06b6d4; }
.kpi-label { font-size: 12px; color: #64748b; display: flex; justify-content: space-between; }
.kpi-arrow { color: #94a3b8; font-size: 11px; }
.kpi-value { font-size: 26px; font-weight: 700; color: #0f172a; margin: 6px 0 4px; }
.kpi-unit { font-size: 12px; font-weight: 400; color: #94a3b8; margin-left: 4px; }
.kpi-sub { font-size: 11px; color: #94a3b8; line-height: 1.5; }
.kpi-warning { background: #fff7ed; }
.kpi-warning .kpi-value { color: #c2410c; }

.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 12px;
  margin-bottom: 14px;
}
.chart-row .wide { grid-column: span 3; }
.chart-card {
  background: #fff; border-radius: 14px; padding: 14px 16px;
  box-shadow: 0 1px 4px rgba(15,23,42,.06);
}
.chart-card h3 { margin: 0 0 6px; font-size: 14px; color: #0f172a; }
.chart-note { font-weight: 400; font-size: 11px; color: #94a3b8; margin-left: 6px; }
.chart-box { height: 230px; }
.chart-box-tall { height: 260px; }
.match-note { font-size: 11px; color: #94a3b8; margin-top: 4px; }

.gap-result {
  background: #fff; border-radius: 14px; padding: 14px 18px;
  box-shadow: 0 1px 4px rgba(15,23,42,.06);
}
.gap-result h4 { margin: 0 0 10px; font-size: 14px; color: #0f172a; }
.gap-items { display: flex; gap: 28px; flex-wrap: wrap; font-size: 13px; color: #475569; }
.gap-item b { color: #0f172a; }
.gap-item b.bad { color: #dc2626; }
.gap-none { margin: 0; color: #94a3b8; font-size: 13px; }

@media (max-width: 1100px) {
  .kpi-row { grid-template-columns: repeat(3, 1fr); }
  .chart-row { grid-template-columns: 1fr; }
  .chart-row .wide { grid-column: span 1; }
}
</style>
