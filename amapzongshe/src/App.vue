<script setup>
import { onMounted, ref, computed, watchEffect, onUnmounted } from "vue";
import AMapLoader from "@amap/amap-jsapi-loader";
import { AMAP_CONFIG } from "./config";
import VehicleVisualization from "./components/VehicleVisualization.vue";
import GoodsVisualization from "./components/GoodsVisualization.vue";
import CostVisualization from "./components/CostVisualization.vue";
import EvaluationDashboard from "./components/EvaluationDashboard.vue";
import SandboxPanel from "./components/SandboxPanel.vue";

// 地图容器引用
const mapContainer = ref(null);
let map = null;
let AMap = null;

// WebSocket连接
let ws = null;

// 车辆数据存储
const vehicles = ref([]);
const vehicleMarkers = new Map(); // 车辆标记缓存
const connectionStatus = ref("disconnected");

// 车辆动画状态管理（时间驱动）
const vehicleAnimations = new Map(); // 存储每辆车的动画状态
let animationFrameId = null; // requestAnimationFrame ID

// 地图交互状态
let isMapInteracting = () => false; // 默认返回false，地图初始化后会替换为实际的检查函数

// WebSocket配置
const WS_CONFIG = {
  url: "ws://localhost:8080/vehicle-simulation",
  reconnectInterval: 5000, // 重连间隔时间（毫秒）
};

// API配置
const API_CONFIG = {
  baseUrl: "http://localhost:8080",
};

// 当前速度倍数（响应式变量）
const currentSpeed = ref(30);

// 防抖函数
function debounce(func, wait) {
  let timeout;
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout);
      func(...args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
}

// 需要更新的车辆ID集合
let pendingUpdates = new Set();

// 批量更新车辆标记（防抖版）
const debouncedUpdateVehicleMarkers = debounce(() => {
  updateVehicleMarkers();
}, 100);

// 批量更新车辆（防抖）
function scheduleVehicleUpdate(vehicle) {
  const id = vehicle.id || vehicle.truckId;
  if (id) {
    pendingUpdates.add(id);
    debouncedBatchUpdate();
  }
}

// 防抖批量更新
const debouncedBatchUpdate = debounce(() => {
  if (pendingUpdates.size > 0) {
    // 批量更新所有待更新的车辆
    vehicles.value.forEach((vehicle) => {
      const id = vehicle.id || vehicle.truckId;
      if (pendingUpdates.has(id)) {
        updateSingleVehicleMarkerOptimized(vehicle);
      }
    });
    pendingUpdates.clear();
  }
}, 100);

// 初始化WebSocket连接
function initWebSocket() {
  try {
    // 关闭可能存在的旧连接
    if (ws) {
      ws.close();
    }

    ws = new WebSocket(WS_CONFIG.url);

    // 连接打开
    ws.onopen = () => {
      console.log("WebSocket 连接成功");
      connectionStatus.value = "connected";
    };

    // 接收消息
    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        
        // 处理事件驱动的消息
        if (data.eventType) {
          if (data.eventType === 'SPEED_CHANGED') {
            // 处理速度变更事件：更新所有动画状态中的速度
            handleSpeedChange(data);
          } else {
            handleTruckEvent(data);
          }
        } else if (Array.isArray(data)) {
          // 批量初始化数据 - 只显示前20辆车以减轻渲染压力
          vehicles.value = data.slice(0, 20);
          
          const activeTaskIds = new Set();
          // 只遍历我们保留的车辆（前20辆），而不是所有数据
          vehicles.value.forEach((vehicle) => {
            if (vehicle.taskId) {
              activeTaskIds.add(vehicle.taskId);
            }
          });
          
          // 先清除所有现有路线（避免残留旧路线）
          routePolylines.forEach((_, taskId) => {
            clearRoute(taskId);
          });
          
          // 延迟更新标记，路线统一由 loadAllRoutes() 加载
          setTimeout(() => {
            debouncedUpdateVehicleMarkers();

            // 为正在运输中的车辆初始化动画（按当前位置投影恢复进度，避免被"传送"回起点）
            vehicles.value.forEach((vehicle) => {
              const truckId = vehicle.id || vehicle.truckId;
              if ((vehicle.status === '运输中' || vehicle.status === '取货中') &&
                  vehicle.taskId &&
                  !vehicleAnimations.has(truckId)) {
                fetchRouteData(vehicle.taskId).then((routeData) => {
                  if (routeData && routeData.points && routeData.points.length > 1) {
                    const state = buildAnimState(truckId, routeData.points.map(p => ({ pointLocation: p.pointLocation })), routeData.totalDistance || 100, Date.now(), currentSpeed.value);
                    if (!state) return;
                    // 按车辆当前位置在路径上投影，反推已行驶里程与 startTime
                    const cur = parseLocation(vehicle);
                    if (cur) {
                      let bestI = 0, bestD = Infinity;
                      for (let i = 0; i < state.routePoints.length; i++) {
                        const d = calculateSegmentDistance(cur, state.routePoints[i]);
                        if (d < bestD) { bestD = d; bestI = i; }
                      }
                      const traveledMeters = (state.cumDist[bestI] || 0) * 1000;
                      const speedMs = (600 * state.simulationSpeed * 1000) / 3600; // 米/毫秒
                      state.startTime = Date.now() - traveledMeters / speedMs;
                    }
                    vehicleAnimations.set(truckId, state);
                    console.log(`车辆 ${truckId} 恢复动画（按当前位置投影），路径点数: ${state.routePoints.length}`);
                  }
                });
              }
            });
          }, 0);
        } else if (typeof data === 'object' && data !== null) {
          // 兼容旧的单条更新格式 - 使用防抖批量更新
          const id = data.id || data.truckId;
          if (id) {
            const index = vehicles.value.findIndex(v => (v.id || v.truckId) === id);
            if (index !== -1) {
              vehicles.value[index] = { ...vehicles.value[index], ...data };
              scheduleVehicleUpdate(vehicles.value[index]);
            } else if (vehicles.value.length < 20) {
              // 只添加不超过20辆车
              vehicles.value.push(data);
              scheduleVehicleUpdate(data);
            }
          }
        }
      } catch (e) {
        console.error("解析车辆数据失败:", e);
      }
    };

       
    // 连接错误
    ws.onerror = (err) => {
      console.error("WebSocket 错误:", err);
      connectionStatus.value = "error";
    };

    // 连接关闭
    ws.onclose = () => {
      console.log("WebSocket 连接关闭");
      connectionStatus.value = "disconnected";
      // 尝试重新连接
      setTimeout(initWebSocket, WS_CONFIG.reconnectInterval);
    };
  } catch (e) {
    console.error("初始化WebSocket失败:", e);
    connectionStatus.value = "error";
  }
}

// 辅助函数：解析位置
function parseLocation(vehicle) {
    let lng, lat;
    if (vehicle.location && typeof vehicle.location === "string") {
      const coords = vehicle.location.split(",");
      if (coords.length === 2) {
        const val1 = parseFloat(coords[0]);
        const val2 = parseFloat(coords[1]);
        
        // 智能识别经纬度（基于中国大致范围：经度73-135，纬度4-53）
        // 如果第一个值在经度范围内且第二个值在纬度范围内 -> lng,lat
        // 如果第二个值在经度范围内且第一个值在纬度范围内 -> lat,lng
        if (val1 > 70 && val1 < 140 && val2 > 0 && val2 < 60) {
           lng = val1;
           lat = val2;
        } else if (val2 > 70 && val2 < 140 && val1 > 0 && val1 < 60) {
           lng = val2;
           lat = val1;
        } else {
           // 默认回退：假设是 lng,lat (后端存的是这个)
           lng = val1;
           lat = val2;
        }

      }
    } else if (vehicle.lon !== undefined && vehicle.lat !== undefined) {
      lng = parseFloat(vehicle.lon);
      lat = parseFloat(vehicle.lat);
    }
    if (!isNaN(lng) && !isNaN(lat)) return [lng, lat];
    return null;
}

