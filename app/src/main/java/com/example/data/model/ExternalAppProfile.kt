package com.example.data.model

/**
 * Perfil de automação para identificar campos em aplicativos externos de entrega.
 * Permite adicionar regras específicas por pacote ou usar detecção genérica inteligente.
 */
data class ExternalAppProfile(
    val packageName: String,
    val appName: String,
    val nameFieldHints: List<String> = listOf("nome", "destinatário", "recebedor", "cliente", "name", "recipient"),
    val documentFieldHints: List<String> = listOf("documento", "cpf", "rg", "doc", "identidade", "document"),
    val signatureFieldHints: List<String> = listOf("assinatura", "assinar", "rubrica", "signature", "sign_here", "area_assinatura"),
    val addressHints: List<String> = listOf("rua", "av", "avenida", "alameda", "travessa", "praça", "cel", "coronel", "bairro", "endereço", "address")
) {
    companion object {
        val GENERIC_DEFAULT = ExternalAppProfile(
            packageName = "*",
            appName = "Genérico",
            nameFieldHints = listOf("nome", "destinatário", "recebedor", "cliente", "name", "recipient", "receiver"),
            documentFieldHints = listOf("documento", "cpf", "rg", "doc", "identidade", "document", "id_number"),
            signatureFieldHints = listOf("assinatura", "assinar", "rubrica", "signature", "sign", "canvas_signature", "draw_area"),
            addressHints = listOf("rua", "r.", "av", "av.", "avenida", "cel", "cel.", "coronel", "alameda", "al.", "travessa", "tv.", "praça", "pca.", "bairro", "nº", "numero", "endereço")
        )

        val PROFILES = listOf(
            GENERIC_DEFAULT,
            ExternalAppProfile(
                packageName = "com.delivery.driver",
                appName = "App Entregas Driver",
                nameFieldHints = listOf("recipient_name", "txt_name", "nome_cliente"),
                documentFieldHints = listOf("recipient_doc", "txt_doc", "cpf_cliente"),
                signatureFieldHints = listOf("signature_view", "btn_sign", "sign_pad")
            )
        )
    }
}
