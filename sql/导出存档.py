# -*- coding: utf-8 -*-
"""
结题数据存档一键导出工具
用法：双击 导出数据存档.bat，或命令行 python 导出存档.py
输出：docs-task/数据存档-YYYY-MM-DD.md（每次运行生成/覆盖当天的存档）
"""
import os
import re
import subprocess
import sys
from datetime import date

# ---------- 配置：mysql 客户端路径（按需修改） ----------
MYSQL_CANDIDATES = [
    r"C:\Users\Lenovo\mysql-9.1.0-winx64\bin\mysql.exe",
    r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
    r"C:\Program Files\MySQL\MySQL Server 8.1\bin\mysql.exe",
    "mysql",  # PATH 里能找到的话
]

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))          # 项目根
OUT_DIR = os.path.join(ROOT, "docs-task")
OUT_FILE = os.path.join(OUT_DIR, f"数据存档-{date.today().isoformat()}.md")


def find_mysql():
    for p in MYSQL_CANDIDATES:
        try:
            subprocess.run([p, "--version"], capture_output=True, check=True)
            return p
        except Exception:
            continue
    raise SystemExit("找不到 mysql.exe，请编辑本脚本顶部的 MYSQL_CANDIDATES")


def read_password():
    """从 application-local.yml（不入库）读取数据库密码；读不到用默认"""
    cfg = os.path.join(ROOT, "zongshe1", "src", "main", "resources", "application-local.yml")
    if os.path.exists(cfg):
        with open(cfg, encoding="utf-8") as f:
            m = re.search(r"password:\s*(\S+)", f.read())
            if m:
                return m.group(1)
    return "Zongshe@123"


MYSQL = find_mysql()
PWD = read_password()


def q(sql):
    r = subprocess.run(
        [MYSQL, "-uroot", f"-p{PWD}", "--default-character-set=utf8mb4", "-N", "-e", "USE zongshe;\n" + sql],
        capture_output=True)
    if r.returncode != 0:
        raise SystemExit("查询失败：" + r.stderr.decode("utf-8", errors="replace")[:300])
    out = r.stdout.decode("utf-8", errors="replace").replace("\r", "")
    return [ln.split("\t") for ln in out.strip().split("\n") if ln.strip()]


def table(headers, rows):
    out = ["| " + " | ".join(headers) + " |", "|" + "---|" * len(headers)]
    for row in rows:
        out.append("| " + " | ".join(row) + " |")
    return "\n".join(out) + "\n"


doc = []
doc.append(f"# 数据存档 · {date.today().isoformat()}\n")
doc.append("> 本次运行会话的指标留底。task/dispatch_batch_log 为永久资产；告警保留 7 天、快照保留 3 天。\n")

doc.append("## 1. 优化前后分时代对比（核心证据）\n")
rows = q("SELECT CASE WHEN complete_time < '2026-10-25 17:36:00' THEN '① 先到先得基线期' ELSE '② 多目标优化期' END, "
         "COUNT(*), ROUND(SUM(loaded_km),1), ROUND(SUM(empty_km),1), "
         "ROUND(SUM(empty_km)/NULLIF(SUM(empty_km)+SUM(loaded_km),0)*100,1), "
         "ROUND(AVG(TIMESTAMPDIFF(SECOND,create_time,complete_time))/60.0,1) "
         "FROM task WHERE status='已完成' AND complete_time IS NOT NULL AND empty_km IS NOT NULL "
         "GROUP BY 1 ORDER BY 1")
doc.append(table(["阶段", "完成任务数", "载重里程km", "空驶里程km", "空驶率%", "平均流转分钟"], rows) + "\n")

doc.append("## 2. 全局优化批次收益\n")
rows = q("SELECT DATE(create_time), COUNT(*), ROUND(AVG(improvement_pct),1), ROUND(MAX(improvement_pct),1), "
         "ROUND(SUM(carbon_kg),1) FROM dispatch_batch_log GROUP BY DATE(create_time) ORDER BY 1")
doc.append(table(["日期", "批次数", "平均降幅%", "最大降幅%", "累计空驶碳排kg"], rows) + "\n")

doc.append("## 3. 司机行为状态分布\n")
rows = q("SELECT status, COUNT(*) FROM driver GROUP BY status ORDER BY 2 DESC")
doc.append(table(["司机状态", "人数"], rows) + "\n")

doc.append("## 4. 随机事件统计（近 7 天）\n")
rows = q("SELECT type, COUNT(*) FROM alert_log WHERE create_time >= CURDATE() - INTERVAL 7 DAY "
         "GROUP BY type ORDER BY 2 DESC")
doc.append(table(["事件类型", "次数"], rows) + "\n")

doc.append("## 5. 维保与损耗\n")
m = q("SELECT COUNT(*), ROUND(SUM(cost),0) FROM maintenance_record")
g = q("SELECT COUNT(*) FROM goods_loss")
doc.append(f"- 维保记录：{m[0][0]} 条，总成本 {m[0][1]} 元\n- 货损记录：{g[0][0]} 条\n")

os.makedirs(OUT_DIR, exist_ok=True)
with open(OUT_FILE, "w", encoding="utf-8") as f:
    f.write("\n".join(doc))
print("✅ 存档已生成：", OUT_FILE)