// 处理后端推送的车辆事件（时间驱动核心逻辑）
function handleTruckEvent(event) {
  const { eventType, truckId, location, status, taskId, routePoints, totalDistance, startTime, simulationSpeed } = event;

  // 更新车辆列表（车辆超出前20名渲染上限时跳过列表，但不影响动画状态管理）
  const index = vehicles.value.findIndex(v => (v.id || v.truckId) === truckId);
  const vehicle = index !== -1 ? vehicles.value[index] : null;
  if (vehicle) {
    vehicles.value[index] = {
      ...vehicle,
      location,
      status,
      taskId,
      simulationSpeed
    };
  }

  switch (eventType) {
    case 'TASK_ASSIGNED':
    case 'DEPARTURE':
      // 车辆出发：初始化动画状态（列表已满且看不见的车不建动画，避免无谓计算）
      if (routePoints && routePoints.length > 0 && (vehicle || vehicles.value.length < 20)) {
        const animState = buildAnimState(truckId, routePoints, totalDistance, startTime, simulationSpeed);
        if (animState) {
          vehicleAnimations.set(truckId, animState);
          console.log(`车辆 ${truckId} 出发，开始动画，路径点数: ${animState.routePoints.length}`);
        }
      }
      break;

    case 'PICKUP_START':
      // 车辆前往取货点：初始化动画状态
      if (routePoints && routePoints.length > 0 && (vehicle || vehicles.value.length < 20)) {
        const animState = buildAnimState(truckId, routePoints, totalDistance, startTime, simulationSpeed);
        if (animState) {
          vehicleAnimations.set(truckId, animState);
          console.log(`车辆 ${truckId} 前往取货点，路径点数: ${animState.routePoints.length}`);
        }
      }
      break;

    case 'ARRIVED_PICKUP':
      // 到达取货点：停止动画，等待装货
      vehicleAnimations.delete(truckId);
      if (vehicle) updateSingleVehicleMarker(vehicle);
      console.log(`车辆 ${truckId} 已到达取货点，开始装货`);
      break;

    case 'POSITION_UPDATE': {
      const anim = vehicleAnimations.get(truckId);
      if (!anim || !anim.isAnimating) {
        // 无本地动画状态（页面中途加入/漏事件）：由后端位置直接驱动（兜底）
        if (vehicle) updateSingleVehicleMarker(vehicle);
        break;
      }
      // 有本地动画状态：本地插值为唯一视觉驱动源；
      // 仅当偏差过大（网络抖动/电脑休眠）时做一次性软同步，消除双源抖动
      const marker = vehicleMarkers.get(truckId);
      const backendPos = parseLocation({ location });
      if (marker && backendPos) {
        const mp = marker.getPosition();
        const driftKm = calculateSegmentDistance([mp.lng, mp.lat], backendPos);
        if (driftKm > 2) {
          const end = anim.routePoints[anim.routePoints.length - 1];
          const remainMarker = calculateSegmentDistance([mp.lng, mp.lat], end);
          const remainBackend = calculateSegmentDistance(backendPos, end);
          const speedMs = (600 * (anim.simulationSpeed || 1) * 1000) / 3600; // 米/毫秒
          const diffMeters = Math.abs(remainMarker - remainBackend) * 1000;
          // 后端更靠前 => 本地进度需要变快 => startTime 回退
          anim.startTime += (remainBackend < remainMarker ? -1 : 1) * (diffMeters / speedMs);
        }
      }
      break;
    }

    case 'ARRIVED':
      // 到达终点：停止动画
      vehicleAnimations.delete(truckId);
      if (vehicle) updateSingleVehicleMarker(vehicle);
      console.log(`车辆 ${truckId} 已到达`);
      break;

    case 'TRAFFIC_JAM':
    case 'WAITING':
    case 'UNLOADING_START':
    case 'JAM_CLEARED':
      // 状态变化：更新标记显示
      if (vehicle) updateSingleVehicleMarker(vehicle);
      break;

    case 'VEHICLE_FAULT':
      // 车辆故障：停止本地动画，标记切换为故障图标（任务已由后端回池）
      vehicleAnimations.delete(truckId);
      if (vehicle) updateSingleVehicleMarker(vehicle);
      console.log(`车辆 ${truckId} 故障，任务已回池，等待维修`);
      break;

    case 'DRIVER_REST':
    case 'REST_DONE':
    case 'REPAIR_DONE':
    case 'MAINT_START':
    case 'MAINT_DONE':
    case 'DRIVER_REJECT':
      // 司机行为/恢复事件：刷新标记状态
      if (vehicle) updateSingleVehicleMarker(vehicle);
      break;
  }
}

// 处理速度变更事件
function handleSpeedChange(event) {
  const { oldSpeed, newSpeed } = event;
  console.log(`收到速度变更事件: ${oldSpeed}x -> ${newSpeed}x`);
  
  // 更新所有正在动画的车辆的速度
  vehicleAnimations.forEach((animState, truckId) => {
    if (animState.isAnimating) {
      // 计算当前已行驶的进度
      const now = Date.now();
      const elapsed = now - animState.startTime;
      const oldSpeedMs = (600 * animState.simulationSpeed * 1000) / 3600;
      const traveledDistance = oldSpeedMs * elapsed;
      const progress = Math.min(traveledDistance / (animState.totalDistance * 1000), 1.0);
      
      if (progress < 1.0) {
        // 关键修复：根据已行驶距离，倒推一个新的startTime
        // 使得在新速度下，从新的startTime开始计算，车辆会处于相同的位置
        const newSpeedMs = (600 * newSpeed * 1000) / 3600;
        const timeNeededSeconds = traveledDistance / newSpeedMs;
        const newStartTime = now - (timeNeededSeconds * 1000);
        
        // 更新动画状态
        animState.startTime = newStartTime;
        animState.simulationSpeed = newSpeed;
        
        console.log(`车辆 ${truckId} 动画速度更新: ${animState.simulationSpeed}x -> ${newSpeed}x, 已行驶 ${(progress * 100).toFixed(2)}%`);
      }
    }
  });
  
  // 同时更新currentSpeed显示
  currentSpeed.value = newSpeed;
}

// 启动全局动画循环（优化版：使用 moveTo 批量更新）
function startAnimationLoop() {
  if (animationFrameId) return; // 防止重复启动
  
  let lastUpdateTime = Date.now();
  const updateInterval = 100; // 每100ms更新一次，减少更新频率
  
  function animate() {
    const now = Date.now();
    
    // 如果地图正在交互（拖拽、缩放），跳过更新以提升性能
    if (isMapInteracting()) {
      animationFrameId = requestAnimationFrame(animate);
      return;
    }
    
    // 限制更新频率，避免过于频繁的重绘
    if (now - lastUpdateTime < updateInterval) {
      animationFrameId = requestAnimationFrame(animate);
      return;
    }
    lastUpdateTime = now;
    
    // 收集需要更新的车辆
    const updates = [];
    
    // 遍历所有正在动画的车辆
    vehicleAnimations.forEach((animState, truckId) => {
      if (!animState.isAnimating) return;
      
      const elapsed = now - animState.startTime;
      const speedMs = (600 * animState.simulationSpeed * 1000) / 3600; // 米/毫秒
      const traveledDistance = speedMs * elapsed;
      const progress = Math.min(traveledDistance / (animState.totalDistance * 1000), 1.0);
      
      // 计算插值位置
      const interpolatedPos = interpolatePosition(animState.routePoints, progress, animState.totalDistance, animState);
      
      if (interpolatedPos) {
        updates.push({ truckId, interpolatedPos, progress });
        
        // 更新车辆数据（不更新数据库，只更新前端显示）
        const index = vehicles.value.findIndex(v => (v.id || v.truckId) === truckId);
        if (index !== -1) {
          const vehicle = vehicles.value[index];
          vehicle.currentLat = interpolatedPos[1];
          vehicle.currentLon = interpolatedPos[0];
        }
        
        // 到达终点
        if (progress >= 1.0) {
          animState.isAnimating = false;
        }
      }
    });
    
    // 批量更新标记位置（使用 moveTo 实现平滑动画）
    updates.forEach(({ truckId, interpolatedPos }) => {
      const marker = vehicleMarkers.get(truckId);
      if (marker) {
        // 使用 moveTo 方法实现平滑移动，设置较短的 duration
        marker.moveTo(interpolatedPos, {
          duration: updateInterval, // 与更新间隔一致
          autoRotation: true, // 自动旋转车头方向
        });
      }
    });

    // 跟随模式：地图中心持续对准被点击的车辆（拖动地图自动退出）
    if (followVehicleId.value !== null) {
      const followMarker = vehicleMarkers.get(followVehicleId.value);
      if (followMarker) {
        map.setCenter(followMarker.getPosition(), false);
      } else {
        followVehicleId.value = null;
        followVehicleLabel.value = "";
      }
    }

    animationFrameId = requestAnimationFrame(animate);
  }
  
  animate();
}

// 前端插值计算（基于距离；有预计算表时用二分查找 O(log n)）
function interpolatePosition(routePoints, progress, totalDistance, animState) {
  if (!routePoints || routePoints.length < 2) return null;
  if (progress <= 0) return routePoints[0];
  if (progress >= 1) return routePoints[routePoints.length - 1];

  // 预计算路径：progress 按折线自身总长归一化，二分定位目标段
  if (animState && animState.cumDist && animState.cumDist.length === routePoints.length) {
    const target = animState.polylineTotal * progress;
    let lo = 1, hi = routePoints.length - 1;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      if (animState.cumDist[mid] < target) lo = mid + 1;
      else hi = mid;
    }
    const i = lo;
    const segStart = animState.cumDist[i - 1];
    const segLen = animState.cumDist[i] - segStart;
    const t = segLen > 0 ? (target - segStart) / segLen : 0;
    return [
      routePoints[i - 1][0] + (routePoints[i][0] - routePoints[i - 1][0]) * t,
      routePoints[i - 1][1] + (routePoints[i][1] - routePoints[i - 1][1]) * t,
    ];
  }

  // 兼容回退：无预计算表时线性扫描
  const targetDistance = totalDistance * progress;
  let accumulatedDistance = 0;

  for (let i = 1; i < routePoints.length; i++) {
    const p1 = routePoints[i - 1];
    const p2 = routePoints[i];

    // 计算两点间距离（简化版，实际应该用Haversine公式）
    const segmentDistance = calculateSegmentDistance(p1, p2);

    if (accumulatedDistance + segmentDistance >= targetDistance) {
      const segmentProgress = (targetDistance - accumulatedDistance) / segmentDistance;

      // 线性插值
      const lng = p1[0] + (p2[0] - p1[0]) * segmentProgress;
      const lat = p1[1] + (p2[1] - p1[1]) * segmentProgress;

      return [lng, lat];
    }

    accumulatedDistance += segmentDistance;
  }

  return routePoints[routePoints.length - 1];
}

// 计算两点间距离（公里）
function calculateSegmentDistance(p1, p2) {
  const R = 6371; // 地球半径（公里）
  const lat1 = p1[1] * Math.PI / 180;
  const lat2 = p2[1] * Math.PI / 180;
  const deltaLat = (p2[1] - p1[1]) * Math.PI / 180;
  const deltaLon = (p2[0] - p1[0]) * Math.PI / 180;

  const a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  return R * c;
}

/**
 * 构造动画状态（统一入口）：
 * 预计算路径折线的累计里程表 cumDist 与折线总长 polylineTotal，
 * 之后每帧插值用二分查找 O(log n)，替代原先的每帧 O(n) 全量扫描（高倍速卡顿的元凶）。
 * progress 归一化使用折线自身总长（而非高德道路距离），保证车走完整条路径才到终点。
 */
function buildAnimState(truckId, rawPoints, totalDistance, startTime, speed) {
  const routePoints = (rawPoints || [])
    .map(p => parseLocation({ location: p.pointLocation }))
    .filter(p => p);
  if (routePoints.length < 2) return null;

  const cumDist = new Array(routePoints.length);
  cumDist[0] = 0;
  for (let i = 1; i < routePoints.length; i++) {
    cumDist[i] = cumDist[i - 1] + calculateSegmentDistance(routePoints[i - 1], routePoints[i]);
  }
  const polylineTotal = cumDist[cumDist.length - 1];
  if (polylineTotal <= 0) return null;

  return {
    truckId,
    routePoints,
    cumDist,
    polylineTotal,
    totalDistance: totalDistance || polylineTotal,
    startTime,
    simulationSpeed: speed || 1,
    isAnimating: true
  };
}

