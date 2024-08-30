package com.example.uiqualityanalyzer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.Rect
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.IOException
import java.io.OutputStream
import kotlin.math.max

class UIQualityAccessibilityService : AccessibilityService() {

    private val touchAreaScores = mutableListOf<Float>()
    private val elementSpacingScores = mutableListOf<Float>()
    private val edgeSpacingScores = mutableListOf<Float>()
    private val contentDescriptionScores = mutableListOf<Float>()
    private val hintTextScores = mutableListOf<Float>()

    // Set coefficients
    private var touchAreaScoreCoefficient = 0.3f
    private var elementSpacingScoreCoefficient = 0.2f
    private var edgeSpacingScoreCoefficient = 0.2f
    private var contentDescriptionScoreCoefficient = 0.15f
    private var hintTextScoreCoefficient = 0.15f
    private val numberOfCoefficients = 5

    private var touchAreaScore = 0.0f
    private var elementSpacingScore = 0.0f
    private var edgeSpacingScore = 0.0f
    private var contentDescriptionScore = 0.0f
    private var hintTextScore = 0.0f
    private var finalScore = 0.0f
    private var finalMinimalScore = 0.0f

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

    @SuppressLint("DefaultLocale")
    private fun analyzeUI(rootNode: AccessibilityNodeInfo?) {
        // Clear previous scores
        touchAreaScores.clear()
        elementSpacingScores.clear()
        edgeSpacingScores.clear()
        contentDescriptionScores.clear()
        hintTextScores.clear()
        touchAreaScore = 0.0f
        elementSpacingScore = 0.0f
        edgeSpacingScore = 0.0f
        contentDescriptionScore = 0.0f
        hintTextScore = 0.0f
        finalScore = 0.0f
        finalMinimalScore = 0.0f

        val results = StringBuilder()
        val resultsWithIssues = StringBuilder()

        val fileName = "ui_analysis_results.csv"
        val fileLocation = "Documents/$fileName"
        resultsWithIssues.append("‣ Both scores are in 0-1 scale, where 0 is the lowest score and 1 is the highest.\n\n\n")
        resultsWithIssues.append("‣ Analysis results have been saved to file: $fileLocation\n\n\n")
        resultsWithIssues.append("List of identified issues:\n")

        if (rootNode != null) {
            analyzeNode(rootNode, results, resultsWithIssues)
        } else {
            results.append("Root node is null, no views analyzed.")
        }

        // Calculate scores
        touchAreaScore = calculateTouchAreaScore()
        elementSpacingScore = calculateElementSpacingScore()
        edgeSpacingScore = calculateEdgeSpacingScore()
        contentDescriptionScore = calculateContentDescriptionScore()
        hintTextScore = calculateHintTextScore()

        // Calculate final score
        finalScore = (
                (touchAreaScore * touchAreaScoreCoefficient) +
                        (elementSpacingScore * elementSpacingScoreCoefficient) +
                        (edgeSpacingScore * edgeSpacingScoreCoefficient) +
                        (contentDescriptionScore * contentDescriptionScoreCoefficient) +
                        (hintTextScore * hintTextScoreCoefficient)
                )

        val minTouchAreaScore = touchAreaScores.minOrNull() ?: 0.0f
        val minElementSpacingScore = elementSpacingScores.minOrNull() ?: 0.0f
        val minEdgeSpacingScore = edgeSpacingScores.minOrNull() ?: 0.0f
        val minContentDescriptionScore = contentDescriptionScores.minOrNull() ?: 0.0f
        val minHintTextScore = hintTextScores.minOrNull() ?: 0.0f

        // Calculate final minimal score
        finalMinimalScore = (
                (minTouchAreaScore * touchAreaScoreCoefficient) +
                        (minElementSpacingScore * elementSpacingScoreCoefficient) +
                        (minEdgeSpacingScore * edgeSpacingScoreCoefficient) +
                        (minContentDescriptionScore * contentDescriptionScoreCoefficient) +
                        (minHintTextScore * hintTextScoreCoefficient)
                )

        Log.i("UIQualityAnalyzer", "touchAreaScore: $touchAreaScore%")
        Log.i("UIQualityAnalyzer", "elementSpacingScore: $elementSpacingScore%")
        Log.i("UIQualityAnalyzer", "edgeSpacingScore: $edgeSpacingScore%")
        Log.i("UIQualityAnalyzer", "contentDescriptionScore: $contentDescriptionScore%")
        Log.i("UIQualityAnalyzer", "hintTextScore: $hintTextScore%")

        val formattedMinimalScore = String.format("%.3f", finalMinimalScore)
        results.append(0, "UI Quality Minimal Score: $formattedMinimalScore%\n\n")
        resultsWithIssues.insert(0, "UI Quality Minimal Score: $formattedMinimalScore\n(calculated from multiplication of lowest quality parameter values and their coefficients)\n\n")
        Log.i("UIQualityAnalyzer", "Final UI Quality Minimal Score: $finalMinimalScore%")

        val formattedScore = String.format("%.3f", finalScore)
        results.append(0, "UI Quality Score: $formattedScore%\n\n")
        resultsWithIssues.insert(0, "UI Quality Score: $formattedScore\n(calculated from multiplication of average quality parameter scores and their coefficients)\n\n")
        Log.i("UIQualityAnalyzer", "Final UI Quality Score: $finalScore%")

        saveResultsToCsv(results.toString())

        // Start OverlayService with the analysis result
        val overlayServiceIntent = Intent(this, OverlayService::class.java).apply {
            putExtra("analysis_result", resultsWithIssues.toString())
        }
        startService(overlayServiceIntent)
    }

