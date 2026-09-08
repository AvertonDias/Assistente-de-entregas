package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.data.repository.AppSettings
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.DeliveryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DeliveryApp
        val action = intent?.getStringExtra("action")
        val initialAddress = intent?.getStringExtra("initial_address")

        setContent {
            val settings by app.settingsRepository.getSettings().collectAsStateWithLifecycle(initialValue = AppSettings())
            DeliveryTheme(themeMode = settings.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 840.dp)
                        ) {
                            val navController = rememberNavController()
                            AppNavigation(
                                navController = navController,
                                app = app,
                                initialAction = action,
                                initialAddress = initialAddress
                            )
                        }
                    }
                }
            }
        }
    }
}