// 停止动画循环
function stopAnimationLoop() {
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId);
    animationFrameId = null;
  }
}


// POI数据存储
const poiMarkers = new Map();
const showPois = ref(true); // 是否显示POI
const showSidebar = ref(true); // 是否显示侧边栏
const simulationRunning = ref(true); // 仿真是否正在运行

// 控制仿真状态
async function controlSimulation(action) {
  try {
    const url = `${API_CONFIG.baseUrl}/simulation/${action}`;
    const response = await fetch(url, { method: 'POST' });
    if (response.ok) {
      const data = await response.json();
      simulationRunning.value = data.running;
      console.log(`仿真${action === 'start' ? '已开始/恢复' : '已暂停'}`);
    }
  } catch (error) {
    console.error("控制仿真失败:", error);
  }
}

// 调整仿真速度
async function adjustSimulationSpeed(speed) {
  try {
    // 确保speed是数字类型
    const speedValue = parseInt(speed);
    if (isNaN(speedValue)) {
      console.error("无效的速度值:", speed);
      return;
    }
    
    // 更新本地显示的速度
    currentSpeed.value = speedValue;
    
    const url = `${API_CONFIG.baseUrl}/simulation/speed`;
    const response = await fetch(url, { 
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ speed: speedValue })
    });
    if (response.ok) {
      const data = await response.json();
      console.log(`仿真速度已调整为${speedValue}倍`);
    } else {
      console.error(`调整仿真速度失败，状态码: ${response.status}`);
    }
  } catch (error) {
    console.error("调整仿真速度失败:", error);
  }
}

// 生成随机任务
async function generateRandomTasks() {
  try {
    const count = prompt("请输入生成任务数量（1-100）:", "10");
    if (count === null) return; // 用户取消
    
    const num = parseInt(count);
    if (isNaN(num) || num < 1 || num > 100) {
      alert("请输入1-100之间的有效数字");
      return;
    }
    
    const response = await fetch(`${API_CONFIG.baseUrl}/generate/random?count=${num}`, {
      method: 'GET',
    });
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    const result = await response.text();
    alert(result);
    console.log("生成随机任务成功:", result);
  } catch (error) {
    console.error("生成随机任务失败:", error);
    alert("生成任务失败: " + error.message);
  }
}


// 获取并绘制POI
async function loadPois() {
  try {
    const response = await fetch(`${API_CONFIG.baseUrl}/point`);
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    const pois = await response.json();
    console.log(`获取到 ${pois.length} 个POI点`);
    
    // 绘制POI
    drawPois(pois);
  } catch (error) {
    console.error("加载POI数据失败:", error);
  }
}

// 生成Canvas标记图标
function createCanvasMarker(type, color) {
  const canvas = document.createElement('canvas');
  canvas.width = 32;
  canvas.height = 40;
  const ctx = canvas.getContext('2d');
  
  if (!ctx) return ''; // 异常保护

  // 1. 绘制水滴形状底座
  ctx.fillStyle = color;
  ctx.beginPath();
  // 绘制一个圆头
  ctx.arc(16, 16, 16, Math.PI, 0); 
  // 绘制下方的尖角
  // 贝塞尔曲线让形状更圆润
  ctx.bezierCurveTo(32, 26, 22, 32, 16, 40);
  ctx.bezierCurveTo(10, 32, 0, 26, 0, 16);
  ctx.fill();
  
  // 2. 绘制白色圆形背景
  ctx.fillStyle = 'white';
  ctx.beginPath();
  ctx.arc(16, 16, 10, 0, Math.PI * 2);
  ctx.fill();
  
  // 3. 绘制内部文字（类型首字）
  // 选取类型的第一个字符，如果是英文则取首字母
  const text = type ? type.charAt(0) : '点';
  
  ctx.fillStyle = color; // 文字颜色与底座一致
  ctx.font = 'bold 14px "Microsoft YaHei", sans-serif'; // 加粗字体
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  // 微调文字位置，使其视觉居中（y=16是圆心，但由于字体基线原因可能需要微调）
  ctx.fillText(text, 16, 17);
  
  // 返回 PNG 格式的 Data URL
  return canvas.toDataURL('image/png');
}

// POI类型颜色映射
const poiColors = {
  '仓库': '#607D8B',      // 蓝灰
  '商场': '#FF9800',      // 橙色
  '公司企业': '#2196F3',  // 蓝色
  '购物服务': '#E91E63',  // 粉色
  '科教文化服务': '#9C27B0', // 紫色
  '商务住宅': '#795548',  // 棕色
  '生活服务': '#4CAF50',  // 绿色
  '汽车服务': '#3F51B5',  // 靛蓝
  '医疗保健服务': '#F44336', // 红色
  'default': '#009688'    // 默认青色
};

// 绘制POI点
function drawPois(pois) {
  if (!map || !AMap) return;
  
  // 清除旧的POI标记
  clearPois();
  
  // 1. 准备样式数组
  // 获取所有出现的类型，加上默认类型
  const types = Object.keys(poiColors);
  const styles = types.map(type => ({
    url: createCanvasMarker(type, poiColors[type]),
    anchor: new AMap.Pixel(16, 40), // 锚点在底部中心 (32x40尺寸)
    size: new AMap.Size(32, 40)
  }));
  
  // 创建类型到样式索引的映射
  const typeStyleIndex = {};
  types.forEach((type, index) => {
    typeStyleIndex[type] = index;
  });
  const defaultStyleIndex = typeStyleIndex['default'];

  // 2. 准备数据
  const massMarksData = pois.map(poi => {
    const coords = poi.location.split(",");
    let lng, lat;
    if (coords.length === 2) {
       lat = parseFloat(coords[0]);
       lng = parseFloat(coords[1]);
       
       if (lat > 70 && lat < 140) {
           const temp = lat;
           lat = lng;
           lng = temp;
       }
    }
        
    // 确定样式索引
    let styleIndex = typeStyleIndex[poi.type];
    if (styleIndex === undefined) {
        styleIndex = defaultStyleIndex;
    }
    
    return {
      lnglat: [lng, lat],
      name: poi.name,
      type: poi.type,
      style: styleIndex // 使用对应的样式索引
    };
  }).filter(p => !isNaN(p.lnglat[0]) && !isNaN(p.lnglat[1]));


  const massMarks = new AMap.MassMarks(massMarksData, {
    zIndex: 5,
    zooms: [3, 19],
    style: styles // 传入样式数组
  });

  massMarks.setMap(map);
  
  // 点击事件
  massMarks.on('click', function (e) {
    const marker = e.data;
    const color = poiColors[marker.type] || poiColors['default'];
    const infoWindow = new AMap.InfoWindow({
        content: `<div style="padding: 5px;">
            <b style="color: ${color}">${marker.name}</b><br>
            类型: ${marker.type}<br>
            坐标: ${marker.lnglat}
        </div>`,
        offset: new AMap.Pixel(0, -40) // 调整信息窗偏移
    });
    infoWindow.open(map, marker.lnglat);
  });
  
  // 保存引用以便清除
  poiMarkers.set('massMarks', massMarks);
  console.log(`已绘制 ${massMarksData.length} 个POI点`);
}

// 清除POI
function clearPois() {
  if (poiMarkers.has('massMarks')) {
    poiMarkers.get('massMarks').setMap(null);
    poiMarkers.delete('massMarks');
  }
}

// 切换POI显示
function togglePois() {
    showPois.value = !showPois.value;
    if (showPois.value) {
        loadPois();
    } else {
        clearPois();
    }
}

// 路线数据存储
const routes = ref([]);
const routePolylines = new Map(); // 存储路线的折线对象
const routeMarkers = new Map(); // 存储路线的起点终点标记
const routeCount = ref(0); // 路线数量计数器
const showRoutes = ref(true); // 路线显示开关

// 地图类型切换状态
const currentMapType = ref("standard"); // 'standard' 或 'satellite'

// 可视化页面状态
const showVisualization = ref(false);
const showGoodsVisualization = ref(false);
const showCostVisualization = ref(false);
const showEvaluation = ref(false);
const showSandbox = ref(false);

// 地图放大
function zoomIn() {
  if (map) {
    map.zoomIn();
  }
}

// 地图缩小
function zoomOut() {
  if (map) {
    map.zoomOut();
  }
}

// 定位到当前位置
function locateMe() {
  if (map && AMap) {
    map.plugin("AMap.Geolocation", function () {
      const geolocation = new AMap.Geolocation({
        enableHighAccuracy: true, // 高精度定位
        timeout: 10000, // 超时时间
        buttonPosition: "RB", // 定位按钮位置
        buttonOffset: new AMap.Pixel(10, 20), // 定位按钮偏移
      });

      map.addControl(geolocation);
      geolocation.getCurrentPosition();

      AMap.event.addListener(geolocation, "complete", function (data) {
        const { position } = data;
        map.setCenter([position.lng, position.lat]);
        map.setZoom(15);
      });

      AMap.event.addListener(geolocation, "error", function () {
        alert("定位失败，请检查定位权限");
      });
    });
  }
}

// 刷新车辆数据
function refreshData() {
  if (ws && connectionStatus.value === "connected") {
    // 关闭并重新连接WebSocket以获取最新数据
    initWebSocket();
    console.log("车辆数据已刷新");
  } else {
    alert("WebSocket未连接，无法刷新数据");
  }
}

// 清除所有标记
function clearMarkers() {
  if (map) {
    vehicleMarkers.forEach((marker) => {
      map.remove(marker);
    });
    vehicleMarkers.clear();
    console.log("所有车辆标记已清除");
  }
}