    private fun analyzeNode(node: AccessibilityNodeInfo, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val viewType = node.className.toString()
        val viewTypeName = node.className.toString().substringAfterLast('.')
        val viewId = node.viewIdResourceName ?: "N/A"

        when (viewType) {
            "android.widget.TextView" -> analyzeTextView(node, viewId, results, resultsWithIssues)
            "android.widget.EditText" -> analyzeEditText(node, viewId, results, resultsWithIssues)
            "android.widget.Button" -> analyzeButton(node, viewId, viewTypeName, results, resultsWithIssues)
            "android.widget.ImageView" -> analyzeImageView(node, viewId, results, resultsWithIssues)
            "android.widget.ImageButton" -> analyzeImageButton(node, viewId, viewTypeName, results, resultsWithIssues)
            "android.widget.CheckBox" -> analyzeCheckBox(node, viewId, viewTypeName, results, resultsWithIssues)
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
            resultsWithIssues.append("• TextView: ID=$viewId\n")
            resultsWithIssues.append(" - Issue: TextView is empty.\n")
            resultsWithIssues.append(" - Suggestion: Add descriptive text to the text attribute.\n")
        }
    }

    private fun analyzeButton(node: AccessibilityNodeInfo, viewId: String, viewTypeName: String, results: StringBuilder, resultsWithIssues: StringBuilder) {
        results.append("Button found: ID=$viewId\n")

        checkTouchArea(node, viewId, viewTypeName, results, resultsWithIssues)
        checkSpacing(node, viewId, viewTypeName, results, resultsWithIssues)
    }

    private fun checkTouchArea(node: AccessibilityNodeInfo, viewId: String, viewTypeName: String, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val widthDp = convertPixelsToDp(bounds.width())
        val heightDp = convertPixelsToDp(bounds.height())

        results.append(" - Width: $widthDp dp, Height: $heightDp dp\n")

        val areaScore = if (widthDp >= 48 && heightDp >= 48) 1.0f else (widthDp * heightDp / (48 * 48))
        touchAreaScores.add(areaScore)

        if (widthDp < 48 || heightDp < 48) { // Check if either dimension is less than 48dp
            results.append(" - Issue: Touch target is too small (${widthDp}x${heightDp} dp).\n")
            results.append(" - Suggestion: Increase the button size to at least 48x48 dp.\n")
            resultsWithIssues.append("• $viewTypeName: ID=$viewId\n")
            resultsWithIssues.append(" - Issue: Touch target is too small (${widthDp}x${heightDp} dp).\n")
            resultsWithIssues.append(" - Suggestion: Increase the button size to at least 48x48 dp.\n")
        }
    }

