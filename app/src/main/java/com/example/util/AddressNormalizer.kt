package com.example.util

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

object AddressNormalizer {

    private val ABBREVIATIONS = mapOf(
        "R." to "RUA",
        "R " to "RUA ",
        "AV." to "AVENIDA",
        "AV " to "AVENIDA ",
        "CEL." to "CORONEL",
        "CEL " to "CORONEL ",
        "AL." to "ALAMEDA",
        "AL " to "ALAMEDA ",
        "TRAV." to "TRAVESSA",
        "TRAV " to "TRAVESSA ",
        "TV." to "TRAVESSA",
        "TV " to "TRAVESSA ",
        "PCA." to "PRACA",
        "PCA " to "PRACA ",
        "PR." to "PRACA",
        "ROD." to "RODOVIA",
        "ROD " to "RODOVIA ",
        "DR." to "DOUTOR",
        "DR " to "DOUTOR ",
        "PROF." to "PROFESSOR",
        "PROF " to "PROFESSOR ",
        "STO." to "SANTO",
        "STA." to "SANTA",
        "Nº" to " ",
        "N." to " ",
        "NUM." to " ",
        "NUMERO" to " "
    )

    /**
     * Extrai apenas a rua e o número da casa a partir de um texto de endereço lido.
     * Trata pontuações, complementos, bairros e cidades no texto.
     */
    fun extractStreetAndNumber(rawAddress: String?): String {
        if (rawAddress.isNullOrBlank()) return ""
        var text = rawAddress.trim()
        
        // Remove cabeçalhos comuns
        text = text.replace("(?i)^(Próxima\\s+entrega|Proxima\\s+entrega|Endereço|Endereco|Destino|Entrega|Para|Destinatário):?\\s*".toRegex(), "")
        text = text.replace(Regex("\\s*\\d{2}/\\d{2}/\\d{4}.*"), "")
        text = text.replace(Regex("\\s*\\(.*?\\)"), " ")

        // 1. Procurar padrão clássico de Logradouro + Nome da Rua + Número (ex: "Rua Presidente Vargas, 450", "Av. Brasil 1000", "R. A, 12B")
        val logradouroRegex = Regex("""((?:Rua|R\.|Av\.|Avenida|Alameda|Al\.|Praça|Praca|Pca\.|Pr\.|Travessa|Trav\.|Tv\.|Rodovia|Rod\.|Estrada|Est\.|Beco|Viela)\s+[^,\n-]+?,?\s*(?:nº|n°|n\.|n|º|°|numero|num)?\s*(\d+[A-Za-z]?))""", RegexOption.IGNORE_CASE)
        val match = logradouroRegex.find(text)
        if (match != null) {
            val fullMatch = match.value.trim()
            return cleanExtractedStreet(fullMatch)
        }

        // 2. Se não tem palavra de logradouro explícita, procurar qualquer nome seguido de número antes de vírgula/hífen
        // Ex: "Presidente Vargas, 450 - Centro - São Paulo" -> "Presidente Vargas, 450"
        val streetNumberRegex = Regex("""^([^,\n-]+?),?\s*(?:nº|n°|n\.|n|º|°|numero|num)?\s*(\d+[A-Za-z]?)""", RegexOption.IGNORE_CASE)
        val secondMatch = streetNumberRegex.find(text)
        if (secondMatch != null && secondMatch.value.any { it.isDigit() }) {
            return cleanExtractedStreet(secondMatch.value.trim())
        }

        // 3. Fallback: Se tiver vírgula ou traço separando partes (ex: "Rua das Flores, 123, Bairro Bela Vista")
        val parts = text.split(",", "-")
        if (parts.isNotEmpty()) {
            val first = parts[0].trim()
            if (first.any { it.isDigit() }) {
                return cleanExtractedStreet(first)
            }
            if (parts.size > 1) {
                val second = parts[1].trim()
                val numMatch = Regex("""\b\d+[A-Za-z]?\b""").find(second)
                if (numMatch != null) {
                    return cleanExtractedStreet("$first, ${numMatch.value}")
                }
            }
            return cleanExtractedStreet(first)
        }

        return cleanExtractedStreet(text)
    }

