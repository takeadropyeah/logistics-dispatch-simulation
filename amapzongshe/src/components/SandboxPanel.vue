<template>
  <div class="sb-overlay">
    <div class="sb-panel">
      <!-- 顶栏 -->
      <div class="sb-header">
        <div>
          <h2>🧪 数据实验沙箱</h2>
          <p class="sb-sub">受控实验环境 · 固定种子可精确复现 · 变量隔离对照 · 同库多实验互不干扰（独立内存引擎，不影响线上仿真）</p>
        </div>
        <button class="close-btn" @click="$emit('close')">×</button>
      </div>

      <div class="sb-main">
        <!-- 左侧：实验方案列表 -->
        <div class="sb-left">
          <div class="sb-left-head">
            <h3>实验方案</h3>
            <button class="mini-btn primary" @click="creating = !creating">{{ creating ? "取消" : "+ 新增" }}</button>
          </div>
          <div v-if="creating" class="create-form">
            <input v-model="newName" placeholder="方案名称（如：贪心vs退火对照）" />
            <input v-model="newDesc" placeholder="实验描述（可空）" />
            <button class="mini-btn primary full" @click="createExperiment">创建方案</button>
          </div>
          <div class="exp-list">
            <div
              v-for="exp in experiments"
              :key="exp.id"
              class="exp-item"
              :class="{ active: exp.id === selectedId }"
              @click="selectExperiment(exp.id)"
            >
              <div class="exp-name">{{ exp.name }}</div>
              <div class="exp-meta">
                <span class="algo-tag" :class="algoClass(exp.config?.algo?.algorithm)">{{ exp.config?.algo?.algorithm || "GREEDY" }}</span>
                <span>{{ exp.run_count }} 批次</span>
              </div>
              <div class="exp-desc">{{ exp.description || "无描述" }}</div>
              <button class="exp-del" title="删除方案" @click.stop="deleteExperiment(exp.id)">×</button>
            </div>
            <div v-if="!experiments.length" class="sb-empty">暂无方案，点击"新增"创建第一个实验</div>
          </div>
        </div>

        <!-- 右侧：配置与结果 -->
        <div class="sb-right">
          <div v-if="!detail" class="sb-empty big">← 选择或创建一个实验方案开始</div>
          <template v-else>
            <div class="sb-right-head">
              <div>
                <h3>{{ detail.experiment.name }}</h3>
                <p>{{ detail.experiment.description || "无描述" }} · 研究人员：{{ detail.experiment.researcher }}</p>
              </div>
              <div class="tab-bar">
                <button v-for="t in ['env', 'algo', 'result']" :key="t" class="tab-btn" :class="{ active: tab === t }" @click="tab = t">
                  {{ { env: "环境配置", algo: "算法配置", result: "结果集查看" }[t] }}
                </button>
              </div>
            </div>

            <!-- 环境配置 -->
            <div v-if="tab === 'env'" class="cfg-body">
              <div class="cfg-grid">
                <label>车队规模 <input type="number" min="1" max="200" v-model.number="env.vehicleCount" /> 辆</label>
                <label>任务总数 <input type="number" min="5" max="1000" v-model.number="env.taskCount" /> 个</label>
                <label>仿真时长 <input type="number" min="100" max="10000" v-model.number="env.ticks" /> tick（1 tick=15虚拟分钟）</label>
                <label>拥堵概率 <input type="number" min="0" max="0.5" step="0.005" v-model.number="env.jamRate" /> /tick/车</label>
                <label>天气概率 <input type="number" min="0" max="0.2" step="0.005" v-model.number="env.weatherRate" /> /tick（触发后全路网降速3tick）</label>
              </div>
              <p class="cfg-tip">环境配置是所有批次共享的"固定变量"；对照实验时保持环境不变，只切换算法配置。保存后对后续运行生效。</p>
              <button class="mini-btn primary" @click="saveConfig">💾 保存环境配置</button>
            </div>

            <!-- 算法配置 -->
            <div v-if="tab === 'algo'" class="cfg-body">
              <div class="algo-radios">
                <label v-for="a in ['GREEDY', 'SA', 'GA']" :key="a" class="algo-radio" :class="{ active: algo.algorithm === a }">
                  <input type="radio" v-model="algo.algorithm" :value="a" />
                  <b>{{ { GREEDY: "贪心最近（基线）", SA: "模拟退火", GA: "遗传算法" }[a] }}</b>
                </label>
              </div>
              <div class="cfg-grid" v-if="algo.algorithm === 'SA'">
                <label>迭代次数 <input type="number" min="100" max="100000" v-model.number="algo.params.iterations" /></label>
                <label>初始温度 <input type="number" min="10" max="100000" v-model.number="algo.params.initTemp" /></label>
                <label>冷却率 <input type="number" min="0.9" max="0.9999" step="0.001" v-model.number="algo.params.coolRate" /></label>
              </div>
              <div class="cfg-grid" v-if="algo.algorithm === 'GA'">
                <label>种群规模 <input type="number" min="10" max="500" v-model.number="algo.params.population" /></label>
                <label>迭代代数 <input type="number" min="10" max="2000" v-model.number="algo.params.generations" /></label>
                <label>交叉率 <input type="number" min="0" max="1" step="0.05" v-model.number="algo.params.crossoverRate" /></label>
                <label>变异率 <input type="number" min="0" max="1" step="0.01" v-model.number="algo.params.mutationRate" /></label>
              </div>
              <div class="cfg-grid">
                <label>随机种子 <input type="number" v-model.number="seedInput" placeholder="留空 = 每批随机" />（固定种子 ⇒ 逐位可复现）</label>
              </div>
              <p class="cfg-tip">对照实验推荐做法：同一环境配置下，分别用三种算法各运行若干批次（种子一致），在"结果集查看"中勾选对比。</p>
              <div class="algo-actions">
                <button class="mini-btn primary" :disabled="running" @click="runBatch">{{ running ? "仿真运行中…" : "▶ 运行一批次" }}</button>
                <button class="mini-btn" :disabled="!selectedRunId || verifying" @click="verifyRun">{{ verifying ? "校验中…" : "🔁 同种子复现校验" }}</button>
              </div>
              <div v-if="runMsg" class="run-msg" :class="{ ok: runOk }">{{ runMsg }}</div>
              <div v-if="verifyResult" class="verify-banner" :class="{ ok: verifyResult.reproducible }">
                <template v-if="verifyResult.reproducible">
                  ✅ 复现验证通过：同种子两次运行总成本逐位一致 = {{ verifyResult.costRun1 }} 元（种子 {{ verifyResult.seed }}）
                </template>
                <template v-else>❌ 两次运行不一致：{{ verifyResult.costRun1 }} vs {{ verifyResult.costRun2 }}</template>
              </div>
            </div>

            <!-- 结果集查看 -->
            <div v-if="tab === 'result'" class="result-body">
              <table class="run-table">
                <thead>
                  <tr>
                    <th style="width:36px">对比</th>
                    <th>批次</th><th>算法</th><th>种子</th>
                    <th>总成本↓</th><th>单位成本</th><th>空驶率</th><th>装载率</th>
                    <th>P95响应</th><th>准时率</th><th>基尼</th><th>碳排kg</th><th>求解ms</th><th style="width:70px">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="run in detail.runs"
                    :key="run.id"
                    :class="{ selected: run.id === selectedRunId }"
                    @click="selectRun(run.id)"
                  >
                    <td><input type="checkbox" :value="run.id" v-model="compareIds" @click.stop /></td>
                    <td>#{{ run.run_no }}</td>
                    <td><span class="algo-tag" :class="algoClass(run.algorithm)">{{ run.algorithm }}</span></td>
                    <td class="mono">{{ run.seed }}</td>
                    <td><b>{{ fv(run.result, "C", "totalCost") }}</b></td>
                    <td>{{ fv(run.result, "C", "unitCost") }}</td>
                    <td>{{ fp(run.result, "U", "emptyRate") }}</td>
                    <td>{{ fp(run.result, "U", "loadRateMean") }}</td>
                    <td>{{ fv(run.result, "T", "respP95") }}h</td>
                    <td>{{ fp(run.result, "totals", "onTimeRate") }}</td>
                    <td>{{ fv(run.result, "F", "gini") }}</td>
                    <td>{{ fv(run.result, "G", "carbonKg") }}</td>
                    <td>{{ run.compute_ms }}</td>
                    <td><button class="mini-btn tiny" @click.stop="rerunSame(run)">再次运行</button></td>
                  </tr>
                </tbody>
              </table>
              <div v-if="!detail.runs.length" class="sb-empty">该方案还没有批次，去"算法配置"运行一批次</div>

              <div class="compare-charts" v-if="compareIds.length">
                <div class="chart-half"><div ref="barEl" class="sb-chart"></div></div>
                <div class="chart-half"><div ref="radarEl" class="sb-chart"></div></div>
              </div>

              <div class="compare-charts" v-if="selectedRunSnapshots.length">
                <div class="chart-half wide"><div ref="snapEl" class="sb-chart"></div></div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from "vue";
