# 🎙️ GPT-SoVITS 集成到 OpenClaw

_目标：让婉儿拥有自己的声音，通过 TTS 输出语音消息_

---

## 📋 需求

| 场景 | 频率 | 要求 |
|------|------|------|
| 日程提醒 | 每日数次 | 短文本，清晰 |
| 天气汇报 | 每日 1 次 | 短文本，自然 |
| 闲聊对话 | 不定期 | 长文本，情感丰富 |
| 故事/内容朗读 | 不定期 | 长文本，表现力 |

**约束：**
- 服务器无 GPU（阿里云 ECS，1.8GB 内存）
- 本地电脑有 GPT-SoVITS 环境和已训练模型
- 需要集成到 OpenClaw TTS 流程
- 成本敏感（优先免费）

---

## 🏗️ 架构

```
OpenClaw 服务器 ──HTTP──▶ Cloudflare Tunnel ──▶ 本地电脑 GPT-SoVITS API
```

- **本地电脑**：运行 GPT-SoVITS 模型 + API 服务（需要 GPU）
- **Cloudflare Tunnel**：内网穿透，把本地 API 暴露给公网
- **OpenClaw 服务器**：通过 tunnel URL 调用 TTS API

---

## 📅 进度记录

### 2026-05-05 → 05-06：首次尝试

**操作：** 在大黄本地电脑启动 GPT-SoVITS API

**遇到的问题：**
- ❌ 本地电脑运行 API 时出现大量依赖缺失报错
- ❌ 之前已经正常运行过 GPT-SoVITS，但这次依赖问题很多

**可能原因（待确认）：**
1. GPT-SoVITS 代码更新后 `requirements.txt` 变了，但没重新安装依赖
2. Python 虚拟环境/conda 环境切换了，当前环境里没有装过依赖
3. `requirements.txt` 本身有版本冲突或不完整
4. 某些系统级依赖（如 ffmpeg、libsndfile）没装

**下一步：** 等大黄在家里台式机上继续操作，排查依赖问题

---

## 🔧 待办

- [ ] 排查本地电脑依赖问题
- [ ] 本地电脑成功启动 GPT-SoVITS API
- [ ] 配置 Cloudflare Tunnel 暴露端口
- [ ] OpenClaw 服务器测试调用 API
- [ ] 集成到 OpenClaw TTS 流程
- [ ] 测试语音效果

---

## 📝 参考

- 方案对比报告：`memory/tts-voice-cloning-comparison.md`
- GPT-SoVITS 官方：https://github.com/RVC-Boss/GPT-SoVITS
- **集成进度仓库：** https://github.com/dahuangbuchifou/GPT-SoVITS

---

_最后更新：2026-05-06 14:30_