    private fun cleanExtractedStreet(text: String): String {
        var res = text.replace(Regex("""\s+"""), " ").trim()
        res = res.trimEnd(',', '-', '.', ' ')
        return res
    }

    /**
     * Normaliza um endereço para fins de comparação e pesquisa.
     * Não altera o endereço original no banco de dados.
     */
    fun normalize(rawText: String?): String {
        if (rawText.isNullOrBlank()) return ""

        var text = rawText.trim().uppercase(Locale.ROOT)

        // 1. Remover acentos (á -> a, ç -> c)
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        text = pattern.matcher(decomposed).replaceAll("")

        // 2. Substituir abreviações conhecidas
        for ((abbr, full) in ABBREVIATIONS) {
            val normalizedAbbr = Normalizer.normalize(abbr, Normalizer.Form.NFD)
                .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
                .uppercase(Locale.ROOT)

            if (text.startsWith("$normalizedAbbr ") || text.startsWith(normalizedAbbr)) {
                text = text.replaceFirst(normalizedAbbr, full)
            }
            text = text.replace(" $normalizedAbbr ", " $full ")
            text = text.replace(" $normalizedAbbr", " $full")
        }

        // 3. Remover caracteres de pontuação (pontos, vírgulas, hífens, barras)
        text = text.replace("[,.\\-/_#()ºª]".toRegex(), " ")

        // 4. Remover múltiplos espaços em branco
        text = text.replace("\\s+".toRegex(), " ").trim()

        return text
    }

    /**
     * Verifica se duas strings de endereço possuem alta probabilidade de correspondência
     */
    fun matches(query: String, target: String): Boolean {
        val nQuery = normalize(query)
        val nTarget = normalize(target)

        if (nQuery.isBlank() || nTarget.isBlank()) return false
        if (nTarget.contains(nQuery) || nQuery.contains(nTarget)) return true

        // Comparação token a token (todas as palavras da query presentes no alvo)
        val queryTokens = nQuery.split(" ").filter { it.length > 1 }
        val targetTokens = nTarget.split(" ").toSet()

        if (queryTokens.isEmpty()) return false
        val matchedCount = queryTokens.count { targetTokens.contains(it) || nTarget.contains(it) }

        return (matchedCount.toFloat() / queryTokens.size) >= 0.75f
    }

    private val STOP_WORDS = setOf(
        "DE", "DA", "DO", "DOS", "DAS", "E", "EM", "NO", "NA", "NOS", "NAS", "AO", "AOS",
        "RUA", "R", "AVENIDA", "AV", "ALAMEDA", "AL", "TRAVESSA", "TRAV", "TV", "PRACA", "PCA", "PR",
        "RODOVIA", "ROD", "ESTRADA", "EST", "CORONEL", "CEL", "DOUTOR", "DR", "PROFESSOR", "PROF",
        "SANTO", "SANTA", "SAO", "BELA", "JARDIM", "JD", "VILA", "VL", "CENTRO", "CIDADE", "BAIRRO",
        "NUMERO", "NUM", "APTO", "APT", "APARTAMENTO", "BLOCO", "BL", "CASA", "CS", "LOTE", "LT",
        "QUADRA", "QD", "ANDAR", "SALA", "SL", "KM", "PROXIMO", "PROXIMA", "ENTREGA", "ENDERECO", "DESTINO"
    )

    fun extractNumbers(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val regex = Regex("""\b\d+[A-Za-z]?\b""")
        return regex.findAll(text).map { it.value.uppercase(Locale.ROOT) }.toList()
    }

    fun extractStreetSignificantWords(text: String?): List<String> {
        val normalized = normalize(text)
        return normalized.split(" ")
            .map { it.trim() }
            .filter { word ->
                word.length >= 2 &&
                !word.all { it.isDigit() } &&
                word !in STOP_WORDS
            }
    }