import * as echarts from "echarts";

defineEmits(["close"]);

const API = "http://localhost:8080";

const experiments = ref([]);
const selectedId = ref(null);
const detail = ref(null);
const tab = ref("env");
const creating = ref(false);
const newName = ref("");
const newDesc = ref("");

const env = ref({ vehicleCount: 12, taskCount: 60, ticks: 720, jamRate: 0.02, weatherRate: 0.01 });
const algo = ref({ algorithm: "GREEDY", params: { iterations: 4000, initTemp: 1000, coolRate: 0.995, population: 40, generations: 80, crossoverRate: 0.8, mutationRate: 0.1 } });
const seedInput = ref(null);

const running = ref(false);
const verifying = ref(false);
const runMsg = ref("");
const runOk = ref(false);
const verifyResult = ref(null);
const selectedRunId = ref(null);
const compareIds = ref([]);
const selectedRunSnapshots = ref([]);

const barEl = ref(null);
const radarEl = ref(null);
const snapEl = ref(null);
let charts = {};

const fv = (result, dim, key) => {
  const v = result?.[dim]?.[key];
  return typeof v === "number" ? (Math.round(v * 100) / 100).toLocaleString() : "-";
};
const fp = (result, dim, key) => {
  const v = result?.[dim]?.[key];
  return typeof v === "number" ? (Math.round(v * 10000) / 100) + "%" : "-";
};

