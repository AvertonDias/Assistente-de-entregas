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
    private val REGEX_HEADER_PREFIXES = Regex("""(?i)^(?:Próxima\s+entrega|Proxima\s+entrega|Entrega\s+atual|Endereço|Endereco|Destino|Entrega|Para|Destinatário|Destinatario|DETALHES|Detalhes|DETALHE|Detalhe|Depois|Objeto)[:\s-]+""")
    private val REGEX_CITY_UF_TRAIL = Regex("""(?i)[,\-]\s*[A-Za-zÀ-ÿ\s\.\']{2,40}\s*/\s*(?:AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)\b\s*$""")
    private val REGEX_DATE_TRAIL = Regex("""\s*\d{2}/\d{2}/\d{4}.*""")
    private val REGEX_PARENTHESES = Regex("""\s*\(.*?\)""")
    private val REGEX_NUMBERS_EXTRACT = Regex("""(?:nº|n°|n\.|n|º|°|numero|num)?\s*(\b\d{1,3}(?:\.\d{3})+[A-Za-z]?\b|\b\d+[A-Za-z]?\b)""", RegexOption.IGNORE_CASE)
    private val REGEX_NUM_MATCH = Regex("""\b\d{1,3}(?:\.\d{3})+[A-Za-z]?\b|\b\d+[A-Za-z]?\b""")
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
    private val REGEX_TORRE_REPLACE = Regex("""(?i)^(torre|tor)\.?\s*""")
    private val REGEX_CASA_REPLACE = Regex("""(?i)^(casa|cs)\.?\s*""")
    private val REGEX_SALA_REPLACE = Regex("""(?i)^(sala|sl)\.?\s*""")
    private val REGEX_QUADRA_REPLACE = Regex("""(?i)^(quadra|qd)\.?\s*""")
    private val REGEX_LOTE_REPLACE = Regex("""(?i)^(lote|lt)\.?\s*""")

    private val REGEX_CLEAN_ABALHADORES = Regex("""(?i)\b(?:torre\s+)?abalhadores\b""")
    private val REGEX_P1_IS_PURE_NUMBER = Regex("""(?i)^\s*(?:nº|n°|n\.|numero|num)?\s*(?:\d{1,3}(?:\.\d{3})+[A-Za-z]?|\d+[A-Za-z]?|s/n|sn)\s*$""")
    private val REGEX_LEADING_NUM_MATCH = Regex("""(?i)^\s*(?:nº|n°|n\.|numero|num)?\s*(\b\d{1,3}(?:\.\d{3})+[A-Za-z]?\b|\b\d+[A-Za-z]?\b|S/N|SN)\b""")
    private val REGEX_EXPLICIT_PREFIX_NUM = Regex("""(?i)\b(?:nº|n°|n\.|numero|num)\s*(\d{1,3}(?:\.\d{3})+[A-Za-z]?|\d+[A-Za-z]?(?:-[A-Za-z0-9]+)?)\b""")
    private val REGEX_COMMA_NUM = Regex("""(?i)[,\-]\s*(?:nº|n°|n\.|numero|num)?\s*(\d{1,3}(?:\.\d{3})+[A-Za-z]?|\d+[A-Za-z]?(?:-[A-Za-z0-9]+)?)\b""")
    private val REGEX_SN_MATCH = Regex("""(?i)\b(?:s/n|sn|sem\s+n[uú]mero)\b""")
    private val REGEX_DIGIT_DOT_DASH = Regex("""(?<=\d)[.\-_](?=\d)""")
    private val REGEX_RUA_X = Regex("""(?i)\b(RUA|AV|AVENIDA|TRAVESSA|ALAMEDA|SETOR|QUADRA|BLOCO)\s+X\b""")
    private val REGEX_RUA_V = Regex("""(?i)\b(RUA|AV|AVENIDA|TRAVESSA|ALAMEDA|SETOR|QUADRA|BLOCO)\s+V\b""")
    private val REGEX_PEDRO_I = Regex("""(?i)\b(PEDRO|DOM PEDRO|D PEDRO)\s+I\b""")
    private val REGEX_PEDRO_II = Regex("""(?i)\b(PEDRO|DOM PEDRO|D PEDRO)\s+II\b""")
    private val REGEX_ORDINAL_DIGITS = Regex("""\b(\d+)[ºª°]\b""")
    private val REGEX_GLUED_PREFIXES = Regex("""(?i)\b(DR|DRA|CEL|PROF|STA|STO|PCA|TRAV|ROD|CAP|MAJ|TEN)\.?([A-Za-zÀ-ÿ]{3,})\b""")

    private val COMPLEMENT_PATTERN = Regex(
        """(?i)\b(?:(apartamento|apto|apt|ap)\.?\s*(\d+[A-Za-z]?|[A-Za-z]\b)|(bloco|blo|bl)\.?\s+([0-9A-Za-z]{1,4})|(torre|tor)\.?\s+([0-9A-Za-z]{1,4}|norte|sul|leste|oeste)|(casa|cs)\.?\s*(\d+[A-Za-z]?|[A-Za-z]\b)|(sala|sl)\.?\s*(\d+[A-Za-z]?|[A-Za-z]\b)|(conjunto|conj|cj)\.?\s*(\d+[A-Za-z]?|[A-Za-z]\b)|(quadra|qd)\.?\s*([0-9A-Za-z]{1,4})|(lote|lt)\.?\s*([0-9A-Za-z]{1,4})|(andar|pavimento|pav)\.?\s*(\d{1,3}[ºª]?|[0-9A-Za-z]{1,3})|(fundos|fds|frente|sobrado|térreo|terreo|galpão|galpao|subsolo))\b"""
    )

    // Mapeamento de numerais romanos comuns em logradouros brasileiros (ex: "XV de Julho", "VII de Setembro", "Rua XV")
    private val ROMAN_NUMERALS_MAP = linkedMapOf(
        "XXXI" to "31", "XXX" to "30", "XXIX" to "29", "XXVIII" to "28", "XXVII" to "27",
        "XXVI" to "26", "XXV" to "25", "XXIV" to "24", "XXIII" to "23", "XXII" to "22",
        "XXI" to "21", "XX" to "20", "XIX" to "19", "XVIII" to "18", "XVII" to "17",
        "XVI" to "16", "XV" to "15", "XIV" to "14", "XIII" to "13", "XII" to "12",
        "XI" to "11", "X" to "10", "IX" to "9", "VIII" to "8", "VII" to "7", "VI" to "6",
        "V" to "5", "IV" to "4", "III" to "3", "II" to "2", "I" to "1"
    )

    // Mapeamento de números por extenso ou ordinais em datas de logradouros (ex: "Quinze de Julho", "Primeiro de Maio")
    private val WRITTEN_DATE_NUMBERS_MAP = linkedMapOf(
        "TRINTA E UM" to "31", "TRINTA" to "30", "VINTE E NOVE" to "29", "VINTE E OITO" to "28",
        "VINTE E SETE" to "27", "VINTE E SEIS" to "26", "VINTE E CINCO" to "25", "VINTE E QUATRO" to "24",
        "VINTE E TRES" to "23", "VINTE E TRÊS" to "23", "VINTE E DOIS" to "22", "VINTE E UM" to "21",
        "VINTE" to "20", "DEZENOVE" to "19", "DEZOITO" to "18", "DEZESSETE" to "17",
        "DEZESSEIS" to "16", "QUINZE" to "15", "QUATORZE" to "14", "CATORZE" to "14",
        "TREZE" to "13", "DOZE" to "12", "ONZE" to "11", "DEZ" to "10",
        "NOVE" to "9", "OITO" to "8", "SETE" to "7", "SEIS" to "6",
        "CINCO" to "5", "QUATRO" to "4", "TRES" to "3", "TRÊS" to "3",
        "DOIS" to "2", "PRIMEIRO" to "1", "1º" to "1", "1°" to "1", "1O" to "1", "1A" to "1"
    )

    private val PRECOMPILED_WRITTEN_DATE_PATTERNS: List<Pair<Regex, String>> by lazy {
        WRITTEN_DATE_NUMBERS_MAP.map { (word, num) ->
            val normWord = REGEX_DIACRITICS.replace(Normalizer.normalize(word, Normalizer.Form.NFD), "").uppercase(Locale.ROOT)
            Regex("""(?i)\b""" + Regex.escape(normWord) + """\s+DE\s+(JANEIRO|FEVEREIRO|MARCO|ABRIL|MAIO|JUNHO|JULHO|AGOSTO|SETEMBRO|OUTUBRO|NOVEMBRO|DEZEMBRO)\b""") to "$num DE $1"
        }
    }

    private val PRECOMPILED_ROMAN_DATE_PATTERNS: List<Pair<Regex, String>> by lazy {
        ROMAN_NUMERALS_MAP.map { (roman, num) ->
            Regex("""(?i)\b""" + Regex.escape(roman) + """\s+DE\s+(JANEIRO|FEVEREIRO|MARCO|ABRIL|MAIO|JUNHO|JULHO|AGOSTO|SETEMBRO|OUTUBRO|NOVEMBRO|DEZEMBRO)\b""") to "$num DE $1"
        }
    }

    private val PRECOMPILED_ROMAN_ISOLATED_PATTERNS: List<Pair<Regex, String>> by lazy {
        ROMAN_NUMERALS_MAP.filter { it.key.length >= 2 }.map { (roman, num) ->
            Regex("""\b""" + Regex.escape(roman) + """\b""") to num
        }
    }

    // Regex para identificar datas de logradouros: "15 de Julho", "XV de Novembro", "7 de Setembro", etc.
    private val REGEX_DATE_IN_STREET = Regex(
        """(?i)\b(\d{1,2}|1º|1°|[0-9]+)\s+de\s+(?:janeiro|fevereiro|março|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)\b"""
    )

    fun normalizeNumber(num: String?): String {
        if (num.isNullOrBlank()) return ""
        var cleaned = num.trim().uppercase(Locale.ROOT)
        if (cleaned == "S/N" || cleaned == "SN" || cleaned.contains("SEM NUMERO") || cleaned.contains("SEM NÚMERO")) {
            return "S/N"
        }
        // Remove separador de milhar (ex: 1.843 -> 1843)
        cleaned = cleaned.replace(".", "")
        val digitsOnly = cleaned.filter { it.isDigit() }
        val nonDigits = cleaned.filter { !it.isDigit() }
        if (digitsOnly.isNotBlank()) {
            val stripped = digitsOnly.trimStart('0').ifEmpty { "0" }
            return "$stripped$nonDigits"
        }
        return cleaned
    }

    fun areNumbersMatching(n1: String, n2: String): Boolean {
        if (n1.isBlank() || n2.isBlank()) return false
        val norm1 = normalizeNumber(n1)
        val norm2 = normalizeNumber(n2)
        if (norm1 == norm2) return true
        val raw1 = n1.trim().uppercase(Locale.ROOT).replace(".", "")
        val raw2 = n2.trim().uppercase(Locale.ROOT).replace(".", "")
        return raw1 == raw2
    }

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
        if (t.isBlank()) return ""

        // Se contiver múltiplos complementos (ex: "Bloco B Apto 101" ou "Casa 2 Fundos"), formata cada um
        val matches = COMPLEMENT_PATTERN.findAll(t).toList()
        if (matches.size > 1) {
            val parts = matches.map { formatSingleComplementToken(it.value.trim()) }.filter { it.isNotBlank() }
            if (parts.isNotEmpty()) {
                return parts.joinToString(" ")
            }
        }

        return formatSingleComplementToken(t)
    }

    private fun formatSingleComplementToken(token: String): String {
        val t = token.trim()
        if (t.isBlank()) return ""
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
            lower.startsWith("torre") || lower.startsWith("tor") -> {
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
            lower.startsWith("cj") || lower.startsWith("conj") || lower.startsWith("conjunto") -> {
                val num = Regex("""(?i)^(conjunto|conj|cj)\.?\s*""").replace(t, "").trim()
                if (num.isNotBlank()) "Conj $num" else "Conjunto"
            }
            lower.startsWith("qd") || lower.startsWith("quadra") -> {
                val num = REGEX_QUADRA_REPLACE.replace(t, "").trim()
                if (num.isNotBlank()) "Qd $num" else "Quadra"
            }
            lower.startsWith("lt") || lower.startsWith("lote") -> {
                val num = REGEX_LOTE_REPLACE.replace(t, "").trim()
                if (num.isNotBlank()) "Lt $num" else "Lote"
            }
            lower.startsWith("andar") || lower.startsWith("pav") || lower.startsWith("pavimento") -> {
                val num = Regex("""(?i)^(andar|pavimento|pav)\.?\s*""").replace(t, "").trim()
                if (num.isNotBlank()) "$num Andar" else "Andar"
            }
            lower.startsWith("fds") || lower == "fundos" -> "Fundos"
            lower == "frente" -> "Frente"
            lower == "sobrado" -> "Sobrado"
            lower.startsWith("terreo") || lower.startsWith("térreo") -> "Térreo"
            lower.startsWith("galp") || lower.startsWith("galpao") || lower.startsWith("galpão") -> "Galpão"
            lower.startsWith("subsolo") -> "Subsolo"
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

        val rawTrim = rawAddress.trim()
        val rawLower = rawTrim.lowercase(Locale.ROOT)
        if (rawLower.contains("nenhum endereço") || rawLower.contains("nenhum endereco") || rawLower.contains("endereço não detectado") || rawLower.contains("endereco nao detectado")) {
            return AddressComponents(
                street = "",
                number = rawNumber.trim(),
                complement = rawComplement.trim(),
                neighborhood = rawNeighborhood.trim()
            )
        }

        var text = rawTrim
        
        // Remove prefixos de cabeçalho e sufixos de data e cidade/UF
        text = REGEX_HEADER_PREFIXES.replace(text, "")
        text = REGEX_DATE_TRAIL.replace(text, "")
        text = REGEX_CITY_UF_TRAIL.replace(text, "").trim()
        text = REGEX_GLUED_PREFIXES.replace(text, "$1 $2")

        var explicitNumber = rawNumber.trim()
        var explicitComplement = rawComplement.trim()
        var explicitBairro = rawNeighborhood.trim()

        if (explicitComplement.contains("ABALHADORES", ignoreCase = true)) {
            explicitComplement = REGEX_CLEAN_ABALHADORES.replace(explicitComplement, "").trim()
        }
        if (text.equals("Rua dos", ignoreCase = true) || text.equals("Trabalhadores", ignoreCase = true)) {
            text = "Rua dos Trabalhadores"
        }

        // 0. FORMATO COM PONTO-E-VÍRGULA (;) (CSV tradicional ou DDA/LOEC de entrega)
        // CSV: "Rua dos Trabalhadores; 115; Centro; Cidade; CEP"
        // DDA 1: "Jd 1 de Maio;Av Limirio Pereira de Melo;2071 2071, 1 - Monte Santo de Minas/MG"
        // DDA 2: "bela vista;José Augusto filho;200 oficina luz tech preta, 200 - Monte Santo de Minas/MG"
        // DDA 3: "matadouro;São Miguel;404 casa, 404 - Monte Santo de Minas/MG"
        if (text.contains(";")) {
            val semiParts = text.split(";").map { it.trim() }.filter { it.isNotBlank() }
            if (semiParts.size >= 2) {
                val p0 = semiParts[0]
                val p1 = semiParts[1]
                val p1IsPureNumber = REGEX_P1_IS_PURE_NUMBER.matches(p1)

                if (p1IsPureNumber) {
                    // Formato CSV padrão: Logradouro; Número; Bairro; Cidade...
                    val p2 = if (semiParts.size > 2) semiParts[2] else ""
                    val bairroFromP2 = if (explicitBairro.isBlank() && !p2.equals("Sem Bairro", ignoreCase = true) && !p2.equals("Sem_Bairro", ignoreCase = true)) p2 else explicitBairro
                    return AddressComponents(
                        street = capitalizeWords(p0),
                        number = if (explicitNumber.isNotBlank()) explicitNumber else p1,
                        complement = explicitComplement,
                        neighborhood = capitalizeWords(bairroFromP2)
                    )
                } else if (semiParts.size >= 3) {
                    // Formato DDA / LOEC: Bairro; Logradouro; Número e Complemento
                    val bairroPart = p0
                    if (explicitBairro.isBlank() && !bairroPart.equals("Sem Bairro", ignoreCase = true) && !bairroPart.equals("Sem_Bairro", ignoreCase = true)) {
                        explicitBairro = bairroPart
                    }

                    val streetPart = p1.trimEnd(',', '-', '.', ' ')

                    var numCompPart = semiParts[2]
                    numCompPart = REGEX_CITY_UF_TRAIL.replace(numCompPart, "").trim()
                    numCompPart = REGEX_DATE_TRAIL.replace(numCompPart, "").trim()

                    var extractedNum = explicitNumber
                    var extractedComp = explicitComplement

                    if (extractedNum.isBlank()) {
                        val leadingNumMatch = REGEX_LEADING_NUM_MATCH.find(numCompPart)
                        if (leadingNumMatch != null) {
                            extractedNum = leadingNumMatch.groupValues[1]
                        } else {
                            val anyNumMatch = REGEX_NUM_MATCH.find(numCompPart)
                            if (anyNumMatch != null) {
                                extractedNum = anyNumMatch.value
                            }
                        }
                    }

                    if (extractedComp.isBlank() && extractedNum.isNotBlank()) {
                        var compRaw = numCompPart
                        val escapedNum = Regex.escape(extractedNum)
                        compRaw = compRaw.replace(Regex("""\b$escapedNum\b"""), " ")
                        compRaw = REGEX_CLEAN_STREET_WORDS.replace(compRaw, " ")
                        compRaw = REGEX_PUNCT_SPLIT.replace(compRaw, " ")
                        compRaw = MULTIPLE_SPACES_REGEX.replace(compRaw, " ").trim()
                        if (compRaw.isNotBlank() && compRaw.length >= 2) {
                            val parsedComp = extractComplement(compRaw)
                            extractedComp = if (parsedComp.isNotBlank()) parsedComp else formatComplementToken(compRaw)
                        }
                    }

                    return AddressComponents(
                        street = capitalizeWords(streetPart),
                        number = extractedNum.trim(),
                        complement = extractedComp.trim(),
                        neighborhood = capitalizeWords(explicitBairro)
                    )
                } else {
                    val p1HasNum = REGEX_NUM_MATCH.containsMatchIn(p1)
                    if (p1HasNum) {
                        val parsedP1 = parseAddressComponents(p1)
                        return AddressComponents(
                            street = capitalizeWords(p0),
                            number = if (explicitNumber.isNotBlank()) explicitNumber else parsedP1.number,
                            complement = if (explicitComplement.isNotBlank()) explicitComplement else parsedP1.complement,
                            neighborhood = if (explicitBairro.isNotBlank()) explicitBairro else parsedP1.neighborhood
                        )
                    }
                }
            }
        }

        // 1. Extrai Bairro se presente após hífen no final (ex: "Av Brasil, 500 - Centro")
        if (text.contains("-")) {
            val dashParts = text.split("-").map { it.trim() }
            if (dashParts.size >= 2) {
                val lastPart = dashParts.last()
                if (lastPart.equals("Sem Bairro", ignoreCase = true) || lastPart.equals("Sem_Bairro", ignoreCase = true)) {
                    text = dashParts.dropLast(1).joinToString(" - ").trim()
                } else if (explicitBairro.isBlank() && !lastPart.any { it.isDigit() } && !COMPLEMENT_PATTERN.containsMatchIn(lastPart) && lastPart.length > 2) {
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
            // Mapeia os intervalos de caracteres que são dias de datas em nomes de ruas (ex: "15" em "15 de Julho")
            val dateDayRanges = REGEX_DATE_IN_STREET.findAll(textWithoutComplement).mapNotNull { it.groups[1]?.range }.toList()

            // Prioridade A: Número explícito com prefixo (ex: "nº 120", "num 120", "n. 120")
            val explicitPrefixMatch = REGEX_EXPLICIT_PREFIX_NUM.find(textWithoutComplement)
            if (explicitPrefixMatch != null) {
                finalNumber = explicitPrefixMatch.groupValues[1].trim()
            } else {
                // Prioridade B: Número logo após vírgula ou hífen (ex: "Rua 15 de Novembro, 120" ou "Av Limirio, 1.843")
                val commaMatch = REGEX_COMMA_NUM.find(textWithoutComplement)
                if (commaMatch != null) {
                    val candidateNum = commaMatch.groupValues[1].trim()
                    val matchRange = commaMatch.groups[1]?.range
                    // Garante que o número após a vírgula não é parte de uma data de logradouro
                    if (matchRange == null || dateDayRanges.none { r -> matchRange.first >= r.first && matchRange.last <= r.last }) {
                        finalNumber = candidateNum
                    }
                }
                
                if (finalNumber.isBlank()) {
                    // Prioridade C: Se houver múltiplos números, em endereços o número do imóvel é o último (ex: "Rua 15 de Novembro 120")
                    // IMPORTANTE: Nunca extrai o dia de uma data que compõe o nome do logradouro (ex: "15" em "15 de Julho")
                    val numMatches = REGEX_NUMBERS_EXTRACT.findAll(textWithoutComplement)
                        .filter { match ->
                            val gRange = match.groups[1]?.range ?: match.range
                            dateDayRanges.none { r -> gRange.first >= r.first && gRange.last <= r.last }
                        }
                        .toList()
                    if (numMatches.isNotEmpty()) {
                        val lastMatch = numMatches.last()
                        finalNumber = lastMatch.groupValues.lastOrNull { it.isNotBlank() && it.any { c -> c.isDigit() } } ?: lastMatch.value.trim()
                    }
                }
            }
            if (finalNumber.isBlank()) {
                val snMatch = REGEX_SN_MATCH.find(textWithoutComplement)
                if (snMatch != null) {
                    finalNumber = "S/N"
                }
            }
        }

        // 4. Extrai Logradouro limpo
        var street = textWithoutComplement
        if (finalNumber.isNotBlank()) {
            if (finalNumber == "S/N") {
                street = REGEX_SN_MATCH.replace(street, " ")
            } else if (street.contains(",") && street.contains(finalNumber)) {
                // Se houver vírgula separando o logradouro do número, o logradouro é o que vem antes da vírgula
                val beforeComma = street.substringBefore(",").trim()
                if (beforeComma.isNotBlank()) {
                    street = beforeComma
                } else {
                    street = street.replace(Regex("""\b""" + Regex.escape(finalNumber) + """\b"""), " ")
                }
            } else {
                // Remove a última ocorrência do número para preservar números no início do nome da rua (ex: "Rua 15")
                val lastIdx = street.lastIndexOf(finalNumber)
                if (lastIdx != -1) {
                    street = street.substring(0, lastIdx) + " " + street.substring(lastIdx + finalNumber.length)
                } else {
                    street = street.replace(Regex("""\b""" + Regex.escape(finalNumber) + """\b"""), " ")
                }
            }
        }
        street = REGEX_CLEAN_STREET_WORDS.replace(street, " ")
        street = REGEX_PUNCT_SPLIT.replace(street, " ")
        street = MULTIPLE_SPACES_REGEX.replace(street, " ").trim()

        if (street.isBlank()) {
            street = text.replace(finalNumber, "").trim(',', ' ', '-')
        }

        return AddressComponents(
            street = capitalizeWords(street),
            number = finalNumber.replace(".", "").trim(),
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
     * Trata pontuações, formatos com ponto-e-vírgula (LOEC/DDA), bairros e cidades no texto.
     */
    fun extractStreetAndNumber(rawAddress: String?): String {
        if (rawAddress.isNullOrBlank()) return ""
        var text = rawAddress.trim()
        
        // Remove cabeçalhos comuns
        text = REGEX_HEADER_PREFIXES.replace(text, "")
        text = REGEX_DATE_TRAIL.replace(text, "")
        text = REGEX_CITY_UF_TRAIL.replace(text, "").trim()
        text = REGEX_PARENTHESES.replace(text, " ")

        // Utiliza parseAddressComponents que extrai precisamente logradouro e número
        val parsed = parseAddressComponents(text)
        if (parsed.street.isNotBlank()) {
            return if (parsed.number.isNotBlank()) {
                cleanExtractedStreet("${parsed.street}, ${parsed.number}")
            } else {
                cleanExtractedStreet(parsed.street)
            }
        }

        // Fallback: Se tiver vírgula, traço ou ponto-e-vírgula separando partes
        val parts = text.split(",", "-", ";")
        if (parts.isNotEmpty()) {
            val first = parts[0].trim()
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

    /**
     * Extrai o endereço limpo preservando logradouro, número e complemento (se houver).
     */
    fun extractCleanAddress(rawAddress: String?): String {
        if (rawAddress.isNullOrBlank()) return ""
        var text = rawAddress.trim()
        
        text = REGEX_HEADER_PREFIXES.replace(text, "")
        text = REGEX_DATE_TRAIL.replace(text, "")
        text = REGEX_CITY_UF_TRAIL.replace(text, "").trim()
        text = REGEX_PARENTHESES.replace(text, " ")

        val parsed = parseAddressComponents(text)
        if (parsed.street.isNotBlank()) {
            val base = if (parsed.number.isNotBlank()) {
                cleanExtractedStreet("${parsed.street}, ${parsed.number}")
            } else {
                cleanExtractedStreet(parsed.street)
            }
            return if (parsed.complement.isNotBlank()) {
                "$base - ${parsed.complement}"
            } else {
                base
            }
        }

        val baseStreetAndNum = extractStreetAndNumber(text)
        val comp = extractComplement(text)
        return if (comp.isNotBlank() && !baseStreetAndNum.contains(comp, ignoreCase = true)) {
            "$baseStreetAndNum - $comp"
        } else {
            baseStreetAndNum
        }
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

        // 1.1 Separar títulos/prefixos grudados por erro de OCR ou digitação (ex: drpedro -> DR PEDRO, celantonio -> CEL ANTONIO)
        text = REGEX_GLUED_PREFIXES.replace(text, "$1 $2")

        // 2. Substituir abreviações conhecidas
        for ((abbr, full) in ABBREVIATIONS) {
            val normalizedAbbr = REGEX_DIACRITICS.replace(Normalizer.normalize(abbr, Normalizer.Form.NFD), "").uppercase(Locale.ROOT)

            if (text.startsWith("$normalizedAbbr ") || text.startsWith(normalizedAbbr)) {
                text = text.replaceFirst(normalizedAbbr, full)
            }
            text = text.replace(" $normalizedAbbr ", " $full ")
            text = text.replace(" $normalizedAbbr", " $full")
        }

        // 3. Normalizar datas e numerais romanos (ex: "XV de Julho" -> "15 DE JULHO", "Quinze de Julho" -> "15 DE JULHO")
        text = canonicalizeDatesAndNumerals(text)

        // 4. Se houver pontos ou traços entre dígitos (ex: 1.843 -> 1843, 123.456.789-00 -> 12345678900), remove o separador
        text = REGEX_DIGIT_DOT_DASH.replace(text, "")

        // 5. Remover caracteres de pontuação
        text = REGEX_PUNCTUATION_NORM.replace(text, " ")

        // 6. Remover múltiplos espaços em branco
        text = MULTIPLE_SPACES_REGEX.replace(text, " ").trim()

        return text
    }

    /**
     * Converte datas expressas com numerais romanos ou por extenso para representação numérica canônica,
     * permitindo que "XV de Julho", "15 de Julho" e "Quinze de Julho" sejam reconhecidos como o mesmo logradouro.
     */
    fun canonicalizeDatesAndNumerals(input: String): String {
        var text = input

        // 1. Substitui números por extenso seguidos de "DE [MÊS]" (ex: "QUINZE DE JULHO" -> "15 DE JULHO")
        for ((pattern, replacement) in PRECOMPILED_WRITTEN_DATE_PATTERNS) {
            text = text.replace(pattern, replacement)
        }

        // 2. Substitui numerais romanos seguidos de "DE [MÊS]" (ex: "XV DE JULHO" -> "15 DE JULHO", "VII DE SETEMBRO" -> "7 DE SETEMBRO")
        for ((pattern, replacement) in PRECOMPILED_ROMAN_DATE_PATTERNS) {
            text = text.replace(pattern, replacement)
        }

        // 3. Substitui numerais romanos de 2 ou mais letras que aparecem isolados em endereços (ex: "RUA XV" -> "RUA 15", "AV XIV" -> "AV 14", "PEDRO II" -> "PEDRO 2")
        for ((pattern, replacement) in PRECOMPILED_ROMAN_ISOLATED_PATTERNS) {
            text = text.replace(pattern, replacement)
        }

        // 4. Trata numerais romanos de 1 caractere quando precedidos por tipos de logradouro ou nomes históricos
        text = REGEX_RUA_X.replace(text, "$1 10")
        text = REGEX_RUA_V.replace(text, "$1 5")
        text = REGEX_PEDRO_I.replace(text, "$1 1")
        text = REGEX_PEDRO_II.replace(text, "$1 2")

        // 5. Converte ordinais com símbolo para dígitos (ex: "1º" ou "1°" -> "1")
        text = REGEX_ORDINAL_DIGITS.replace(text, "$1")

        return text
    }

    /**
     * Verifica se duas strings de endereço possuem alta probabilidade de correspondência
     */
    fun matches(query: String, target: String): Boolean {
        val nQuery = normalize(query)
        val nTarget = normalize(target)

        if (nQuery.isBlank() || nTarget.isBlank()) return false

        val parsedQ = parseAddressComponents(query)
        val parsedT = parseAddressComponents(target)

        val qNum = parsedQ.number.ifBlank { extractNumbers(nQuery).firstOrNull() ?: "" }
        val tNum = parsedT.number.ifBlank { extractNumbers(nTarget).firstOrNull() ?: "" }

        if (qNum.isNotBlank() && tNum.isNotBlank()) {
            if (!areNumbersMatching(qNum, tNum)) return false
        } else if (qNum.isNotBlank() && tNum.isBlank()) {
            return false
        }

        val qStreet = parsedQ.street.ifBlank { nQuery }
        val tStreet = parsedT.street.ifBlank { target }
        if (qStreet.isNotBlank() && tStreet.isNotBlank()) {
            return areStreetNamesMatching(qStreet, tStreet)
        }

        return false
    }

    private val STOP_WORDS = setOf(
        "DE", "DA", "DO", "DOS", "DAS", "E", "EM", "NO", "NA", "NOS", "NAS", "AO", "AOS",
        "RUA", "R", "AVENIDA", "AV", "ALAMEDA", "AL", "TRAVESSA", "TRAV", "TV", "PRACA", "PCA", "PR",
        "RODOVIA", "ROD", "ESTRADA", "EST", "CORONEL", "CEL", "DOUTOR", "DR", "PROFESSOR", "PROF",
        "NUMERO", "NUM", "APTO", "APT", "APARTAMENTO", "BLOCO", "BL", "CASA", "CS", "LOTE", "LT",
        "QUADRA", "QD", "ANDAR", "SALA", "SL", "KM", "PROXIMO", "PROXIMA", "ENTREGA", "ENDERECO", "DESTINO",
        "SEM", "BAIRRO", "DETALHES", "DETALHE", "OBJETO", "DEPOIS"
    )

    fun extractNumbers(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        // Ignora números que façam parte de datas no nome do logradouro (ex: "15" em "15 de Julho")
        val dateDayRanges = REGEX_DATE_IN_STREET.findAll(text).mapNotNull { it.groups[1]?.range }.toList()
        val rawMatches = REGEX_NUM_MATCH.findAll(text)
            .filter { match ->
                dateDayRanges.none { range -> match.range.first >= range.first && match.range.last <= range.last }
            }
            .map { it.value.uppercase(Locale.ROOT) }
            .toList()
        val result = mutableListOf<String>()
        for (m in rawMatches) {
            val norm = normalizeNumber(m)
            if (norm.isNotBlank() && !result.contains(norm)) {
                result.add(norm)
            }
            if (!result.contains(m)) {
                result.add(m)
            }
        }
        return result
    }

    fun extractStreetSignificantWords(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val parsed = parseAddressComponents(text)
        val streetInput = parsed.street.ifBlank { text }
        val stripped = stripStreetType(streetInput).ifBlank { streetInput }
        val normalized = normalize(stripped)
        val words = normalized.split(" ")
            .map { it.trim() }
            .filter { word ->
                word.isNotEmpty() &&
                word !in STOP_WORDS
            }
        if (words.isEmpty()) {
            val nonStop = normalized.split(" ").map { it.trim() }.filter { it.isNotEmpty() && it !in STOP_WORDS }
            if (nonStop.isNotEmpty()) return nonStop
        }
        return words
    }

    /**
     * Verifica com precisão se dois nomes de rua / logradouro correspondem à MESMA rua.
     * Requer que as palavras essenciais do nome da rua combinem, impedindo falsos positivos
     * entre ruas distintas (ex: "São Pedro" vs "São Paulo", ou "Tiradentes" vs "Getúlio Vargas").
     */
    fun areStreetNamesMatching(street1: String?, street2: String?): Boolean {
        if (street1.isNullOrBlank() || street2.isNullOrBlank()) return false

        val s1 = normalize(street1).trim()
        val s2 = normalize(street2).trim()
        if (s1 == s2) return true

        val stripped1 = stripStreetType(s1).trim().ifBlank { s1 }
        val stripped2 = stripStreetType(s2).trim().ifBlank { s2 }
        if (stripped1 == stripped2) return true

        val core1 = stripped1.filter { it.isLetterOrDigit() }
        val core2 = stripped2.filter { it.isLetterOrDigit() }
        if (core1 == core2 && core1.isNotBlank()) return true

        val words1 = extractStreetSignificantWords(stripped1)
        val words2 = extractStreetSignificantWords(stripped2)

        if (words1.isEmpty() || words2.isEmpty()) {
            return core1.isNotBlank() && core1 == core2
        }

        // Se ambos são de palavra única (ex: "TIRADENTES" vs "TIRADENTES", ou "A" vs "A")
        if (words1.size == 1 && words2.size == 1) {
            return isSimilarWord(words1[0], words2[0])
        }

        // Títulos e modificadores comuns em logradouros que NÃO podem ser o único match
        val streetTitles = setOf(
            "SAO", "SANTO", "SANTA", "PRESIDENTE", "GOVERNADOR", "DOUTOR", "DR", "CORONEL", "CEL",
            "PROFESSOR", "PROF", "PADRE", "PE", "DOM", "MINISTRO", "GENERAL", "GEN", "ALMIRANTE",
            "CAPITAO", "TENENTE", "MAJOR", "SARGENTO", "DELEGADO", "VEREADOR", "PREFEITO",
            "DEPUTADO", "SENADOR", "PRINCESA", "PRINCEPE", "BARAO", "BARONESA", "CONDE", "VISCONDE"
        )

        val nonTitleWords1 = words1.filter { it !in streetTitles }
        val nonTitleWords2 = words2.filter { it !in streetTitles }

        if (nonTitleWords1.isEmpty() || nonTitleWords2.isEmpty()) {
            return words1 == words2
        }

        val matchedNonTitles1 = nonTitleWords1.count { w1 ->
            nonTitleWords2.any { w2 -> isSimilarWord(w1, w2) }
        }
        val matchedNonTitles2 = nonTitleWords2.count { w2 ->
            nonTitleWords1.any { w1 -> isSimilarWord(w2, w1) }
        }

        // Se nenhuma palavra essencial/não-título bateu, NÃO CORRESPONDE!
        if (matchedNonTitles1 == 0 || matchedNonTitles2 == 0) {
            return false
        }

        val ratio1 = matchedNonTitles1.toFloat() / nonTitleWords1.size
        val ratio2 = matchedNonTitles2.toFloat() / nonTitleWords2.size

        return (ratio1 >= 0.6f && ratio2 >= 0.6f) ||
               (ratio1 >= 0.75f && nonTitleWords2.size == 1) ||
               (ratio2 >= 0.75f && nonTitleWords1.size == 1) ||
               (matchedNonTitles1 >= 2 && matchedNonTitles2 >= 2)
    }

    /**
     * Compara se dois endereços correspondem ao MESMO local exigindo rua E número juntos.
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

        // 1. Validação estrita do Número do Imóvel / Casa:
        // Ambos DEVEM ter número e os números DEVEM ser correspondentes!
        val qNum = parsedQuery.number.ifBlank {
            extractNumbers(nQuery).firstOrNull() ?: ""
        }
        val tNum = parsedTarget.number.ifBlank {
            if (targetNumber.isNotBlank()) targetNumber else extractNumbers(nTarget).firstOrNull() ?: ""
        }

        if (qNum.isNotBlank() && tNum.isNotBlank()) {
            if (!areNumbersMatching(qNum, tNum)) {
                return false
            }
        } else if (qNum.isNotBlank() && tNum.isBlank()) {
            return false
        } else if (qNum.isBlank() && tNum.isNotBlank()) {
            return false
        }

        // 2. Validação estrita do Nome da Rua / Logradouro:
        // OBRIGATÓRIO: A rua DEVE corresponder! Não pode puxar residência de outra rua!
        val qStreet = parsedQuery.street.ifBlank { nQuery }
        val tStreet = parsedTarget.street.ifBlank { targetAddress }
        if (!areStreetNamesMatching(qStreet, tStreet)) {
            return false
        }

        // 3. Validação de Complemento / Unidade / Apartamento (se informado em ambos)
        val queryComp = if (parsedQuery.complement.isNotBlank()) parsedQuery.complement else extractComplement(queryAddress)
        val targetComp = if (parsedTarget.complement.isNotBlank()) parsedTarget.complement else if (targetComplement.isNotBlank()) targetComplement else extractComplement(targetAddress)

        if (queryComp.isNotBlank() && targetComp.isNotBlank()) {
            val nQueryComp = normalize(queryComp)
            val nTargetComp = normalize(targetComp)
            val qCompDigits = nQueryComp.filter { it.isDigit() }
            val tCompDigits = nTargetComp.filter { it.isDigit() }
            if (qCompDigits.isNotBlank() && tCompDigits.isNotBlank()) {
                if (qCompDigits != tCompDigits) {
                    return false
                }
            } else if (qCompDigits.isNotBlank() != tCompDigits.isNotBlank()) {
                return false
            } else if (nQueryComp != nTargetComp && !nQueryComp.contains(nTargetComp) && !nTargetComp.contains(nQueryComp)) {
                return false
            }
        }

        return true
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

