package com.damaihelper.utils

import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.max
import kotlin.math.min

object NodeUtilsExtensions {

    /**
     * 查找包含模糊匹配文本的节点
     * @param rootNode 根节点
     * @param text 待匹配文本或正则表达式
     * @param threshold 相似度阈值 (0.0 - 1.0)，用于模糊匹配
     * @return 匹配到的第一个节点，或 null
     */
    fun findNodeByFuzzyText(rootNode: AccessibilityNodeInfo, text: String, threshold: Double): AccessibilityNodeInfo? {
        val regex = Regex(text, RegexOption.IGNORE_CASE)
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(rootNode, allNodes)

        for (node in allNodes) {
            val nodeText = node.text?.toString() ?: ""
            val nodeContentDescription = node.contentDescription?.toString() ?: ""

            // 1. 正则匹配（用于“立即购买|抢票”等）
            if (regex.containsMatchIn(nodeText) || regex.containsMatchIn(nodeContentDescription)) {
                return node
            }

            // 2. 模糊匹配（用于“刘宇宁大连”等）
            if (threshold > 0.0) {
                if (calculateSimilarity(nodeText, text) >= threshold ||
                    calculateSimilarity(nodeContentDescription, text) >= threshold) {
                    return node
                }
            }
        }
        return null
    }

    private fun collectAllNodes(rootNode: AccessibilityNodeInfo, nodes: MutableList<AccessibilityNodeInfo>) {
        nodes.add(rootNode)
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i)
            if (child != null) {
                collectAllNodes(child, nodes)
            }
        }
    }

    /**
     * 计算两个字符串的相似度（基于 Levenshtein 距离）
     */
    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val maxLength = max(s1.length, s2.length)
        if (maxLength == 0) return 1.0

        val distance = levenshteinDistance(s1, s2)
        return 1.0 - distance.toDouble() / maxLength
    }

    /**
     * 计算 Levenshtein 距离
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) {
            for (j in 0..n) {
                if (i == 0) {
                    dp[i][j] = j
                } else if (j == 0) {
                    dp[i][j] = i
                } else {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
                }
            }
        }
        return dp[m][n]
    }
}

