package com.example.util

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

object AddressNormalizer {

    data class AddressComponents(
        val street: String,
        val number: String,
        val complement: String,
        val neighborhood: String = "",
        val cityState: String = ""
    ) {
        val fullDisplay: String
            get() = buildString {
                append(street.ifBlank { "Sem logradouro" })
                if (number.isNotBlank()) append(", $number")
                if (complement.isNotBlank()) append(" - $complement")
                if (neighborhood.isNotBlank()) append(" ($neighborhood)")
            }

        val hasComplement: Boolean
            get() = complement.isNotBlank()

        val unitBadgeText: String
            get() {
                if (complement.isBlank()) return ""
                val lower = complement.lowercase(Locale.ROOT)
                return when {
                    lower.contains("ap") -> "🏢 $complement"
                    lower.contains("bl") -> "🏢 $complement"
                    lower.contains("casa") || lower.contains("cs") -> "🏠 $complement"
                    lower.contains("sala") || lower.contains("sl") -> "💼 $complement"
                    lower.contains("qd") || lower.contains("lt") -> "📍 $complement"
                    else -> "🏷️ $complement"
                }
            }
    }

    // Static pre-compiled Regex constants to avoid allocation overhead during list processing & searches
    private val MULTIPLE_SPACES_REGEX = Regex("""\s+""")
    private val REGEX_HEADER_PREFIXES = Regex("""(?i)^(Próxima\s+entrega|Proxima\s+entrega|Endereço|Endereco|Destino|Entrega|Para|Destinatário):?\s*""")
    private val REGEX_DATE_TRAIL = Regex("""\s*\d{2}/\d{2}/\d{4}.*""")
    private val REGEX_PARENTHESES = Regex("""\s*\(.*?\)""")
    private val REGEX_NUMBERS_EXTRACT = Regex("""(?:nº|n°|n\.|n|º|°|numero|num)?\s*(\b\d+[A-Za-z]?\b)""", RegexOption.IGNORE_CASE)
    private val REGEX_NUM_MATCH = Regex("""\b\d+[A-Za-z]?\b""")
    private val REGEX_PUNCT_SPLIT = Regex("""[,;\-_/]+""")
    private val REGEX_CLEAN_STREET_WORDS = Regex("""(?i)(nº|n°|n\.|numero|num)""")
    private val REGEX_BLOCK_MATCH = Regex("""\b(?:BLOCO|BL|BLO)\s*([A-Z0-9]+)\b""")
    private val REGEX_UNIT_MATCH = Regex("""\b(?:APTO|APT|AP|APARTAMENTO|CASA|CS|SALA|SL|LOJA|TORRE|TR)\s*([0-9]+)\b""")
    private val REGEX_LOGRADOURO = Regex("""((?:Rua|R\.|Av\.|Avenida|Alameda|Al\.|Praça|Praca|Pca\.|Pr\.|Travessa|Trav\.|Tv\.|Rodovia|Rod\.|Estrada|Est\.|Beco|Viela)\s+[^,\n;]+?,?\s*(?:nº|n°|n\.|n|º|°|numero|num)?\s*(\d+[A-Za-z]?))""", RegexOption.IGNORE_CASE)
    private val REGEX_STREET_NUMBER = Regex("""^([^,\n;-]+?),?\s*(?:nº|n°|n\.|n|º|°|numero|num)?\s*(\d+[A-Za-z]?)""", RegexOption.IGNORE_CASE)
    private val REGEX_STREET_NUM_START = Regex("""\b\d+.*""")
    private val REGEX_DIACRITICS = Regex("""\p{InCombiningDiacriticalMarks}+""")
    private val REGEX_PUNCTUATION_NORM = Regex("""[,.\-/_#()ºª;]""")
    private val REGEX_APTO_REPLACE = Regex("""(?i)^(apartamento|apto|apt|ap)\.?\s*""")
    private val REGEX_BLOCO_REPLACE = Regex("""(?i)^(bloco|blo|bl)\.?\s*""")
    private val REGEX_TORRE_REPLACE = Regex("""(?i)^(torre|tor|tr)\.?\s*""")
    private val REGEX_CASA_REPLACE = Regex("""(?i)^(casa|cs)\.?\s*""")
    private val REGEX_SALA_REPLACE = Regex("""(?i)^(sala|sl)\.?\s*""")
    private val REGEX_QUADRA_REPLACE = Regex("""(?i)^(quadra|qd)\.?\s*""")
    private val REGEX_LOTE_REPLACE = Regex("""(?i)^(lote|lt)\.?\s*""")