    private fun checkSpacing(node: AccessibilityNodeInfo, viewId: String, viewTypeName: String, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val minElementSpacingDp = 8

        checkEdgeSpacing(bounds, viewId, viewTypeName, results, resultsWithIssues)

        val parentNode = node.parent ?: return
        val spacingScores = mutableListOf<Float>()

        for (i in 0 until parentNode.childCount) {
            val sibling = parentNode.getChild(i) ?: continue
            if (sibling == node) continue

            val siblingBounds = Rect()
            sibling.getBoundsInScreen(siblingBounds)

            val spacing = calculateSpacing(bounds, siblingBounds)
            val spacingDp = convertPixelsToDp(spacing)

            val spacingScore = if (spacingDp >= minElementSpacingDp) 1.0f else (spacingDp / minElementSpacingDp)
            spacingScores.add(spacingScore)
            Log.d("ElementSpacingScores", "Sibling spacing: $spacingDp dp, Score: $spacingScore")

            if (spacingDp < minElementSpacingDp) {
                results.append(" - Issue: Insufficient spacing between elements ($spacingDp dp).\n")
                results.append(" - Suggestion: Increase spacing between elements to at least 8 dp.\n")
                resultsWithIssues.append("• $viewTypeName: ID=$viewId\n")
                resultsWithIssues.append(" - Issue: Insufficient spacing between elements ($spacingDp dp).\n")
                resultsWithIssues.append(" - Suggestion: Increase spacing between elements to at least 8 dp.\n")
            }
        }

        val finalSpacingScore = if (spacingScores.isEmpty()) {
            100f
        } else {
            spacingScores.minOrNull() ?: 1.0f
        }

        Log.d("FinalElementSpacingScore", "$viewTypeName ID=$viewId, Final Spacing Score: $finalSpacingScore")
        elementSpacingScores.add(finalSpacingScore)
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

        return max(horizontalSpacing, verticalSpacing).coerceAtLeast(0)
    }

    private fun checkEdgeSpacing(nodeBounds: Rect, viewId: String, viewTypeName: String, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val minEdgeSpacingDp = 16

        val leftSpacingDp = convertPixelsToDp(nodeBounds.left)
        val rightSpacingDp =
            convertPixelsToDp(resources.displayMetrics.widthPixels - nodeBounds.right)
        val topSpacingDp = convertPixelsToDp(nodeBounds.top)
        val bottomSpacingDp =
            convertPixelsToDp(resources.displayMetrics.heightPixels - nodeBounds.bottom)

        val spacings = listOf(leftSpacingDp, rightSpacingDp, topSpacingDp, bottomSpacingDp)

        var minScore = 1.0f

        spacings.forEach { spacing ->
            val score = if (spacing >= minEdgeSpacingDp) 1.0f else (spacing / minEdgeSpacingDp.coerceAtLeast(1)).coerceAtLeast(0.0f)
            Log.d("EdgeSpacingScores", "Spacing: $spacing dp, Score: $score")

            if (score < minScore) {
                minScore = score
            }
        }

        edgeSpacingScores.add(minScore)

        Log.d("ElementEdgeScore", "$viewTypeName ID=$viewId, Final Score: $minScore")

        if (minScore < 1.0f) {
            resultsWithIssues.append("• $viewTypeName: ID=$viewId\n")
            spacings.forEachIndexed { index, spacing ->
                when (index) {
                    0 -> if (spacing < minEdgeSpacingDp) {
                        results.append(" - Issue: Element is too close to the left edge ($leftSpacingDp dp).\n")
                        results.append(" - Suggestion: Increase spacing from the left edge to at least $minEdgeSpacingDp dp.\n")
                        resultsWithIssues.append(" - Issue: Element is too close to the left edge ($leftSpacingDp dp).\n")
                        resultsWithIssues.append(" - Suggestion: Increase spacing from the left edge to at least $minEdgeSpacingDp dp.\n")
                    }
                    1 -> if (spacing < minEdgeSpacingDp) {
                        results.append(" - Issue: Element is too close to the right edge ($rightSpacingDp dp).\n")
                        results.append(" - Suggestion: Increase spacing from the right edge to at least $minEdgeSpacingDp dp.\n")
                        resultsWithIssues.append(" - Issue: Element is too close to the right edge ($rightSpacingDp dp).\n")
                        resultsWithIssues.append(" - Suggestion: Increase spacing from the right edge to at least $minEdgeSpacingDp dp.\n")
                    }
                    2 -> if (spacing < minEdgeSpacingDp) {
                        results.append(" - Issue: Element is too close to the top edge ($topSpacingDp dp).\n")
                        results.append(" - Suggestion: Increase spacing from the top edge to at least $minEdgeSpacingDp dp.\n")
                        resultsWithIssues.append(" - Issue: Element is too close to the top edge ($topSpacingDp dp).\n")
                        resultsWithIssues.append(" - Suggestion: Increase spacing from the top edge to at least $minEdgeSpacingDp dp.\n")
                    }
                    3 -> if (spacing < minEdgeSpacingDp) {
                        results.append(" - Issue: Element is too close to the bottom edge ($bottomSpacingDp dp).\n")
                        results.append(" - Suggestion: Increase spacing from the bottom edge to at least $minEdgeSpacingDp dp.\n")
                        resultsWithIssues.append(" - Issue: Element is too close to the bottom edge ($bottomSpacingDp dp).\n")
                        resultsWithIssues.append(" - Suggestion: Increase spacing from the bottom edge to at least $minEdgeSpacingDp dp.\n")
                    }
                }
            }
        }
    }

