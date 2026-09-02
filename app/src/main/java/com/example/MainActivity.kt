package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
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

