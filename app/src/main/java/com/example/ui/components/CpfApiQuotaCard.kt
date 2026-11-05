package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.CpfApiUsageTracker

@Composable
fun CpfApiQuotaCard(
    modifier: Modifier = Modifier
) {
    val usageState by CpfApiUsageTracker.usageState.collectAsState()

    val progress = usageState.progressFraction
    val remainingToday = usageState.remainingToday
    val isNearLimit = remainingToday in 1..15
    val isLimitReached = remainingToday <= 0

    val statusColor = when {
        isLimitReached -> Color(0xFFD32F2F)
        isNearLimit -> Color(0xFFF57C00)
        else -> Color(0xFF2E7D32)
    }

    val badgeBgColor = when {
        isLimitReached -> Color(0xFFFFEBEE)
        isNearLimit -> Color(0xFFFFF3E0)
        else -> Color(0xFFE8F5E9)
    }

    val badgeBorderColor = when {
        isLimitReached -> Color(0xFFFFCDD2)
        isNearLimit -> Color(0xFFFFCC80)
        else -> Color(0xFFA5D6A7)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Cabeçalho: Título + Badge de Restantes Hoje
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isLimitReached || isNearLimit) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Cota Diária da API CPF",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Limite: ${usageState.dailyLimit} consultas/dia",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBgColor,
                    border = BorderStroke(1.dp, badgeBorderColor)
                ) {
                    Text(
                        text = if (isLimitReached) "Esgotado Hoje" else "Restam $remainingToday hoje",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }
            }

            // Barra de Progresso do Limite Diário
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = Color(0xFFE2E8F0)
            )

            // Resumo de contagem e última consulta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hoje: ${usageState.todayLookups}/${usageState.dailyLimit} (${(progress * 100).toInt()}%) • Total: ${usageState.totalLookups}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )

                if (usageState.lastLookupTime.isNotBlank()) {
                    Text(
                        text = "Última: ${usageState.lastLookupTime}",
                        fontSize = 9.5.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            if (isLimitReached) {
                Text(
                    text = "⚠️ Limite diário de 100 consultas atingido. A cota reinicia automaticamente à meia-noite (00:00).",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC62828)
                )
            }
        }
    }
}
