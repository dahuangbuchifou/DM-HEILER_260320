package com.damaihelper.utils

import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

/**
 * 节点工具类 - 提供模糊匹配和遍历功能
 */
object NodeUtils {
    private const val TAG = "NodeUtils"

    /**
     * 模糊文本查找（支持正则表达式和相似度匹配）
     * @param rootNode 根节点
     * @param pattern 匹配模式（支持 "关键词1|关键词2" 格式）
     * @param threshold 相似度阈值（0.0-1.0，0.7 表示 70% 相似）
     * @return 匹配的节点（优先返回完全匹配）
     */
    fun findNodeByFuzzyText(
        rootNode: AccessibilityNodeInfo,
        pattern: String,
        threshold: Double = 0.7
    ): AccessibilityNodeInfo? {
        val keywords = pattern.split("|").map { it.trim() }

        // 优先查找完全匹配
        for (keyword in keywords) {
            val exactMatch = findNodeByExactText(rootNode, keyword)
            if (exactMatch != null) {
                Log.d(TAG, "✓ 完全匹配: $keyword")
                return exactMatch
            }
        }

        // 模糊匹配
        for (keyword in keywords) {
            val fuzzyMatch = findNodeByContainsText(rootNode, keyword)
            if (fuzzyMatch != null) {
                Log.d(TAG, "✓ 模糊匹配: $keyword")
                return fuzzyMatch
            }
        }

        return null
    }

    /**
     * 精确文本匹配
     */
    private fun findNodeByExactText(
        node: AccessibilityNodeInfo,
        text: String
    ): AccessibilityNodeInfo? {
        // 检查当前节点
        if (node.text?.toString() == text) {
            return node
        }

        // ContentDescription 也要检查
        if (node.contentDescription?.toString() == text) {
            return node
        }

        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByExactText(child, text)
            if (found != null) return found
            child.recycle()
        }

        return null
    }

    /**
     * 包含文本匹配（忽略大小写）
     */
    private fun findNodeByContainsText(
        node: AccessibilityNodeInfo,
        keyword: String
    ): AccessibilityNodeInfo? {
        // 检查 text
        if (node.text?.toString()?.contains(keyword, ignoreCase = true) == true) {
            return node
        }

        // 检查 contentDescription
        if (node.contentDescription?.toString()?.contains(keyword, ignoreCase = true) == true) {
            return node
        }

        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByContainsText(child, keyword)
            if (found != null) return found
            child.recycle()
        }

        return null
    }

    /**
     * 按类名查找节点
     */
    fun findNodeByClassName(
        node: AccessibilityNodeInfo,
        className: String
    ): AccessibilityNodeInfo? {
        if (node.className?.toString() == className) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByClassName(child, className)
            if (found != null) return found
            child.recycle()
        }

        return null
    }

    /**
     * 按 ID 查找节点
     */
    fun findNodeByResourceId(
        node: AccessibilityNodeInfo,
        resourceId: String
    ): AccessibilityNodeInfo? {
        if (node.viewIdResourceName == resourceId) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByResourceId(child, resourceId)
            if (found != null) return found
            child.recycle()
        }

        return null
    }

    /**
     * 查找所有可点击节点
     */
    fun findAllClickableNodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val clickableNodes = mutableListOf<AccessibilityNodeInfo>()

        if (node.isClickable) {
            clickableNodes.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            clickableNodes.addAll(findAllClickableNodes(child))
        }

        return clickableNodes
    }

    /**
     * 打印节点树（调试用）
     */
    fun printNodeTree(node: AccessibilityNodeInfo, depth: Int = 0) {
        val indent = "  ".repeat(depth)
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val className = node.className?.toString() ?: ""

        Log.d(TAG, "$indent[$className] text='$text' desc='$desc' clickable=${node.isClickable}")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            printNodeTree(child, depth + 1)
            child.recycle()
        }
    }
}