    private fun convertPixelsToDp(px: Int): Float {
        return px / resources.displayMetrics.density
    }

    private fun analyzeImageButton(node: AccessibilityNodeInfo, viewId: String, viewTypeName: String, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val contentDescription = node.contentDescription?.toString().orEmpty()

        results.append("ImageButton found: ID=$viewId\n")

        if (contentDescription.isEmpty()) {
            results.append(" - Issue: Missing content description.\n")
            results.append(" - Suggestion: Add a content description for accessibility.\n")
            resultsWithIssues.append("• ImageButton: ID=$viewId\n")
            resultsWithIssues.append(" - Issue: Missing content description.\n")
            resultsWithIssues.append(" - Suggestion: Add a content description for accessibility.\n")
        }

        contentDescriptionScores.add(if (contentDescription.isEmpty()) 0.0f else 1.0f)

        checkTouchArea(node, viewId, viewTypeName, results, resultsWithIssues)
        checkSpacing(node, viewId, viewTypeName, results, resultsWithIssues)
    }

    private fun analyzeCheckBox(node: AccessibilityNodeInfo, viewId: String, viewTypeName: String, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val contentDescription = node.contentDescription?.toString().orEmpty()

        results.append("CheckBox found: ID=$viewId\n")

        if (contentDescription.isEmpty()) {
            results.append(" - Issue: Missing content description.\n")
            results.append(" - Suggestion: Add a content description for accessibility.\n")
            resultsWithIssues.append("• CheckBox: ID=$viewId\n")
            resultsWithIssues.append(" - Issue: Missing content description.\n")
            resultsWithIssues.append(" - Suggestion: Add a content description for accessibility.\n")
        }

        contentDescriptionScores.add(if (contentDescription.isEmpty()) 0.0f else 1.0f)

        checkSpacing(node, viewId, viewTypeName, results, resultsWithIssues)
    }

    private fun analyzeImageView(node: AccessibilityNodeInfo, viewId: String, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val contentDescription = node.contentDescription?.toString().orEmpty()

        results.append("ImageView found: ID=$viewId\n")

        if (contentDescription.isEmpty()) {
            results.append(" - Issue: Missing content description.\n")
            results.append(" - Suggestion: Add a content description for accessibility.\n")
            resultsWithIssues.append("• ImageView: ID=$viewId\n")
            resultsWithIssues.append(" - Issue: Missing content description.\n")
            resultsWithIssues.append(" - Suggestion: Add a content description for accessibility.\n")
        }

        contentDescriptionScores.add(if (contentDescription.isEmpty()) 0.0f else 1.0f)
    }

