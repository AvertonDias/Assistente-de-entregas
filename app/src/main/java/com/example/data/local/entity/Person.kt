package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pessoas")
data class Person(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nome: String,
    val documento: String,
    val endereco: String,
    val numero: String = "",
    val complemento: String = "",
    val bairro: String = "",
    val cidade: String = "",
    val uf: String = "MG",
    val observacao: String = "",
    val assinatura: String = "", // JSON estruturado de traços ou Base64
    val coRecebedoresJson: String = "", // JSON de Recebedor adicionais para o mesmo endereço
    val dataCriacao: Long = System.currentTimeMillis(),
    val dataAtualizacao: Long = System.currentTimeMillis()
)
