# 🤖 多模型切换 Skill

> 根据任务类型自动选择最优模型，提升效率、节省成本

---

##  可用模型

| 模型 | 别名 | 适用场景 | 成本 |
|------|------|----------|------|
| dashscope-coding/qwen3.5-plus | qwen3.5-plus | 编码、复杂任务 | 中 |
| dashscope/qwen3.5-plus | qwen3.5-plus | 通用对话 | 中 |
| dashscope-coding/MiniMax-M2.5 | minimax-m2.5 | 编码优化 | 中 |
| dashscope-us/qwen3-max-2025-09-23 | qwen3-max | 高精度任务 | 高 |
| dashscope/qwen3-vl-plus | qwen3-vl-plus | 图像分析 | 中 |

---

## 🎯 切换规则

### 自动选择逻辑

```
IF 任务包含代码/编程 → qwen3.5-plus (coding)
IF 任务包含图片分析 → qwen3-vl-plus
IF 任务需要高精度推理 → qwen3-max
IF 日常对话/简单任务 → qwen3.5-plus (default)
IF 长文本处理 → qwen3.5-plus (context 大)
```

### 用户手动指定

```
"用 qwen3-max 分析一下这个..."
"切换到 minimax 帮我写代码"
```

---

## 💬 婉儿提示

**模型切换时提醒：**

- "大黄，这个任务我用 qwen3-max 来处理，更准一些"
- "简单问题，qwen3.5 就够了，省钱～"
- "要分析图片？我切 qwen3-vl..."

---

## 🔧 技术实现

### 方案 A：session_status 模型覆盖

```bash
# 查看当前模型
openclaw status

# 临时切换
openclaw model set qwen3-max
```

### 方案 B：sessions_spawn 指定模型

```json
{
  "runtime": "subagent",
  "model": "dashscope-us/qwen3-max-2025-09-23",
  "task": "..."
}
```

### 方案 C：任务级模型配置

```json
// skills/model-router/config.json
{
  "rules": [
    {
      "pattern": ["代码", "编程", "写脚本"],
      "model": "dashscope-coding/qwen3.5-plus"
    },
    {
      "pattern": ["图片", "分析", "看图"],
      "model": "dashscope/qwen3-vl-plus"
    },
    {
      "pattern": ["复杂", "推理", "深度"],
      "model": "dashscope-us/qwen3-max-2025-09-23"
    }
  ],
  "default": "dashscope-coding/qwen3.5-plus"
}
```

---

## 📊 成本统计

### memory/model-usage.json

```json
{
  "2026-04": {
    "qwen3.5-plus": {
      "requests": 150,
      "tokens_in": 50000,
      "tokens_out": 30000,
      "cost_cny": 5.00
    },
    "qwen3-max": {
      "requests": 20,
      "tokens_in": 10000,
      "tokens_out": 5000,
      "cost_cny": 8.00
    }
  },
  "total_cost_cny": 13.00
}
```

---

## 🎯 实施步骤

1. **创建配置目录**
   - skills/model-router/
   - skills/model-router/config.json

2. **更新婉儿 system-prompt**
   - 添加模型切换逻辑
   - 添加成本感知提醒

3. **创建使用记录**
   - memory/model-usage.json
   - 定期统计成本

4. **测试验证**
   - 手动切换模型
   - 确认自动选择逻辑

---

**创建时间：** 2026-04-17  
**负责人：** 婉儿（决策） + 户部（成本核算）
