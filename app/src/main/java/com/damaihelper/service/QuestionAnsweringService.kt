package com.damaihelper.service

import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.damaihelper.utils.HumanBehaviorSimulator
import com.damaihelper.utils.QuestionBankManager
import com.damaihelper.utils.NodeUtils
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 答题验证服务
 */
class QuestionAnsweringService(
    private val questionBankManager: QuestionBankManager,
    private val humanBehaviorSimulator: HumanBehaviorSimulator
) {
    private val TAG = "QuestionAnswering"

    data class QuestionInfo(
        val question: String,
        val options: List<String>,
        val correctAnswer: String?
    )

    /**
     * 提取问题信息
     */
    fun extractQuestionInfo(rootNode: AccessibilityNodeInfo): QuestionInfo? {
        // 简化实现：查找问题文本
        val questionNode = NodeUtils.findNodeByFuzzyText(rootNode, "问题|题目|验证", 0.7)
        if (questionNode == null) return null

        val question = questionNode.text?.toString() ?: return null

        // 查找选项
        val options = mutableListOf<String>()
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            val text = child.text?.toString()
            if (text != null && text.matches(Regex("[A-D][.、:].*"))) {
                options.add(text)
            }
            child.recycle()
        }

        // 从题库查找答案（修复方法名）
        val correctAnswer = questionBankManager.queryAnswer(question)

        return QuestionInfo(question, options, correctAnswer)
    }

    /**
     * 自动选择答案
     */
    suspend fun autoSelectAnswer(
        rootNode: AccessibilityNodeInfo,
        questionInfo: QuestionInfo
    ): Boolean {
        val answer = questionInfo.correctAnswer ?: return false

        // ✅ 修复：使用协程的 delay，不调用 simulateRandomDelay
        delay(Random.nextLong(200L, 500L))

        val answerNode = NodeUtils.findNodeByFuzzyText(rootNode, answer, 0.8)
        if (answerNode != null) {
            Log.d(TAG, "✓ 找到答案: $answer")
            humanBehaviorSimulator.performClick(answerNode)
            delay(300L)
            return true
        }

        return false
    }

    /**
     * 提交答案
     */
    suspend fun submitAnswer(rootNode: AccessibilityNodeInfo): Boolean {
        delay(Random.nextLong(200L, 500L))

        val submitButton = NodeUtils.findNodeByFuzzyText(rootNode, "提交|确定|确认", 0.9)
        if (submitButton != null) {
            humanBehaviorSimulator.performClick(submitButton)
            delay(500L)
            return true
        }
        return false
    }
}