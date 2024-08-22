package com.example.uiqualityanalyzer

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Inflate the layout for the overlay
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        // Set up layout parameters
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.END
        layoutParams.x = 0
        layoutParams.y = 100

        // Add the view to the window
        windowManager.addView(overlayView, layoutParams)

        // Set up button click listener
        val button = overlayView.findViewById<Button>(R.id.overlay_button)
        button.setOnClickListener {
            Toast.makeText(this, "Overlay button clicked", Toast.LENGTH_SHORT).show()
            // Start analysis here
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove the view when the service is destroyed
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