// 切换地图类型
function toggleMapType() {
  if (map && AMap) {
    if (currentMapType.value === "standard") {
      map.setMapStyle("amap://styles/satellite");
      currentMapType.value = "satellite";
    } else {
      map.setMapStyle("amap://styles/normal");
      currentMapType.value = "standard";
    }
    console.log("地图类型已切换为:", currentMapType.value);
  }
}

// 显示所有车辆
function fitBounds() {
  if (map && vehicleMarkers.size > 0) {
    const markers = Array.from(vehicleMarkers.values());
    map.setFitView(markers, false, [50, 50, 50, 50]); // 设置边距，避免标记显示在屏幕边缘
    console.log("地图已调整以显示所有车辆");
  } else if (vehicleMarkers.size === 0) {
    alert("当前没有车辆标记");
  }
}

// 获取路线数据
async function fetchRouteData(taskId) {
  try {
    const response = await fetch(`${API_CONFIG.baseUrl}/route/${taskId}`);
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    const routeData = await response.json();
    console.log(`获取到任务${taskId}的路线数据:`, routeData);
    return routeData;
  } catch (error) {
    console.error(`获取任务${taskId}路线数据失败:`, error);
    return null;
  }
}

// 获取路线颜色
function getRouteColor(taskId) {
  const colors = [
    "#FF6B6B", // 红色
    "#4ECDC4", // 青色
    "#45B7D1", // 蓝色
    "#96CEB4", // 绿色
    "#FECA57", // 黄色
    "#FF9FF3", // 粉色
    "#54A0FF", // 深蓝色
    "#48DBFB", // 天蓝色
    "#A29BFE", // 紫色
    "#FD79A8", // 玫红色
  ];
  return colors[(taskId - 2) % colors.length]; // 任务2-11对应索引0-9
}

// 绘制路线
function drawRoute(routeData, taskId) {
  if (!map || !AMap || !routeData || routeData.length === 0) return;

  // 清除之前的路线
  if (routePolylines.has(taskId)) {
    map.remove(routePolylines.get(taskId));
    routePolylines.delete(taskId);
  }
  if (routePolylines.has(`${taskId}_pickup`)) {
    map.remove(routePolylines.get(`${taskId}_pickup`));
    routePolylines.delete(`${taskId}_pickup`);
  }

  // 分离取货路线（负数sequence）和运输路线（正数sequence）
  const pickupPoints = routeData
    .filter(p => p.sequence < 0)
    .sort((a, b) => b.sequence - a.sequence)
    .map((point) => {
      const parts = point.pointLocation.split(",").map((coord) => parseFloat(coord));
      if (parts.length >= 2) {
          return [parts[0], parts[1]];
      }
      return null;
    })
    .filter(p => p !== null);

  const deliveryPoints = routeData
    .filter(p => p.sequence >= 0)
    .sort((a, b) => a.sequence - b.sequence)
    .map((point) => {
      const parts = point.pointLocation.split(",").map((coord) => parseFloat(coord));
      if (parts.length >= 2) {
          return [parts[0], parts[1]];
      }
      return null;
    })
    .filter(p => p !== null);

  // 获取路线颜色
  const routeColor = getRouteColor(taskId);

  // 绘制运输路线（主路线）
  if (deliveryPoints.length >= 2) {
    const deliveryPolyline = new AMap.Polyline({
      path: deliveryPoints,
      strokeColor: routeColor,
      strokeWeight: 4,
      strokeOpacity: 0.7,
      strokeStyle: "solid",
      lineJoin: "round",
      lineCap: "round",
      zIndex: 50,
      showDir: true,
    });
    deliveryPolyline.setMap(map);
    routePolylines.set(taskId, deliveryPolyline);

    // 添加起点和终点标记（运输路线）
    const startMarker = new AMap.Marker({
      position: deliveryPoints[0],
      map: map,
      icon: new AMap.Icon({
        size: new AMap.Size(25, 35),
        image: "https://webapi.amap.com/theme/v1.3/markers/n/start.png",
        imageSize: new AMap.Size(25, 35),
        imageOffset: new AMap.Pixel(0, 0),
      }),
      offset: new AMap.Pixel(-12, -35),
      title: `任务${taskId}起点`,
      zIndex: 100,
    });

    const endMarker = new AMap.Marker({
      position: deliveryPoints[deliveryPoints.length - 1],
      map: map,
      icon: new AMap.Icon({
        size: new AMap.Size(25, 35),
        image: "https://webapi.amap.com/theme/v1.3/markers/n/end.png",
        imageSize: new AMap.Size(25, 35),
        imageOffset: new AMap.Pixel(0, 0),
      }),
      offset: new AMap.Pixel(-12, -35),
      title: `任务${taskId}终点`,
      zIndex: 100,
    });

    routeMarkers.set(`${taskId}_start`, startMarker);
    routeMarkers.set(`${taskId}_end`, endMarker);
  }

  // 绘制取货路线（辅助路线，虚线）
  if (pickupPoints.length >= 2) {
    const pickupPolyline = new AMap.Polyline({
      path: pickupPoints,
      strokeColor: routeColor,
      strokeWeight: 2,
      strokeOpacity: 0.3,
      strokeStyle: "dashed",
      lineJoin: "round",
      lineCap: "round",
      zIndex: 45,
      showDir: true,
    });
    pickupPolyline.setMap(map);
    routePolylines.set(`${taskId}_pickup`, pickupPolyline);
  }

  // 更新路线计数
  routeCount.value = Math.floor(routePolylines.size / 1.5);

  console.log(
    `任务${taskId}路线绘制完成，运输路线${deliveryPoints.length}个点，取货路线${pickupPoints.length}个点`
  );
}

// 清除指定任务的路线
function clearRoute(taskId) {
  // 清除运输路线折线
  if (routePolylines.has(taskId)) {
    map.remove(routePolylines.get(taskId));
    routePolylines.delete(taskId);
  }

  // 清除取货路线折线
  if (routePolylines.has(`${taskId}_pickup`)) {
    map.remove(routePolylines.get(`${taskId}_pickup`));
    routePolylines.delete(`${taskId}_pickup`);
  }

  // 清除起点标记
  if (routeMarkers.has(`${taskId}_start`)) {
    map.remove(routeMarkers.get(`${taskId}_start`));
    routeMarkers.delete(`${taskId}_start`);
  }

  // 清除终点标记
  if (routeMarkers.has(`${taskId}_end`)) {
    map.remove(routeMarkers.get(`${taskId}_end`));
    routeMarkers.delete(`${taskId}_end`);
  }

  // 更新路线计数
  routeCount.value = Math.floor(routePolylines.size / 1.5);

  console.log(`已清除任务${taskId}的路线及标记`);
}

// 清除所有路线
function clearAllRoutes() {
  // 清除所有路线折线（运输路线和取货路线）
  routePolylines.forEach((polyline, key) => {
    map.remove(polyline);
  });
  routePolylines.clear();

  // 清除所有起点终点标记
  routeMarkers.forEach((marker) => {
    map.remove(marker);
  });
  routeMarkers.clear();

  // 重置路线计数
  routeCount.value = 0;

  console.log("已清除所有路线及标记");
}

// 切换路线显示/隐藏
function toggleRoutes() {
  showRoutes.value = !showRoutes.value;
  
  routePolylines.forEach((polyline) => {
    if (showRoutes.value) {
      polyline.setMap(map);
    } else {
      polyline.setMap(null);
    }
  });
  
  routeMarkers.forEach((marker) => {
    if (showRoutes.value) {
      marker.setMap(map);
    } else {
      marker.setMap(null);
    }
  });
  
  console.log(`路线${showRoutes.value ? '显示' : '隐藏'}状态: ${showRoutes.value}`);
}

// 更新车辆标记
function updateVehicleMarkers() {
  if (!map || !AMap) return;

  // 清除不再存在的车辆标记
  const currentVehicleIds = new Set(
    vehicles.value.map((v) => v.id || v.truckId)
  );
  const markersToRemove = [];

  vehicleMarkers.forEach((marker, id) => {
    if (!currentVehicleIds.has(id)) {
      markersToRemove.push(id);
      map.remove(marker);
    }
  });

  markersToRemove.forEach((id) => vehicleMarkers.delete(id));

  // 更新或添加车辆标记
  vehicles.value.forEach((vehicle) => {
    updateSingleVehicleMarker(vehicle);
  });
}

