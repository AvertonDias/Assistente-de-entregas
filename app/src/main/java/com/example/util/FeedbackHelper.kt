package com.example.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.SoundEffectConstants

/**
 * Utilitário centralizado para feedback háptico e sonoro sutil de sucesso, clique e alertas.
 */
object FeedbackHelper {

    private var toneGenerator: ToneGenerator? = null

    private fun getToneGenerator(): ToneGenerator? {
        if (toneGenerator == null) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35)
            } catch (_: Exception) {}
        }
        return toneGenerator
    }

    /**
     * Feedback tátil e sonoro sutil de SUCESSO (ex: preenchimento de campos ou desenho de assinatura concluído)
     */
    fun triggerSuccess(context: Context, vibrationEnabled: Boolean = true, soundEnabled: Boolean = true) {
        if (vibrationEnabled) {
            triggerSuccessVibration(context)
        }
        if (soundEnabled) {
            triggerSuccessSound(context)
        }
    }

    /**
     * Feedback sutil de clique / toque
     */
    fun triggerClick(context: Context, vibrationEnabled: Boolean = true) {
        if (!vibrationEnabled) return
        try {
            val vibrator = getVibrator(context)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(20, 100))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(20)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Feedback tátil de alerta / erro
     */
    fun triggerError(context: Context, vibrationEnabled: Boolean = true, soundEnabled: Boolean = true) {
        if (vibrationEnabled) {
            try {
                val vibrator = getVibrator(context)
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createWaveform(
                                longArrayOf(0, 60, 50, 80),
                                intArrayOf(0, 200, 0, 240),
                                -1
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 60, 50, 80), -1)
                    }
                }
            } catch (_: Exception) {}
        }

        if (soundEnabled) {
            try {
                getToneGenerator()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 100)
            } catch (_: Exception) {}
        }
    }

    private fun triggerSuccessVibration(context: Context) {
        try {
            val vibrator = getVibrator(context)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Padrão suave de confirmação em 2 pulsos leves: "tac-tac"
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 30, 45, 45),
                            intArrayOf(0, 160, 0, 230),
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 30, 45, 45), -1)
                }
            }
        } catch (_: Exception) {}
    }

    private fun triggerSuccessSound(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            // Toca um efeito suave padrão do sistema ou um pequeno tom agradável
            val played = audioManager?.let {
                it.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.6f)
                true
            } ?: false

            if (!played) {
                getToneGenerator()?.startTone(ToneGenerator.TONE_PROP_ACK, 60)
            }
        } catch (_: Exception) {
            try {
                getToneGenerator()?.startTone(ToneGenerator.TONE_PROP_ACK, 60)
            } catch (_: Exception) {}
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
