package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entregas")
data class Delivery(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pessoaId: Long? = null,
    val nomeDestinatario: String = "",
    val documentoDestinatario: String = "",
    val endereco: String,
    val dataHora: Long = System.currentTimeMillis(),
    val observacao: String = "",
    val assinatura: String = "", // Representação dos traços / vetores coletados nesta entrega
    val status: String = "CONCLUÍDA", // PENDENTE, CONCLUÍDA, CANCELADA
    val nomePreenchido: Boolean = false,
    val documentoPreenchido: Boolean = false,
    val assinaturaAplicada: Boolean = false
)
