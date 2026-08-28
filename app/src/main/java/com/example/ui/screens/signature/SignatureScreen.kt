package com.example.ui.screens.signature

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.AccessibilityAutomationEngine
import com.example.data.model.SignatureData
import com.example.data.repository.SignatureRepository
import com.example.ui.components.SignatureCanvas
import kotlinx.coroutines.launch

@Composable
fun SignatureScreen(
    deliveryId: Long,
    personId: Long,
    signatureRepository: SignatureRepository,
    onSignatureConfirmed: (SignatureData) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savedSignature by remember { mutableStateOf<SignatureData?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        SignatureCanvas(
            modifier = Modifier.fillMaxSize(),
            initialSignature = savedSignature,
            isDarkTheme = true,
            onSignatureConfirmed = { signatureData ->
                savedSignature = signatureData
                scope.launch {
                    signatureRepository.saveSignature(
                        personId = if (personId > 0) personId else null,
                        deliveryId = if (deliveryId > 0) deliveryId else null,
                        signature = signatureData
                    )
                    Toast.makeText(context, "Assinatura registrada com sucesso.", Toast.LENGTH_SHORT).show()
                    onSignatureConfirmed(signatureData)
                    onNavigateBack()
                }
            },
            onCancel = onNavigateBack
        )
    }
}
