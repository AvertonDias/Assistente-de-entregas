package com.example.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Overlay flutuante de confirmação visual animada após desenhar/salvar a área alvo.
 * Mostra um flash do retângulo verde com cantos realçados, ícone de check e mensagem elegante "ÁREA ALVO CONFIGURADA".
 */
class SelectionSuccessOverlay(
    private val context: Context,
    private val leftNorm: Float,
    private val topNorm: Float,
    private val rightNorm: Float,
    private val bottomNorm: Float
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: SuccessView? = null
    private val handler = Handler(Looper.getMainLooper())

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val view = SuccessView(context, leftNorm, topNorm, rightNorm, bottomNorm) {
            dismiss()
        }
        overlayView = view

        try {
            windowManager.addView(view, params)
            view.startAnimation()
        } catch (e: Exception) {
            e.printStackTrace()
            dismiss()
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

    private class SuccessView(
        context: Context,
        private val leftNorm: Float,
        private val topNorm: Float,
        private val rightNorm: Float,
        private val bottomNorm: Float,
        private val onFinish: () -> Unit
    ) : View(context) {

        private var progress = 0f
        private var alphaMultiplier = 1f

        private val boxBorderPaint = Paint().apply {
            color = Color.parseColor("#10B981") // Verde esmeralda vibrante
            style = Paint.Style.STROKE
            strokeWidth = 8f
            isAntiAlias = true
        }

        private val boxGlowPaint = Paint().apply {
            color = Color.argb(80, 16, 185, 129)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val badgeBackgroundPaint = Paint().apply {
            color = Color.parseColor("#0F172A") // Slate escuro
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val badgeBorderPaint = Paint().apply {
            color = Color.parseColor("#10B981")
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }

        private val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 34f
            isFakeBoldText = true
            isAntiAlias = true
        }

        private val subTextPaint = Paint().apply {
            color = Color.parseColor("#6EE7B7") // Verde claro
            textSize = 24f
            isAntiAlias = true
        }

        fun startAnimation() {
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2200
                addUpdateListener { va ->
                    val fraction = va.animatedFraction
                    progress = fraction
                    if (fraction > 0.7f) {
                        // Fade out no final
                        alphaMultiplier = (1f - ((fraction - 0.7f) / 0.3f)).coerceIn(0f, 1f)
                    } else {
                        alphaMultiplier = 1f
                    }
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onFinish()
                    }
                })
            }
            animator.start()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            val rect = RectF(
                leftNorm * w,
                topNorm * h,
                rightNorm * w,
                bottomNorm * h
            )

            // Ajustar alphas
            boxBorderPaint.alpha = (255 * alphaMultiplier).toInt()
            boxGlowPaint.alpha = (80 * alphaMultiplier).toInt()
            badgeBackgroundPaint.alpha = (240 * alphaMultiplier).toInt()
            badgeBorderPaint.alpha = (255 * alphaMultiplier).toInt()
            textPaint.alpha = (255 * alphaMultiplier).toInt()
            subTextPaint.alpha = (255 * alphaMultiplier).toInt()

            // 1. Desenhar retângulo com brilho
            canvas.drawRoundRect(rect, 16f, 16f, boxGlowPaint)
            canvas.drawRoundRect(rect, 16f, 16f, boxBorderPaint)

            // Cantoneiras decorativas para sensação de mira de precisão
            val cornerLen = 28f
            val cornerPaint = Paint(boxBorderPaint).apply {
                strokeWidth = 14f
            }
            // Top-Left
            canvas.drawLine(rect.left - 4, rect.top, rect.left + cornerLen, rect.top, cornerPaint)
            canvas.drawLine(rect.left, rect.top - 4, rect.left, rect.top + cornerLen, cornerPaint)
            // Top-Right
            canvas.drawLine(rect.right + 4, rect.top, rect.right - cornerLen, rect.top, cornerPaint)
            canvas.drawLine(rect.right, rect.top - 4, rect.right, rect.top + cornerLen, cornerPaint)
            // Bottom-Left
            canvas.drawLine(rect.left - 4, rect.bottom, rect.left + cornerLen, rect.bottom, cornerPaint)
            canvas.drawLine(rect.left, rect.bottom + 4, rect.left, rect.bottom - cornerLen, cornerPaint)
            // Bottom-Right
            canvas.drawLine(rect.right + 4, rect.bottom, rect.right - cornerLen, rect.bottom, cornerPaint)
            canvas.drawLine(rect.right, rect.bottom + 4, rect.right, rect.bottom - cornerLen, cornerPaint)

            // 2. Badge flutuante centralizado logo acima ou dentro da área
            val badgeWidth = 460f
            val badgeHeight = 110f
            val badgeCenterX = rect.centerX().coerceIn(badgeWidth / 2 + 20f, w - badgeWidth / 2 - 20f)
            
            var badgeTop = rect.top - badgeHeight - 20f
            if (badgeTop < 100f) {
                // Se estiver muito no topo da tela, coloca dentro da caixa ou abaixo
                badgeTop = rect.bottom + 20f
            }

            val badgeRect = RectF(
                badgeCenterX - badgeWidth / 2,
                badgeTop,
                badgeCenterX + badgeWidth / 2,
                badgeTop + badgeHeight
            )

            // Fundo e borda do Badge
            canvas.drawRoundRect(badgeRect, 20f, 20f, badgeBackgroundPaint)
            canvas.drawRoundRect(badgeRect, 20f, 20f, badgeBorderPaint)

            // Textos no Badge
            canvas.drawText("✅ ÁREA SALVA COM SUCESSO!", badgeRect.left + 24f, badgeRect.top + 48f, textPaint)
            canvas.drawText("🎯 O leitor focará exatamente aqui", badgeRect.left + 24f, badgeRect.top + 86f, subTextPaint)
        }
    }
}
