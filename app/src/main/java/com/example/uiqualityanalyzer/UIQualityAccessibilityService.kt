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
        val resultsWithIssues = StringBuilder()

        val fileName = "ui_analysis_results.csv"
        val fileLocation = "Documents/$fileName"
        resultsWithIssues.append("Analysis results have been saved to file: $fileLocation\n\n")

        if (rootNode != null) {
            analyzeNode(rootNode, results, resultsWithIssues)
        } else {
            results.append("Root node is null, no views analyzed.")
        }

        saveResultsToCsv(results.toString())

        // Start OverlayService with the analysis result
        val overlayServiceIntent = Intent(this, OverlayService::class.java).apply {
            putExtra("analysis_result", resultsWithIssues.toString())
        }
        startService(overlayServiceIntent)
    }

    private fun analyzeNode(node: AccessibilityNodeInfo, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val viewType = node.className.toString()
        val viewId = node.viewIdResourceName ?: "N/A"

        when (viewType) {
            "android.widget.TextView" -> analyzeTextView(node, viewId, results, resultsWithIssues)
            "android.widget.EditText" -> analyzeEditText(node, viewId, results, resultsWithIssues)
            "android.widget.Button" -> analyzeButton(node, viewId, results, resultsWithIssues)
            "android.widget.ImageView" -> analyzeImageView(node, viewId, results, resultsWithIssues)
            "android.widget.ImageButton" -> analyzeImageButton(node, viewId, results, resultsWithIssues)
            "android.widget.CheckBox" -> analyzeCheckBox(node, viewId, results, resultsWithIssues)
            else -> {
                // Analyze other view types if needed
            }
        }

        // Recursive analysis of child nodes
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                analyzeNode(child, results, resultsWithIssues)
            }
        }
    }

    private fun analyzeTextView(node: AccessibilityNodeInfo, viewId: String, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val text = node.text?.toString().orEmpty()

        results.append("TextView found: ID=$viewId\n")
        results.append(" - Text: $text\n")

        if (text.isEmpty()) {
            results.append(" - Issue: TextView is empty.\n")
            results.append(" - Suggestion: Add descriptive text to the TextView.\n")
            resultsWithIssues.append("TextView: ID=$viewId\n")
            resultsWithIssues.append(" - Issue: TextView is empty.\n")
            resultsWithIssues.append(" - Suggestion: Add descriptive text to the TextView.\n")
        }
    }

    private fun analyzeEditText(node: AccessibilityNodeInfo, viewId: String, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val hint = node.text ?: node.hintText

        results.append("EditText found: ID=$viewId\n")

        if (hint.isNullOrEmpty()) {
            results.append(" - Issue: EditText is missing a hint.\n")
            results.append(" - Suggestion: Add a hint to the EditText to provide context to users.\n")
            resultsWithIssues.append("EditText: ID=$viewId\n")
            resultsWithIssues.append(" - Issue: EditText is missing a hint.\n")
            resultsWithIssues.append(" - Suggestion: Add a hint to the EditText to provide context to users.\n")
        }
    }

    private fun analyzeButton(node: AccessibilityNodeInfo, viewId: String, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val widthDp = convertPixelsToDp(bounds.width())
        val heightDp = convertPixelsToDp(bounds.height())

        results.append("Button found: ID=$viewId\n")
        results.append(" - Width: $widthDp dp, Height: $heightDp dp\n")

        if (widthDp < 48 || heightDp < 48) { // Check if either dimension is less than 48dp
            results.append(" - Issue: Touch target is too small.\n")
            results.append(" - Suggestion: Increase the button size to at least 48x48 dp.\n")
            resultsWithIssues.append("Button: ID=$viewId\n")
            resultsWithIssues.append(" - Issue: Touch target is too small.\n")
            resultsWithIssues.append(" - Suggestion: Increase the button size to at least 48x48 dp.\n")
        }

        checkSpacing(node, viewId, results, resultsWithIssues)
    }

    private fun checkSpacing(node: AccessibilityNodeInfo, viewId: String, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val nodeBounds = Rect()
        node.getBoundsInScreen(nodeBounds)

        val minEdgeSpacingDp = 8
        checkEdgeSpacing(nodeBounds, viewId, results, resultsWithIssues, minEdgeSpacingDp)

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
                resultsWithIssues.append("Button: ID=$viewId\n")
                resultsWithIssues.append(" - Issue: Insufficient spacing between elements ($spacingDp dp).\n")
                resultsWithIssues.append(" - Suggestion: Increase spacing to at least 8 dp.\n")
            }
        }
    }

    private fun checkEdgeSpacing(nodeBounds: Rect, viewId: String, results: StringBuilder, resultsWithIssues: StringBuilder, minEdgeSpacingDp: Int) {
        val leftSpacingDp = convertPixelsToDp(nodeBounds.left)
        val rightSpacingDp =
            convertPixelsToDp(resources.displayMetrics.widthPixels - nodeBounds.right)
        val topSpacingDp = convertPixelsToDp(nodeBounds.top)
        val bottomSpacingDp =
            convertPixelsToDp(resources.displayMetrics.heightPixels - nodeBounds.bottom)

        if (leftSpacingDp < minEdgeSpacingDp) {
            results.append(" - Issue: Element is too close to the left edge ($leftSpacingDp dp).\n")
            results.append(" - Suggestion: Increase spacing from the left edge to at least ${minEdgeSpacingDp}dp.\n")
            resultsWithIssues.append("Button: ID=$viewId\n")
            resultsWithIssues.append(" - Issue: Element is too close to the left edge ($leftSpacingDp dp).\n")
            resultsWithIssues.append(" - Suggestion: Increase spacing from the left edge to at least ${minEdgeSpacingDp}dp.\n")
        }

        if (rightSpacingDp < minEdgeSpacingDp) {
            results.append(" - Issue: Element is too close to the right edge ($rightSpacingDp dp).\n")
            results.append(" - Suggestion: Increase spacing from the right edge to at least ${minEdgeSpacingDp}dp.\n")
            resultsWithIssues.append(" - Issue: Element is too close to the right edge ($rightSpacingDp dp).\n")
            resultsWithIssues.append(" - Suggestion: Increase spacing from the right edge to at least ${minEdgeSpacingDp}dp.\n")
        }

        if (topSpacingDp < minEdgeSpacingDp) {
            results.append(" - Issue: Element is too close to the top edge ($topSpacingDp dp).\n")
            results.append(" - Suggestion: Increase spacing from the top edge to at least ${minEdgeSpacingDp}dp.\n")
            resultsWithIssues.append(" - Issue: Element is too close to the top edge ($topSpacingDp dp).\n")
            resultsWithIssues.append(" - Suggestion: Increase spacing from the top edge to at least ${minEdgeSpacingDp}dp.\n")
        }

        if (bottomSpacingDp < minEdgeSpacingDp) {
            results.append(" - Issue: Element is too close to the bottom edge ($bottomSpacingDp dp).\n")
            results.append(" - Suggestion: Increase spacing from the bottom edge to at least ${minEdgeSpacingDp}dp.\n")
            resultsWithIssues.append(" - Issue: Element is too close to the bottom edge ($bottomSpacingDp dp).\n")
            resultsWithIssues.append(" - Suggestion: Increase spacing from the bottom edge to at least ${minEdgeSpacingDp}dp.\n")
        }
    }

    private fun convertPixelsToDp(px: Int): Float {
        return px / resources.displayMetrics.density
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

    private fun analyzeImageView(node: AccessibilityNodeInfo, viewId: String, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val contentDescription = node.contentDescription?.toString().orEmpty()

        results.append("ImageView found: ID=$viewId\n")

        if (contentDescription.isEmpty()) {
            results.append(" - Issue: Missing content description.\n")
            results.append(" - Suggestion: Add a content description for accessibility.\n")
            resultsWithIssues.append("ImageView: ID=$viewId\n")
            resultsWithIssues.append(" - Issue: Missing content description.\n")
            resultsWithIssues.append(" - Suggestion: Add a content description for accessibility.\n")
        }
    }

    private fun analyzeImageButton(node: AccessibilityNodeInfo, viewId: String, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val contentDescription = node.contentDescription?.toString().orEmpty()

        results.append("ImageButton found: ID=$viewId\n")

        if (contentDescription.isEmpty()) {
            results.append(" - Issue: Missing content description.\n")
            results.append(" - Suggestion: Add a content description for accessibility.\n")
            resultsWithIssues.append("ImageButton: ID=$viewId\n")
            resultsWithIssues.append(" - Issue: Missing content description.\n")
            resultsWithIssues.append(" - Suggestion: Add a content description for accessibility.\n")
        }
    }

    private fun analyzeCheckBox(node: AccessibilityNodeInfo, viewId: String, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val contentDescription = node.contentDescription?.toString().orEmpty()

        results.append("CheckBox found: ID=$viewId\n")

        if (contentDescription.isEmpty()) {
            results.append(" - Issue: Missing content description.\n")
            results.append(" - Suggestion: Add a content description for accessibility.\n")
            resultsWithIssues.append("CheckBox: ID=$viewId\n")
            resultsWithIssues.append(" - Issue: Missing content description.\n")
            resultsWithIssues.append(" - Suggestion: Add a content description for accessibility.\n")
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