// 更新单个车辆标记（完整版）
function updateSingleVehicleMarker(vehicle) {
  const id = vehicle.id || vehicle.truckId;
  if (!id) return;

  // 解析坐标
  let lng, lat;
  const parsed = parseLocation(vehicle);
  if (parsed) {
    lng = parsed[0];
    lat = parsed[1];
  } else {
    // 兼容其他字段
    if (vehicle.x !== undefined && vehicle.y !== undefined) { lng = parseFloat(vehicle.x); lat = parseFloat(vehicle.y); }
    else if (vehicle.longitude !== undefined && vehicle.latitude !== undefined) { lng = parseFloat(vehicle.longitude); lat = parseFloat(vehicle.latitude); }
    else {
        // 默认坐标
        const angle = (id * 36 * Math.PI) / 180;
        lng = 116.397428 + Math.cos(angle) * 0.05;
        lat = 39.90923 + Math.sin(angle) * 0.05;
    }
  }
  
  // 确保坐标有效
  if (isNaN(lng) || isNaN(lat)) {
      lng = 116.397428; lat = 39.90923;
  }
  
  const position = [lng, lat];

  // 确定图标和状态
  let carIcon = "https://webapi.amap.com/images/car.png";
  let isCar = true;

  if (vehicle.status === "装货") {
    carIcon = "https://a.amap.com/jsapi_demos/static/demo-center/icons/poi-marker-red.png";
    isCar = false;
  } else if (vehicle.status === "卸货") {
    carIcon = "https://a.amap.com/jsapi_demos/static/demo-center/icons/poi-marker-default.png";
    isCar = false;
  } else if (vehicle.status === "故障") {
    carIcon = "https://webapi.amap.com/theme/v1.3/markers/n/mark_r.png";
    isCar = false;
  }
  // "拥堵"、"运输中"、"空闲" 使用默认车辆图标

  // 准备图标内容
  let content = "";
  let offset;

  if (isCar) {
    // 车辆图标：旋转90度使其朝右（适配autoRotation）
    content = `<img src="${carIcon}" style="width: 52px; height: 26px; transform: rotate(90deg); display: block;">`;
    offset = new AMap.Pixel(-26, -13);
  } else {
    // 普通图标：不旋转，底部中心对齐
    content = `<img src="${carIcon}" style="width: 25px; height: 34px; display: block;">`;
    offset = new AMap.Pixel(-12, -34);
  }

  // 更新或创建标记
  if (vehicleMarkers.has(id)) {
    const marker = vehicleMarkers.get(id);

    // 更新样式和标题
    marker.setContent(content);
    marker.setOffset(offset);
    marker.setTitle(`${vehicle.plateNumber || `车辆${id}`} - 状态: ${vehicle.status || "未知"}`);

    // 有本地插值驱动的车辆：位置交由动画循环管理，后端位置不再驱动，避免双源抖动
    if (isCar && vehicleAnimations.has(id)) return;

    // 更新位置：使用 moveTo 方法触发 autoRotation（车头方向跟随移动方向）
    const currentPos = marker.getPosition();
    if (Math.abs(currentPos.lng - lng) > 0.000001 || Math.abs(currentPos.lat - lat) > 0.000001) {
      if (isCar) {
        // 车辆图标：使用 moveTo 触发自动旋转，duration 设为 100ms 让旋转生效
        marker.moveTo(position, {
          duration: 100,
          autoRotation: true,
        });
      } else {
        // 非车辆图标：直接设置位置
        marker.setPosition(position);
      }
    }
    
  } else {
    // 创建新标记
    const marker = new AMap.Marker({
      map: map,
      position: position,
      title: `${vehicle.plateNumber || `车辆${id}`} - 状态: ${vehicle.status || "未知"}`,
      content: content,
      offset: offset,
      autoRotation: isCar,
      label: {
        content: vehicle.plateNumber || `车辆${id}`,
        direction: "top",
        offset: new AMap.Pixel(0, -10),
      },
      animation: "AMAP_ANIMATION_DROP",
    });
    
    vehicleMarkers.set(id, marker);
    
    // 点击事件
    marker.on("click", () => {
      const infoWindow = new AMap.InfoWindow({
        content: `<div style="padding: 10px; color: #1f2937; font-size: 13px; line-height: 1.8; min-width: 180px;">
          <p style="margin:0"><strong>车辆ID:</strong> ${id}</p>
          <p style="margin:0"><strong>车牌号:</strong> ${vehicle.plateNumber || "未知"}</p>
          <p style="margin:0"><strong>类型:</strong> ${vehicle.type || "普通货车"}</p>
          <p style="margin:0"><strong>位置:</strong> ${lng.toFixed(5)}, ${lat.toFixed(5)} (经度,纬度)</p>
          <p style="margin:0"><strong>状态:</strong> ${vehicle.status || "正常"}</p>
        </div>`,
        offset: [0, -30],
      });
      infoWindow.open(map, marker.getPosition());
    });
  }
}

// 更新单个车辆标记（优化版：只更新位置，不更新图标）
function updateSingleVehicleMarkerOptimized(vehicle) {
  const id = vehicle.id || vehicle.truckId;
  if (!id || !vehicleMarkers.has(id)) return;

  // 快速解析坐标
  let lng, lat;
  const parsed = parseLocation(vehicle);
  if (parsed) {
    lng = parsed[0];
    lat = parsed[1];
  } else if (vehicle.x !== undefined && vehicle.y !== undefined) {
    lng = parseFloat(vehicle.x);
    lat = parseFloat(vehicle.y);
  } else if (vehicle.longitude !== undefined && vehicle.latitude !== undefined) {
    lng = parseFloat(vehicle.longitude);
    lat = parseFloat(vehicle.latitude);
  } else {
    return; // 无法获取坐标，跳过
  }
  
  // 确保坐标有效
  if (isNaN(lng) || isNaN(lat)) {
    return;
  }
  
  const position = [lng, lat];
  const marker = vehicleMarkers.get(id);
  
  // 只更新位置，不更新图标内容（减少DOM操作）
  const currentPos = marker.getPosition();
  if (Math.abs(currentPos.lng - lng) > 0.000001 || Math.abs(currentPos.lat - lat) > 0.000001) {
    // 使用 moveTo 实现平滑移动
    marker.moveTo(position, {
      duration: 100,
      autoRotation: true,
    });
  }
}

// 初始化地图
onMounted(() => {
  // 确保容器存在且有尺寸
  if (mapContainer.value) {
    // 设置一些基本样式确保容器有明确尺寸
    mapContainer.value.style.width = "100%";
    mapContainer.value.style.height = "100vh";

    // 设置安全密钥（高德地图需要）：2021-12 以后申请的 key 需在 .env.local 配置 VITE_AMAP_SECURITY_CODE
    window._AMapSecurityConfig = {
      securityJsCode: AMAP_CONFIG.securityCode || AMAP_CONFIG.key,
    };

    AMapLoader.load({
      key: AMAP_CONFIG.key,
      version: AMAP_CONFIG.version,
      plugins: ["AMap.ToolBar", "AMap.Scale", "AMap.HawkEye", "AMap.MoveAnimation"],
      AMapUI: {
        version: "1.1",
        plugins: [],
      },
      Loca: {
        version: "2.0",
      },
    })
      .then((_AMap) => {
        // 使用try-catch来捕获可能的初始化错误
        try {
          AMap = _AMap;
          // 创建地图实例
          map = new AMap.Map(mapContainer.value, {
            viewMode: "3D",
            zoom: 11,
            center: [116.397428, 39.90923], // 北京市中心坐标
            resizeEnable: true,
          });

          console.log("高德地图初始化成功");

          // 添加一些地图控件
          map.addControl(new AMap.ToolBar());
          map.addControl(new AMap.Scale());
          map.addControl(new AMap.HawkEye());

          // 地图操作时暂停动画，提升性能
          let mapInteracting = false;
          
          // 更新全局的 isMapInteracting 函数
          isMapInteracting = () => mapInteracting;
          
          map.on('dragstart', () => {
            mapInteracting = true;
            followVehicleId.value = null;
            followVehicleLabel.value = "";
            console.log('地图拖拽开始，暂停动画');
          });
          
          map.on('dragend', () => {
            mapInteracting = false;
            console.log('地图拖拽结束，恢复动画');
          });
          
          map.on('zoomstart', () => {
            mapInteracting = true;
            console.log('地图缩放开始，暂停动画');
          });
          
          map.on('zoomend', () => {
            mapInteracting = false;
            console.log('地图缩放结束，恢复动画');
          });

          // 地图初始化成功后，建立WebSocket连接
          initWebSocket();
          
          // 启动动画循环（60fps平滑渲染）
          startAnimationLoop();

          // 自动加载所有路线数据
          setTimeout(() => {
            loadAllRoutes();
            loadPois(); // 自动加载POI
          }, 1000); // 延迟1秒确保地图完全初始化
        } catch (err) {
          console.error("地图实例创建失败:", err);
        }
      })
      .catch((e) => {
        console.error("高德地图API加载失败:", e);
      });
  } else {
    console.error("地图容器不存在");
  }
});

// 监听窗口大小变化，调整地图大小
watchEffect(() => {
  window.addEventListener("resize", () => {
    if (map) {
      map.resize();
    }
  });
});

// 加载所有路线数据（只加载当前车辆正在执行的任务路线）
async function loadAllRoutes() {
  console.log("开始加载当前车辆正在执行的任务路线...");

  // 获取所有车辆的任务ID（去重）
  const activeTaskIds = new Set();
  vehicles.value.forEach((vehicle) => {
    if (vehicle.taskId && vehicle.status !== '空闲') {
      activeTaskIds.add(vehicle.taskId);
    }
  });

  console.log(`当前有 ${activeTaskIds.size} 个任务正在执行:`, Array.from(activeTaskIds));

  const routePromises = [];
  activeTaskIds.forEach((taskId) => {
    routePromises.push(
      fetchRouteData(taskId).then((routeData) => {
        if (routeData && routeData.length > 0) {
          drawRoute(routeData, taskId);
          return { taskId, success: true, pointCount: routeData.length };
        } else {
          return { taskId, success: false, pointCount: 0 };
        }
      })
    );
  });

  try {
    const results = await Promise.all(routePromises);
    const successCount = results.filter((r) => r.success).length;
    const totalPoints = results.reduce((sum, r) => sum + r.pointCount, 0);

    console.log(
      `路线加载完成！成功加载 ${successCount}/${activeTaskIds.size} 条路线，总计 ${totalPoints} 个路径点`
    );
    console.log("详细结果:", results);
  } catch (error) {
    console.error("批量加载路线时发生错误:", error);
  }
}

// 测试加载路线数据（可以在控制台调用）
window.loadTestRoute = async function (taskId = 2) {
  console.log(`开始加载任务${taskId}的路线数据...`);
  const routeData = await fetchRouteData(taskId);
  if (routeData) {
    drawRoute(routeData, taskId);
  }
};

// 清除所有路线（可以在控制台调用）
window.clearAllRoutes = clearAllRoutes;

// 组件卸载时清理资源
onUnmounted(() => {
  // 停止动画循环
  stopAnimationLoop();
  
  if (ws) {
    ws.close();
    ws = null;
  }

  if (map && vehicleMarkers.size > 0) {
    vehicleMarkers.forEach((marker) => {
      map.remove(marker);
    });
    vehicleMarkers.clear();
  }

  // 清除所有路线
  clearAllRoutes();
    
  // 清除POI
  clearPois();
  
  // 清除动画状态
  vehicleAnimations.clear();
});

// 格式化位置显示
function formatLocation(loc) {
    if (!loc) return '-';
    if (typeof loc === 'string') {
        const parts = loc.split(',');
        if (parts.length >= 2) {
            return `${parseFloat(parts[1]).toFixed(4)}, ${parseFloat(parts[0]).toFixed(4)}`;
        }
        return loc;
    }
    return '-';
}

