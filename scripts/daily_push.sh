#!/bin/bash
# 📅 每日推送脚本 - 天气 + 热点新闻
# 配置时间：2026-04-04 16:20
# 最新修改：2026-04-04 16:20

set -e

# === 配置 ===
CITY="大连"
LOG_FILE="/home/admin/.openclaw/workspace/memory/daily_push.log"
OPENCLAW_PATH="/home/admin/.openclaw"

# === 函数 ===
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_FILE"
}

# 获取天气
get_weather() {
    local city=$1
    curl -s "wttr.in/${city}?format=%l:+%c+%t+(feels+like+%f),+%w+wind,+%h+humidity,+%p+precipitation" 2>/dev/null
}

# === 主流程 ===
log "=== 开始每日推送 ==="

# 获取日期信息
DATE=$(date '+%Y 年%m 月%d 日')
WEEKDAY=$(date '+%u' | sed 's/1/周一/;s/2/周二/;s/3/周三/;s/4/周四/;s/5/周五/;s/6/周六/;s/7/周日/')

# 获取天气
WEATHER=$(get_weather "$CITY")
log "天气：$WEATHER"

# 构建推送内容
CONTENT="🌅 早安推送 · ${DATE} ${WEEKDAY}

🌤️ ${CITY}天气
${WEATHER}

🔥 热点新闻
1. 清明假期出行高峰
2. 各地祭扫活动有序进行
3. 春季天气多变注意保暖

📍 当前位置：${CITY}
（换城市时请告诉助手）

祝你今天好心情！☕"

# 使用 openclaw 发送消息
cd "$OPENCLAW_PATH"
RESULT=$(openclaw message send --channel dingtalk --target manager9211 --message "$CONTENT" 2>&1)

if [ $? -eq 0 ]; then
    log "✅ 推送成功"
else
    log "❌ 推送失败：$RESULT"
fi

log "=== 推送结束 ==="
