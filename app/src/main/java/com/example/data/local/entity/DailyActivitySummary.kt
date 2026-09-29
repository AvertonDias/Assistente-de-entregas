package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resumo_diario_atividades")
data class DailyActivitySummary(
    @PrimaryKey
    val dateKey: String, // ex: "2026-09-15"
    val totalActiveTimeMs: Long = 0L,
    val firstOpenTime: Long = System.currentTimeMillis(),
    val lastActiveTime: Long = System.currentTimeMillis(),
    val totalDeliveries: Int = 0,
    val totalAddressesDetected: Int = 0,
    val totalSignaturesApplied: Int = 0,
    val totalPeopleCreatedOrEdited: Int = 0,
    val totalSearches: Int = 0,
    val totalActionsCount: Int = 0
)