const algoClass = (a) => ({ GREEDY: "t-greedy", SA: "t-sa", GA: "t-ga" }[a] || "t-greedy");

async function loadExperiments() {
  const res = await fetch(`${API}/sandbox/experiments`);
  const json = await res.json();
  experiments.value = json.experiments || [];
}

async function selectExperiment(id) {
  selectedId.value = id;
  tab.value = "env";
  const res = await fetch(`${API}/sandbox/experiment/${id}`);
  const json = await res.json();
  if (json.status !== "success") return;
  detail.value = json;
  const cfg = json.experiment.config || {};
  if (cfg.env) env.value = { ...env.value, ...cfg.env };
  if (cfg.algo) {
    // 兼容历史脏数据：算法值不合法时回落为贪心基线
    const savedAlgo = cfg.algo.algorithm;
    const valid = ["GREEDY", "SA", "GA"].includes(savedAlgo) ? savedAlgo : "GREEDY";
    algo.value = {
      algorithm: valid,
      params: { ...algo.value.params, ...(cfg.algo.params || {}) },
    };
  }
  seedInput.value = cfg.seed ?? null;
  selectedRunId.value = json.runs.length ? json.runs[json.runs.length - 1].id : null;
  compareIds.value = [];
  selectedRunSnapshots.value = [];
  if (selectedRunId.value) loadSnapshots(selectedRunId.value);
}

async function createExperiment() {
  if (!newName.value.trim()) return;
  const res = await fetch(`${API}/sandbox/experiment`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name: newName.value, description: newDesc.value }),
  });
  const json = await res.json();
  if (json.status === "success") {
    creating.value = false;
    newName.value = "";
    newDesc.value = "";
    await loadExperiments();
    selectExperiment(json.id);
  }
}

async function deleteExperiment(id) {
  if (!confirm("删除该方案及其全部批次数据？")) return;
  await fetch(`${API}/sandbox/experiment/${id}`, { method: "DELETE" });
  if (selectedId.value === id) { selectedId.value = null; detail.value = null; }
  loadExperiments();
}

function currentConfig() {
  return {
    env: { ...env.value },
    algo: JSON.parse(JSON.stringify(algo.value)),
    seed: seedInput.value === null || seedInput.value === "" || isNaN(seedInput.value) ? null : Number(seedInput.value),
  };
}

