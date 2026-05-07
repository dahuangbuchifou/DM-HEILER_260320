# 🛠️ 新项目排查清单

_接手新项目时按顺序过一遍，避免走弯路_

---

## 📋 排查顺序（重要！）

### 第 1 步：确认路径 📍

> **先搞清楚"东西在哪"，再搞"缺什么"**

```bash
pwd                    # 当前工作目录
ls                     # 目录结构
find . -name "*.py" | head -20  # 项目文件分布
```

**检查项：**
- [ ] 项目根目录是否正确？
- [ ] 相对路径引用能否找到？（如 `from xxx import yyy` 中的 xxx 是项目自带还是 pip 包）
- [ ] 运行目录和代码期望的目录是否一致？

**常见坑：**
- Python 报 `ModuleNotFoundError` 不一定是缺包，可能是路径不对
- 项目自带模块 vs pip 包要分清
- Windows 路径（`F:\xxx`）和 Linux 路径（`/home/xxx`）要对应

---

### 第 2 步：快速验证模块 🔍

> **30 秒确认能不能找到，比装半天依赖快多了**

```bash
# 检查 Python 路径
python -c "import sys; print('\n'.join(sys.path))"

# 逐个检查项目自带模块
python -c "import split_lang; print('OK')"
python -c "import xxx_module; print('OK')"
```

**检查项：**
- [ ] `sys.path` 是否包含项目目录？
- [ ] 项目自带模块能否 import？
- [ ] 需要设置 `PYTHONPATH` 吗？

---

### 第 3 步：依赖安装 📦

> **先跑项目自带脚本，别自己手动拼**

```bash
# 优先：项目自带的一键安装
bash install.sh          # 或项目文档提到的安装方式

# 备选：requirements.txt（注意跳过不需要的）
pip install -r requirements.txt --ignore-installed

# 跳过不需要的平台依赖
pip install xxx --no-deps  # 只装指定包
```

**检查项：**
- [ ] 有没有项目自带的一键安装脚本？
- [ ] requirements.txt 是否包含不需要的平台依赖？（如 Windows 项目在 Linux 上）
- [ ] 版本冲突？（如 `torchaudio 2.7.1` vs `torch 2.11.0`）

**常见坑：**
- `pyopenjtalk` — 日语库，中文项目不需要
- 版本不匹配 — 注意依赖链的版本对应关系
- 编译失败 — 某些包需要系统库（`apt install xxx-dev`）

---

### 第 4 步：环境确认 🖥️

```bash
python --version         # Python 版本
pip list                 # 已安装包
nvidia-smi               # GPU/CUDA（如有）
which python             # Python 路径
```

**检查项：**
- [ ] Python 版本是否符合要求？
- [ ] CUDA/cuDNN 版本是否匹配？
- [ ] 是否在正确的虚拟环境中？

---

### 第 5 步：最小化测试 ✅

> **先跑通最小功能，再搞复杂集成**

```bash
# 跑项目自带的测试/示例
python test.py
python examples/hello.py

# 或者最简单的 import 测试
python -c "import project_module; print('项目可运行')"
```

**检查项：**
- [ ] 最小功能能否跑通？
- [ ] 有没有报错日志？
- [ ] 输出是否符合预期？

---

## 🎯 一句话总结

> **先确认路径，再装依赖。Python 报错不一定是缺包，可能是你站错了地方。**

---

## 📌 排查决策树

```
Python 报错 ModuleNotFoundError
    │
    ├── 是 pip 包？
    │   └── pip install xxx
    │
    ├── 是项目自带模块？
    │   ├── 检查当前工作目录 → cd 到正确位置
    │   ├── 检查 PYTHONPATH → export PYTHONPATH=项目路径
    │   └── 检查是否用了相对路径 → 改绝对路径或加 sys.path
    │
    └── 是系统库？
        └── apt install xxx-dev / yum install xxx-devel
```

---

## 📝 来源

_基于 GPT-SoVITS 集成项目的排查经验总结（2026-05-06）_

---

_最后更新：2026-05-07_