    private fun analyzeEditText(node: AccessibilityNodeInfo, viewId: String, results: StringBuilder, resultsWithIssues: StringBuilder) {
        val hint = node.text ?: node.hintText

        results.append("EditText found: ID=$viewId\n")

        if (hint.isNullOrEmpty()) {
            results.append(" - Issue: EditText is missing a hint.\n")
            results.append(" - Suggestion: Add a hint to the EditText to provide context to users.\n")
            resultsWithIssues.append("• EditText: ID=$viewId\n")
            resultsWithIssues.append(" - Issue: EditText is missing a hint.\n")
            resultsWithIssues.append(" - Suggestion: Add a hint to provide context to users.\n")
        }

        // Calculate hint text score
        hintTextScores.add(if (hint.isNullOrEmpty()) 0.0f else 1.0f)
    }

    private fun calculateTouchAreaScore(): Float {
        Log.d("TouchAreaScores", "Values: ${touchAreaScores.joinToString(", ")}")
        return if (touchAreaScores.isEmpty()) {
            elementSpacingScoreCoefficient += touchAreaScoreCoefficient/(numberOfCoefficients - 1)
            edgeSpacingScoreCoefficient += touchAreaScoreCoefficient/(numberOfCoefficients - 1)
            contentDescriptionScoreCoefficient += touchAreaScoreCoefficient/(numberOfCoefficients - 1)
            hintTextScoreCoefficient += touchAreaScoreCoefficient/(numberOfCoefficients - 1)
            touchAreaScoreCoefficient = 0.0f
            0.0f
        } else touchAreaScores.average().toFloat()
    }

    private fun calculateElementSpacingScore(): Float {
        Log.d("ElementSpacingScores", "Values: ${elementSpacingScores.joinToString(", ")}")
        return if (elementSpacingScores.isEmpty()) {
            touchAreaScoreCoefficient += elementSpacingScoreCoefficient/(numberOfCoefficients - 1)
            edgeSpacingScoreCoefficient += elementSpacingScoreCoefficient/(numberOfCoefficients - 1)
            contentDescriptionScoreCoefficient += elementSpacingScoreCoefficient/(numberOfCoefficients - 1)
            hintTextScoreCoefficient += elementSpacingScoreCoefficient/(numberOfCoefficients - 1)
            elementSpacingScoreCoefficient = 0.0f
            0.0f
        } else elementSpacingScores.average().toFloat()
    }

    private fun calculateEdgeSpacingScore(): Float {
        Log.d("EdgeSpacingScores", "Values: ${edgeSpacingScores.joinToString(", ")}")
        return if (edgeSpacingScores.isEmpty()) {
            touchAreaScoreCoefficient += edgeSpacingScoreCoefficient/(numberOfCoefficients - 1)
            elementSpacingScoreCoefficient += edgeSpacingScoreCoefficient/(numberOfCoefficients - 1)
            contentDescriptionScoreCoefficient += edgeSpacingScoreCoefficient/(numberOfCoefficients - 1)
            hintTextScoreCoefficient += edgeSpacingScoreCoefficient/(numberOfCoefficients - 1)
            edgeSpacingScoreCoefficient = 0.0f
            0.0f
        } else edgeSpacingScores.average().toFloat()
    }

    private fun calculateContentDescriptionScore(): Float {
        Log.d("ContentDescriptionScores", "Values: ${contentDescriptionScores.joinToString(", ")}")
        return if (contentDescriptionScores.isEmpty()) {
            touchAreaScoreCoefficient += contentDescriptionScoreCoefficient/(numberOfCoefficients - 1)
            elementSpacingScoreCoefficient += contentDescriptionScoreCoefficient/(numberOfCoefficients - 1)
            edgeSpacingScoreCoefficient += contentDescriptionScoreCoefficient/(numberOfCoefficients - 1)
            hintTextScoreCoefficient += contentDescriptionScoreCoefficient/(numberOfCoefficients - 1)
            contentDescriptionScoreCoefficient = 0.0f
            0.0f
        } else contentDescriptionScores.average().toFloat()
    }

