package com.example.uiqualityanalyzer

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var analysisTextView: TextView
    private lateinit var minimizeResultsButton: Button
    private lateinit var scrollViewAnalysis: ScrollView
    private var isMinimized = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay()
    }

    private fun setupOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        analysisTextView = overlayView.findViewById(R.id.analysis_text)
        minimizeResultsButton = overlayView.findViewById(R.id.minimize_results_button)
        scrollViewAnalysis = overlayView.findViewById(R.id.scroll_view_analysis)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        windowManager.addView(overlayView, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("analysis_result")?.let { result ->
            updateOverlayContent(result)
        }

        overlayView.findViewById<Button>(R.id.start_analysis_button).setOnClickListener {
            startAnalysis()
        }

        minimizeResultsButton.setOnClickListener {
            toggleResultsVisibility()
        }

        return START_STICKY
    }

    private fun startAnalysis() {
        // Send a broadcast to the accessibility service to start the analysis
        val analysisIntent = Intent(this, UIQualityAccessibilityService::class.java).apply {
            action = "START_ANALYSIS"
        }
        startService(analysisIntent)
    }

    fun updateOverlayContent(analysisResult: String) {
        analysisTextView.text = analysisResult
        scrollViewAnalysis.visibility = View.VISIBLE
        minimizeResultsButton.visibility = View.VISIBLE
        minimizeResultsButton.text = "Minimize Results"
        isMinimized = false
    }

    private fun toggleResultsVisibility() {
        if (::analysisTextView.isInitialized && ::minimizeResultsButton.isInitialized) {
            if (isMinimized) {
                scrollViewAnalysis.visibility = View.VISIBLE
                minimizeResultsButton.text = "Minimize Results"
            } else {
                scrollViewAnalysis.visibility = View.GONE
                minimizeResultsButton.text = "Show Results"
            }
            isMinimized = !isMinimized
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            windowManager.removeView(overlayView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
