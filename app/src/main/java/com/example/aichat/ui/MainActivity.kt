package com.example.aichat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.aichat.ui.chat.ChatScreen
import com.example.aichat.ui.chat.ChatViewModel
import com.example.aichat.ui.theme.AIChatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val isPermissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onMicrophonePermissionResult(isPermissionGranted)

        val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onMicrophonePermissionResult(granted)
        }

        setContent {
            AIChatTheme {
                val hasPermission by viewModel.hasMicrophonePermission.collectAsState()

                LaunchedEffect(hasPermission) {
                    viewModel.microphonePermissionRequested.collect { requested ->
                        if (requested && !hasPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }

                ChatScreen(
                    viewModel = viewModel,
                    onRequestPermission = {
                        viewModel.askForMicrophonePermission()
                    }
                )
            }
        }
    }
}
