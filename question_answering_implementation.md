## 自动化答题机制实现文档

### 概述

本文档详细说明了如何在大麦抢票辅助插件中实现自动化答题机制。该机制能够自动识别答题界面、提取问题和选项、从题库中查询答案、自动选择正确答案并提交。

### 核心组件

#### 1. QuestionBankManager（答题题库管理器）

**功能**：负责加载、管理和查询答题题库。

**主要方法**：
*   `loadQuestionBank()`：从assets中的`question_bank.json`文件加载题库。
*   `queryAnswer(question: String): String?`：根据问题查询答案，支持精确匹配和模糊匹配。
*   `addQuestion(question: String, answer: String)`：添加新的问题-答案对到题库。
*   `getAllQuestions(): List<String>`：获取题库中的所有问题。
*   `getQuestionCount(): Int`：获取题库大小。

**相似度算法**：
*   使用Levenshtein距离算法计算两个字符串的相似度。
*   相似度大于80%时认为两个问题相似。
*   支持模糊匹配，处理问题文本中的空格和标点符号差异。

#### 2. QuestionAnsweringService（答题自动化服务）

**功能**：负责识别答题界面、提取问题和选项、匹配答案、自动选择并提交。

**主要方法**：
*   `isQuestionScreenDetected(rootNode: AccessibilityNodeInfo?): Boolean`：检测当前屏幕是否显示答题界面。通过查找常见的答题相关关键词（如"请回答"、"答题"等）和提交按钮（如"提交"、"确认"等）来判断。
*   `extractQuestionInfo(rootNode: AccessibilityNodeInfo?): QuestionInfo?`：从当前屏幕提取问题和选项信息。返回包含问题文本、选项列表和正确答案的`QuestionInfo`对象。
*   `autoSelectAnswer(rootNode: AccessibilityNodeInfo?, questionInfo: QuestionInfo): Boolean`：自动选择答案。首先查找与答案匹配的选项节点，然后模拟人类行为（随机延迟）后点击该节点。
*   `submitAnswer(rootNode: AccessibilityNodeInfo?): Boolean`：提交答题。查找提交按钮并点击。

**问题提取逻辑**：
1.  首先尝试查找包含特定关键词（如"请回答"、"问题"等）的文本节点。
2.  获取该节点的父节点或兄弟节点中的文本。
3.  如果上述方法失效，尝试查找所有文本节点，选择最长的（通常是问题）。

**选项提取逻辑**：
1.  首先查找所有可点击的节点，这些通常是选项按钮。
2.  如果没有找到可点击的节点，尝试查找RadioButton或CheckBox。
3.  从节点的text属性或contentDescription属性中提取选项文本。

#### 3. question_bank.json（题库文件）

**位置**：`app/src/main/assets/question_bank.json`

**格式**：JSON格式的键值对，其中键是问题，值是答案。

**示例**：
```json
{
  "队长是谁": "G-Dragon",
  "谁会最早入伍": "T.O.P",
  "熊猫指的是谁": "太阳",
  ...
}
```

**扩展方法**：
*   用户可以通过编辑此文件来添加更多的问题-答案对。
*   App可以提供UI界面，允许用户在运行时动态添加新的问题-答案对。

### 集成步骤

#### 步骤1：初始化QuestionBankManager和QuestionAnsweringService

在无障碍服务（如`TicketGrabbingAccessibilityService`）中初始化这两个组件：

```kotlin
private lateinit var questionBankManager: QuestionBankManager
private lateinit var questionAnsweringService: QuestionAnsweringService

override fun onServiceConnected() {
    super.onServiceConnected()
    questionBankManager = QuestionBankManager(this)
    questionAnsweringService = QuestionAnsweringService(
        questionBankManager,
        HumanBehaviorSimulator()
    )
}
```

#### 步骤2：在抢票流程中检测和处理答题

在无障碍服务的事件处理方法中，定期检测是否出现答题界面：