async function saveConfig() {
  await fetch(`${API}/sandbox/experiment/${selectedId.value}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ config: currentConfig() }),
  });
  runOk.value = true;
  runMsg.value = "✅ 配置已保存";
  loadExperiments();
}

async function runBatch() {
  running.value = true;
  runMsg.value = "";
  verifyResult.value = null;
  try {
    // 先保存配置再运行，保证方案与批次一致
    await fetch(`${API}/sandbox/experiment/${selectedId.value}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ config: currentConfig() }),
    });
    const res = await fetch(`${API}/sandbox/experiment/${selectedId.value}/run`, { method: "POST" });
    const json = await res.json();
    if (json.status === "success") {
      const r = json.result;
      runOk.value = true;
      runMsg.value = `✅ 批次#${json.runNo} 完成（${json.computeMs}ms，种子 ${json.seed}）：完成${r.totals.completed}/${r.totals.taskCount} 单，总成本 ${r.C.totalCost} 元，空驶率 ${(r.U.emptyRate * 100).toFixed(1)}%`;
      await selectExperiment(selectedId.value);
      selectedRunId.value = json.runId;
      detail.value.runs.forEach((run) => { if (run.id === json.runId) selectRun(json.runId); });
      tab.value = "result";
    } else {
      runOk.value = false;
      runMsg.value = "❌ 运行失败：" + json.message;
    }
  } catch (e) {
    runOk.value = false;
    runMsg.value = "❌ 请求失败：" + e.message;
  }
  running.value = false;
  loadExperiments();
}

async function rerunSame(run) {
  // 再次运行：固定该批次的种子重新执行（结果应逐位一致）
  seedInput.value = run.seed;
  await selectExperiment(selectedId.value);
  seedInput.value = run.seed;
  await runBatch();
}

async function verifyRun() {
  verifying.value = true;
  verifyResult.value = null;
  try {
    const res = await fetch(`${API}/sandbox/run/${selectedRunId.value}/verify`, { method: "POST" });
    verifyResult.value = await res.json();
  } catch (e) {
    verifyResult.value = { reproducible: false, costRun1: "-", costRun2: "-" };
  }
  verifying.value = false;
}

function selectRun(runId) {
  selectedRunId.value = runId;
  loadSnapshots(runId);
}

async function loadSnapshots(runId) {
  const res = await fetch(`${API}/sandbox/run/${runId}/snapshots`);
  const json = await res.json();
  selectedRunSnapshots.value = json.snapshots || [];
  await nextTick();
  renderSnap();
}

watch(compareIds, async () => {
  if (!compareIds.value.length) return;
  await nextTick();
  renderCompare();
});

// 切到结果页时图表容器才挂载，需要补一次渲染
watch(tab, async (t) => {
  if (t !== "result") return;
  await nextTick();
  renderSnap();
  if (compareIds.value.length) renderCompare();
});

async function renderCompare() {
  const res = await fetch(`${API}/sandbox/compare?runIds=${compareIds.value.join(",")}`);
  const json = await res.json();
  const runs = json.runs || [];
  if (!runs.length) return;
  const names = runs.map((r) => `${r.experimentName}·#${r.runNo}·${r.algorithm}`);

  setChart("bar", barEl.value, {
    tooltip: { trigger: "axis" },
    legend: { top: 0, textStyle: { fontSize: 11 } },
    grid: { left: 60, right: 20, top: 40, bottom: 26 },
    xAxis: { type: "category", data: names, axisLabel: { fontSize: 10, interval: 0, rotate: names.length > 3 ? 16 : 0 } },
    yAxis: [
      { type: "value", name: "元 / %", axisLabel: { fontSize: 10 } },
    ],
    series: [
      { name: "总成本(元)", type: "bar", data: runs.map((r) => r.result?.C?.totalCost ?? 0), itemStyle: { color: "#6366f1" } },
      { name: "单位成本(元/吨km)", type: "bar", data: runs.map((r) => r.result?.C?.unitCost ?? 0), itemStyle: { color: "#f59e0b" } },
      { name: "空驶率(%)", type: "bar", data: runs.map((r) => Math.round((r.result?.U?.emptyRate ?? 0) * 10000) / 100), itemStyle: { color: "#ef4444" } },
      { name: "装载率(%)", type: "bar", data: runs.map((r) => Math.round((r.result?.U?.loadRateMean ?? 0) * 10000) / 100), itemStyle: { color: "#10b981" } },
    ],
  });

  setChart("sbradar", radarEl.value, {
    tooltip: {},
    legend: { top: 0, textStyle: { fontSize: 11 }, data: names },
    radar: {
      indicator: [
        { name: "匹配F1", max: 100 }, { name: "装载率", max: 100 }, { name: "里程利用", max: 100 },
        { name: "准时率", max: 100 }, { name: "可用性", max: 100 }, { name: "响应效率", max: 100 },
      ],
      radius: "58%",
      axisName: { color: "#475569", fontSize: 11 },
    },
    series: [{
      type: "radar",
      data: runs.map((r, i) => ({
        name: names[i],
        value: [
          (r.result?.M?.f1 ?? 0) * 100,
          (r.result?.U?.loadRateMean ?? 0) * 100,
          (r.result?.U?.mileageUtilization ?? 0) * 100,
          (r.result?.totals?.onTimeRate ?? 0) * 100,
          (r.result?.R?.availability ?? 0) * 100,
          Math.max(0, 100 - (r.result?.T?.respP95 ?? 0) * 15),
        ].map((v) => Math.round(v * 10) / 10),
        areaStyle: { opacity: 0.12 },
      })),
    }],
  });
}