    private fun calculateHintTextScore(): Float {
        Log.d("HintTextScores", "Values: ${hintTextScores.joinToString(", ")}")
        return if (hintTextScores.isEmpty()) {
            touchAreaScoreCoefficient += hintTextScoreCoefficient/(numberOfCoefficients - 1)
            elementSpacingScoreCoefficient += hintTextScoreCoefficient/(numberOfCoefficients - 1)
            edgeSpacingScoreCoefficient += hintTextScoreCoefficient/(numberOfCoefficients - 1)
            contentDescriptionScoreCoefficient += hintTextScoreCoefficient/(numberOfCoefficients - 1)
            hintTextScoreCoefficient = 0.0f
            0.0f
        } else hintTextScores.average().toFloat()
    }

    @SuppressLint("DefaultLocale")
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
                    writer.append("Element Type;ID;Issues;Suggestions;;;TouchAreaScore;ElementSpacingScore;EdgeSpacingScore;ContentDescriptionScore;HintTextScore\n")

                    val lines = results.split("\n")
                    // Indexes for accessing the score lists
                    var touchAreaScoresIndex = 0
                    var elementSpacingScoresIndex = 0
                    var edgeSpacingScoresIndex = 0
                    var contentDescriptionScoresIndex = 0
                    var hintTextScoresIndex = 0

                    var elementType = ""
                    var elementId = ""
                    var issues = ""
                    var suggestions = ""

                    for (line in lines) {
                        when {
                            line.contains("found:") -> {
                                if (elementType.isNotEmpty()) {
                                    // Get the scores from the lists
                                    val touchAreaScore = if (elementType == "Button" || elementType == "ImageButton") touchAreaScores.getOrNull(touchAreaScoresIndex) ?: "" else ""
                                    if (elementType == "Button") {
                                        touchAreaScoresIndex++
                                    }

                                    val elementSpacingScore = if (elementType == "Button" || elementType == "ImageButton" || elementType == "CheckBox") elementSpacingScores.getOrNull(elementSpacingScoresIndex) ?: "" else ""
                                    if (elementType == "Button" || elementType == "CheckBox") {
                                        elementSpacingScoresIndex++
                                    }

                                    val edgeSpacingScore = if (elementType == "Button" || elementType == "ImageButton" || elementType == "CheckBox") edgeSpacingScores.getOrNull(edgeSpacingScoresIndex) ?: "" else ""
                                    if (elementType == "Button" || elementType == "CheckBox") {
                                        edgeSpacingScoresIndex++
                                    }

                                    val contentDescriptionScore = if (elementType == "CheckBox" || elementType == "ImageView" || elementType == "ImageButton") contentDescriptionScores.getOrNull(contentDescriptionScoresIndex) ?: "" else ""
                                    if (elementType == "CheckBox" || elementType == "ImageView" || elementType == "ImageButton") {
                                        contentDescriptionScoresIndex++
                                    }

                                    val hintTextScore = if (elementType == "EditText") hintTextScores.getOrNull(hintTextScoresIndex) ?: "" else ""
                                    if (elementType == "EditText") {
                                        hintTextScoresIndex++
                                    }

                                    // Convert the scores to strings and replace dots with commas
                                    val touchAreaScoreFormatted = touchAreaScore.toString().replace('.', ',')
                                    val elementSpacingScoreFormatted = elementSpacingScore.toString().replace('.', ',')
                                    val edgeSpacingScoreFormatted = edgeSpacingScore.toString().replace('.', ',')
                                    val contentDescriptionScoreFormatted = contentDescriptionScore.toString().replace('.', ',')
                                    val hintTextScoreFormatted = hintTextScore.toString().replace('.', ',')

                                    writer.append("$elementType;$elementId;$issues;$suggestions;;;$touchAreaScoreFormatted;$elementSpacingScoreFormatted;$edgeSpacingScoreFormatted;$contentDescriptionScoreFormatted;$hintTextScoreFormatted;\n")
                                }
                                elementType = line.substringBefore(" found:").trim()
                                elementId = line.substringAfter("ID=").substringBefore("\n").trim()
                                issues = ""
                                suggestions = ""
                            }
                            line.contains("Issue:") -> {
                                val currentIssue = line.substringAfter("Issue:").trim()
                                if (issues.isNotEmpty()) {
                                    issues += " "
                                }
                                issues += currentIssue
                            }
                            line.contains("Suggestion:") -> {
                                val currentSuggestion = line.substringAfter("Suggestion:").trim()
                                if (suggestions.isNotEmpty()) {
                                    suggestions += " "
                                }
                                suggestions += currentSuggestion
                            }
                        }
                    }

