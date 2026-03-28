# 📋 发布检查清单

_防止遗忘版本时间同步和开发日志更新_

---

## ✅ 每次提交前检查（必须完成）

### 1. 版本时间同步（三位置）

- [ ] **TicketTask.kt** 头部注释
  ```kotlin
  // 📅 最新修复：2026-03-28 22:20
  ```

- [ ] **MainActivity.kt** updateVersionTime()
  ```kotlin
  versionUpdateTimeText.text = "📅 版本更新时间：2026-03-28 22:20"
  ```

- [ ] **Git commit** 标注版本号和时间
  ```bash
  git commit -m "🐛 Fix: xxx (v1.2.1 2026-03-28 22:20)"
  ```

### 2. 开发日志更新

- [ ] **docs/开发日志.md** 添加修复记录
  - 日期标题
  - 问题描述
  - 修复方案
  - 版本号

- [ ] **memory/YYYY-MM-DD.md** 记录重要变更
  - 日期文件（如：memory/2026-03-28.md）
  - 重要功能/修复摘要

### 3. COMPILER_ERRORS_LOG.md（如有编译错误）

- [ ] 更新错误汇总表
- [ ] 添加详细修复记录
- [ ] 总结错误模式和教训

---

## 🏷️ 版本号规则

```
v主版本。次版本.修订版本
  ↑      ↑      ↑
  |      |      └─ Bug 修复
  |      └─ 功能增加
  └─ 重大变更

示例：
v1.0.0 - 初始版本
v1.1.0 - 添加预约 + 秒杀模式
v1.1.1 - 修复 audienceName 字段缺失
v1.1.2 - 修复 getTaskById 类型不匹配
```

---

## 🕐 时间格式

- **日期：** `2026-03-28`
- **时间：** `22:20`
- **完整：** `2026-03-28 22:20`

---

## 📝 Commit 消息模板

### Bug 修复
```
🐛 Fix: 修复 xxx 问题

问题描述：
原因分析：
修复方案：

版本：v1.1.2 2026-03-28 22:20

[ci skip]
```

### 新功能
```
🆕 Feature: 添加 xxx 功能

功能说明：
用户需求：
实现方案：

版本：v1.1.0 2026-03-28 21:45

[ci skip]
```

### 文档更新
```
📝 Docs: 更新 xxx 文档

更新内容：

版本：v1.1.0 2026-03-28 21:45

[ci skip]
```

---

## 🚀 快速操作流程

### 步骤 1：修改代码
```bash
# 编辑文件...
```

### 步骤 2：更新时间（三位置）
```bash
# 1. TicketTask.kt 头部
# 2. MainActivity.kt updateVersionTime()
# 3. 准备 commit 消息
```

### 步骤 3：更新日志
```bash
# 1. docs/开发日志.md 添加记录
# 2. memory/2026-03-28.md 追加内容
```

### 步骤 4：提交推送
```bash
git add -A
git commit -m "🐛 Fix: xxx (v1.1.2 2026-03-28 22:20)"
git push origin main
```

---

## 💡 自动化建议（未来实现）

### 1. Git Hook（pre-commit）
```bash
#!/bin/bash
# 检查是否更新了版本时间
if ! grep -q "最新修复：$(date +%Y-%m-%d)" app/src/main/java/com/damaihelper/model/TicketTask.kt; then
    echo "❌ 请更新 TicketTask.kt 头部注释中的日期"
    exit 1
fi
```

### 2. Gradle Task
```groovy
task updateVersionTime {
    doLast {
        def timestamp = new Date().format('YYYY-MM-dd HH:mm')
        // 自动更新文件中的版本时间
    }
}
```

### 3. IDE Live Template
```
// 📅 最新修复：$DATE$
// 🔧 修复内容：$CONTENT$
```

---

**创建日期：** 2026-03-28 22:20  
**最后更新：** 2026-03-28 22:20
