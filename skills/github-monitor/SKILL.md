# 🔍 GitHub 监控 Skill

> 跟踪项目更新、PR、Issue、Star 变化

---

##  功能

1. **Repo 监控**
   - 新 Commit 通知
   - 新 PR/Issue 通知
   - Star 数变化统计

2. **动态汇总**
   - 每日/周项目动态
   - 重要更新摘要
   - 趋势分析

3. **多 Repo 支持**
   - 同时监控多个仓库
   - 分类汇总（个人/工作）

---

## 🎯 监控项目

| Repo | 用途 | 优先级 |
|------|------|--------|
| dahuangbuchifou/DM-HEILER_260320 | 大麦项目 | 🔴 P0 |
| dahuangbuchifou/OpenClaw-three-provinces | 三省六部 | 🔴 P0 |
| openclaw/openclaw | OpenClaw 主项目 | 🟡 P1 |

---

## 💬 汇报格式（婉儿风格）

### 📊 日报格式

```
大黄，GitHub 动态：

【DM-HEILER】
- 新 Commit: 1 个（您昨天推送的）
- Star 变化：+0（目前 XX 个）
- 状态：安静

【三省六部】
- 新 Commit: X 个
- 新 Issue: X 个
- 状态：活跃

今天没什么大动静，您放心～
```

### 📈 周报格式

```
大黄，本周 GitHub 汇总：

【DM-HEILER】
- Commit: X 个
- Star: XX → XX (+X)
- 主要变更：[摘要]

【三省六部】
- Commit: X 个
- 新增文件：X 个
- 主要变更：[摘要]

这周挺充实的，您辛苦了～
```

---

## 🔧 技术实现

### 方案 A：GitHub API + Cron

```bash
# 使用 GitHub API 查询
curl -H "Authorization: token $GITHUB_TOKEN" \
  https://api.github.com/repos/dahuangbuchifou/DM-HEILER_260320/events

# 解析 JSON，提取关键事件
# 发送到婉儿汇总
```

### 方案 B：GitHub Webhook

```json
// 配置 Webhook 到 OpenClaw
{
  "events": ["push", "pull_request", "issues"],
  "url": "https://your-openclaw-instance/webhook/github"
}
```

### 方案 C：RSS 订阅

```
https://github.com/dahuangbuchifou/DM-HEILER_260320/commits/main.atom
https://github.com/dahuangbuchifou/DM-HEILER_260320/releases.atom
```

---

## 📊 数据记录

### memory/github-monitor-state.json

```json
{
  "repos": {
    "DM-HEILER_260320": {
      "lastCheck": "2026-04-17T14:00:00Z",
      "lastCommit": "142754a",
      "starCount": 0,
      "openIssues": 0,
      "openPRs": 0
    }
  },
  "dailyReport": {
    "enabled": true,
    "time": "09:00"
  },
  "weeklyReport": {
    "enabled": true,
    "day": "Monday",
    "time": "09:00"
  }
}
```

---

## 🎯 实施步骤

1. **获取 GitHub Token**
   - 创建 Personal Access Token
   - 权限：repo, read:user

2. **创建监控配置**
   - memory/github-monitor-state.json
   - 配置监控的 repo 列表

3. **设置定时任务**
   - 每日 09:00 检查
   - 每周一汇总

4. **集成到婉儿汇报**
   - 更新 HEARTBEAT.md
   - 添加 GitHub 检查项

---

**创建时间：** 2026-04-17  
**负责人：** 婉儿（汇报） + 工部（技术实现）
