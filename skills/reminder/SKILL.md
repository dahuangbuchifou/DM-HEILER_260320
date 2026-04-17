---
name: reminder
description: 定时提醒技能（子代理方案）。支持一次性延迟发送消息。
metadata: {"openclaw": {"emoji": "⏰"}}
---

# 定时提醒技能（子代理方案）

用 `sessions_spawn` 创建延迟任务，替代有 bug 的 `openclaw cron`。

## 使用方式

### 方法 1：直接调用（推荐）

```bash
# 创建子代理，延迟发送
openclaw sessions spawn --mode run --runtime subagent --task "
等待 N 秒后，给钉钉用户 manager9211 发送消息：
'提醒内容'
使用 message 工具发送：action=send, channel=dingtalk, target=manager9211
"
```

### 方法 2：AI 自动调用

AI 收到定时任务请求时，自动执行：

```javascript
sessions_spawn({
  task: `等待 ${delaySeconds} 秒后，给钉钉用户 ${userId} 发送消息：
  "${message}"
  使用 message 工具：action=send, channel=dingtalk, target=${userId}`,
  mode: "run",
  runtime: "subagent",
  runTimeoutSeconds: delaySeconds + 30,
  label: `定时提醒：${taskName}`
})
```

## 时间计算

| 用户说法 | 延迟秒数 |
|----------|----------|
| 1 分钟后 | 60 |
| 5 分钟后 | 300 |
| 10 分钟后 | 600 |
| 1 小时后 | 3600 |

## 参数说明

| 参数 | 说明 | 示例 |
|------|------|------|
| `delaySeconds` | 延迟秒数 | 300（5 分钟）|
| `userId` | 钉钉用户 ID | manager9211 |
| `message` | 消息内容 | "喝水时间到！" |
| `taskName` | 任务标签 | "喝水提醒" |

## 注意事项

1. **超时设置**：`runTimeoutSeconds` = 延迟秒数 + 30 秒缓冲
2. **消息渠道**：根据当前渠道动态调整（dingtalk/telegram/等）
3. **任务追踪**：用 `subagents list` 查看运行中的任务

## 替代 cron 的原因

`openclaw cron` 的 delivery 环节有 bug：
- ✅ 任务正常执行
- ✅ 完成通知能发送
- ❌ 消息内容没送达用户

子代理方案已验证可用，作为临时替代。