    private val COMPLEMENT_PATTERN = Regex(
        """(?i)\b(?:(bloco|bl|blo)\.?\s*([0-9a-z\-]+)|(apartamento|apto|apt|ap)\.?\s*([0-9a-z\-]+)|(torre|tor|tr)\.?\s*([0-9a-z\-]+)|(casa|cs)\.?\s*([0-9a-z\-]+)|(sala|sl)\.?\s*([0-9a-z\-]+)|(conjunto|conj|cj)\.?\s*([0-9a-z\-]+)|(quadra|qd)\.?\s*([0-9a-z\-]+)|(lote|lt)\.?\s*([0-9a-z\-]+)|(andar|pavimento|pav)\.?\s*([0-9a-z\-]+)|(fundos|fds|frente|sobrado|térreo|terreo|galpão|galpao|subsolo))\b"""
    )

    /**
     * Extrai complementos comuns em endereços brasileiros (Apto, Bloco, Casa, Torre, Sala, etc.)
     * Ex: "Rua das Flores, 100 - Bloco B Apto 24" -> "Bloco B Apto 24"
     *     "Av Brasil 500, Ap 102" -> "Apto 102"
     *     "Rua 15, 45 Casa 2" -> "Casa 2"
     */
    fun extractComplement(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val clean = MULTIPLE_SPACES_REGEX.replace(text, " ").trim()
        val matches = COMPLEMENT_PATTERN.findAll(clean).toList()
        if (matches.isEmpty()) return ""

        val parts = mutableListOf<String>()
        for (m in matches) {
            val v = m.value.trim()
            if (v.isNotBlank()) {
                val formatted = formatComplementToken(v)
                if (!parts.contains(formatted)) {
                    parts.add(formatted)
                }
            }
        }
        return parts.joinToString(" ")
    }

    fun formatComplementToken(token: String): String {
        val t = token.trim()
        val lower = t.lowercase(Locale.ROOT)
        return when {
            lower.startsWith("ap") || lower.startsWith("apt") -> {
                val num = REGEX_APTO_REPLACE.replace(t, "").trim()
                if (num.isNotBlank()) "Apto $num" else "Apto"
            }
            lower.startsWith("bl") -> {
                val num = REGEX_BLOCO_REPLACE.replace(t, "").trim()
                if (num.isNotBlank()) "Bloco ${num.uppercase(Locale.ROOT)}" else "Bloco"
            }
            lower.startsWith("tor") || lower.startsWith("tr") -> {
                val num = REGEX_TORRE_REPLACE.replace(t, "").trim()
                if (num.isNotBlank()) "Torre ${num.uppercase(Locale.ROOT)}" else "Torre"
            }
            lower.startsWith("cs") || lower.startsWith("casa") -> {
                val num = REGEX_CASA_REPLACE.replace(t, "").trim()
                if (num.isNotBlank()) "Casa $num" else "Casa"
            }
            lower.startsWith("sl") || lower.startsWith("sala") -> {
                val num = REGEX_SALA_REPLACE.replace(t, "").trim()
                if (num.isNotBlank()) "Sala $num" else "Sala"
            }
            lower.startsWith("qd") || lower.startsWith("quadra") -> {
                val num = REGEX_QUADRA_REPLACE.replace(t, "").trim()
                if (num.isNotBlank()) "Qd $num" else "Quadra"
            }
            lower.startsWith("lt") || lower.startsWith("lote") -> {
                val num = REGEX_LOTE_REPLACE.replace(t, "").trim()
                if (num.isNotBlank()) "Lt $num" else "Lote"
            }
            lower.startsWith("fds") || lower == "fundos" -> "Fundos"
            lower == "frente" -> "Frente"
            lower == "sobrado" -> "Sobrado"
            lower.startsWith("terreo") || lower.startsWith("térreo") -> "Térreo"
            else -> capitalizeWords(t)
        }
    }

