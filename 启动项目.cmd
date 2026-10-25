@echo off
chcp 65001 >nul
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo 请先安装 Node.js 22 或更新版本。
  pause
  exit /b 1
)
if not exist "node_modules\vite\bin\vite.js" (
  echo 正在安装项目依赖，请稍候...
  call npm ci --registry=https://registry.npmjs.org
  if errorlevel 1 (
    echo 依赖安装失败，请检查网络后重新运行。
    pause
    exit /b 1
  )
)
echo 即将启动物流运输调度仿真系统，请保持此窗口打开。
echo 地图车辆与仿真操作需要原 8080 后端运行。
call npm run dev -- --open
pause
