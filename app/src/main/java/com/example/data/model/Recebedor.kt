package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

data class Recebedor(
    val id: String = java.util.UUID.randomUUID().toString(),
    val nome: String,
    val documento: String,
    val assinatura: String = "", // JSON estruturado da assinatura
    val dataUso: Long = 0L
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("nome", nome)
        obj.put("documento", documento)
        obj.put("assinatura", assinatura)
        obj.put("dataUso", dataUso)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): Recebedor {
            val rawId = obj.optString("id", "").trim()
            val finalId = if (rawId.isNotBlank()) rawId else java.util.UUID.randomUUID().toString()
            return Recebedor(
                id = finalId,
                nome = obj.optString("nome", ""),
                documento = obj.optString("documento", ""),
                assinatura = obj.optString("assinatura", ""),
                dataUso = obj.optLong("dataUso", 0L)
            )
        }

        fun listToJson(list: List<Recebedor>): String {
            val arr = JSONArray()
            for (item in list) {
                arr.put(item.toJsonObject())
            }
            return arr.toString()
        }

        fun listFromJson(jsonStr: String): List<Recebedor> {
            if (jsonStr.isBlank()) return emptyList()
            val list = mutableListOf<Recebedor>()
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(fromJsonObject(obj))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return list
        }
    }
}
