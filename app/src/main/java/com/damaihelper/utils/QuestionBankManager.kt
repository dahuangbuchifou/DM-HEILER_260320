package com.damaihelper.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

/**
 * 答题题库管理器
 * 负责加载、管理和查询答题题库
 */
class QuestionBankManager(private val context: Context) {

    private val questionBank = mutableMapOf<String, String>()
    private val gson = Gson()
    private val questionCacheMap = mutableMapOf<String, String>()

    init {
        loadQuestionBank()
    }

    /**
     * 从assets中加载题库JSON文件
     */
    private fun loadQuestionBank() {
        try {
            val inputStream = context.assets.open("question_bank.json")
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<Map<String, String>>() {}.type
            val loadedBank: Map<String, String> = gson.fromJson(reader, type)
            questionBank.putAll(loadedBank)
            reader.close()
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 根据问题查询答案
     * @param question 问题文本
     * @return 答案，如果未找到则返回null
     */
    fun queryAnswer(question: String): String? {
        // 首先尝试精确匹配
        if (questionBank.containsKey(question)) {
            return questionBank[question]
        }

        // 尝试模糊匹配（缓存结果以提高性能）
        if (questionCacheMap.containsKey(question)) {
            return questionCacheMap[question]
        }

        // 遍历题库进行模糊匹配
        for ((key, value) in questionBank) {
            if (isSimilarQuestion(question, key)) {
                questionCacheMap[question] = value
                return value
            }
        }

        return null
    }

    /**
     * 判断两个问题是否相似（简单的相似度算法）
     * @param question1 问题1
     * @param question2 问题2
     * @return 是否相似
     */
    private fun isSimilarQuestion(question1: String, question2: String): Boolean {
        // 移除空格和标点符号后进行比较
        val q1 = question1.replace(Regex("[\\s\\p{P}]"), "").lowercase()
        val q2 = question2.replace(Regex("[\\s\\p{P}]"), "").lowercase()

        // 如果完全相同，返回true
        if (q1 == q2) return true

        // 计算相似度（简单的Levenshtein距离算法）
        val similarity = calculateSimilarity(q1, q2)
        return similarity > 0.8 // 相似度大于80%时认为相似
    }

    /**
     * 计算两个字符串的相似度（Levenshtein距离）
     * @param s1 字符串1
     * @param s2 字符串2
     * @return 相似度（0-1）
     */
    private fun calculateSimilarity(s1: String, s2: String): Double {
        val maxLength = maxOf(s1.length, s2.length)
        if (maxLength == 0) return 1.0

        val distance = levenshteinDistance(s1, s2)
        return 1.0 - (distance.toDouble() / maxLength)
    }

    /**
     * 计算Levenshtein距离
     * @param s1 字符串1
     * @param s2 字符串2
     * @return 距离
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) {
            dp[i][0] = i
        }
        for (j in 0..s2.length) {
            dp[0][j] = j
        }

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // 删除
                    dp[i][j - 1] + 1,      // 插入
                    dp[i - 1][j - 1] + cost // 替换
                )
            }
        }

        return dp[s1.length][s2.length]
    }

    /**
     * 添加新的问题-答案对到题库
     * @param question 问题
     * @param answer 答案
     */
    fun addQuestion(question: String, answer: String) {
        questionBank[question] = answer
        // 清除缓存，以便重新计算相似度
        questionCacheMap.clear()
    }

    /**
     * 获取题库中的所有问题
     * @return 问题列表
     */
    fun getAllQuestions(): List<String> {
        return questionBank.keys.toList()
    }

    /**
     * 获取题库大小
     * @return 题库中的问题数量
     */
    fun getQuestionCount(): Int {
        return questionBank.size
    }
}

