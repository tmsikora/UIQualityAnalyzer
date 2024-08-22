package com.example.uiqualityanalyzer

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.uiqualityanalyzer.ui.theme.UIQualityAnalyzerTheme

class MainActivity : ComponentActivity() {
    private var isServiceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UIQualityAnalyzerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        isServiceEnabled = isServiceEnabled
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isServiceEnabled = isAccessibilityServiceEnabled(this, UIQualityAccessibilityService::class.java)
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (enabledServices.isNullOrEmpty()) {
            return false
        }
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals("${context.packageName}/${service.name}", ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier, isServiceEnabled: Boolean) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "UI Quality Analyzer")
        Spacer(modifier = Modifier.height(16.dp))
        if (isServiceEnabled) {
            Text(text = "Accessibility service is enabled.")
        } else {
            Text(text = "Accessibility service is disabled. Please enable it to use the analyzer.")
            Button(onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            }) {
                Text(text = "Enable Accessibility Service")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    UIQualityAnalyzerTheme {
        MainScreen(isServiceEnabled = false)
    }
}
