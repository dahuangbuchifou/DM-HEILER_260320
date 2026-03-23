# 双轨开发工作流程

## 📋 当前状态

| 项目 | 状态 | 说明 |
|------|------|------|
| **本地开发** | ✅ 进行中 | 你在本地 Android Studio 开发 |
| **服务器编译** | ⏳ 待配置 | 需要安装 Android SDK |
| **代码同步** | 🔄 手动 | 通过 Git 或文件传输 |

---

## 🎯 双轨模式设计

### 轨道 1：本地开发（主）
- **工具**：Android Studio
- **职责**：代码编辑、UI 设计、即时调试
- **流程**：修改 → 编译 → 测试 → 提交 Git

### 轨道 2：服务器编译（辅）
- **工具**：Gradle CLI
- **职责**：自动化编译、CI/CD、批量测试
- **流程**：拉取代码 → 编译 → 生成 APK

---

## 📁 代码同步策略

### 推荐：使用 Git
```bash
# 本地
git add .
git commit -m "描述"
git push origin main

# 服务器
cd /home/admin/.openclaw/workspace/DM-HEILER_260320
git pull origin main
```

### 备选：直接文件同步
```bash
# 本地 → 服务器
scp -r app/ admin@server:/home/admin/.openclaw/workspace/DM-HEILER_260320/
```

---

## 🤖 AI 协作流程

### 场景 A：本地开发遇到问题
1. 你发送错误信息（截图/文本）
2. AI 分析并给出修改建议
3. 你在本地修改
4. （可选）提交 Git，AI 可在服务器上验证

### 场景 B：服务器编译问题
1. AI 在服务器上直接修改文件
2. AI 执行 `./gradlew assembleDebug`
3. AI 报告编译结果
4. 你拉取更新后的代码

---

## ✅ 待办事项

### 服务器环境配置
- [ ] 安装 Android SDK (API 34)
- [ ] 配置 ANDROID_HOME 环境变量
- [ ] 接受 SDK licenses
- [ ] 测试编译 `./gradlew assembleDebug`

### Git 仓库设置（推荐）
- [ ] 初始化 Git 仓库
- [ ] 配置远程仓库（GitHub/GitLab）
- [ ] 设置自动同步

---

## 📝 沟通约定

- **本地问题** → 直接发错误信息
- **服务器任务** → 明确标注【服务器任务】
- **同步代码** → 完成后告知 AI

---

最后更新：2026-03-23
