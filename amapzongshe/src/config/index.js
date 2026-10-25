// 高德地图配置
// 优先从 .env.local（已被 .gitignore 排除，不入库）读取自己的 key；
// 未配置时使用占位符——请到 https://lbs.amap.com 申请"Web端(JS API)"类型 key
export const AMAP_CONFIG = {
  key: import.meta.env.VITE_AMAP_KEY || "YOUR_AMAP_JS_KEY",
  securityCode: import.meta.env.VITE_AMAP_SECURITY_CODE || "",
  version: "2.0",
};
