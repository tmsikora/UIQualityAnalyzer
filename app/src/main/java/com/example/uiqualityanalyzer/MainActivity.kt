package com.example.uiqualityanalyzer

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var isServiceEnabled by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)
    private var isOverlayRunning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(
                modifier = Modifier.fillMaxSize(),
                isServiceEnabled = isServiceEnabled,
                hasOverlayPermission = hasOverlayPermission,
                isOverlayRunning = isOverlayRunning,
                onRequestOverlayPermission = { requestOverlayPermission() },
                onStartOverlayClick = { startOverlayService() },
                onStopOverlayClick = { stopOverlayService() },
                onRequestAccessibilitySettings = { requestAccessibilitySettings() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isServiceEnabled = isAccessibilityServiceEnabled()
        hasOverlayPermission = Settings.canDrawOverlays(this)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (enabledServices.isNullOrEmpty()) {
            return false
        }
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals("${packageName}/${UIQualityAccessibilityService::class.java.name}", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun requestAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = android.net.Uri.parse("package:$packageName")
            overlayPermissionResultLauncher.launch(intent)
        } else {
            hasOverlayPermission = true
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        if (isAccessibilityServiceEnabled()) {
            val intent = Intent(this, OverlayService::class.java)
            ContextCompat.startForegroundService(this, intent)
            isOverlayRunning = true
        } else {
            Toast.makeText(this, "Please enable the accessibility service first.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        isOverlayRunning = false
    }

    private val overlayPermissionResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            hasOverlayPermission = Settings.canDrawOverlays(this)
            if (hasOverlayPermission) {
                startOverlayService()
            }
        }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    isServiceEnabled: Boolean,
    hasOverlayPermission: Boolean,
    isOverlayRunning: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlayClick: () -> Unit,
    onStopOverlayClick: () -> Unit,
    onRequestAccessibilitySettings: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "UI Quality Analyzer",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        when {
            !isServiceEnabled -> {
                Text(
                    text = "Accessibility service is disabled. Please enable it to use the analyzer.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { onRequestAccessibilitySettings() }) {
                    Text(text = "Enable Accessibility Service")
                }
            }
            !hasOverlayPermission -> {
                Text(
                    text = "Overlay permission is not granted. Please grant it to use the overlay.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { onRequestOverlayPermission() }) {
                    Text(text = "Request Overlay Permission")
                }
            }
            else -> {
                Text(
                    text = "All permissions are granted.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (isOverlayRunning) {
                    Button(
                        onClick = { onStopOverlayClick() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text(text = "Stop Overlay")
                    }
                } else {
                    Button(
                        onClick = { onStartOverlayClick() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008000))
                    ) {
                        Text(text = "Start Overlay")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MainScreen(
        isServiceEnabled = false,
        hasOverlayPermission = false,
        isOverlayRunning = false,
        onRequestOverlayPermission = {},
        onStartOverlayClick = {},
        onStopOverlayClick = {},
        onRequestAccessibilitySettings = {}
    )
}
