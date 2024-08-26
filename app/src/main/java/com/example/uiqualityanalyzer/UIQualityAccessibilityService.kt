package com.example.uiqualityanalyzer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

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

        when (viewType) {
            "android.widget.TextView" -> analyzeTextView(node, viewId, results)
            "android.widget.Button" -> analyzeButton(node, viewId, results)
            else -> {
                // Analyze other view types if needed
            }
        }

        // Recursive analysis of child nodes
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                analyzeNode(child, results)
            }
        }
    }

    private fun analyzeTextView(node: AccessibilityNodeInfo, viewId: String, results: StringBuilder) {
        val textColor = getTextColor(node)
        val backgroundColor = getBackgroundColor(node)
        val contrast = calculateContrast(textColor, backgroundColor)

        results.append("TextView found: ID=$viewId\n")
        results.append(" - Contrast ratio: ${"%.2f".format(contrast)}\n")

        if (contrast < 4.5) { // Example threshold for contrast ratio
            results.append(" - Issue: Contrast is too low.\n")
            results.append(" - Suggestion: Increase contrast between text and background.\n")
        }

        checkSpacing(node, results)
    }

    private fun analyzeButton(node: AccessibilityNodeInfo, viewId: String, results: StringBuilder) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val widthDp = convertPixelsToDp(bounds.width())
        val heightDp = convertPixelsToDp(bounds.height())

        results.append("Button found: ID=$viewId\n")
        results.append(" - Width: $widthDp dp, Height: $heightDp dp\n")

        if (widthDp < 48 || heightDp < 48) { // Check if either dimension is less than 48dp
            results.append(" - Issue: Touch target is too small.\n")
            results.append(" - Suggestion: Increase the button size to at least 48x48 dp.\n")
        }

        checkSpacing(node, results)
    }

    private fun checkSpacing(node: AccessibilityNodeInfo, results: StringBuilder) {
        // Placeholder for spacing check
        val nodeBounds = Rect()
        node.getBoundsInScreen(nodeBounds)

        val parentNode = node.parent ?: return
        for (i in 0 until parentNode.childCount) {
            val sibling = parentNode.getChild(i) ?: continue
            if (sibling == node) continue

            val siblingBounds = Rect()
            sibling.getBoundsInScreen(siblingBounds)

            val spacing = calculateSpacing(nodeBounds, siblingBounds)
            if (spacing < 8) { // Example threshold for spacing in dp
                results.append(" - Issue: Insufficient spacing between elements.\n")
                results.append(" - Suggestion: Increase spacing to at least 8dp.\n")
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

    private fun convertPixelsToDp(px: Int): Float {
        return px / resources.displayMetrics.density
    }

    private fun calculateContrast(textColor: Int, backgroundColor: Int): Double {
        val textLuminance = (0.2126 * Color.red(textColor) + 0.7152 * Color.green(textColor) + 0.0722 * Color.blue(textColor)) / 255.0
        val backgroundLuminance = (0.2126 * Color.red(backgroundColor) + 0.7152 * Color.green(backgroundColor) + 0.0722 * Color.blue(backgroundColor)) / 255.0

        return if (textLuminance > backgroundLuminance) {
            (textLuminance + 0.05) / (backgroundLuminance + 0.05)
        } else {
            (backgroundLuminance + 0.05) / (textLuminance + 0.05)
        }
    }

    private fun calculateSpacing(bounds1: Rect, bounds2: Rect): Int {
        val horizontalSpacing = maxOf(0, bounds2.left - bounds1.right, bounds1.left - bounds2.right)
        val verticalSpacing = maxOf(0, bounds2.top - bounds1.bottom, bounds1.top - bounds2.bottom)
        return horizontalSpacing.coerceAtLeast(verticalSpacing)
    }
}
