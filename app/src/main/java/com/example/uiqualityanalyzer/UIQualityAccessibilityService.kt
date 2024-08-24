package com.example.uiqualityanalyzer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Color
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

class UIQualityAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events if needed
    }

    override fun onInterrupt() {
        // Handle interrupt
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START_ANALYSIS") {
            val rootNode = rootInActiveWindow
            analyzeUI(rootNode)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun analyzeUI(rootNode: AccessibilityNodeInfo?) {
        val results = StringBuilder()

        if (rootNode != null) {
            analyzeNode(rootNode, results)
        } else {
            results.append("Root node is null, no views analyzed.")
        }

        // Start OverlayService with the analysis result
        val overlayServiceIntent = Intent(this, OverlayService::class.java).apply {
            putExtra("analysis_result", results.toString())
        }
        startService(overlayServiceIntent)
    }

    private fun analyzeNode(node: AccessibilityNodeInfo, results: StringBuilder) {
        val viewType = node.className.toString()
        val viewId = node.viewIdResourceName ?: "N/A"

        // Check for TextViews
        if (viewType == "android.widget.TextView") {
            val textColor = getTextColor(node)
            val backgroundColor = getBackgroundColor(node)
            val contrast = calculateContrast(textColor, backgroundColor)

            results.append("TextView found: ID=$viewId\n")
            results.append(" - Contrast ratio: ${"%.2f".format(contrast)}\n")

            if (contrast < 4.5) { // Example threshold for contrast ratio
                results.append(" - Issue: Contrast is too low.\n")
                results.append(" - Suggestion: Increase contrast between text and background.\n")
            }
        }

        // Check for Button views
        if (viewType == "android.widget.Button") {
            val minTouchSize = getMinimumTouchSize(node)
            results.append("Button found: ID=$viewId\n")
            results.append(" - Minimum touch size: $minTouchSize dp\n")

            if (minTouchSize < 48) { // Example threshold for touch size
                results.append(" - Issue: Touch target is too small.\n")
                results.append(" - Suggestion: Increase the touch target size to improve accessibility.\n")
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                analyzeNode(child, results)
            }
        }
    }

    private fun getTextColor(node: AccessibilityNodeInfo): Int {
        // Placeholder for text color retrieval
        return Color.BLACK // Default value, adjust as needed
    }

    private fun getBackgroundColor(node: AccessibilityNodeInfo): Int {
        // Placeholder for background color retrieval
        return Color.WHITE // Default value, adjust as needed
    }

    private fun getMinimumTouchSize(node: AccessibilityNodeInfo): Float {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        return bounds.height().toFloat() // Using height as a proxy for touch size
    }

    private fun calculateContrast(textColor: Int, backgroundColor: Int): Double {
        // Simple contrast calculation (using luminance values)
        val textLuminance = (0.2126 * Color.red(textColor) + 0.7152 * Color.green(textColor) + 0.0722 * Color.blue(textColor)) / 255.0
        val backgroundLuminance = (0.2126 * Color.red(backgroundColor) + 0.7152 * Color.green(backgroundColor) + 0.0722 * Color.blue(backgroundColor)) / 255.0
        val contrast = abs(textLuminance - backgroundLuminance) / textLuminance.coerceAtMost(
            backgroundLuminance
        )
        return contrast
    }
}