function renderSnap() {
  const s = selectedRunSnapshots.value;
  if (!s.length) return;
  setChart("snap", snapEl.value, {
    tooltip: { trigger: "axis" },
    legend: { top: 0, textStyle: { fontSize: 11 } },
    grid: { left: 60, right: 60, top: 40, bottom: 26 },
    xAxis: { type: "category", data: s.map((p) => "t" + p.tick), axisLabel: { fontSize: 10 } },
    yAxis: [
      { type: "value", name: "单量", axisLabel: { fontSize: 10 } },
      { type: "value", name: "元/%", axisLabel: { fontSize: 10 } },
    ],
    series: [
      { name: "已完成", type: "line", smooth: true, showSymbol: false, data: s.map((p) => p.completed), itemStyle: { color: "#10b981" } },
      { name: "待分配", type: "line", smooth: true, showSymbol: false, data: s.map((p) => p.pending), itemStyle: { color: "#94a3b8" } },
      { name: "空驶率%", type: "line", smooth: true, showSymbol: false, yAxisIndex: 1, data: s.map((p) => Math.round(p.emptyRate * 10000) / 100), itemStyle: { color: "#ef4444" } },
      { name: "单位成本", type: "line", smooth: true, showSymbol: false, yAxisIndex: 1, data: s.map((p) => p.unitCost), itemStyle: { color: "#f59e0b" } },
    ],
  });
}

function setChart(key, el, option) {
  if (!el) return;
  if (!charts[key]) charts[key] = echarts.init(el);
  charts[key].setOption(option, true);
  charts[key].resize();
}

onMounted(loadExperiments);

onUnmounted(() => {
  Object.values(charts).forEach((c) => c && c.dispose());
  charts = {};
});
</script>

