# 📅 钉钉日历技能

> 使用钉钉日历 API 管理日程、创建提醒

---

## 功能

1. **查看日程** — 查询未来 X 天的日程
2. **创建日程** — 添加新日程
3. **删除日程** — 取消日程
4. **日程提醒** — 提前通知即将开始的日程

---

## API 配置

### 钉钉 Calendar API

**文档：** https://open.dingtalk.com/document/orgapp/calendar

**接口：**
- `POST /v1.0/calendar/calendars` — 创建日历
- `GET /v1.0/calendar/calendars/{calendarId}/events` — 查询日程
- `POST /v1.0/calendar/calendars/{calendarId}/events` — 创建日程
- `DELETE /v1.0/calendar/calendars/{calendarId}/events/{eventId}` — 删除日程

---

## 使用方法

### 查看今日日程

```bash
dingtalk-calendar list --days 1
```

### 查看本周日程

```bash
dingtalk-calendar list --days 7
```

### 创建日程

```bash
dingtalk-calendar add --title "会议" --start "2026-04-19T10:00:00+08:00" --end "2026-04-19T11:00:00+08:00" --desc "项目讨论"
```

### 删除日程

```bash
dingtalk-calendar delete --event-id <event_id>
```

---

## 集成 HEARTBEAT

在 HEARTBEAT.md 中添加：

```markdown
### 日程检查（每日 08:00）
- [ ] 查询今日日程
- [ ] 查询明日日程
- [ ] 有重要日程时提前通知
```

---

## 测试

```bash
# 测试查询
dingtalk-calendar list --days 1

# 测试创建
dingtalk-calendar add --title "测试日程" --start "2026-04-19T15:00:00+08:00" --end "2026-04-19T15:30:00+08:00"
```

---

**三省六部 · 礼部 敬呈** 🏛️
