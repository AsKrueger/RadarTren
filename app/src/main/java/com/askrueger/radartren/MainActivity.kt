package com.askrueger.radartren

import android.Manifest
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.askrueger.radartren.data.ThemeMode
import com.askrueger.radartren.ui.AppRoot
import com.askrueger.radartren.ui.AppViewModel
import com.askrueger.radartren.ui.theme.RadarTrenTheme

class MainActivity : AppCompatActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current
            val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

            LaunchedEffect(Unit) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.pollWhileVisible() }
            }

            settings?.let { current ->
                val dark = when (current.theme) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
                RadarTrenTheme(darkTheme = dark) {
                    AppRoot(viewModel, current) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }
}
