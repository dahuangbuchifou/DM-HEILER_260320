# 版本归档

本目录用于保存重要版本的代码快照和关键参数，便于后续分析和回溯。

## 归档命名规则

`v{版本号}_{YYYYMMDD}_{描述}/`

例如：`v1.0.0_20260321_Phase1-API 抢票实现/`

## 当前归档

| 版本 | 日期 | 描述 | Commit |
|------|------|------|--------|
| v1.0.0 | 20260321 | Phase 1 - API 抢票核心实现 | 1f0d588 |

## 归档内容

每个版本归档包含：

- `CHANGELOG.md` - 版本变更日志
- `key-parameters.md` - 关键参数配置
- `api-endpoints.md` - API 端点文档
- `source-snapshot/` - 核心源码快照（可选）

## 使用方法

```bash
# 创建新版本归档
./scripts/create-archive.sh v1.1.0 "Phase 2 - 反检测增强"

# 查看历史版本
ls -la archives/
```