    /**
     * Analisa e divide um endereço em componentes separados (Rua, Número, Complemento/Unidade, Bairro).
     */
    fun parseAddressComponents(
        rawAddress: String?,
        rawNumber: String = "",
        rawComplement: String = "",
        rawNeighborhood: String = ""
    ): AddressComponents {
        if (rawAddress.isNullOrBlank()) {
            return AddressComponents(
                street = "",
                number = rawNumber.trim(),
                complement = rawComplement.trim(),
                neighborhood = rawNeighborhood.trim()
            )
        }

        var text = rawAddress.trim()
        
        // Remove prefixos de cabeçalho
        text = REGEX_HEADER_PREFIXES.replace(text, "")
        text = REGEX_DATE_TRAIL.replace(text, "")

        var explicitNumber = rawNumber.trim()
        var explicitComplement = rawComplement.trim()
        var explicitBairro = rawNeighborhood.trim()

        // 1. Extrai Bairro se presente após hífen no final
        if (explicitBairro.isBlank() && text.contains("-")) {
            val dashParts = text.split("-").map { it.trim() }
            if (dashParts.size >= 2) {
                val lastPart = dashParts.last()
                if (!lastPart.any { it.isDigit() } && !COMPLEMENT_PATTERN.containsMatchIn(lastPart) && lastPart.length > 2) {
                    explicitBairro = lastPart
                    text = dashParts.dropLast(1).joinToString(" - ").trim()
                }
            }
        }

        // 2. Extrai Complemento embutido no texto
        val foundComplement = extractComplement(text)
        val finalComplement = if (explicitComplement.isNotBlank()) {
            explicitComplement
        } else {
            foundComplement
        }

        var textWithoutComplement = text
        if (foundComplement.isNotBlank()) {
            textWithoutComplement = COMPLEMENT_PATTERN.replace(textWithoutComplement, " ")
        }

        // 3. Extrai Número
        var finalNumber = explicitNumber
        if (finalNumber.isBlank()) {
            val numMatches = REGEX_NUMBERS_EXTRACT.findAll(textWithoutComplement).toList()
            if (numMatches.isNotEmpty()) {
                val lastOrPrimary = numMatches.firstOrNull { it.groupValues.size > 1 } ?: numMatches.first()
                finalNumber = lastOrPrimary.groupValues.lastOrNull { it.isNotBlank() && it.any { c -> c.isDigit() } } ?: lastOrPrimary.value.trim()
            }
        }

        // 4. Extrai Logradouro limpo
        var street = textWithoutComplement
        if (finalNumber.isNotBlank()) {
            street = street.replace(Regex("""\b""" + Regex.escape(finalNumber) + """\b"""), " ")
        }
        street = REGEX_CLEAN_STREET_WORDS.replace(street, " ")
        street = REGEX_PUNCT_SPLIT.replace(street, " ")
        street = MULTIPLE_SPACES_REGEX.replace(street, " ").trim()

        if (street.isBlank()) {
            street = text.replace(finalNumber, "").trim(',', ' ', '-')
        }

        return AddressComponents(
            street = capitalizeWords(street),
            number = finalNumber.trim(),
            complement = finalComplement.trim(),
            neighborhood = capitalizeWords(explicitBairro)
        )
    }

    /**
     * Gera chave de ordenação para unidades e complementos (ex: Bloco A Apto 2 -> "BL_000A_UN_000002")
     * Garante que entregas no mesmo condomínio fiquem organizadas por bloco e número da unidade.
     */
    fun getUnitSortKey(complement: String?): String {
        if (complement.isNullOrBlank()) return "BL_0000_UN_000000"
        val compNorm = normalize(complement)

        var blockKey = "BL_ZZZZ"
        val blockMatch = REGEX_BLOCK_MATCH.find(compNorm)
        if (blockMatch != null) {
            val bVal = blockMatch.groupValues[1]
            blockKey = "BL_" + bVal.padStart(4, '0')
        }

        var unitNumKey = "UN_999999"
        val unitMatch = REGEX_UNIT_MATCH.find(compNorm)
        if (unitMatch != null) {
            val num = unitMatch.groupValues[1].toIntOrNull() ?: 999999
            unitNumKey = "UN_" + num.toString().padStart(6, '0')
        } else {
            val digits = compNorm.filter { it.isDigit() }
            if (digits.isNotBlank()) {
                val num = digits.toIntOrNull() ?: 999999
                unitNumKey = "UN_" + num.toString().padStart(6, '0')
            }
        }

        return "${blockKey}_${unitNumKey}_${compNorm}"
    }

