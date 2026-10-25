@echo off
chcp 65001 >nul
title 物流运输调度仿真系统 - 一键启动
echo ============================================
echo  物流运输调度仿真系统 - 一键启动
echo  关闭本窗口不会停止服务；停止请关两个子窗口
echo ============================================

cd /d "%~dp0zongshe1"
start "后端(8080) - 关闭此窗口即停止后端" cmd /k "mvn spring-boot:run"

cd /d "%~dp0amapzongshe"
start "前端(5173) - 关闭此窗口即停止前端" cmd /k "npm run dev"

echo.
echo 两个服务已在独立窗口启动：
echo   后端 http://localhost:8080/doc.html   （首次启动约 30-60 秒）
echo   前端 http://localhost:5173/
echo 数据库随 Windows 自动启动，无需操作。
pause
