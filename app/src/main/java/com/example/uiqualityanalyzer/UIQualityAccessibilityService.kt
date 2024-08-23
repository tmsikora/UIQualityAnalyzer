package com.example.uiqualityanalyzer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class UIQualityAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events if needed
    }

    override fun onInterrupt() {
        // Handle interrupt
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START_ANALYSIS") {
            analyzeUI(rootInActiveWindow)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun analyzeUI(rootNode: AccessibilityNodeInfo?) {
        val results = StringBuilder()

        if (rootNode != null) {
            val textViewCount = countViewsOfType(rootNode, "android.widget.TextView")
            val imageViewCount = countViewsOfType(rootNode, "android.widget.ImageView")
            val buttonCount = countViewsOfType(rootNode, "android.widget.Button")

            results.append("TextViews found: $textViewCount\n")
            results.append("ImageViews found: $imageViewCount\n")
            results.append("Buttons found: $buttonCount\n")

            // Additional view types and attributes can be added here
        } else {
            results.append("Root node is null, no views analyzed.")
        }

        // Start OverlayService with the analysis result
        val overlayServiceIntent = Intent(this, OverlayService::class.java).apply {
            putExtra("analysis_result", results.toString())
        }
        startService(overlayServiceIntent)
    }

    private fun countViewsOfType(node: AccessibilityNodeInfo, viewType: String): Int {
        var count = 0
        if (node.className == viewType) {
            count++
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                count += countViewsOfType(child, viewType)
            }
        }
        return count
    }

    private fun AccessibilityNodeInfo.textColor(): String? {
        return null
    }

    private fun AccessibilityNodeInfo.textSize(): String? {
        return null
    }
}