                    if (elementType.isNotEmpty() || elementId.isNotEmpty() || issues.isNotEmpty() || suggestions.isNotEmpty()) {
                        // Get the scores for the last element
                        val touchAreaScore = touchAreaScores.getOrNull(touchAreaScoresIndex) ?: ""
                        val elementSpacingScore = elementSpacingScores.getOrNull(elementSpacingScoresIndex) ?: ""
                        val edgeSpacingScore = edgeSpacingScores.getOrNull(edgeSpacingScoresIndex) ?: ""
                        val contentDescriptionScore = contentDescriptionScores.getOrNull(contentDescriptionScoresIndex) ?: ""
                        val hintTextScore = hintTextScores.getOrNull(hintTextScoresIndex) ?: ""

                        // Convert the scores to strings and replace dots with commas
                        val touchAreaScoreFormatted = touchAreaScore.toString().replace('.', ',')
                        val elementSpacingScoreFormatted = elementSpacingScore.toString().replace('.', ',')
                        val edgeSpacingScoreFormatted = edgeSpacingScore.toString().replace('.', ',')
                        val contentDescriptionScoreFormatted = contentDescriptionScore.toString().replace('.', ',')
                        val hintTextScoreFormatted = hintTextScore.toString().replace('.', ',')

                        writer.append("$elementType;$elementId;$issues;$suggestions;;;$touchAreaScoreFormatted;$elementSpacingScoreFormatted;$edgeSpacingScoreFormatted;$contentDescriptionScoreFormatted;$hintTextScoreFormatted;\n")
                    }

                    // Format the scores and replace dots with commas
                    val touchAreaScoreFormatted = String.format("%.3f", touchAreaScore).replace('.', ',')
                    val elementSpacingScoreFormatted = String.format("%.3f", elementSpacingScore).replace('.', ',')
                    val edgeSpacingScoreFormatted = String.format("%.3f", edgeSpacingScore).replace('.', ',')
                    val contentDescriptionScoreFormatted = String.format("%.3f", contentDescriptionScore).replace('.', ',')
                    val hintTextScoreFormatted = String.format("%.3f", hintTextScore).replace('.', ',')
                    val finalScoreFormatted = String.format("%.3f", finalScore).replace('.', ',')
                    val finalMinimalScoreFormatted = String.format("%.3f", finalMinimalScore).replace('.', ',')

                    // Format the coefficients and replace dots with commas
                    val touchAreaScoreCoefficientFormatted = touchAreaScoreCoefficient.toString().replace('.', ',')
                    val elementSpacingScoreCoefficientFormatted = elementSpacingScoreCoefficient.toString().replace('.', ',')
                    val edgeSpacingScoreCoefficientFormatted = edgeSpacingScoreCoefficient.toString().replace('.', ',')
                    val contentDescriptionScoreCoefficientFormatted = contentDescriptionScoreCoefficient.toString().replace('.', ',')
                    val hintTextScoreCoefficientFormatted = hintTextScoreCoefficient.toString().replace('.', ',')

                    writer.append(";;;;;Average scores:")
                    writer.append(";" +
                            "$touchAreaScoreFormatted;" +
                            "$elementSpacingScoreFormatted;" +
                            "$edgeSpacingScoreFormatted;" +
                            "$contentDescriptionScoreFormatted;" +
                            "$hintTextScoreFormatted\n"
                    )
                    writer.append(";;;;;Coefficients:")
                    writer.append(";" +
                            "$touchAreaScoreCoefficientFormatted;" +
                            "$elementSpacingScoreCoefficientFormatted;" +
                            "$edgeSpacingScoreCoefficientFormatted;" +
                            "$contentDescriptionScoreCoefficientFormatted;" +
                            "$hintTextScoreCoefficientFormatted\n")
                    writer.append(";;;;;UI Quality Score:;$finalScoreFormatted\n")
                    writer.append(";;;;;UI Quality Minimal Score:;$finalMinimalScoreFormatted\n")
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
