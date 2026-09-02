package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as CanvasStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Point
import com.example.data.model.SignatureData
import com.example.data.model.Stroke

/**
 * Constrói um traço suave usando interpolação quadrática de Bézier nos pontos médios.
 * Isso elimina traços retos/quadrados e garante curva contínua e natural de caneta.
 */
fun buildSmoothComposePath(points: List<Point>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) {
        return path
    }
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }

    for (i in 1 until points.size - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val midX = (p0.x + p1.x) / 2f
        val midY = (p0.y + p1.y) / 2f
        path.quadraticBezierTo(p0.x, p0.y, midX, midY)
    }

    val last = points.last()
    path.lineTo(last.x, last.y)
    return path
}

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) {
            return ctx
        }
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun SignatureCanvas(
    modifier: Modifier = Modifier,
    initialSignature: SignatureData? = null,
    isDarkTheme: Boolean = false,
    onSignatureConfirmed: (SignatureData) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Forçar orientação landscape (paisagem) enquanto o componente estiver visível
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    val strokes = remember {
        mutableStateListOf<Stroke>().apply {
            if (initialSignature != null && initialSignature.strokes.isNotEmpty()) {
                addAll(initialSignature.strokes)
            }
        }
    }
    val initialStrokeCount = remember { initialSignature?.strokes?.size ?: 0 }
    val currentStrokePoints = remember { mutableStateListOf<Point>() }
    var canvasWidth by remember { mutableStateOf(1200f) }
    var canvasHeight by remember { mutableStateOf(600f) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val handleAttemptClose: () -> Unit = {
        // Se houver novos traços desenhados e não salvos, pede confirmação
        if (strokes.isNotEmpty() && strokes.size != initialStrokeCount) {
            showDiscardConfirmDialog = true
        } else {
            onCancel()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blur(if (showClearConfirmDialog || showDiscardConfirmDialog) 12.dp else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // Cabeçalho fino e ultra-compacto para maximizar a área vertical do canvas na orientação paisagem
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Draw,
                    contentDescription = null,
                    tint = if (isDarkTheme) Color(0xFF6EE7B7) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Área de Assinatura",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "• Desenhe na área branca",
                    fontSize = 11.sp,
                    color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
            }

            IconButton(
                onClick = handleAttemptClose,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fechar",
                    tint = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Barra de Botões compacta e ergonômica na PARTE SUPERIOR da tela
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botão Limpar
            OutlinedButton(
                onClick = {
                    showClearConfirmDialog = true
                },
                enabled = strokes.isNotEmpty() || currentStrokePoints.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("clear_signature_button"),
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF475569) else Color(0xFFCBD5E1)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isDarkTheme) Color(0xFFF87171) else Color(0xFFDC2626)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("LIMPAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Botão Desfazer
            OutlinedButton(
                onClick = {
                    if (strokes.isNotEmpty()) {
                        strokes.removeAt(strokes.lastIndex)
                    }
                },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("undo_signature_button"),
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF475569) else Color(0xFFCBD5E1)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF334155)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("DESFAZER", fontSize = 11.sp)
            }

            // Botão Sair / Cancelar
            OutlinedButton(
                onClick = handleAttemptClose,
                modifier = Modifier
                    .weight(0.9f)
                    .height(38.dp)
                    .testTag("cancel_signature_button"),
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF475569) else Color(0xFFCBD5E1)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("SAIR", fontSize = 11.sp)
            }

            // Botão Confirmar
            Button(
                onClick = {
                    val finalData = SignatureData(
                        strokes = strokes.toList(),
                        canvasWidth = canvasWidth,
                        canvasHeight = canvasHeight
                    )
                    onSignatureConfirmed(finalData)
                },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier
                    .weight(1.3f)
                    .height(38.dp)
                    .testTag("confirm_signature_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDarkTheme) Color(0xFF2563EB) else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CONFIRMAR", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
            }
        }

        // Moldura do Canvas ocupando a área máxima da tela
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 2.dp)
                .shadow(2.dp, RoundedCornerShape(8.dp))
                .border(
                    width = 1.5.dp,
                    color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1),
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
                .testTag("signature_canvas_area"),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        if (size.width > 50 && size.height > 50) {
                            canvasWidth = size.width.toFloat()
                            canvasHeight = size.height.toFloat()
                        }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            currentStrokePoints.clear()
                            val startPoint = Point(down.position.x, down.position.y, System.currentTimeMillis())
                            currentStrokePoints.add(startPoint)

                            var isDrag = false
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.pressed) {
                                    if (change.position != down.position) {
                                        isDrag = true
                                    }
                                    change.consume()
                                    currentStrokePoints.add(
                                        Point(change.position.x, change.position.y, System.currentTimeMillis())
                                    )
                                } else {
                                    break
                                }
                            } while (true)

                            // Registra o traço (seja um único ponto/pingo do i ou um arrasto completo)
                            if (currentStrokePoints.isNotEmpty()) {
                                strokes.add(
                                    Stroke(
                                        points = currentStrokePoints.toList(),
                                        strokeWidth = 5.5f,
                                        color = 0xFF0D47A1.toInt() // Azul caneta executiva
                                    )
                                )
                                currentStrokePoints.clear()
                            }
                        }
                    }
            ) {
                // Guia visual de marca d'água sutil
                if (strokes.isEmpty() && currentStrokePoints.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✍️ ASSINE AQUI",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB0BEC5).copy(alpha = 0.6f)
                        )
                    }
                }

                // Desenho dos traços vetoriais com interpolação Bézier contínua
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Desenhar traços confirmados
                    for (stroke in strokes) {
                        if (stroke.points.size == 1) {
                            drawCircle(
                                color = Color(stroke.color),
                                radius = (stroke.strokeWidth * 0.55f).coerceAtLeast(3f),
                                center = Offset(stroke.points[0].x, stroke.points[0].y)
                            )
                        } else if (stroke.points.size >= 2) {
                            val path = buildSmoothComposePath(stroke.points)
                            drawPath(
                                path = path,
                                color = Color(stroke.color),
                                style = CanvasStroke(
                                    width = stroke.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Desenhar traço dinâmico ativo
                    if (currentStrokePoints.size == 1) {
                        drawCircle(
                            color = Color(0xFF0D47A1),
                            radius = 3.5f,
                            center = Offset(currentStrokePoints[0].x, currentStrokePoints[0].y)
                        )
                    } else if (currentStrokePoints.size >= 2) {
                        val activePath = buildSmoothComposePath(currentStrokePoints.toList())
                        drawPath(
                            path = activePath,
                            color = Color(0xFF0D47A1),
                            style = CanvasStroke(
                                width = 5.5f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }

    // SOBREPOSIÇÃO INLINE DE CONFIRMAÇÃO PARA LIMPAR ASSINATURA
    if (showClearConfirmDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 420.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Limpar toda a assinatura?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                    )
                    Text(
                        text = "Todos os traços desenhados na tela serão apagados. Deseja continuar?",
                        fontSize = 13.5.sp,
                        color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showClearConfirmDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancelar")
                        }
                        Button(
                            onClick = {
                                strokes.clear()
                                currentStrokePoints.clear()
                                showClearConfirmDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Sim, Limpar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // SOBREPOSIÇÃO INLINE DE CONFIRMAÇÃO PARA DESCARTAR E SAIR
    if (showDiscardConfirmDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 420.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Descartar assinatura?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                    )
                    Text(
                        text = "Você desenhou uma assinatura que ainda não foi salva. Deseja realmente sair sem salvar?",
                        fontSize = 13.5.sp,
                        color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDiscardConfirmDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Continuar")
                        }
                        Button(
                            onClick = {
                                showDiscardConfirmDialog = false
                                onCancel()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Descartar e Sair", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
}

