# 📅 Google Calendar & Gmail 配置指南

> **更新时间：** 2026-04-08 13:10  
> **适用账号：** `jf.zhang (iamzjf01@gmail.com)`  
> **Google Cloud 项目：** `OpenClaw-Calendar-260406`

---

## 📋 当前状态

| 项目 | 状态 | 说明 |
|------|------|------|
| Google Cloud 项目 | ✅ 已创建 | OpenClaw-Calendar-260406 |
| Google 账号 | ✅ 已确认 | iamzjf01@gmail.com |
| ClawHub 技能安装 | ⏸️ 速率限制中 | 等待恢复 |
| OAuth 配置 | ⏸️ 待执行 | 需要浏览器授权 |

---

## 🔧 方案 A：ClawHub 自动安装（推荐）

### 步骤 1：等待速率限制解除

ClawHub 有速率限制（约 10-30 分钟），自动恢复后执行：

```bash
# 安装 Google Calendar
clawhub install google-calendar-api --force

# 安装 Gmail
clawhub install gmail --force
```

### 步骤 2：OAuth 授权

安装完成后，系统会提示进行 OAuth 授权：

1. **浏览器弹出授权页面**
2. **登录 Google 账号** (`iamzjf01@gmail.com`)
3. **授权权限：**
   - Calendar API：查看/编辑日历
   - Gmail API：读取/发送邮件
4. **复制授权码**（如有）

### 步骤 3：测试验证

```bash
# 测试日历
openclaw agent --prompt "查看我的日历"

# 测试邮件
openclaw agent --prompt "检查我的 Gmail"
```

---

## 🔧 方案 B：手动配置（如 ClawHub 持续失败）

### 步骤 1：启用 Google API

1. 访问：https://console.cloud.google.com/
2. 选择项目：`OpenClaw-Calendar-260406`
3. 启用 API：
   - **Google Calendar API**
   - **Gmail API**

### 步骤 2：创建 OAuth 凭据

1. 进入 **「API 和服务」** → **「凭据」**
2. 点击 **「创建凭据」** → **「OAuth 客户端 ID」**
3. **应用类型：** 桌面应用
4. **名称：** `OpenClaw`
5. **下载 JSON 文件**（保存为 `google-credentials.json`）

### 步骤 3：配置到 OpenClaw

```bash
# 将 JSON 文件放到指定位置
cp google-credentials.json ~/.openclaw/credentials/google.json

# 重启 Gateway
openclaw gateway restart
```

---

## 🔍 检查 Google Cloud 项目配置

### 需要确认的项目

从你的截图看，项目已创建。请确认：

1. **API 已启用：**
   - [ ] Google Calendar API
   - [ ] Gmail API

2. **OAuth 同意屏幕：**
   - [ ] 已配置
   - [ ] 用户类型：外部（External）

3. **凭据：**
   - [ ] OAuth 2.0 客户端 ID 已创建

---

## 📊 预期效果

### Google Calendar 功能

- ✅ 查看日历事件
- ✅ 创建/编辑事件
- ✅ 日程提醒
- ✅ 与天气结合（"明天有雨，记得带伞"）
- ✅ HEARTBEAT 自动检查

### Gmail 功能

- ✅ 检查未读邮件
- ✅ 邮件摘要推送
- ✅ 自动分类/标记
- ✅ 发送邮件

---

## 🛑 常见问题

### Q1: ClawHub 速率限制

**解决：** 等待 10-30 分钟自动恢复，或使用方法 B 手动配置

### Q2: OAuth 授权失败

**解决：**
1. 确认 Google 账号可访问
2. 检查网络是否可达 Google 服务
3. 确认 OAuth 同意屏幕已配置

### Q3: API 调用失败

**解决：**
1. 检查 API 是否已启用
2. 检查凭据是否有效
3. 查看 Gateway 日志

---

## 📞 需要帮助？

- **文档位置：** `/home/admin/.openclaw/workspace/三省六部/Google 配置指南.md`
- **联系：** 户部 - 资源财务官

---

**三省六部 敬呈** 🏛️  
*2026-04-08 13:10*
