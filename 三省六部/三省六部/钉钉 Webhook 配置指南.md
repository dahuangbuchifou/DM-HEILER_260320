# 📌 钉钉 Webhook Access Token 获取指南

> **更新时间：** 2026-04-08 11:40  
> **适用场景：** 获取钉钉机器人 Webhook 用于消息推送

---

## 🔍 方法一：通过钉钉开放平台（推荐）

### 步骤 1：进入开发者后台

1. 访问：https://open-dev.dingtalk.com/
2. 登录你的钉钉账号
3. 点击顶部菜单 **「开发者后台」**

### 步骤 2：找到应用

1. 在左侧菜单选择 **「应用开发」** → **「企业内部开发」**
2. 找到你创建的应用（或创建一个新应用）
3. 点击进入应用详情

### 步骤 3：添加机器人

1. 在应用详情页面，点击 **「添加应用能力」**
2. 选择 **「机器人」**
3. 填写机器人信息：
   - 机器人名称：如「三省六部助手」
   - 头像：可选
   - 描述：AI 助手消息推送

### 步骤 4：获取 Webhook

1. 进入机器人配置页面
2. 找到 **「消息接收模式」**
3. 选择 **「Webhook」**
4. 复制 **Webhook 地址**，格式如下：

```
https://oapi.dingtalk.com/robot/send?access_token=xxxxxxxxxxxxxx
```

5. **access_token** 就是 `=` 后面的那串字符

---

## 🔍 方法二：通过钉钉群聊（快速创建）

### 步骤 1：打开钉钉群

1. 打开任意钉钉群（或创建一个新群）
2. 点击右上角 **「群设置」**（齿轮图标）

### 步骤 2：添加机器人

1. 找到 **「智能群助手」** 或 **「群机器人」**
2. 点击 **「添加机器人」**
3. 选择 **「自定义」** 机器人

### 步骤 3：配置机器人

1. 填写机器人名称：如「三省六部」
2. 可选：设置头像
3. **安全设置**（三选一）：
   - ✅ **自定义关键词**（推荐）：添加关键词如「小虾」「三省六部」
   - 加签：需要额外配置签名
   - IP 地址：限制来源 IP

### 步骤 4：获取 Webhook

1. 完成配置后，会显示 Webhook 地址
2. 复制并保存：

```
https://oapi.dingtalk.com/robot/send?access_token=xxxxxxxxxxxxxx
```

---

## 📝 配置到 OpenClaw

### 方式 A：更新 openclaw.json（推荐）

编辑 `~/.openclaw/openclaw.json`，找到 `channels.dingtalk` 部分：

```json
{
  "channels": {
    "dingtalk": {
      "enabled": true,
      "webhook": "https://oapi.dingtalk.com/robot/send?access_token=你的 token"
    }
  }
}
```

### 方式 B：通过 CLI 配置

```bash
openclaw configure --section channels
```

然后按照提示输入 Webhook URL

---

## ✅ 测试验证

配置完成后，运行以下命令测试：

```bash
curl "https://oapi.dingtalk.com/robot/send?access_token=你的 token" \
  -H "Content-Type: application/json" \
  -d '{
    "msgtype": "text",
    "text": {
      "content": "测试消息 - 三省六部系统"
    }
  }'
```

**成功响应：**
```json
{"errcode":0,"errmsg":"ok"}
```

**失败响应：**
```json
{"errcode":300005,"errmsg":"token is not exist"}
```

---

## 🔒 安全提示

1. **不要公开分享 access_token**
2. 设置安全关键词，防止滥用
3. 定期轮换 token（钉钉支持重置）
4. 如 token 泄露，立即在钉钉后台重置

---

## 📞 需要帮助？

- 钉钉开放平台文档：https://open.dingtalk.com/document/robots/custom-robot-access
- 联系：门下省 - 审核官

---

**最后更新：** 2026-04-08 11:40
