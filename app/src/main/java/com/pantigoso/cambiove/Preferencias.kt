package com.pantigoso.cambiove

import android.content.Context

// Modelo de datos unificado
data class TasasGlobales(
    val dolarBcv: Double,
    val euroBcv: Double,
    val usdtBinance: Double,
    val fecha: String
)

class Preferencias(context: Context) {
    private val storage = context.getSharedPreferences("bcv_cache", Context.MODE_PRIVATE)

    fun guardarTasas(tasas: TasasGlobales) {
        storage.edit().apply {
            putString("fecha", tasas.fecha)
            putString("dolar", tasas.dolarBcv.toString())
            putString("euro", tasas.euroBcv.toString())
            putString("usdt", tasas.usdtBinance.toString())
            apply()
        }
    }

    fun obtenerTasasGuardadas(): TasasGlobales? {
        val fecha = storage.getString("fecha", null)
        val dolarStr = storage.getString("dolar", null)
        val euroStr = storage.getString("euro", null)
        val usdtStr = storage.getString("usdt", "0.0")

        if (fecha != null && dolarStr != null && euroStr != null) {
            return TasasGlobales(
                dolarBcv = dolarStr.toDoubleOrNull() ?: 0.0,
                euroBcv = euroStr.toDoubleOrNull() ?: 0.0,
                usdtBinance = usdtStr?.toDoubleOrNull() ?: 0.0,
                fecha = fecha
            )
        }
        return null
    }
}