package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun CourierSearchProgressBar(
    visible: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
    statusText: String = "Buscando destinatários..."
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "courier_search_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "courier_run_cycle")
    val runPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "courier_stride"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + expandVertically(tween(180)),
        exit = fadeOut(tween(220)) + shrinkVertically(tween(220)),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF0F7FF),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1565C0)
                    )
                    Text(
                        text = "${(animatedProgress * 100f).toInt().coerceIn(1, 100)}%",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2)
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    val trackHeight = 7.dp.toPx()
                    val trackTop = size.height - trackHeight - 2.dp.toPx()
                    val startPad = 14.dp.toPx()
                    val endPad = 18.dp.toPx()
                    val usableWidth = (size.width - startPad - endPad).coerceAtLeast(24f)

                    // Fundo da trilha da barra de progresso
                    drawRoundRect(
                        color = Color(0xFFD6E8FA),
                        topLeft = Offset(0f, trackTop),
                        size = Size(size.width, trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                    )

                    // Pontilhado discreto na parte ainda não percorrida
                    val dotSpacing = 12.dp.toPx()
                    val dotY = trackTop + trackHeight / 2f
                    var dx = startPad + animatedProgress * usableWidth + 12.dp.toPx()
                    while (dx < size.width - 6.dp.toPx()) {
                        drawCircle(
                            color = Color(0xFF90CAF9),
                            radius = 1.3.dp.toPx(),
                            center = Offset(dx, dotY)
                        )
                        dx += dotSpacing
                    }

                    // Preenchimento ativo da barra até a ponta onde o entregador está correndo
                    val tipX = startPad + animatedProgress * usableWidth
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF1565C0),
                                Color(0xFF1E88E5),
                                Color(0xFF42A5F5)
                            ),
                            startX = 0f,
                            endX = tipX.coerceAtLeast(1f)
                        ),
                        topLeft = Offset(0f, trackTop),
                        size = Size(tipX.coerceAtLeast(trackHeight), trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                    )

                    // Linhas de velocidade atrás do entregador
                    val streakOffset = abs(sin(runPhase)) * 2.dp.toPx()
                    for (i in 0..2) {
                        val sy = trackTop - 7.dp.toPx() - i * 4.5.dp.toPx()
                        val sxEnd = tipX - 11.dp.toPx() - i * 2.dp.toPx()
                        val sxStart = sxEnd - (8.dp.toPx() - i * 1.5.dp.toPx()) - streakOffset
                        if (sxStart > 2f) {
                            drawLine(
                                color = Color(0xFF64B5F6).copy(alpha = 0.75f - i * 0.18f),
                                start = Offset(sxStart, sy),
                                end = Offset(sxEnd, sy),
                                strokeWidth = 1.6.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Entregador correndo com uma caixa na mão na ponta da barra
                    val stride = sin(runPhase)
                    val bobY = abs(stride) * 1.8.dp.toPx()
                    val groundY = trackTop
                    val hipX = tipX - 2.dp.toPx()
                    val hipY = groundY - 9.dp.toPx() - bobY

                    // Perna de trás (escura)
                    val backFootX = hipX - stride * 6.5.dp.toPx() - 1.5.dp.toPx()
                    val backKneeX = (hipX + backFootX) / 2f + 1.5.dp.toPx()
                    val backKneeY = (hipY + groundY) / 2f - if (stride < 0f) 1.8.dp.toPx() else 0f
                    val backFootY = groundY - if (stride < 0f) 2.2.dp.toPx() else 0.5.dp.toPx()
                    val backLegPath = Path().apply {
                        moveTo(hipX, hipY)
                        lineTo(backKneeX, backKneeY)
                        lineTo(backFootX, backFootY)
                    }
                    drawPath(
                        path = backLegPath,
                        color = Color(0xFF0D47A1),
                        style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // Tênis de trás
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(backFootX - 0.5.dp.toPx(), backFootY),
                        end = Offset(backFootX + 2.8.dp.toPx(), backFootY),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Perna da frente
                    val frontFootX = hipX + stride * 6.5.dp.toPx() + 0.5.dp.toPx()
                    val frontKneeX = (hipX + frontFootX) / 2f + 2.dp.toPx()
                    val frontKneeY = (hipY + groundY) / 2f - if (stride > 0f) 1.8.dp.toPx() else 0f
                    val frontFootY = groundY - if (stride > 0f) 2.2.dp.toPx() else 0.5.dp.toPx()
                    val frontLegPath = Path().apply {
                        moveTo(hipX, hipY)
                        lineTo(frontKneeX, frontKneeY)
                        lineTo(frontFootX, frontFootY)
                    }
                    drawPath(
                        path = frontLegPath,
                        color = Color(0xFF1565C0),
                        style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // Tênis da frente
                    drawLine(
                        color = Color(0xFF0F172A),
                        start = Offset(frontFootX - 0.5.dp.toPx(), frontFootY),
                        end = Offset(frontFootX + 3.dp.toPx(), frontFootY),
                        strokeWidth = 2.3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Tronco inclinado para frente (postura de corrida)
                    val shoulderX = hipX + 2.4.dp.toPx()
                    val shoulderY = hipY - 8.5.dp.toPx()
                    drawLine(
                        color = Color(0xFF1976D2),
                        start = Offset(hipX, hipY),
                        end = Offset(shoulderX, shoulderY),
                        strokeWidth = 4.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Faixa de uniforme no peito
                    drawLine(
                        color = Color(0xFFBBDEFB),
                        start = Offset(hipX + 0.8.dp.toPx(), hipY - 3.dp.toPx()),
                        end = Offset(shoulderX - 0.3.dp.toPx(), shoulderY + 2.2.dp.toPx()),
                        strokeWidth = 1.3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Cabeça
                    val headCenter = Offset(shoulderX + 1.2.dp.toPx(), shoulderY - 4.2.dp.toPx())
                    val headR = 3.4.dp.toPx()
                    drawCircle(
                        color = Color(0xFFFFCC80),
                        radius = headR,
                        center = headCenter
                    )

                    // Boné azul de entregador voltado para frente
                    drawArc(
                        color = Color(0xFF0D47A1),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(headCenter.x - headR - 0.3.dp.toPx(), headCenter.y - headR - 0.5.dp.toPx()),
                        size = Size((headR + 0.3.dp.toPx()) * 2f, headR * 1.9f)
                    )
                    drawLine(
                        color = Color(0xFF0D47A1),
                        start = Offset(headCenter.x + 0.5.dp.toPx(), headCenter.y - 1.3.dp.toPx()),
                        end = Offset(headCenter.x + headR + 2.6.dp.toPx(), headCenter.y - 1.1.dp.toPx()),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Caixa de entrega nas mãos (balança levemente com a corrida)
                    val boxW = 8.8.dp.toPx()
                    val boxH = 7.2.dp.toPx()
                    val boxLeft = shoulderX + 4.0.dp.toPx()
                    val boxTop = shoulderY + 0.2.dp.toPx() - bobY * 0.35f

                    // Braços segurando a caixa
                    drawLine(
                        color = Color(0xFF1565C0),
                        start = Offset(shoulderX, shoulderY + 1.5.dp.toPx()),
                        end = Offset(boxLeft + 1.2.dp.toPx(), boxTop + boxH * 0.6f),
                        strokeWidth = 2.3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFFFCC80),
                        start = Offset(boxLeft + 0.5.dp.toPx(), boxTop + boxH * 0.62f),
                        end = Offset(boxLeft + 2.2.dp.toPx(), boxTop + boxH * 0.62f),
                        strokeWidth = 2.1.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Corpo da caixa de papelão
                    drawRoundRect(
                        color = Color(0xFFD89C56),
                        topLeft = Offset(boxLeft, boxTop),
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(1.4.dp.toPx(), 1.4.dp.toPx())
                    )
                    // Fita adesiva na caixa
                    drawRect(
                        color = Color(0xFFF3E5AB),
                        topLeft = Offset(boxLeft + boxW * 0.36f, boxTop),
                        size = Size(boxW * 0.28f, boxH * 0.42f)
                    )
                    // Contorno da caixa
                    drawRoundRect(
                        color = Color(0xFF8D5524),
                        topLeft = Offset(boxLeft, boxTop),
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(1.4.dp.toPx(), 1.4.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }
    }
}
