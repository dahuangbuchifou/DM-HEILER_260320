# 第8阶段：综合测试与模拟环境设计方案

## 概述

本阶段旨在为**大麦抢票助手增强版**设计一套全面的测试和模拟方案，以验证新集成的**智能决策与策略切换引擎**（Stage 7）以及所有前期模块（Stage 1-6）的稳定性和有效性。由于应用是基于Android平台且与实时服务（大麦网）交互，测试重点将放在**模拟关键外部反馈**和**验证内部逻辑**上。

## 1. 测试目标

1.  **策略切换验证**：确保智能决策引擎能够根据模拟的实时反馈（API失败、UI阻塞、风控触发）正确选择和切换抢票策略（API -> UI，UI -> API，或混合模式）。
2.  **核心功能验证**：确保所有核心模块（行为模拟、反检测、答题、会话管理）在不同策略下都能正常工作。
3.  **异常处理验证**：验证应用对各种异常情况（网络延迟、服务器错误、"太火爆"、验证码/答题失败）的鲁棒性。
4.  **性能验证**：评估抢票策略的响应速度和资源消耗（概念性）。

## 2. 关键测试场景与模拟设计

| 场景编号 | 场景描述 | 触发条件模拟 | 预期结果 | 涉及模块 |
| :---: | :--- | :--- | :--- | :--- |
| **T1** | **理想抢票** | API接口返回200，并直接进入支付页面。 | 决策引擎选择**API抢票**，抢票成功。 | 决策引擎, API抢票, SessionManager |
| **T2** | **API风控** | API接口返回403 Forbidden或特定风控错误码（如"签名校验失败"）。 | 决策引擎迅速切换到**UI自动化**，并尝试通过UI完成抢票。 | 决策引擎, API抢票, UI自动化, AntiDetectionModule |
| **T3** | **UI阻塞** | UI自动化尝试点击时，发现页面元素索引发生变化或出现新的弹窗（模拟UI变化）。 | UI自动化模块触发**动态UI识别**，若失败，决策引擎切换到**API抢票**。 | 决策引擎, UI自动化, Dynamic UI Module |
| **T4** | **答题验证** | 页面出现模拟的抢票问答弹窗。 | **QuestionAnsweringService** 自动识别问题，从 `question_bank.json` 中查找答案并输入，继续抢票流程。 | UI自动化, QuestionAnsweringService |
| **T5** | **"太火爆"** | 抢票过程中，服务器返回"当前排队人数过多"或"太火爆了"的提示。 | **TooManyPeopleHandler** 模块启动高频并发轮询，并应用指数退避重试策略。 | TooManyPeopleHandler, API/UI策略 |
| **T6** | **API/UI混合** | 决策引擎选择**混合模式**。API请求失败，但UI自动化成功。 | 策略平滑切换，最终通过UI完成抢票。 | 决策引擎, HybridStrategy |
| **T7** | **反检测失效** | 模拟设备指纹被识别（例如，API请求头缺少关键字段或User-Agent异常）。 | **AntiDetectionModule** 自动轮换指纹，并尝试重新发起请求。 | AntiDetectionModule, DeviceFingerprintSpoofing |
| **T8** | **精确时间** | 模拟抢票时间到达，NTP时间同步模块确保请求在毫秒级精确时间点发出。 | 请求时间与模拟的开票时间偏差在可接受范围内（< 100ms）。 | NTP Time Sync, API/UI策略 |

## 3. 模拟环境准备（概念性）

由于无法在沙箱中运行Android应用，我们提供以下**用户侧**的模拟和测试建议：

### 3.1 单元测试与集成测试（开发侧）

*   **Mocking**：在Android Studio环境中，使用 **MockWebServer** (OkHttp/Retrofit) 模拟大麦API的各种响应（T1, T2, T5）。
*   **Shadowing**：使用 **Robolectric** 模拟Android框架组件，测试 `StrategyDecisionEngine`、`QuestionAnsweringService` 等核心Kotlin/Java逻辑。
*   **Instrumentation Tests**：使用 **Espresso** 编写UI测试，验证 `TicketGrabbingAccessibilityService` 在模拟设备上的行为和元素识别能力（T3, T4）。

### 3.2 真实环境模拟（用户侧）

*   **小额商品测试**：在非热门商品的抢购中进行低风险的真实环境测试，验证流程的完整性。
*   **模拟网络环境**：使用网络工具（如Charles Proxy, Fiddler）模拟高延迟、丢包等网络异常，测试应用的鲁棒性。
*   **风控触发测试**：通过快速、重复的请求故意触发风控（T2），观察决策引擎的切换行为。

## 4. 性能数据收集与分析指标

在执行测试（Stage 9）时，应用应记录以下关键数据：

| 指标名称 | 描述 | 理想值/目标 | 涉及模块 |
| :--- | :--- | :--- | :--- |
| **策略切换时间** | 从检测到失败到切换到新策略所需时间。 | < 500ms | StrategyDecisionEngine |
| **API请求成功率** | API策略在测试中的成功率。 | > 70% | APIGrabbingStrategy |
| **UI操作成功率** | UI自动化在测试中的成功率（排除答题）。 | > 80% | UIAutomationStrategy |
| **平均响应时间** | 策略执行一次抢票操作的平均耗时。 | 越低越好 | All Strategies |
| **答题准确率** | QuestionAnsweringService 的答题准确率。 | 100% (基于题库) | QuestionAnsweringService |
| **时间同步偏差** | 应用内部时间与NTP服务器时间的偏差。 | < 50ms | NTP Time Sync |

## 5. 结论

通过上述设计方案，我们能够在Android Studio环境中对抢票助手进行全面、系统的测试。重点在于利用Mocking和Instrumentation Tests来验证复杂的内部逻辑和策略切换机制，确保应用在面对大麦网的各种反制措施时，能够灵活、智能地做出反应。