    private val STREET_PREFIXES = listOf(
        "rua", "r.", "r",
        "avenida", "av.", "av",
        "travessa", "trav.", "trav", "tv.", "tv",
        "alameda", "al.", "al",
        "praca", "praça", "pca.", "pca", "pr.", "pr",
        "rodovia", "rod.", "rod",
        "estrada", "est.", "est",
        "viela", "beco", "bc.", "bc",
        "servidao", "servidão", "serv.", "serv",
        "passagem", "psg.", "psg",
        "largo", "lgo.", "lgo",
        "parque", "pq.", "pq",
        "reserva", "res.", "res",
        "jardim", "jd.", "jd",
        "vila", "vl.", "vl",
        "condominio", "cond.", "cond",
        "setor", "st.", "st",
        "quadra", "qd.", "qd",
        "bloco", "bl.", "bl",
        "chacara", "chácara", "ch.", "ch",
        "sitio", "sítio", "fazenda", "faz."
    )

    /**
     * Remove o tipo de logradouro (Rua, Av, Travessa, etc.) do início do endereço.
     * Ex: "Rua 15 de Novembro, 120" -> "15 de Novembro, 120"
     *     "Av. Brasil, 500" -> "Brasil, 500"
     *     "Travessa Carlos Gomes" -> "Carlos Gomes"
     */
    fun stripStreetType(rawText: String?): String {
        if (rawText.isNullOrBlank()) return ""
        var text = rawText.trim()
        
        // Remove pontuações comuns no início
        text = text.trimStart('-', ',', '.', ' ')

        for (prefix in STREET_PREFIXES) {
            val pLen = prefix.length
            if (text.length >= pLen) {
                val head = text.substring(0, pLen)
                if (head.equals(prefix, ignoreCase = true)) {
                    if (text.length == pLen) {
                        return ""
                    }
                    val nextChar = text[pLen]
                    if (nextChar == ' ' || nextChar == '.' || nextChar == ',' || nextChar == '-') {
                        val remaining = text.substring(pLen).trimStart(' ', '.', ',', '-')
                        return remaining.trim()
                    }
                }
            }
        }
        return text.trim()
    }

    /**
     * Gera chave padronizada para ordenação alfabética exclusivamente pelo nome da rua.
     */
    fun getStreetSortKey(rawText: String?): String {
        if (rawText.isNullOrBlank()) return "zzzzzzzz"
        val stripped = stripStreetType(rawText)
        val normalized = normalize(stripped)
        return if (normalized.isNotBlank()) normalized else normalize(rawText)
    }

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
     * Trata pontuações, complementos, formatos com ponto-e-vírgula (LOEC/DDA), bairros e cidades no texto.
     */
    fun extractStreetAndNumber(rawAddress: String?): String {
        if (rawAddress.isNullOrBlank()) return ""
        var text = rawAddress.trim()
        
        // Remove cabeçalhos comuns
        text = REGEX_HEADER_PREFIXES.replace(text, "")
        text = REGEX_DATE_TRAIL.replace(text, "")
        text = REGEX_PARENTHESES.replace(text, " ")

        // 0. Formato comum de aplicativos de entrega dos Correios / DDA / LOEC separados por ponto-e-vírgula (;)
        if (text.contains(";")) {
            val semiParts = text.split(";").map { it.trim() }.filter { it.isNotBlank() }
            val logradouroIndex = semiParts.indexOfFirst { part ->
                val pUpper = part.uppercase(Locale.ROOT)
                pUpper.startsWith("RUA ") || pUpper.startsWith("R.") || pUpper.startsWith("R ") ||
                pUpper.startsWith("AV") || pUpper.startsWith("AVENIDA") || pUpper.startsWith("ALAMEDA") ||
                pUpper.startsWith("AL.") || pUpper.startsWith("TRAVESSA") || pUpper.startsWith("TV") ||
                pUpper.startsWith("PRAÇA") || pUpper.startsWith("PRACA") || pUpper.startsWith("RODOVIA") ||
                pUpper.startsWith("ESTRADA") || pUpper.startsWith("BECO") || pUpper.startsWith("VIELA")
            }

            if (logradouroIndex != -1) {
                val streetPart = semiParts[logradouroIndex]
                var numberFound = ""
                if (logradouroIndex + 1 < semiParts.size) {
                    val nextPart = semiParts[logradouroIndex + 1]
                    val numMatch = REGEX_NUM_MATCH.find(nextPart)
                    if (numMatch != null) {
                        numberFound = numMatch.value
                    }
                }
                if (numberFound.isBlank()) {
                    val numInStreet = REGEX_NUM_MATCH.find(streetPart)
                    if (numInStreet != null) {
                        numberFound = numInStreet.value
                    }
                }

                if (numberFound.isNotBlank()) {
                    val cleanStreet = REGEX_STREET_NUM_START.replace(streetPart, "").trimEnd(',', ' ', ';')
                    return cleanExtractedStreet("$cleanStreet, $numberFound")
                } else {
                    return cleanExtractedStreet(streetPart)
                }
            }
        }

        // 1. Procurar padrão clássico de Logradouro + Nome da Rua + Número
        val match = REGEX_LOGRADOURO.find(text)
        if (match != null) {
            val fullMatch = match.value.trim()
            return cleanExtractedStreet(fullMatch)
        }

        // 2. Se não tem palavra de logradouro explícita, procurar qualquer nome seguido de número
        val secondMatch = REGEX_STREET_NUMBER.find(text)
        if (secondMatch != null && secondMatch.value.any { it.isDigit() }) {
            return cleanExtractedStreet(secondMatch.value.trim())
        }

        // 3. Fallback: Se tiver vírgula, traço ou ponto-e-vírgula separando partes
        val parts = text.split(",", "-", ";")
        if (parts.isNotEmpty()) {
            val first = parts[0].trim()
            if (first.any { it.isDigit() }) {
                return cleanExtractedStreet(first)
            }
            if (parts.size > 1) {
                val second = parts[1].trim()
                val numMatch = REGEX_NUM_MATCH.find(second)
                if (numMatch != null) {
                    return cleanExtractedStreet("$first, ${numMatch.value}")
                }
            }
            return cleanExtractedStreet(first)
        }

        return cleanExtractedStreet(text)
    }

