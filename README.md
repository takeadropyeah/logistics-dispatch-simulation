# 物流运输调度仿真系统

进阶式挑战性综合课程设计 III（智能交通）课题：**运输车辆调度优化与仿真**。

基于高德地图真实路网的物流运输调度仿真平台：钢铁产业链运输任务自动生成 → 车辆派单匹配 → 路径规划 → 200ms 周期移动仿真 → WebSocket 实时大屏可视化，配套 **八维专业评价指标体系** 与 **可复现的数据实验沙箱**。

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | JDK 17 · Spring Boot 3.5.5 · MyBatis · MySQL 8 · WebSocket · Knife4j(OpenAPI) |
| 前端 | Vue 3 · Vite 7 · ECharts 6 · 高德 JS API 2.0 |
| 外部服务 | 高德 Web 服务 API（路径规划 / POI 搜索） |

## 核心功能

- **实时调度仿真大屏**：地图车辆动画（跟随模式/车队总览）、路线绘制、POI 海量点、顶部系统栏
- **八维评价指标看板**（📈 综合评价）：匹配精准度(M)、资源利用(U)、吞吐响应(T)、算法效率(A)、成本效益(C)、可靠性(R)、公平性(F)、绿色低碳(G)，30 秒快照趋势
- **数据实验沙箱**（🧪）：确定性仿真引擎（同种子结果逐位可复现），贪心/模拟退火/遗传三种派单算法对照实验，批次管理与对比分析
- **司机行为状态引擎**：拒单冷却 / 疲劳休息 / 休假 / 保养 / 故障，5 种以数学概率驱动的状态
- **随机事件**：交通拥堵、货物损耗、极端天气、车辆故障、缺货（5 种）

## 快速开始

### 环境要求

- JDK 17+、Maven 3.6+（或 IDEA 自带）
- Node.js 20.19+ / 22.12+
- MySQL 5.7 / 8.0 / 9.x（本机 3306）
- 高德开放平台账号（免费）：https://lbs.amap.com

### 1. 初始化数据库

用 Workbench 或命令行执行 [`sql/init.sql`](sql/init.sql)（创建 `zongshe` 库与 5 张核心表，其余表后端启动时自动创建）。

### 2. 申请高德 key（免费，约 5 分钟）

1. 登录高德开放平台 → 控制台 → 应用管理 → 创建新应用；
2. 为应用添加两个 key：
   - **Web端(JS API)** 类型 → 用于前端地图；
   - **Web服务** 类型 → 用于后端路径规划/POI。

### 3. 配置密钥（不入库）

- 后端：新建 `zongshe1/src/main/resources/application-local.yml`（已被 .gitignore 排除）：

  ```yaml
  amap:
    key: "你的Web服务key"
  spring:
    datasource:
      password: 你的MySQL密码
  ```

- 前端：在 `amapzongshe` 目录新建 `.env.local`（已排除）：

  ```ini
  VITE_AMAP_KEY=你的JS_API_key
  VITE_AMAP_SECURITY_CODE=对应的安全密钥（没有可留空）
  ```

> 仓库里的配置文件只有占位符，真实密钥都在各自的本地文件里，不要提交。

### 4. 启动后端（8080）

```bash
cd zongshe1
mvn spring-boot:run
```

启动成功后接口文档：http://localhost:8080/doc.html

### 5. 启动前端（5173）

```bash
cd amapzongshe
npm install
npm run dev
```

打开 http://localhost:5173 ，点左侧"▶ 开始仿真"，在 doc.html 调用 `POST /simulation/generate-vehicles?count=10` 生成车队（或等系统每 30 秒自动生成任务）。

## 常用接口

| 接口 | 说明 |
|---|---|
| `GET /metrics/overview` | 八维指标实时总览 |
| `GET /metrics/gap` | 优化 gap（精确枚举 vs 贪心/退火） |
| `POST /sandbox/experiment/{id}/run` | 沙箱运行一批次 |
| `POST /sandbox/run/{runId}/verify` | 同种子复现校验 |
| `POST /simulation/generate-vehicles?count=N` | 生成车队 |
| `GET /generate/steelChain?count=N` | 生成钢铁产业链任务 |

## 协作流程（Fork + Pull Request）

本仓库为公开仓库，**直接推送需要协作者权限**；小组成员默认走 fork 流程：

1. **Fork**：点仓库右上角 **Fork** 按钮，把仓库复制到自己账号下；
2. **Clone 自己的 fork**：

   ```bash
   git clone https://github.com/你的用户名/logistics-dispatch-simulation.git
   cd logistics-dispatch-simulation
   ```

3. **开发**：新建分支写代码（可选但推荐）：

   ```bash
   git checkout -b feature/你的功能名
   ```

4. **提交并推送到自己的 fork**：

   ```bash
   git add -A && git commit -m "简述改动"
   git push origin feature/你的功能名
   ```

5. **发起 PR**：回到 GitHub 你的 fork 页面 → **Compare & pull request** → 填写改动说明 → 提交；
6. 仓库所有者审核后合并进 main。

### 同步上游更新（定期做）

```bash
git remote add upstream https://github.com/takeadropyeah/logistics-dispatch-simulation.git   # 只需一次
git fetch upstream && git merge upstream/main
```

## 注意事项

- 密钥文件（`application-local.yml`、`.env.local`）**永远不要提交**；
- 提交前先同步上游更新，避免冲突；
- 提交信息用中文简述变更内容，如 `修复沙箱复现校验的参数绑定`；
- 高德个人 key 有配额，不要把 key 硬编码回仓库代码里。
