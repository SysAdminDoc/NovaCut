package com.novacut.editor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.novacut.editor.ui.editor.EditorScreen
import com.novacut.editor.ui.theme.NovaCutTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permissions handled, UI will adapt */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()

        setContent {
            NovaCutTheme {
                EditorScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                )
            }
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkPerm(Manifest.permission.READ_MEDIA_VIDEO)) needed.add(Manifest.permission.READ_MEDIA_VIDEO)
            if (checkPerm(Manifest.permission.READ_MEDIA_AUDIO)) needed.add(Manifest.permission.READ_MEDIA_AUDIO)
            if (checkPerm(Manifest.permission.READ_MEDIA_IMAGES)) needed.add(Manifest.permission.READ_MEDIA_IMAGES)
            if (checkPerm(Manifest.permission.POST_NOTIFICATIONS)) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (checkPerm(Manifest.permission.READ_EXTERNAL_STORAGE)) needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (checkPerm(Manifest.permission.RECORD_AUDIO)) needed.add(Manifest.permission.RECORD_AUDIO)

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun checkPerm(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
    }
}
