package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "historico_acoes_app",
    indices = [
        Index(value = ["dateKey"], name = "index_historico_acoes_dateKey"),
        Index(value = ["timestamp"], name = "index_historico_acoes_timestamp")
    ]
)
data class AppActionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateKey: String, // ex: "2026-09-15"
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String,
    val title: String,
    val details: String = "",
    val category: String = "Geral", // Sessão, Automação, Cadastros, Sistema, Diagnóstico
    val durationMs: Long = 0L // Tempo de execução da ação (em milissegundos)
)
