package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.util.SpeechHelper

/**
 * Botão com ícone de microfone reutilizável para qualquer campo de texto.
 * Trata permissão de RECORD_AUDIO e reconhecimento de voz de forma unificada.
 */
@Composable
fun VoiceInputIconButton(
    modifier: Modifier = Modifier,
    hintPrompt: String = "Fale agora...",
    tint: Color = MaterialTheme.colorScheme.primary,
    onResult: (String) -> Unit
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isListening = true
            SpeechHelper.startListening(
                context = context,
                onReady = {
                    Toast.makeText(context, hintPrompt, Toast.LENGTH_SHORT).show()
                },
                onResult = { text ->
                    isListening = false
                    if (text.isNotBlank()) {
                        onResult(text)
                    }
                },
                onError = { err ->
                    isListening = false
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            Toast.makeText(context, "Permissão de microfone necessária para falar", Toast.LENGTH_SHORT).show()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    val iconTint by animateColorAsState(
        targetValue = if (isListening) MaterialTheme.colorScheme.error else tint,
        label = "mic_tint"
    )

    IconButton(
        onClick = {
            if (isListening) return@IconButton
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPerm) {
                isListening = true
                SpeechHelper.startListening(
                    context = context,
                    onReady = {
                        Toast.makeText(context, hintPrompt, Toast.LENGTH_SHORT).show()
                    },
                    onResult = { text ->
                        isListening = false
                        if (text.isNotBlank()) {
                            onResult(text)
                        }
                    },
                    onError = { err ->
                        isListening = false
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        modifier = modifier.size(36.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Falar por voz",
            tint = iconTint,
            modifier = Modifier
                .size(20.dp)
                .scale(if (isListening) scale else 1f)
        )
    }
}