<style scoped>
.sb-overlay { position: fixed; inset: 0; background: #eef2f7; z-index: 3000; overflow-y: auto; }
.sb-panel { max-width: 1500px; margin: 0 auto; padding: 20px 28px 40px; }
.sb-header { display: flex; justify-content: space-between; align-items: flex-start; padding-bottom: 14px; }
.sb-header h2 { margin: 0 0 4px; color: #0f172a; font-size: 22px; }
.sb-sub { margin: 0; color: #64748b; font-size: 12px; }
.close-btn {
  border: none; background: #fff; color: #334155;
  width: 38px; height: 38px; border-radius: 50%; cursor: pointer;
  box-shadow: 0 1px 4px rgba(15,23,42,.15);
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; line-height: 1; padding: 0; flex: none;
}
.sb-main { display: grid; grid-template-columns: 300px 1fr; gap: 14px; align-items: start; }

.sb-left, .sb-right { background: #fff; border-radius: 14px; box-shadow: 0 1px 4px rgba(15,23,42,.06); }
.sb-left { padding: 14px; position: sticky; top: 16px; }
.sb-left-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.sb-left-head h3 { margin: 0; font-size: 15px; color: #0f172a; }
.create-form { display: flex; flex-direction: column; gap: 8px; margin-bottom: 10px; }
.create-form input {
  border: 1px solid #e2e8f0; border-radius: 8px; padding: 8px 10px; font-size: 12px; outline: none;
}
.create-form input:focus { border-color: #6366f1; }

.mini-btn {
  border: 1px solid #e2e8f0; background: #fff; color: #334155; border-radius: 8px;
  padding: 7px 12px; font-size: 12px; cursor: pointer;
}
.mini-btn:hover { border-color: #cbd5e1; }
.mini-btn.primary { background: #4f46e5; border-color: #4f46e5; color: #fff; }
.mini-btn.primary:disabled { opacity: 0.5; cursor: default; }
.mini-btn.full { width: 100%; }
.mini-btn.tiny { padding: 3px 8px; font-size: 11px; }

.exp-list { display: flex; flex-direction: column; gap: 8px; max-height: 640px; overflow-y: auto; }
.exp-item {
  border: 1px solid #e2e8f0; border-radius: 10px; padding: 10px 12px; cursor: pointer; position: relative;
}
.exp-item:hover { border-color: #c7d2fe; }
.exp-item.active { border-color: #4f46e5; background: #eef2ff; }
.exp-name { font-size: 13px; font-weight: 600; color: #0f172a; }
.exp-meta { display: flex; gap: 8px; align-items: center; font-size: 11px; color: #94a3b8; margin-top: 4px; }
.exp-desc { font-size: 11px; color: #94a3b8; margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.exp-del {
  position: absolute; top: 6px; right: 8px; border: none; background: none; color: #cbd5e1;
  font-size: 16px; cursor: pointer; line-height: 1;
}
.exp-del:hover { color: #ef4444; }
.algo-tag { font-size: 10px; padding: 1px 7px; border-radius: 99px; font-weight: 600; }
.t-greedy { background: #f1f5f9; color: #475569; }
.t-sa { background: #fef3c7; color: #b45309; }
.t-ga { background: #ddd6fe; color: #6d28d9; }

.sb-right { padding: 16px 20px; min-height: 400px; }
.sb-right-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; flex-wrap: wrap; }
.sb-right-head h3 { margin: 0 0 2px; font-size: 17px; color: #0f172a; }
.sb-right-head p { margin: 0; font-size: 12px; color: #94a3b8; }
.tab-bar { display: flex; gap: 6px; }
.tab-btn {
  border: 1px solid #e2e8f0; background: #fff; color: #475569; border-radius: 99px;
  padding: 6px 16px; font-size: 12px; cursor: pointer;
}
.tab-btn.active { background: #0f172a; border-color: #0f172a; color: #fff; }

.cfg-body { margin-top: 16px; }
.cfg-grid { display: flex; flex-wrap: wrap; gap: 12px 22px; margin-bottom: 12px; }
.cfg-grid label { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #334155; }
.cfg-grid input {
  width: 110px; border: 1px solid #e2e8f0; border-radius: 8px; padding: 7px 10px; font-size: 13px; outline: none;
}
.cfg-grid input:focus { border-color: #6366f1; }
.cfg-tip { font-size: 12px; color: #94a3b8; margin: 4px 0 14px; }
.algo-radios { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.algo-radio {
  border: 1px solid #e2e8f0; border-radius: 10px; padding: 10px 14px; cursor: pointer; font-size: 13px; color: #334155;
}
.algo-radio.active { border-color: #4f46e5; background: #eef2ff; }
.algo-radio input { display: none; }
.algo-actions { display: flex; gap: 10px; margin-top: 6px; }
.run-msg { margin-top: 12px; font-size: 13px; color: #b91c1c; }
.run-msg.ok { color: #047857; }
.verify-banner {
  margin-top: 12px; border-radius: 10px; padding: 10px 14px; font-size: 13px;
  background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca;
}
.verify-banner.ok { background: #ecfdf5; color: #047857; border-color: #a7f3d0; }

.result-body { margin-top: 16px; }
.run-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.run-table th {
  text-align: left; padding: 8px 8px; background: #f8fafc; color: #64748b; font-weight: 600;
  border-bottom: 1px solid #e2e8f0; white-space: nowrap;
}
.run-table td { padding: 8px; border-bottom: 1px solid #f1f5f9; color: #334155; white-space: nowrap; }
.run-table tr { cursor: pointer; }
.run-table tr:hover { background: #f8fafc; }
.run-table tr.selected { background: #eef2ff; }
.mono { font-family: Consolas, monospace; font-size: 11px; }

.compare-charts { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 16px; }
.chart-half { background: #f8fafc; border: 1px solid #f1f5f9; border-radius: 12px; padding: 8px; }
.chart-half.wide { grid-column: span 2; }
.sb-chart { height: 280px; }

.sb-empty { color: #94a3b8; font-size: 13px; padding: 20px 0; text-align: center; }
.sb-empty.big { padding: 120px 0; font-size: 15px; }

@media (max-width: 1100px) {
  .sb-main { grid-template-columns: 1fr; }
  .sb-left { position: static; }
  .compare-charts { grid-template-columns: 1fr; }
  .chart-half.wide { grid-column: span 1; }
}
</style>
