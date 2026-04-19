# 📅 简单日程提醒技能

> 本地存储 + 钉钉推送的轻量级日程管理

---

## 🚀 快速开始

### 查询日程
```bash
# 查询未来 1 天
reminder list

# 查询未来 7 天
reminder list --days 7
```

### 创建日程
```bash
reminder add --title '会议' --time '2026-04-20 14:00' --desc '项目讨论'
```

### 删除日程
```bash
reminder delete --id 1
```

### 检查提醒
```bash
reminder check
```

---

## 📋 命令说明

| 命令 | 参数 | 说明 |
|------|------|------|
| `list` | `--days N` | 查询未来 N 天日程 |
| `add` | `--title`, `--time`, `--desc` | 创建日程 |
| `delete` | `--id N` | 删除日程 |
| `check` | 无 | 检查待提醒日程（提前 15 分钟） |

---

## 💡 使用场景

1. **会议提醒** - 提前 15 分钟推送钉钉消息
2. **日常待办** - 记录每日任务
3. **重要日期** - 生日、纪念日等

---

## 🔧 配置钉钉推送（可选）

编辑 `~/.openclaw/openclaw.json`，确保钉钉配置正确：

```json
{
  "channels": {
    "dingtalk": {
      "enabled": true,
      "clientId": "dingjrsh99vf3azfp24k",
      "clientSecret": "..."
    }
  }
}
```

---

## 📁 数据存储

- **位置：** `~/.openclaw/workspace/memory/schedule.json`
- **格式：** JSON
- **备份：** 建议定期备份

---

_最后更新：2026-04-19_
