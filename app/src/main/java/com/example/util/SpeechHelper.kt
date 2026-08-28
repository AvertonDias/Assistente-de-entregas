package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import java.util.Locale

object SpeechHelper {

    /**
     * Processa a fala para nomes de pessoas:
     * - Remove palavras introdutórias ("o nome é", "chama", "nome")
     * - Capitaliza cada palavra ("maria da silva" -> "Maria Da Silva")
     */
    fun processSpokenName(rawSpoken: String): String {
        if (rawSpoken.isBlank()) return ""

        var cleaned = rawSpoken.trim()
        val prefixesToRemove = listOf(
            "^o nome é\\s+".toRegex(RegexOption.IGNORE_CASE),
            "^meu nome é\\s+".toRegex(RegexOption.IGNORE_CASE),
            "^nome\\s+".toRegex(RegexOption.IGNORE_CASE),
            "^o recebedor é\\s+".toRegex(RegexOption.IGNORE_CASE),
            "^recebedor\\s+".toRegex(RegexOption.IGNORE_CASE)
        )

        for (regex in prefixesToRemove) {
            cleaned = cleaned.replace(regex, "")
        }

        return AddressNormalizer.capitalizeWords(cleaned.trim())
    }

    /**
     * Processa a fala para documentos (CPF / RG / Números):
     * - Converte números falados por extenso em dígitos ("um dois três", "quatrocentos", "meia")
     * - Remove palavras como "meu cpf é", "rg", "documento número"
     * - Converte "ponto", "traço", "hífen", "letra x"
     * - Se atingir 11 dígitos numéricos, formata no padrão clássico de CPF (000.000.000-00)
     */
    fun processSpokenDocument(rawSpoken: String): String {
        if (rawSpoken.isBlank()) return ""

        var text = rawSpoken.lowercase(Locale.getDefault()).trim()

        // Mapeamento de palavras faladas para dígitos/caracteres
        val replacements = listOf(
            "meu cpf é" to "",
            "meu rg é" to "",
            "cpf é" to "",
            "rg é" to "",
            "documento é" to "",
            "documento número" to "",
            "documento" to "",
            "ponto" to "",
            "traço" to "",
            "hífen" to "",
            "barra" to "",
            "dígito" to "",
            "zero" to "0",
            "meia" to "6", // muito comum no Brasil falar "meia" para o número 6
            "um" to "1",
            "uma" to "1",
            "dois" to "2",
            "duas" to "2",
            "três" to "3",
            "tres" to "3",
            "quatro" to "4",
            "cinco" to "5",
            "seis" to "6",
            "sete" to "7",
            "oito" to "8",
            "nove" to "9",
            "letra x" to "X",
            "xis" to "X"
        )

        for ((target, rep) in replacements) {
            text = text.replace(Regex("\\b$target\\b", RegexOption.IGNORE_CASE), rep)
        }

        // Remove espaços entre dígitos e caracteres indesejados
        val cleaned = text.replace("[^0-9a-zA-Z]".toRegex(), "").trim()

        // Se tiver exatamente 11 dígitos, formata automaticamente como CPF
        return if (cleaned.length == 11 && cleaned.all { it.isDigit() }) {
            "${cleaned.substring(0, 3)}.${cleaned.substring(3, 6)}.${cleaned.substring(6, 9)}-${cleaned.substring(9, 11)}"
        } else {
            cleaned
        }
    }

    /**
     * Valida se um documento (CPF ou RG) é válido.
     */
    fun isValidDocument(doc: String): Boolean {
        val clean = doc.trim()
        if (clean.isBlank()) return false

        val digitsOnly = clean.filter { it.isDigit() }

        // Validação de CPF (11 dígitos numéricos)
        if (digitsOnly.length == 11) {
            return isValidCpf(digitsOnly)
        }

        // Validação de RG (7 a 10 caracteres alfanuméricos)
        val rgClean = clean.replace(".", "").replace("-", "").replace("/", "").trim()
        if (rgClean.length in 7..10 && rgClean.all { it.isDigit() || it.equals('x', ignoreCase = true) }) {
            return true
        }

        return false
    }

    private fun isValidCpf(digits: String): Boolean {
        if (digits.length != 11) return false
        if (digits.all { it == digits[0] }) return false

        val dv1 = (0..8).sumOf { (10 - it) * (digits[it] - '0') } % 11
        val firstCheck = if (dv1 < 2) 0 else 11 - dv1
        if (firstCheck != (digits[9] - '0')) return false

        val dv2 = (0..9).sumOf { (11 - it) * (digits[it] - '0') } % 11
        val secondCheck = if (dv2 < 2) 0 else 11 - dv2
        return secondCheck == (digits[10] - '0')
    }

    /**
     * Cria um listener simples de SpeechRecognizer para ouvir a voz do usuário.
     */
    fun startListening(
        context: Context,
        onReady: () -> Unit = {},
        onResult: (String) -> Unit,
        onError: (String) -> Unit = {}
    ): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "Reconhecimento de voz indisponível neste aparelho", Toast.LENGTH_SHORT).show()
            onError("Reconhecimento de voz não disponível")
            return null
        }

        return try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale agora...")
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onReady()
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Erro de áudio"
                        SpeechRecognizer.ERROR_CLIENT -> "Erro do cliente"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissão de microfone necessária"
                        SpeechRecognizer.ERROR_NETWORK -> "Erro de rede"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tempo de rede esgotado"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Nenhuma fala reconhecida"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconhecedor ocupado"
                        SpeechRecognizer.ERROR_SERVER -> "Erro no servidor"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nenhuma fala detectada"
                        else -> "Erro ao ouvir ($error)"
                    }
                    onError(message)
                    try { recognizer.destroy() } catch (_: Exception) {}
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    onResult(text)
                    try { recognizer.destroy() } catch (_: Exception) {}
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
            recognizer
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Falha ao iniciar reconhecimento")
            null
        }
    }
}