    /**
     * Compara se dois endereços correspondem ao MESMO local com alta tolerância a formatação,
     * pontuações, vírgulas, abreviações (R./Rua, Av./Avenida) e textos adicionais de bairros/cidades.
     */
    fun matchesPrecise(queryAddress: String, targetAddress: String, targetNumber: String = ""): Boolean {
        val nQuery = normalize(queryAddress)
        val combinedTarget = if (targetNumber.isNotBlank() && !targetAddress.contains(targetNumber)) {
            "$targetAddress, $targetNumber"
        } else {
            targetAddress
        }
        val nTarget = normalize(combinedTarget)

        if (nQuery.isBlank() || nTarget.isBlank()) return false

        // Se uma contém a outra diretamente após normalização
        if (nQuery == nTarget || nQuery.contains(nTarget) || nTarget.contains(nQuery)) {
            return true
        }

        // Extrai números
        val queryNumbers = extractNumbers(nQuery)
        val targetNumbers = extractNumbers(nTarget)

        // Se ambos têm números, DEVE haver número coincidente (ex: 450 == 450 ou 450A == 450)
        if (queryNumbers.isNotEmpty() && targetNumbers.isNotEmpty()) {
            val hasCommonNumber = queryNumbers.any { qNum ->
                targetNumbers.any { tNum ->
                    qNum == tNum || 
                    (qNum.filter { it.isDigit() } == tNum.filter { it.isDigit() } && qNum.filter { it.isDigit() }.isNotEmpty())
                }
            }
            if (!hasCommonNumber) {
                // Números explicitamente diferentes (ex: casa 450 vs casa 120)
                return false
            }
        }

        // Palavras significativas do logradouro (sem números e sem stop words como RUA, DE, DA, etc.)
        val queryWords = extractStreetSignificantWords(nQuery)
        val targetWords = extractStreetSignificantWords(nTarget)

        if (queryWords.isEmpty() || targetWords.isEmpty()) {
            return nQuery.contains(nTarget) || nTarget.contains(nQuery)
        }

        // Verifica correspondência de palavras-chave da rua
        val targetInQueryCount = targetWords.count { word ->
            nQuery.contains(word) || queryWords.any { qWord ->
                isSimilarWord(qWord, word)
            }
        }
        val queryInTargetCount = queryWords.count { word ->
            nTarget.contains(word) || targetWords.any { tWord ->
                isSimilarWord(tWord, word)
            }
        }

        val targetRatio = targetInQueryCount.toFloat() / targetWords.size
        val queryRatio = queryInTargetCount.toFloat() / queryWords.size

        // Se todas ou a grande maioria das palavras do logradouro cadastrado foram achadas no texto da tela
        return targetRatio >= 0.6f || (queryRatio >= 0.6f && targetInQueryCount > 0) || (targetInQueryCount >= 2) || (targetWords.size == 1 && targetInQueryCount == 1)
    }

    /**
     * Capitaliza a primeira letra de cada palavra (Title Case), mantendo preposições (da, de, do, das, dos, e, du) em minúsculo no meio do nome.
     * Ex: "FULANO DA SILVA" -> "Fulano da Silva"
     */
    fun capitalizeWords(input: String): String {
        if (input.isBlank()) return input
        val particles = setOf("da", "de", "do", "das", "dos", "du", "e")
        val words = input.trim().split(Regex("\\s+"))
        return words.mapIndexed { index, word ->
            if (word.isEmpty()) return@mapIndexed ""
            val lowerWord = word.lowercase(Locale.getDefault())
            if (index > 0 && particles.contains(lowerWord)) {
                lowerWord
            } else {
                lowerWord.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }.joinToString(" ")
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    fun isSimilarWord(w1: String, w2: String): Boolean {
        if (w1 == w2) return true
        if (w1.isBlank() || w2.isBlank()) return false
        if (w1.length >= 4 && w2.length >= 4 && (w1.startsWith(w2) || w2.startsWith(w1))) return true

        val maxLen = maxOf(w1.length, w2.length)
        if (maxLen < 3) return w1 == w2

        val dist = levenshteinDistance(w1, w2)
        return when {
            maxLen <= 4 -> dist <= 1
            maxLen <= 8 -> dist <= 2
            else -> dist <= 3
        }
    }
}