```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    super.onAccessibilityEvent(event)
    
    val rootNode = rootInActiveWindow
    if (rootNode != null && questionAnsweringService.isQuestionScreenDetected(rootNode)) {
        // 检测到答题界面，进行自动答题
        handleQuestionScreen(rootNode)
    }
}

private suspend fun handleQuestionScreen(rootNode: AccessibilityNodeInfo) {
    // 提取问题信息
    val questionInfo = questionAnsweringService.extractQuestionInfo(rootNode)
    if (questionInfo != null) {
        // 自动选择答案
        if (questionAnsweringService.autoSelectAnswer(rootNode, questionInfo)) {
            // 提交答题
            questionAnsweringService.submitAnswer(rootNode)
        }
    }
}
```

#### 步骤3：处理未知问题（可选）

对于题库中不存在的问题，可以采取以下策略：

1.  **提示用户**：在App的UI中显示未知问题，提示用户手动输入答案，并将其添加到题库。
2.  **集成OCR和AI**（高级）：使用Google ML Kit的文本识别功能识别问题文本，然后通过NLP模型或API进行智能分析和作答。

### 题库管理

#### 添加新问题

**方法1：编辑JSON文件**
直接编辑`app/src/main/assets/question_bank.json`文件，添加新的问题-答案对。

**方法2：运行时添加**
通过调用`QuestionBankManager.addQuestion()`方法在运行时添加新问题：

```kotlin
questionBankManager.addQuestion("新问题", "新答案")
```

**方法3：用户界面**
在App的设置或管理页面中提供UI界面，允许用户添加、编辑和删除问题-答案对。

#### 更新题库

定期更新题库以包含最新的大麦网答题问题。可以：
1.  收集用户反馈的新问题。
2.  定期发布App更新，包含新的题库数据。
3.  实现在线题库同步功能，从服务器定期更新题库。

### 性能优化

1.  **缓存**：`QuestionBankManager`使用`questionCacheMap`缓存已匹配的问题，避免重复计算相似度。
2.  **延迟加载**：题库在`QuestionBankManager`初始化时加载，之后保存在内存中，提高查询速度。
3.  **限制搜索范围**：在提取问题和选项时，可以限制搜索范围到答题界面的特定区域，而不是整个屏幕。

### 局限性与改进方向

#### 当前局限性

1.  **题库依赖**：自动答题的成功率完全依赖于题库的完整性。如果题库中没有某个问题，则无法自动作答。
2.  **文本识别精度**：从UI节点提取的文本可能存在格式差异（如空格、标点符号等），可能导致匹配失败。
3.  **动态问题**：某些问题可能包含动态内容（如时间、日期等），难以预先添加到题库中。

#### 改进方向

1.  **集成OCR**：使用Google ML Kit或Tesseract等OCR库，从屏幕截图中识别问题文本，提高识别精度。
2.  **集成NLP和AI**：对于未知问题，通过NLP模型或调用AI API（如OpenAI的GPT模型）进行智能分析和作答。
3.  **在线题库同步**：实现从服务器定期同步题库的功能，确保题库始终保持最新。
4.  **用户贡献**：建立社区机制，允许用户贡献新的问题-答案对，共同维护题库。

### 测试建议

1.  **单元测试**：测试`QuestionBankManager`的查询和相似度计算功能。
2.  **集成测试**：在模拟的答题界面上测试`QuestionAnsweringService`的问题提取和自动答题功能。
3.  **实战测试**：在实际的大麦App上进行测试，收集用户反馈，不断改进。

### 总结

自动化答题机制是大麦抢票辅助插件的关键功能之一。通过`QuestionBankManager`和`QuestionAnsweringService`的组合，我们能够实现自动识别、提取、匹配和作答的完整流程。随着题库的不断完善和AI技术的集成，这一机制将变得越来越强大，最终实现对大麦网答题防护的有效突破。