// 获取状态样式类
function getStatusClass(status) {
    if (status === '运输中') return 'status-transit';
    if (status === '取货中') return 'status-pickup';
    if (status === '装货') return 'status-loading';
    if (status === '卸货') return 'status-unloading';
    if (status === '休息中') return 'status-rest';
    if (status === '保养') return 'status-maint';
    if (status === '空闲') return 'status-idle';
    if (status === '故障' || status === '拥堵') return 'status-warning';
    return '';
}

// ===== 车辆跟随模式：点击车辆卡片后地图持续跟随该车辆，拖动地图或点提示条退出 =====
const followVehicleId = ref(null);
const followVehicleLabel = ref("");

// 聚焦车辆
function focusVehicle(vehicle) {
    const id = vehicle.id || vehicle.truckId;
    if (vehicleMarkers.has(id)) {
        const marker = vehicleMarkers.get(id);
        const pos = marker.getPosition();
        followVehicleId.value = id;
        followVehicleLabel.value = vehicle.plateNumber || `车辆${id}`;
        // 跟随视野控制在 15 级：高倍速下车辆每 100ms 移动可达数公里，
        // 视野太大（低 zoom）看不到车，太小（高 zoom）车会频繁冲出屏幕
        const targetZoom = Math.min(map.getZoom() || 15, 15);
        map.setZoomAndCenter(Math.max(targetZoom, 12), pos);
        // 不自动弹信息窗：卡片信息已足够，弹窗会停留原地挡住跟随视野；
        // 需要详情可直接点击地图上的车辆图标
    }
}

// 总览车队：一键缩放至能看到全部车辆
function fitAllVehicles() {
    if (!map || vehicleMarkers.size === 0) return;
    map.setFitView(Array.from(vehicleMarkers.values()), false, [90, 90, 90, 90], 12);
}

// ===== 左侧面板实时核心指标（每5秒轮询，失败时显示 --，不影响其他功能） =====
const sideMetrics = ref(null);
let sideMetricsTimer = null;
const sidePct = (v) => (typeof v === "number" ? Math.round(v * 10000) / 100 + "%" : "--");
const sideNum = (v) => (typeof v === "number" ? (Math.round(v * 100) / 100).toLocaleString() : "--");
async function fetchSideMetrics() {
  try {
    const res = await fetch("http://localhost:8080/metrics/overview");
    if (res.ok) sideMetrics.value = await res.json();
  } catch (e) { /* 静默失败 */ }
}
onMounted(() => {
  fetchSideMetrics();
  sideMetricsTimer = setInterval(fetchSideMetrics, 5000);
});
onUnmounted(() => {
  if (sideMetricsTimer) clearInterval(sideMetricsTimer);
});

// ===== 车辆状态统计（面板与侧边栏顶部的彩色计数） =====
const STATUS_CLS = {
  "运输中": "transit",
  "取货中": "pickup",
  "装货": "loading",
  "卸货": "unloading",
  "休息中": "rest",
  "保养": "maint",
  "空闲": "idle",
  "拥堵": "jam",
  "故障": "jam",
};
const STATUS_ORDER = ["运输中", "取货中", "装货", "卸货", "休息中", "保养", "空闲", "拥堵", "故障"];
const statusSummary = computed(() => {
  const counts = {};
  for (const v of vehicles.value) {
    const s = v.status || "未知";
    counts[s] = (counts[s] || 0) + 1;
  }
  return Object.entries(counts)
    .sort((a, b) => {
      const ia = STATUS_ORDER.indexOf(a[0]), ib = STATUS_ORDER.indexOf(b[0]);
      return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
    })
    .map(([name, count]) => ({ name, count, cls: STATUS_CLS[name] || "other" }));
});

