# 📋 DM-HEILER 开发协作 Checklist

_最后更新：2026-03-29 15:47_

---

## 🔄 代码交流流程（基础方法）

这是我们与 AI 助手协作的标准流程，**后期也这么做**：

### 步骤 1：AI 修改代码
- [ ] AI 在本地 workspace 修改代码
- [ ] AI 验证修改内容正确
- [ ] AI 提交到 Git 并推送到 GitHub

### 步骤 2：开发者 Pull 代码
- [ ] 在 Android Studio 中执行 `git pull`
- [ ] 同步最新代码到本地
- [ ] 检查版本更新时间和变更内容

### 步骤 3：编译与测试
- [ ] 重新编译项目（Build → Rebuild Project）
- [ ] 安装到测试设备
- [ ] 验证功能是否正常

### 步骤 4：反馈与迭代
- [ ] 如有问题，提供 logcat 日志
- [ ] AI 分析日志并修复
- [ ] 重复步骤 1-3 直到问题解决

---

## 📝 数据库版本升级记录

### 2026-03-29 15:47 - Version 1 → 2

**原因：** 添加新字段后 Room 检测到 schema 变化，需要升级版本号并添加迁移策略

**变更内容：**
- `version`: 1 → 2
- 添加 `MIGRATION_1_2` 迁移对象
- 新增字段：
  - `audienceName` - 用于自动选择观演人
  - `selectedPrice` - 用于步骤 2 选择票档
  - `sessionId` - 指定场次 ID
  - `priceTiers` - 多票档备选
  - `audienceIndex` - 观影人索引
  - `grabMode` - 抢票模式（normal/snap）
  - `quantity` - 票数（兼容字段）

**修改文件：**
- `app/src/main/java/com/damaihelper/model/TaskDatabase.kt`

**测试验证：**
- [ ] 清除旧数据后新建任务 → 保存成功
- [ ] 旧版本升级后数据不丢失 → 待验证
- [ ] Database Inspector 显示所有字段 → 待验证

---

## 🧪 测试 Checklist

### 阶段 1：任务创建与保存
- [ ] 输入演出关键词
- [ ] 选择抢票日期和时间
- [ ] 输入票价关键词
- [ ] 输入观演人姓名
- [ ] 点击保存按钮
- [ ] 验证提示"✅ 任务保存成功"
- [ ] 验证主界面显示新任务
- [ ] 验证 Database Inspector 有数据

### 阶段 2：任务列表显示
- [ ] 任务卡片格式正确
- [ ] 日期显示为 MM-dd HH:mm 格式
- [ ] 观众姓名显示正确
- [ ] 票档信息显示正确
- [ ] 多任务排序正确

### 阶段 3：数据库迁移验证
- [ ] 旧版本用户升级后数据不丢失
- [ ] 新字段有默认值
- [ ] 无崩溃、无异常

---

## 📊 Git 操作规范

### 提交信息格式
```
<emoji> <类型>: <简短描述>

- 详细变更 1
- 详细变更 2
- 详细变更 3
```

### 常用 Emoji
- 📅 日期相关/版本更新
- 🔧 修复/配置
- 🆕 新功能
- 🐛 Bug 修复
- 📝 文档
- 🧪 测试
- 🚀 发布

### 示例
```bash
git add -A
git commit -m "📅 数据库版本升级：v1 → v2

- 添加 MIGRATION_1_2 迁移策略
- 新增 7 个字段（audienceName, selectedPrice 等）
- 支持旧版本数据平滑升级"
git push origin main
```

---

## 🛠️ 常见问题速查

### 问题 1：Room cannot verify data integrity
**原因：** Schema 变化但版本号未更新  
**解决：** version++ 并添加 Migration

### 问题 2：数据库是空的
**原因：** 保存逻辑被注释掉  
**解决：** 检查 `saveTask()` 方法中的 `taskDao.insertTask(task)`

### 问题 3：列表不显示
**原因：** 使用了假数据  
**解决：** 从数据库加载真实数据

### 问题 4：编译错误
**原因：** 依赖/导入缺失  
**解决：** 检查 import 语句和 Gradle 配置

---

## 📁 关键文件位置

| 文件 | 路径 | 用途 |
|------|------|------|
| TaskDatabase.kt | `app/src/main/java/com/damaihelper/model/` | 数据库配置 |
| TicketTask.kt | `app/src/main/java/com/damaihelper/model/` | 数据模型 |
| TaskDao.kt | `app/src/main/java/com/damaihelper/model/` | 数据访问层 |
| TaskConfigActivity.kt | `app/src/main/java/com/damaihelper/` | 任务配置页面 |
| MainActivity.kt | `app/src/main/java/com/damaihelper/` | 主界面 |

---

## 📞 协作记录

| 日期 | 内容 | 状态 |
|------|------|------|
| 2026-03-29 22:15 | 修复时间同步 + 数据库迁移 + 分屏检测 | ✅ 完成 |
| 2026-03-29 15:47 | 建立协作流程，数据库版本升级 | ✅ 完成 |

---

## ⚠️ 重要规范：时间同步

**每次修改代码后必须同步更新时间！**

### 需要同步的位置

1. **TicketTask.kt** - 头部注释
   ```kotlin
   // 📅 最新修复：2026-03-29 22:15
   ```

2. **MainActivity.kt** - 版本显示
   ```kotlin
   versionUpdateTimeText.text = "📅 版本更新时间：2026-03-29 22:15"
   ```

### 检查清单

每次提交前必须确认：
- [ ] TicketTask.kt 头部时间已更新
- [ ] MainActivity.kt 版本显示已更新
- [ ] 两个时间保持一致
- [ ] Commit message 包含时间戳

---

## 🐛 已修复问题记录

### 2026-03-29 22:15 - 分屏模式大麦检测失败

**问题：** 分屏模式下，焦点在助手 App 时，"从大麦抓取"功能提示"找不到大麦"

**原因：** `extractTaskInfoFromDamaiPage()` 使用 `rootInActiveWindow` 检测，但焦点在助手时检测不到大麦

**解决：** 改用 `findDamaiRootNode()` 方法，支持分屏模式检测

**修改文件：**
- `app/src/main/java/com/damaihelper/service/TicketGrabbingAccessibilityService.kt`

### 2026-03-29 22:15 - 数据库迁移重复字段异常

**问题：** `duplicate column name: audienceName`

**原因：** 用户数据库已经是 version 2，迁移时重复添加字段

**解决：** 在 `MIGRATION_1_2` 中为每个 `ALTER TABLE` 添加 try-catch

**修改文件：**
- `app/src/main/java/com/damaihelper/model/TaskDatabase.kt`

---

_此文件由 AI 助手创建，用于记录开发协作流程和重要检查项_
