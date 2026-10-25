@echo off
chcp 65001 >nul
title 结题数据存档导出
cd /d "%~dp0"
python "sql\导出存档.py"
echo.
echo 完成后可将本窗口关闭。
pause
