package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

data class Recebedor(
    val id: String = java.util.UUID.randomUUID().toString(),
    val nome: String,
    val documento: String,
    val assinatura: String = "" // JSON estruturado da assinatura
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("nome", nome)
        obj.put("documento", documento)
        obj.put("assinatura", assinatura)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): Recebedor {
            return Recebedor(
                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                nome = obj.optString("nome", ""),
                documento = obj.optString("documento", ""),
                assinatura = obj.optString("assinatura", "")
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
