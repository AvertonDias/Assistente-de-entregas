package com.example.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast

class AreaSelectorOverlay(
    private val context: Context,
    private val onAreaSelected: (leftNorm: Float, topNorm: Float, rightNorm: Float, bottomNorm: Float) -> Unit,
    private val onDismiss: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: SelectionDrawView? = null

    fun show() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        overlayView = SelectionDrawView(context, { left, top, right, bottom ->
            dismiss()
            onAreaSelected(left, top, right, bottom)
            // Feedback visual flutuante animado confirmando que a área foi configurada
            try {
                SelectionSuccessOverlay(context, left, top, right, bottom).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, {
            dismiss()
            onDismiss()
        })

        try {
            windowManager.addView(overlayView, params)
            Toast.makeText(context, "✏️ Arraste o dedo na tela para desenhar o retângulo do endereço", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            onDismiss()
        }
    }

    fun dismiss() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    private class SelectionDrawView(
        context: Context,
        private val onFinishSelection: (Float, Float, Float, Float) -> Unit,
        private val onCancel: () -> Unit
    ) : View(context) {

        private var startX = 0f
        private var startY = 0f
        private var currentX = 0f
        private var currentY = 0f

        private enum class Mode { DRAWING, CONFIGURED, MOVING }
        private var mode = Mode.DRAWING

        private var lastTouchX = 0f
        private var lastTouchY = 0f

        private var saveButtonRect = RectF()
        private var redosButtonRect = RectF()

        private val dimPaint = Paint().apply {
            color = Color.argb(140, 0, 0, 0)
            style = Paint.Style.FILL
        }

        private val boxPaint = Paint().apply {
            color = Color.parseColor("#10B981") // Verde Esmeralda
            style = Paint.Style.STROKE
            strokeWidth = 6f
            pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
        }

        private val boxFillPaint = Paint().apply {
            color = Color.argb(50, 16, 185, 129)
            style = Paint.Style.FILL
        }

        private val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 38f
            isAntiAlias = true
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        private val bannerPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }

        private val btnSavePaint = Paint().apply {
            color = Color.parseColor("#10B981")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val btnRedoPaint = Paint().apply {
            color = Color.parseColor("#334155")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val btnTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            isFakeBoldText = true
            isAntiAlias = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            // Fundo escurecido
            canvas.drawRect(0f, 0f, w, h, dimPaint)

            // Banner superior de instruções
            canvas.drawRect(0f, 0f, w, 180f, bannerPaint)
            if (mode == Mode.DRAWING) {
                canvas.drawText("🎯 Desenhe o retângulo sobre a área do endereço", 40f, 90f, textPaint)
                textPaint.textSize = 26f
                canvas.drawText("Toque e arraste para delimitar a área alvo.", 40f, 140f, textPaint)
                textPaint.textSize = 38f
            } else {
                canvas.drawText("✨ Arraste a caixa para ajustar a posição", 40f, 90f, textPaint)
                textPaint.textSize = 26f
                canvas.drawText("Toque em SALVAR quando estiver perfeito.", 40f, 140f, textPaint)
                textPaint.textSize = 38f
            }

            if (mode != Mode.DRAWING || (startX != currentX && startY != currentY)) {
                val rect = getNormalizedRect()
                canvas.drawRect(rect, boxFillPaint)
                canvas.drawRect(rect, boxPaint)

                // Cantoneiras decorativas de precisão
                val cornerLen = 24f
                val cornerPaint = Paint(boxPaint).apply { strokeWidth = 10f }
                canvas.drawLine(rect.left - 2, rect.top, rect.left + cornerLen, rect.top, cornerPaint)
                canvas.drawLine(rect.left, rect.top - 2, rect.left, rect.top + cornerLen, cornerPaint)

                canvas.drawLine(rect.right + 2, rect.top, rect.right - cornerLen, rect.top, cornerPaint)
                canvas.drawLine(rect.right, rect.top - 2, rect.right, rect.right + cornerLen, cornerPaint)

                canvas.drawLine(rect.left - 2, rect.bottom, rect.left + cornerLen, rect.bottom, cornerPaint)
                canvas.drawLine(rect.left, rect.bottom + 2, rect.left, rect.bottom - cornerLen, cornerPaint)

                canvas.drawLine(rect.right + 2, rect.bottom, rect.right - cornerLen, rect.bottom, cornerPaint)
                canvas.drawLine(rect.right, rect.bottom + 2, rect.right, rect.bottom - cornerLen, cornerPaint)
            }

            // Se configurado, desenhar barra flutuante de botões na parte inferior
            if (mode == Mode.CONFIGURED) {
                val barHeight = 130f
                val barTop = h - barHeight - 40f
                val btnWidth = (w - 100f) / 2f

                redosButtonRect = RectF(40f, barTop, 40f + btnWidth, barTop + 80f)
                saveButtonRect = RectF(60f + btnWidth, barTop, 60f + btnWidth * 2f, barTop + 80f)

                // Botão Redesenhar
                canvas.drawRoundRect(redosButtonRect, 16f, 16f, btnRedoPaint)
                canvas.drawText("🔄 Redesenhar", redosButtonRect.left + 24f, redosButtonRect.top + 50f, btnTextPaint)

                // Botão Salvar
                canvas.drawRoundRect(saveButtonRect, 16f, 16f, btnSavePaint)
                canvas.drawText("💾 Salvar Área", saveButtonRect.left + 24f, saveButtonRect.top + 50f, btnTextPaint)
            }
        }

        private fun getNormalizedRect(): RectF {
            val left = minOf(startX, currentX)
            val top = minOf(startY, currentY)
            val right = maxOf(startX, currentX)
            val bottom = maxOf(startY, currentY)
            return RectF(left, top, right, bottom)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x
            val y = event.y

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (mode == Mode.CONFIGURED) {
                        if (saveButtonRect.contains(x, y)) {
                            // Salvar
                            val rect = getNormalizedRect()
                            val w = width.toFloat()
                            val h = height.toFloat()
                            if (rect.width() > 20 && rect.height() > 20 && w > 0 && h > 0) {
                                val leftNorm = (rect.left / w).coerceIn(0f, 1f)
                                val topNorm = (rect.top / h).coerceIn(0f, 1f)
                                val rightNorm = (rect.right / w).coerceIn(0f, 1f)
                                val bottomNorm = (rect.bottom / h).coerceIn(0f, 1f)
                                onFinishSelection(leftNorm, topNorm, rightNorm, bottomNorm)
                            } else {
                                onCancel()
                            }
                            return true
                        } else if (redosButtonRect.contains(x, y)) {
                            // Redesenhar
                            mode = Mode.DRAWING
                            startX = 0f
                            startY = 0f
                            currentX = 0f
                            currentY = 0f
                            invalidate()
                            return true
                        } else {
                            // Tocou na tela para mover o retângulo
                            lastTouchX = x
                            lastTouchY = y
                            mode = Mode.MOVING
                            return true
                        }
                    } else if (mode == Mode.DRAWING) {
                        startX = x
                        startY = y
                        currentX = x
                        currentY = y
                        invalidate()
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mode == Mode.DRAWING) {
                        currentX = x
                        currentY = y
                        invalidate()
                        return true
                    } else if (mode == Mode.MOVING) {
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        lastTouchX = x
                        lastTouchY = y

                        // Mover toda a caixa
                        startX += dx
                        startY += dy
                        currentX += dx
                        currentY += dy
                        invalidate()
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (mode == Mode.DRAWING) {
                        val rect = getNormalizedRect()
                        if (rect.width() > 40 && rect.height() > 30) {
                            mode = Mode.CONFIGURED
                        } else {
                            onCancel()
                        }
                        invalidate()
                        return true
                    } else if (mode == Mode.MOVING) {
                        mode = Mode.CONFIGURED
                        invalidate()
                        return true
                    }
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