    private fun cleanExtractedStreet(text: String): String {
        var res = MULTIPLE_SPACES_REGEX.replace(text, " ").trim()
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
        text = REGEX_DIACRITICS.replace(decomposed, "")

        // 2. Substituir abreviações conhecidas
        for ((abbr, full) in ABBREVIATIONS) {
            val normalizedAbbr = REGEX_DIACRITICS.replace(Normalizer.normalize(abbr, Normalizer.Form.NFD), "").uppercase(Locale.ROOT)

            if (text.startsWith("$normalizedAbbr ") || text.startsWith(normalizedAbbr)) {
                text = text.replaceFirst(normalizedAbbr, full)
            }
            text = text.replace(" $normalizedAbbr ", " $full ")
            text = text.replace(" $normalizedAbbr", " $full")
        }

        // 3. Remover caracteres de pontuação
        text = REGEX_PUNCTUATION_NORM.replace(text, " ")

        // 4. Remover múltiplos espaços em branco
        text = MULTIPLE_SPACES_REGEX.replace(text, " ").trim()

        return text
    }

    /**
     * Verifica se duas strings de endereço possuem alta probabilidade de correspondência
     */
    fun matches(query: String, target: String): Boolean {
        val nQuery = normalize(query)
        val nTarget = normalize(target)

        if (nQuery.isBlank() || nTarget.isBlank()) return false

        // Validação estrita de número: se ambos têm números extraídos e diferem, não correspondem
        val parsedQ = parseAddressComponents(query)
        val parsedT = parseAddressComponents(target)
        if (parsedQ.number.isNotBlank() && parsedT.number.isNotBlank() && parsedQ.number != parsedT.number) {
            return false
        }

        if (nTarget.contains(nQuery) || nQuery.contains(nTarget)) return true

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
        return REGEX_NUM_MATCH.findAll(text).map { it.value.uppercase(Locale.ROOT) }.toList()
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
     * Compara se dois endereços correspondem ao MESMO local com alta tolerância
     */
    fun matchesPrecise(
        queryAddress: String,
        targetAddress: String,
        targetNumber: String = "",
        targetComplement: String = ""
    ): Boolean {
        val nQuery = normalize(queryAddress)
        val combinedTarget = buildString {
            append(targetAddress)
            if (targetNumber.isNotBlank() && !targetAddress.contains(targetNumber)) {
                append(", ").append(targetNumber)
            }
            if (targetComplement.isNotBlank() && !targetAddress.contains(targetComplement)) {
                append(" ").append(targetComplement)
            }
        }
        val nTarget = normalize(combinedTarget)

        if (nQuery.isBlank() || nTarget.isBlank()) return false

        val parsedQuery = parseAddressComponents(queryAddress)
        val parsedTarget = parseAddressComponents(
            rawAddress = targetAddress,
            rawNumber = targetNumber,
            rawComplement = targetComplement
        )

        // 1. Validação estrita do Número do Imóvel / Casa (DEVE SER FEITA ANTES DE CONTAINS)
        if (parsedQuery.number.isNotBlank() && parsedTarget.number.isNotBlank()) {
            if (parsedQuery.number != parsedTarget.number) {
                // Números da casa/imóvel são diferentes (ex: 10 vs 20 ou 10 vs 100) -> NÃO CORRESPONDE
                return false
            }
        } else if (parsedQuery.number.isNotBlank() && parsedTarget.number.isBlank()) {
            // Busca especifica número (ex: 10), mas o cadastro não possui número registrado -> NÃO CORRESPONDE
            return false
        } else {
            // Fallback de segurança com extração direta de números se a decomposição de componentes falhou
            val queryNumbers = extractNumbers(nQuery)
            val targetNumbers = if (targetNumber.isNotBlank()) {
                extractNumbers(targetNumber).ifEmpty { extractNumbers(nTarget) }
            } else {
                extractNumbers(nTarget)
            }

            if (queryNumbers.isNotEmpty() && targetNumbers.isNotEmpty()) {
                val hasCommonNumber = queryNumbers.any { qNum ->
                    targetNumbers.any { tNum ->
                        qNum == tNum || 
                        (qNum.filter { it.isDigit() } == tNum.filter { it.isDigit() } && qNum.filter { it.isDigit() }.isNotEmpty())
                    }
                }
                if (!hasCommonNumber) {
                    return false
                }
            } else if (queryNumbers.isNotEmpty() && targetNumbers.isEmpty()) {
                return false
            }
        }

        // 2. Validação de Complemento / Unidade / Apartamento (se informado em ambos)
        val queryComp = if (parsedQuery.complement.isNotBlank()) parsedQuery.complement else extractComplement(queryAddress)
        val targetComp = if (parsedTarget.complement.isNotBlank()) parsedTarget.complement else if (targetComplement.isNotBlank()) targetComplement else extractComplement(targetAddress)

        if (queryComp.isNotBlank() && targetComp.isNotBlank()) {
            val nQueryComp = normalize(queryComp)
            val nTargetComp = normalize(targetComp)
            val qCompDigits = nQueryComp.filter { it.isDigit() }
            val tCompDigits = nTargetComp.filter { it.isDigit() }
            if (qCompDigits.isNotBlank() && tCompDigits.isNotBlank() && qCompDigits != tCompDigits) {
                // Apartamentos/unidades diferentes (ex: Apto 101 vs Apto 102) -> NÃO CORRESPONDE
                return false
            }
        }

        // 3. Se após validações estritas de número e complemento os textos forem idênticos
        if (nQuery == nTarget) {
            return true
        }

        // 4. Validação do Nome da Rua / Logradouro (palavras significativas)
        val queryWords = extractStreetSignificantWords(nQuery)
        val targetWords = extractStreetSignificantWords(nTarget)

        if (queryWords.isEmpty() || targetWords.isEmpty()) {
            return nQuery.contains(nTarget) || nTarget.contains(nQuery)
        }

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

        return targetRatio >= 0.6f || (queryRatio >= 0.6f && targetInQueryCount > 0) || (targetInQueryCount >= 2) || (targetWords.size == 1 && targetInQueryCount == 1)
    }

    /**
     * Capitaliza a primeira letra de cada palavra (Title Case)
     */
    fun capitalizeWords(input: String): String {
        if (input.isBlank()) return input
        val particles = setOf("da", "de", "do", "das", "dos", "du", "e")
        val words = MULTIPLE_SPACES_REGEX.split(input.trim())
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

    /**
     * Distância de Levenshtein otimizada com 2 vetores 1D (O(N) espaço) para não inflar o Garbage Collector
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        if (m == 0) return n
        if (n == 0) return m

        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[n]
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