// ===== 顶部栏：实时时钟与全屏切换 =====
const clock = ref("");
let clockTimer = null;
const isFullscreen = ref(false);
function updateClock() {
  const d = new Date();
  clock.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}:${String(d.getSeconds()).padStart(2, "0")}`;
}
function onFsChange() {
  isFullscreen.value = !!document.fullscreenElement;
}
function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen?.();
  } else {
    document.exitFullscreen?.();
  }
}
onMounted(() => {
  updateClock();
  clockTimer = setInterval(updateClock, 1000);
  document.addEventListener("fullscreenchange", onFsChange);
});
onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer);
  document.removeEventListener("fullscreenchange", onFsChange);
});
</script>

<template>
  <div id="app-root">
    <div class="map-container">
    <div id="map-container" ref="mapContainer"></div>

    <!-- 顶部系统栏 -->
    <header class="top-header">
      <div class="th-brand">
        <span class="th-logo" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="#ffffff">
            <path d="M20 8h-3V4H3c-1.1 0-2 .9-2 2v11h2c0 1.66 1.34 3 3 3s3-1.34 3-3h6c0 1.66 1.34 3 3 3s3-1.34 3-3h2v-5l-3-4zM6 18.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm13.5-9l1.96 2.5H17V9.5h2.5zm-1.5 9c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z"/>
          </svg>
        </span>
        <div class="th-title">
          <b>物流运输调度仿真系统</b>
          <i>运输车辆调度优化与仿真 · 综合项目III</i>
        </div>
      </div>
      <div class="th-center">
        <span class="th-chip" :class="simulationRunning ? 'run' : 'stop'">
          <i></i>{{ simulationRunning ? "仿真运行中" : "仿真已暂停" }}
        </span>
        <span class="th-chip">倍速 {{ currentSpeed }}×</span>
        <span class="th-chip">🚚 车辆 {{ vehicles.length }}</span>
        <span class="th-chip" v-if="sideMetrics">📋 完成 {{ sideMetrics?.T?.completedTotal ?? 0 }} · 待分配 {{ sideMetrics?.M?.pendingCount ?? 0 }}</span>
      </div>
      <div class="th-right">
        <span class="th-clock">{{ clock }}</span>
        <button class="th-fs" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '进入全屏'">
          {{ isFullscreen ? '🗗 退出全屏' : '⛶ 全屏' }}
        </button>
      </div>
    </header>

    <div class="ctrl-panel">
      <!-- 面板头 -->
      <div class="panel-head">
        <div class="panel-title">
          <span class="logo-dot"></span>
          <div>
            <div class="title-main">物流运输调度仿真</div>
            <div class="title-sub">综合项目III · 实时监控台</div>
          </div>
        </div>
        <div class="ws-chip" :class="connectionStatus">
          <span class="ws-dot"></span>{{ connectionStatus === "connected" ? "已连接" : connectionStatus === "error" ? "错误" : "断开" }}
        </div>
      </div>

      <!-- 实时核心指标（直显于地图侧边，点击打开八维看板） -->
      <div class="metric-tiles" @click="showEvaluation = true" title="点击打开综合评价看板">
        <div class="m-tile">
          <div class="m-val">{{ sidePct(sideMetrics?.U?.emptyRate) }}</div>
          <div class="m-label">空驶率</div>
        </div>
        <div class="m-tile">
          <div class="m-val">{{ sidePct(sideMetrics?.U?.loadRateMean) }}</div>
          <div class="m-label">装载率</div>
        </div>
        <div class="m-tile">
          <div class="m-val">{{ sideNum(sideMetrics?.C?.unitCost) }}</div>
          <div class="m-label">单位成本</div>
        </div>
        <div class="m-tile" :class="{ 'm-warn': sideMetrics?.F?.warning }">
          <div class="m-val">{{ sideNum(sideMetrics?.F?.gini) }}</div>
          <div class="m-label">基尼系数</div>
        </div>
        <div class="m-tile">
          <div class="m-val">{{ sidePct(sideMetrics?.M?.f1) }}</div>
          <div class="m-label">匹配F1</div>
        </div>
        <div class="m-tile">
          <div class="m-val">{{ sidePct(sideMetrics?.R?.availability) }}</div>
          <div class="m-label">可用性</div>
        </div>
      </div>

      <!-- 车队概况 -->
      <div class="fleet-line">
        <span>🚚 车辆 <b>{{ vehicles.length }}</b></span>
        <span>🛣️ 路线 <b>{{ routeCount }}</b></span>
        <span v-for="st in statusSummary" :key="st.name" class="fleet-st"><i :class="'dot-' + st.cls"></i>{{ st.name }} <b>{{ st.count }}</b></span>
      </div>

      <!-- 图层操作 -->
      <div class="layer-chips">
        <button class="chip" @click="loadAllRoutes" title="重新加载所有路线">↻ 重载</button>
        <button class="chip" :class="{ on: showPois }" @click="togglePois" title="显示/隐藏POI">📍 POI</button>
        <button class="chip" :class="{ on: showRoutes }" @click="toggleRoutes" title="显示/隐藏路线">🛣️ 路线</button>
        <button class="chip" :class="{ on: showSidebar }" @click="showSidebar = !showSidebar" title="显示/隐藏车辆列表">🚚 列表</button>
        <button class="chip" @click="fitAllVehicles" title="缩放至显示全部车辆">🎯 总览</button>
      </div>

      <!-- 仿真控制 -->
      <div class="sim-row">
        <button
          class="sim-toggle"
          :class="simulationRunning ? 'running' : 'paused'"
          @click="controlSimulation(simulationRunning ? 'pause' : 'start')"
          :title="simulationRunning ? '暂停仿真' : '开始/恢复仿真'"
        >
          {{ simulationRunning ? '⏸ 暂停仿真' : '▶ 开始仿真' }}
        </button>
        <div class="speed-pill">
          <span class="spd-label">倍速</span>
          <input type="range" min="1" max="100" v-model.number="currentSpeed" step="1" @input="adjustSimulationSpeed(currentSpeed)">
          <span class="spd-val">{{ currentSpeed }}×</span>
        </div>
        <button class="sim-gen" @click="generateRandomTasks">🎲 生成一批随机任务</button>
      </div>

      <!-- 分析入口 -->
      <div class="analysis-grid">
        <button class="ana-btn a-blue" @click="showVisualization = true"><span>📊</span>车辆分析</button>
        <button class="ana-btn a-green" @click="showGoodsVisualization = true"><span>📦</span>货物分析</button>
        <button class="ana-btn a-amber" @click="showCostVisualization = true"><span>💰</span>成本分析</button>
        <button class="ana-btn a-cyan" @click="showEvaluation = true"><span>📈</span>综合评价</button>
        <button class="ana-btn a-indigo" @click="showSandbox = true"><span>🧪</span>数据实验沙箱 · 对照实验与复现</button>
      </div>
    </div>

    <!-- 跟随模式提示条 -->
    <div v-if="followVehicleLabel" class="follow-chip" @click="followVehicleId = null; followVehicleLabel = ''">
      🎯 正在跟随 {{ followVehicleLabel }} · 点击退出跟随
    </div>

    <!-- 车辆信息侧边栏 -->
    <div class="vehicle-sidebar" :class="{ 'sidebar-hidden': !showSidebar }">
      <div class="sidebar-header">
        <div>
          <h3>🚚 车辆监控</h3>
          <div class="sidebar-sub">共 {{ vehicles.length }} 辆 · 点击卡片在地图定位</div>
        </div>
        <span class="close-btn" @click="showSidebar = false">×</span>
      </div>
      <div class="status-counts">
        <span v-for="st in statusSummary" :key="st.name" class="sc-chip">
          <i :class="'dot-' + st.cls"></i>{{ st.name }} {{ st.count }}
        </span>
      </div>
      <div class="sidebar-content">
        <div v-if="vehicles.length === 0" class="no-data">
          暂无在线车辆
        </div>
        <div
          v-else
          v-for="vehicle in vehicles"
          :key="vehicle.id || vehicle.truckId"
          class="vehicle-card"
          :class="getStatusClass(vehicle.status)"
          @click="focusVehicle(vehicle)"
        >
          <div class="vehicle-header">
            <span class="plate-number">{{ vehicle.plateNumber || `车辆${vehicle.id || vehicle.truckId}` }}</span>
            <span class="status-badge" :class="getStatusClass(vehicle.status)">{{ vehicle.status || '未知' }}</span>
          </div>
          <div class="vehicle-detail">
            <div class="detail-item">
              <span class="label">类型</span>
              <span class="value">{{ vehicle.type || '普通货车' }}</span>
            </div>
            <div class="detail-item">
              <span class="label">任务</span>
              <span class="value">{{ vehicle.taskId ? `任务#${vehicle.taskId}` : '无任务' }}</span>
            </div>
             <div class="detail-item" v-if="vehicle.location">
              <span class="label">位置</span>
              <span class="value">{{ formatLocation(vehicle.location) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  
  <!-- 车辆分析可视化模态窗口 -->
  <div v-if="showVisualization" class="visualization-modal">
    <div class="modal-content">
      <div class="modal-header">
        <h2>车辆分析可视化</h2>
        <button class="close-btn" @click="showVisualization = false">×</button>
      </div>
      <div class="modal-body">
        <VehicleVisualization />
      </div>
    </div>
  </div>
  
  <!-- 货物分析可视化模态窗口 -->
  <div v-if="showGoodsVisualization" class="visualization-modal">
    <div class="modal-content">
      <div class="modal-header">
        <h2>货物分析可视化</h2>
        <button class="close-btn" @click="showGoodsVisualization = false">×</button>
      </div>
      <div class="modal-body">
        <GoodsVisualization />
      </div>
    </div>
  </div>
  
  <!-- 成本分析可视化模态窗口 -->
  <div v-if="showCostVisualization" class="visualization-modal">
    <div class="modal-content">
      <div class="modal-header">
        <h2>成本分析可视化</h2>
        <button class="close-btn" @click="showCostVisualization = false">×</button>
      </div>
      <div class="modal-body">
        <CostVisualization />
      </div>
    </div>
  </div>

  <!-- 综合评价指标看板（全屏覆盖） -->
  <EvaluationDashboard v-if="showEvaluation" @close="showEvaluation = false" />

  <!-- 数据实验沙箱（全屏覆盖） -->
  <SandboxPanel v-if="showSandbox" @close="showSandbox = false" />
  </div>
</template>

<style scoped>
.map-container {
  width: 100%;
  height: 100vh;
  position: relative;
  overflow: hidden;
}

#map-container {
  width: 100%;
  height: 100%;
  min-width: 300px;
  min-height: 300px;
}

.status-panel {
  position: absolute;
  top: 20px;
  left: 20px;
  background: rgba(255, 255, 255, 0.9);
  padding: 10px 15px;
  border-radius: 5px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  z-index: 1000;
  font-size: 14px;
  line-height: 1.5;
}

.connection-status {
  font-weight: bold;
  margin-bottom: 5px;
}

.connection-status.connected {
  color: #10b981;
}

.connection-status.error {
  color: #ef4444;
}

.connection-status.disconnected {
  color: #f59e0b;
}

.vehicle-count, .route-count {
  margin: 5px 0;
  font-weight: bold;
}

.load-routes-btn {
  margin-top: 10px;
  padding: 5px 10px;
  background-color: #3b82f6;
  color: white;
  border: none;
  border-radius: 3px;
  cursor: pointer;
  font-size: 14px;
}

.load-routes-btn:hover {
  background-color: #2563eb;
}

.control-group {
  margin-top: 10px;
  display: flex;
  gap: 10px;
}

.control-btn {
  padding: 5px 15px;
  border: none;
  border-radius: 3px;
  cursor: pointer;
  font-size: 14px;
  font-weight: bold;
}

.control-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-start {
  background-color: #10b981;
  color: white;
}

.btn-start:hover {
  background-color: #059669;
}

.btn-pause {
  background-color: #f59e0b;
  color: white;
}

.btn-pause:hover {
  background-color: #d97706;
}

.speed-control {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.speed-control label {
  font-size: 14px;
  font-weight: bold;
}

.speed-control input[type="range"] {
  flex: 1;
  min-width: 150px;
}

.speed-control span {
  font-size: 14px;
  font-weight: bold;
  color: #3b82f6;
}

.connection-status.disconnected {
  color: #f59e0b;
}

.connection-status.error {
  color: #ef4444;
}

.vehicle-count {
  color: #374151;
}

.route-count {
  color: #374151;
  margin-top: 5px;
}

.load-routes-btn {
  margin-top: 10px;
  padding: 6px 12px;
  background: #3366ff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: background-color 0.2s;
}

.load-routes-btn:hover {
  background: #2851e0;
}

.load-routes-btn:active {
  background: #1e40af;
}

/* 仿真控制按钮组 */
.control-group {
  margin-top: 15px;
  display: flex;
  gap: 10px;
  border-top: 1px solid #eee;
  padding-top: 10px;
}

.control-btn {
  flex: 1;
  padding: 6px 12px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: white;
  transition: all 0.2s;
}

.control-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-start {
  background-color: #3b82f6;
}

.btn-start:hover:not(:disabled) {
  background-color: #2563eb;
}

.btn-pause {
  background-color: #ef4444;
}

.btn-pause:hover:not(:disabled) {
  background-color: #dc2626;
}

/* 侧边栏样式 */
.vehicle-sidebar {
  position: absolute;
  top: 0;
  right: 0;
  width: 300px;
  height: 100%;
  background: white;
  box-shadow: -2px 0 10px rgba(0,0,0,0.1);
  z-index: 1001;
  transition: transform 0.3s ease;
  display: flex;
  flex-direction: column;
}

.sidebar-hidden {
  transform: translateX(100%);
}

.sidebar-header {
  padding: 15px;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #f8f9fa;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 16px;
  color: #333;
}

.close-btn {
  cursor: pointer;
  font-size: 20px;
  color: #999;
}

.sidebar-content {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

.no-data {
  text-align: center;
  color: #999;
  padding: 20px;
}

.vehicle-card {
  background: white;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}

.vehicle-card:hover {
  box-shadow: 0 4px 6px rgba(0,0,0,0.1);
  border-color: #3366ff;
}

.vehicle-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.plate-number {
  font-weight: bold;
  font-size: 15px;
  color: #333;
}

.status-badge {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  background: #eee;
  color: #666;
}

.status-transit { background: #e3f2fd; color: #1976d2; }
.status-loading { background: #ffebee; color: #d32f2f; }
.status-unloading { background: #e8f5e9; color: #388e3c; }
.status-idle { background: #f5f5f5; color: #757575; }
.status-warning { background: #fff3e0; color: #f57c00; }

.vehicle-detail {
  font-size: 12px;
  color: #666;
}

.detail-item {
  display: flex;
  margin-bottom: 4px;
}

.detail-item .label {
  width: 40px;
  color: #999;
}

.detail-item .value {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ================= UI 改版：深色玻璃控制台 + 浮动车辆列表 ================= */
.ctrl-panel {
  position: absolute;
  top: 74px;
  left: 18px;
  width: 322px;
  background: linear-gradient(165deg, rgba(15, 23, 42, 0.94), rgba(30, 41, 59, 0.9));
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 18px;
  padding: 16px;
  z-index: 1000;
  color: #e2e8f0;
  box-shadow: 0 16px 44px rgba(2, 6, 23, 0.45);
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 13px;
  line-height: 1.5;
}
.panel-head { display: flex; justify-content: space-between; align-items: flex-start; }
.panel-title { display: flex; gap: 10px; align-items: center; }
.logo-dot {
  width: 12px; height: 12px; border-radius: 4px; flex: none;
  background: linear-gradient(135deg, #22d3ee, #6366f1);
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.8);
}
.title-main { font-size: 15px; font-weight: 700; color: #f8fafc; letter-spacing: 0.5px; }
.title-sub { font-size: 10px; color: #94a3b8; margin-top: 2px; }
.ws-chip {
  display: inline-flex; align-items: center; gap: 5px; font-size: 11px;
  padding: 3px 9px; border-radius: 99px; background: rgba(148, 163, 184, 0.12); color: #cbd5e1; white-space: nowrap;
}
.ws-dot { width: 7px; height: 7px; border-radius: 50%; background: #64748b; }
.ws-chip.connected { color: #6ee7b7; }
.ws-chip.connected .ws-dot { background: #34d399; box-shadow: 0 0 8px rgba(52, 211, 153, 0.9); }
.ws-chip.disconnected { color: #fcd34d; }
.ws-chip.disconnected .ws-dot { background: #fbbf24; }
.ws-chip.error { color: #fca5a5; }
.ws-chip.error .ws-dot { background: #f87171; }

.metric-tiles { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; cursor: pointer; }
.m-tile {
  background: rgba(148, 163, 184, 0.08);
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 12px;
  padding: 9px 4px 7px;
  text-align: center;
  transition: all 0.2s;
}
.metric-tiles:hover .m-tile { border-color: rgba(56, 189, 248, 0.45); background: rgba(56, 189, 248, 0.08); }
.m-val { font-size: 16px; font-weight: 700; color: #f1f5f9; font-variant-numeric: tabular-nums; }
.m-label { font-size: 10px; color: #94a3b8; margin-top: 2px; }
.m-warn .m-val { color: #fbbf24; }

.fleet-line { display: flex; flex-wrap: wrap; gap: 4px 10px; font-size: 10.5px; color: #cbd5e1; min-height: 33px; align-content: flex-start; }
.fleet-line b { color: #f8fafc; font-variant-numeric: tabular-nums; }
.fleet-st { display: inline-flex; align-items: center; gap: 4px; }
.fleet-line i, .sc-chip i { width: 7px; height: 7px; border-radius: 50%; display: inline-block; }
.dot-transit { background: #38bdf8; }
.dot-pickup { background: #a78bfa; }
.dot-loading { background: #fb923c; }
.dot-unloading { background: #34d399; }
.dot-idle { background: #64748b; }
.dot-jam { background: #f87171; }
.dot-other { background: #94a3b8; }

.layer-chips { display: grid; grid-template-columns: repeat(5, 1fr); gap: 6px; }
.chip {
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(148, 163, 184, 0.08);
  color: #cbd5e1;
  font-size: 10.5px;
  padding: 7px 2px;
  border-radius: 9px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.chip:hover { background: rgba(148, 163, 184, 0.2); }
.chip.on {
  background: rgba(56, 189, 248, 0.14);
  border-color: rgba(56, 189, 248, 0.5);
  color: #7dd3fc;
}

.sim-row { display: flex; flex-direction: column; gap: 8px; }
.sim-toggle {
  border: none; border-radius: 12px; padding: 11px; font-size: 14px; font-weight: 700; cursor: pointer;
  color: #052e21;
  background: linear-gradient(135deg, #34d399, #10b981);
  box-shadow: 0 4px 16px rgba(16, 185, 129, 0.35);
  transition: all 0.2s;
}
.sim-toggle.paused {
  color: #3b2503;
  background: linear-gradient(135deg, #fbbf24, #f59e0b);
  box-shadow: 0 4px 16px rgba(245, 158, 11, 0.35);
}
.sim-toggle:hover { transform: translateY(-1px); filter: brightness(1.06); }
.speed-pill {
  display: flex; align-items: center; gap: 10px;
  background: rgba(148, 163, 184, 0.08);
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 12px;
  padding: 8px 12px;
}
.spd-label { font-size: 11px; color: #94a3b8; white-space: nowrap; }
.speed-pill input[type="range"] { flex: 1; min-width: 0; accent-color: #38bdf8; }
.spd-val { font-size: 12px; font-weight: 700; color: #7dd3fc; min-width: 40px; text-align: right; font-variant-numeric: tabular-nums; }
.sim-gen {
  border: 1px dashed rgba(167, 139, 250, 0.55);
  background: rgba(139, 92, 246, 0.12);
  color: #c4b5fd;
  border-radius: 12px;
  padding: 9px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.sim-gen:hover { background: rgba(139, 92, 246, 0.24); }

.analysis-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 7px; }
.ana-btn {
  display: flex; align-items: center; gap: 8px;
  border: none; border-radius: 11px; padding: 10px 12px;
  font-size: 12px; font-weight: 600; cursor: pointer; color: #f8fafc;
  transition: all 0.15s; text-align: left;
}
.ana-btn span { font-size: 14px; }
.ana-btn:hover { transform: translateY(-1px); filter: brightness(1.12); }
.a-blue { background: linear-gradient(135deg, rgba(59, 130, 246, 0.9), rgba(37, 99, 235, 0.9)); }
.a-green { background: linear-gradient(135deg, rgba(16, 185, 129, 0.9), rgba(5, 150, 105, 0.9)); }
.a-amber { background: linear-gradient(135deg, rgba(245, 158, 11, 0.92), rgba(217, 119, 6, 0.92)); }
.a-cyan { background: linear-gradient(135deg, rgba(6, 182, 212, 0.9), rgba(8, 145, 178, 0.9)); }
.a-indigo {
  grid-column: span 2;
  justify-content: center;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.92), rgba(79, 70, 229, 0.92));
}

/* 右侧车辆列表：浮动玻璃面板 */
.vehicle-sidebar {
  top: 74px;
  right: 18px;
  width: 302px;
  height: calc(100% - 92px);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 18px;
  box-shadow: 0 16px 44px rgba(2, 6, 23, 0.18);
  overflow: hidden;
}
.sidebar-hidden { transform: translateX(calc(100% + 26px)); }
.sidebar-header { padding: 14px 16px 10px; border-bottom: 1px solid #eef2f7; background: transparent; }
.sidebar-header h3 { font-size: 14px; color: #0f172a; }
.sidebar-sub { font-size: 10px; color: #94a3b8; margin-top: 2px; }
.status-counts { display: flex; gap: 6px; flex-wrap: wrap; padding: 10px 16px 4px; }
.sc-chip {
  font-size: 10px; padding: 3px 9px; border-radius: 99px;
  background: #f1f5f9; color: #475569;
  display: inline-flex; align-items: center; gap: 5px;
}
.vehicle-card {
  border-left: 3px solid #cbd5e1;
  border-radius: 12px;
}
.vehicle-card.status-transit { border-left-color: #38bdf8; }
.vehicle-card.status-pickup { border-left-color: #a78bfa; }
.vehicle-card.status-loading { border-left-color: #fb923c; }
.vehicle-card.status-unloading { border-left-color: #34d399; }
.vehicle-card.status-idle { border-left-color: #cbd5e1; }
.vehicle-card.status-warning { border-left-color: #f87171; }
.vehicle-card:hover { transform: translateX(-3px); }
.status-badge { border-radius: 99px; font-weight: 600; }
.status-pickup { background: #ede9fe; color: #6d28d9; }

/* 跟随模式提示条 */
.follow-chip {
  position: absolute;
  bottom: 26px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(15, 23, 42, 0.88);
  color: #e2e8f0;
  border: 1px solid rgba(56, 189, 248, 0.45);
  border-radius: 99px;
  padding: 9px 18px;
  font-size: 12px;
  z-index: 1000;
  cursor: pointer;
  backdrop-filter: blur(10px);
  box-shadow: 0 6px 20px rgba(2, 6, 23, 0.35);
  white-space: nowrap;
}
.follow-chip:hover { background: rgba(15, 23, 42, 0.96); }

/* ================= 顶部系统栏 ================= */
.top-header {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 20px;
  background: linear-gradient(90deg, rgba(8, 15, 35, 0.94), rgba(15, 23, 42, 0.86) 38%, rgba(15, 23, 42, 0.86) 62%, rgba(8, 15, 35, 0.94));
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
  box-shadow: 0 4px 24px rgba(2, 6, 23, 0.35);
  z-index: 1200;
  color: #e2e8f0;
}
.th-brand { display: flex; align-items: center; gap: 10px; min-width: 0; }
.th-logo {
  width: 34px; height: 34px; border-radius: 10px; flex: none;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #22d3ee, #6366f1);
  box-shadow: 0 0 14px rgba(56, 189, 248, 0.45);
}
.th-title b { display: block; font-size: 15px; letter-spacing: 0.5px; color: #f8fafc; }
.th-title i { display: block; font-style: normal; font-size: 10px; color: #94a3b8; margin-top: 1px; }
.th-center { display: flex; align-items: center; gap: 8px; overflow: hidden; }
.th-chip {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 12px; color: #cbd5e1;
  background: rgba(148, 163, 184, 0.1);
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 99px;
  padding: 5px 12px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.th-chip i { width: 7px; height: 7px; border-radius: 50%; background: #64748b; flex: none; }
.th-chip.run { color: #6ee7b7; border-color: rgba(52, 211, 153, 0.4); }
.th-chip.run i { background: #34d399; box-shadow: 0 0 8px rgba(52, 211, 153, 0.9); }
.th-chip.stop { color: #fcd34d; border-color: rgba(251, 191, 36, 0.4); }
.th-chip.stop i { background: #fbbf24; }
.th-right { display: flex; align-items: center; gap: 12px; flex: none; }
.th-clock { font-size: 13px; color: #e2e8f0; font-variant-numeric: tabular-nums; letter-spacing: 0.5px; }
.th-fs {
  border: 1px solid rgba(148, 163, 184, 0.25);
  background: rgba(148, 163, 184, 0.1);
  color: #cbd5e1;
  border-radius: 9px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}
.th-fs:hover { background: rgba(148, 163, 184, 0.22); color: #f1f5f9; }

@media (max-width: 1200px) {
  .th-title i, .th-center .th-chip:nth-child(n + 3) { display: none; }
}
</style>

<style>
/* 全局样式重置 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body {
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #0b1220;
}

#app {
  width: 100%;
  height: 100%;
}

/* 可视化模态窗口 */
.visualization-modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 2000;
}

.modal-content {
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  width: 90%;
  max-width: 1400px;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #e0e0e0;
  background: #f8f9fa;
}

.modal-header h2 {
  margin: 0;
  font-size: 20px;
  color: #333;
}

.modal-header .close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #666;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s;
}

.modal-header .close-btn:hover {
  background: #e9ecef;
  color: #333;
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

@media (max-width: 768px) {
  .modal-content {
    width: 95%;
    height: 95vh;
    max-height: 95vh;
  }
}
</style>
