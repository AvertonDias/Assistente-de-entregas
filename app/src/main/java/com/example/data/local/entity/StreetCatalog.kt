package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "catalogo_ruas",
    indices = [
        Index(value = ["cidade", "uf"]),
        Index(value = ["nome"]),
        Index(value = ["bairro"])
    ]
)
data class StreetCatalog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nome: String,
    val tipo: String = "Rua", // Rua, Avenida, Praça, Travessa, Alameda, Rodovia, etc.
    val bairro: String = "",
    val cidade: String = "Monte Santo de Minas",
    val uf: String = "MG",
    val cep: String = ""
) {
    val nomeCompleto: String
        get() = if (tipo.isNotBlank() && !nome.startsWith(tipo, ignoreCase = true)) {
            "$tipo $nome"
        } else {
            nome
        }
}
