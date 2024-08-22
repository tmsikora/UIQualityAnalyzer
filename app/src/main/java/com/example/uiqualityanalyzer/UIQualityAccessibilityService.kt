package com.example.uiqualityanalyzer

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.pow

class UIQualityAccessibilityService : AccessibilityService() {

    private val issues = mutableListOf<String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        analyzeUIElements(rootNode)
    }

    private fun analyzeUIElements(node: AccessibilityNodeInfo) {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                checkAccessibilityIssues(child)
                analyzeUIElements(child)
            }
        }
    }

    private fun checkAccessibilityIssues(node: AccessibilityNodeInfo) {
        val contentDescription = node.contentDescription
        val text = node.text

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val width = bounds.width()
        val height = bounds.height()

        if (contentDescription == null && text == null) {
            reportIssue("Element bez opisu", node)
        }

        if (width < 48 || height < 48) {
            reportIssue("Element za mały", node)
        }

        if (text != null) {
            val textColor = getTextColor(node) ?: Color.BLACK
            val backgroundColor = getBackgroundColor(node) ?: Color.WHITE

            val contrastRatio = calculateContrastRatio(textColor, backgroundColor)
            if (contrastRatio < 4.5) {
                reportIssue("Niewystarczający kontrast: $contrastRatio", node)
            }

            checkTextSize(node)
        }

        checkHierarchyDepth(node)
    }

    private fun reportIssue(description: String, node: AccessibilityNodeInfo) {
        val issue = "Problem: $description w elemencie ${node.viewIdResourceName}"
        Log.d("AccessibilityIssue", issue)
        issues.add(issue)
    }

    fun getIssues(): List<String> {
        return issues
    }

    private fun calculateLuminance(color: Int): Double {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0

        val rLuminance = if (r <= 0.03928) r / 12.92 else ((r + 0.055) / 1.055).pow(2.4)
        val gLuminance = if (g <= 0.03928) g / 12.92 else ((g + 0.055) / 1.055).pow(2.4)
        val bLuminance = if (b <= 0.03928) b / 12.92 else ((b + 0.055) / 1.055).pow(2.4)

        return 0.2126 * rLuminance + 0.7152 * gLuminance + 0.0722 * bLuminance
    }

    private fun calculateContrastRatio(color1: Int, color2: Int): Double {
        val luminance1 = calculateLuminance(color1)
        val luminance2 = calculateLuminance(color2)
        return if (luminance1 > luminance2) {
            (luminance1 + 0.05) / (luminance2 + 0.05)
        } else {
            (luminance2 + 0.05) / (luminance1 + 0.05)
        }
    }

    private fun checkTextSize(node: AccessibilityNodeInfo) {
        val textSize = getTextSize(node)
        if (textSize != null && textSize < 12) {
            reportIssue("Tekst jest zbyt mały: ${textSize}sp", node)
        }
    }

    private fun checkHierarchyDepth(node: AccessibilityNodeInfo) {
        var depth = 0
        var parent = node.parent
        while (parent != null) {
            depth++
            parent = parent.parent
        }

        if (depth > 5) {
            reportIssue("Element jest zbyt głęboko zagnieżdżony: $depth poziomów", node)
        }
    }

    private fun getTextColor(node: AccessibilityNodeInfo): Int? {
        return null
    }

    private fun getBackgroundColor(node: AccessibilityNodeInfo): Int? {
        return null
    }

    private fun getTextSize(node: AccessibilityNodeInfo): Float? {
        return null
    }

    override fun onInterrupt() {
    }
}
