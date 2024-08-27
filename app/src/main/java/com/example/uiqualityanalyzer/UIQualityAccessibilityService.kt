package com.example.uiqualityanalyzer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.provider.MediaStore
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.io.IOException
import java.io.OutputStream

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
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
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

        saveResultsToCsv(results.toString())

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

    private fun analyzeTextView(
        node: AccessibilityNodeInfo,
        viewId: String,
        results: StringBuilder
    ) {
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
        val nodeBounds = Rect()
        node.getBoundsInScreen(nodeBounds)

        val minEdgeSpacingDp = 8
        checkEdgeSpacing(nodeBounds, results, minEdgeSpacingDp)

        val parentNode = node.parent ?: return
        for (i in 0 until parentNode.childCount) {
            val sibling = parentNode.getChild(i) ?: continue
            if (sibling == node) continue

            val siblingBounds = Rect()
            sibling.getBoundsInScreen(siblingBounds)

            val spacing = calculateSpacing(nodeBounds, siblingBounds)
            val spacingDp = convertPixelsToDp(spacing)

            if (spacingDp < minEdgeSpacingDp) {
                results.append(" - Issue: Insufficient spacing between elements ($spacingDp dp).\n")
                results.append(" - Suggestion: Increase spacing to at least 8 dp.\n")
            }
        }
    }

    private fun checkEdgeSpacing(nodeBounds: Rect, results: StringBuilder, minEdgeSpacingDp: Int) {
        val leftSpacingDp = convertPixelsToDp(nodeBounds.left)
        val rightSpacingDp =
            convertPixelsToDp(resources.displayMetrics.widthPixels - nodeBounds.right)
        val topSpacingDp = convertPixelsToDp(nodeBounds.top)
        val bottomSpacingDp =
            convertPixelsToDp(resources.displayMetrics.heightPixels - nodeBounds.bottom)

        if (leftSpacingDp < minEdgeSpacingDp) {
            results.append(" - Issue: Element is too close to the left edge ($leftSpacingDp dp).\n")
            results.append(" - Suggestion: Increase spacing from the left edge to at least ${minEdgeSpacingDp}dp.\n")
        }

        if (rightSpacingDp < minEdgeSpacingDp) {
            results.append(" - Issue: Element is too close to the right edge ($rightSpacingDp dp).\n")
            results.append(" - Suggestion: Increase spacing from the right edge to at least ${minEdgeSpacingDp}dp.\n")
        }

        if (topSpacingDp < minEdgeSpacingDp) {
            results.append(" - Issue: Element is too close to the top edge ($topSpacingDp dp).\n")
            results.append(" - Suggestion: Increase spacing from the top edge to at least ${minEdgeSpacingDp}dp.\n")
        }

        if (bottomSpacingDp < minEdgeSpacingDp) {
            results.append(" - Issue: Element is too close to the bottom edge ($bottomSpacingDp dp).\n")
            results.append(" - Suggestion: Increase spacing from the bottom edge to at least ${minEdgeSpacingDp}dp.\n")
        }
    }

    private fun calculateSpacing(bounds1: Rect, bounds2: Rect): Int {
        val horizontalSpacing = when {
            bounds1.right <= bounds2.left -> bounds2.left - bounds1.right
            bounds2.right <= bounds1.left -> bounds1.left - bounds2.right
            else -> 0
        }

        val verticalSpacing = when {
            bounds1.bottom <= bounds2.top -> bounds2.top - bounds1.bottom
            bounds2.bottom <= bounds1.top -> bounds1.top - bounds2.bottom
            else -> 0
        }

        return horizontalSpacing.coerceAtLeast(verticalSpacing)
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
        val textLuminance =
            (0.2126 * Color.red(textColor) + 0.7152 * Color.green(textColor) + 0.0722 * Color.blue(
                textColor
            )) / 255.0
        val backgroundLuminance =
            (0.2126 * Color.red(backgroundColor) + 0.7152 * Color.green(backgroundColor) + 0.0722 * Color.blue(
                backgroundColor
            )) / 255.0

        return if (textLuminance > backgroundLuminance) {
            (textLuminance + 0.05) / (backgroundLuminance + 0.05)
        } else {
            (backgroundLuminance + 0.05) / (textLuminance + 0.05)
        }
    }

    private fun saveResultsToCsv(results: String) {
        val fileName = "ui_analysis_results.csv"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/")
        }

        val contentResolver = applicationContext.contentResolver
        val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

        if (uri != null) {
            var outputStream: OutputStream? = null
            try {
                outputStream = contentResolver.openOutputStream(uri)
                outputStream?.bufferedWriter()?.use { writer ->
                    writer.append("Element Type;ID;Issue;Suggestion\n")

                    val lines = results.split("\n")

                    var elementType = ""
                    var elementId = ""
                    var issue = ""
                    var suggestion = ""

                    for (line in lines) {
                        when {
                            line.contains("found:") -> {
                                if (elementType.isNotEmpty()) {
                                    writer.append("$elementType;$elementId;$issue;$suggestion\n")
                                }
                                elementType = line.substringBefore(" found:").trim()
                                elementId = line.substringAfter("ID=").substringBefore("\n").trim()
                                issue = ""
                                suggestion = ""
                            }
                            line.contains("Issue:") -> {
                                issue = line.substringAfter("Issue:").trim()
                            }
                            line.contains("Suggestion:") -> {
                                suggestion = line.substringAfter("Suggestion:").trim()
                            }
                        }
                    }

                    if (elementType.isNotEmpty() || elementId.isNotEmpty() || issue.isNotEmpty() || suggestion.isNotEmpty()) {
                        writer.append("$elementType;$elementId;$issue;$suggestion\n")
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                try {
                    outputStream?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }
}
