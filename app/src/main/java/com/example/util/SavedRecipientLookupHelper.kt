package com.example.util

import com.example.DeliveryApp
import com.example.data.model.Recebedor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SavedRecipientInfo(
    val nome: String,
    val documento: String,
    val assinatura: String = "",
    val enderecoResumo: String = ""
)

object SavedRecipientLookupHelper {

    private var cachedRecipients: List<SavedRecipientInfo>? = null
    private var lastCacheTime: Long = 0L
    private const val CACHE_EXPIRATION_MS = 30_000L // 30 segundos de cache em memória

    fun invalidateCache() {
        cachedRecipients = null
        lastCacheTime = 0L
    }

    /**
     * Extrai todos os recebedores únicos (titular + co-recebedores) de todas as pessoas salvas,
     * utilizando cache em memória e executando no pool Dispatchers.Default para não travar a UI.
     */
    suspend fun getAllSavedRecipients(): List<SavedRecipientInfo> = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val existing = cachedRecipients
        if (existing != null && (now - lastCacheTime) < CACHE_EXPIRATION_MS) {
            return@withContext existing
        }

        val persons = try {
            DeliveryApp.instance.personRepository.getAllPersonsDirect()
        } catch (_: Throwable) {
            emptyList()
        }

        val list = ArrayList<SavedRecipientInfo>(persons.size * 2)
        for (p in persons) {
            val addr = buildString {
                append(p.endereco)
                if (p.numero.isNotBlank()) append(", nº ${p.numero}")
                if (p.complemento.isNotBlank()) append(" (${p.complemento})")
            }

            if (p.nome.isNotBlank() || p.documento.isNotBlank()) {
                list.add(
                    SavedRecipientInfo(
                        nome = p.nome.trim(),
                        documento = p.documento.trim(),
                        assinatura = p.assinatura,
                        enderecoResumo = addr
                    )
                )
            }

            if (p.coRecebedoresJson.isNotBlank()) {
                try {
                    val coRecs = Recebedor.listFromJson(p.coRecebedoresJson)
                    for (cr in coRecs) {
                        if (cr.nome.isNotBlank() || cr.documento.isNotBlank()) {
                            list.add(
                                SavedRecipientInfo(
                                    nome = cr.nome.trim(),
                                    documento = cr.documento.trim(),
                                    assinatura = cr.assinatura,
                                    enderecoResumo = addr
                                )
                            )
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        cachedRecipients = list
        lastCacheTime = now
        list
    }

    /**
     * Localiza recebedor salvo pelo documento (CPF ou RG).
     * Compara dígitos ou texto limpo em segundo plano.
     */
    suspend fun findByDocument(rawDoc: String): SavedRecipientInfo? = withContext(Dispatchers.Default) {
        val trimmed = rawDoc.trim()
        if (trimmed.length < 5) return@withContext null

        val cleanDigits = trimmed.filter { it.isDigit() }
        val cleanAlnum = trimmed.filter { it.isLetterOrDigit() }.uppercase()
        val all = getAllSavedRecipients()

        // 1. Busca por dígitos limpos (para CPF ou RG numérico com 6 ou mais dígitos)
        if (cleanDigits.length >= 6) {
            val match = all.firstOrNull {
                it.documento.filter { c -> c.isDigit() } == cleanDigits && it.nome.isNotBlank() && it.assinatura.isNotBlank()
            } ?: all.firstOrNull {
                it.documento.filter { c -> c.isDigit() } == cleanDigits && it.nome.isNotBlank()
            } ?: all.firstOrNull {
                it.documento.filter { c -> c.isDigit() } == cleanDigits
            }
            if (match != null) return@withContext match
        }

        // 2. Busca por alfanumérico limpo (para RGs com letras como 'MG-12345678' ou '12345678-X')
        if (cleanAlnum.length >= 5) {
            val match = all.firstOrNull {
                it.documento.filter { c -> c.isLetterOrDigit() }.uppercase() == cleanAlnum && it.nome.isNotBlank()
            }
            if (match != null) return@withContext match
        }

        val upper = trimmed.uppercase()
        all.firstOrNull {
            it.documento.trim().uppercase() == upper && it.nome.isNotBlank()
        }
    }

    /**
     * Localiza recebedor salvo pelo nome de forma assíncrona.
     */
    suspend fun findByName(rawName: String): SavedRecipientInfo? = withContext(Dispatchers.Default) {
        val q = rawName.trim()
        if (q.length < 3) return@withContext null

        val normQ = AddressNormalizer.normalize(q)
        if (normQ.length < 3) return@withContext null

        val all = getAllSavedRecipients()

        // 1. Busca correspondência exata (normalizada)
        val exact = all.firstOrNull {
            AddressNormalizer.normalize(it.nome) == normQ && (it.documento.isNotBlank() || it.assinatura.isNotBlank())
        }
        if (exact != null) return@withContext exact

        // 2. Começa com o termo
        val starts = all.firstOrNull {
            val normIt = AddressNormalizer.normalize(it.nome)
            (normIt.startsWith("$normQ ") || normIt == normQ) && (it.documento.isNotBlank() || it.assinatura.isNotBlank())
        }
        if (starts != null) return@withContext starts

        // 3. Contém o termo
        val contains = all.firstOrNull {
            val normIt = AddressNormalizer.normalize(it.nome)
            normIt.contains(normQ) && (it.documento.isNotBlank() || it.assinatura.isNotBlank())
        }
        contains
    }
}
