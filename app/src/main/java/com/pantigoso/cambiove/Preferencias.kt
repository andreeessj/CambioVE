package com.pantigoso.cambiove

import android.content.Context
import android.content.SharedPreferences

data class TasasGlobales(
    val dolarBcv: Double = 0.0,
    val euroBcv: Double = 0.0,
    val usdtBinance: Double = 0.0,
    val fecha: String = "Sin fecha"
)

class Preferencias(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tasas_prefs", Context.MODE_PRIVATE)

    fun guardarTasas(tasas: TasasGlobales) {
        prefs.edit().apply {
            putFloat("dolar", tasas.dolarBcv.toFloat())
            putFloat("euro", tasas.euroBcv.toFloat())
            putFloat("usdt", tasas.usdtBinance.toFloat())
            putString("fecha", tasas.fecha)
            apply()
        }
    }

    fun obtenerTasasGuardadas(): TasasGlobales? {
        val dolar = prefs.getFloat("dolar", 0f).toDouble()
        if (dolar == 0.0) return null

        return TasasGlobales(
            dolarBcv = dolar,
            euroBcv = prefs.getFloat("euro", 0f).toDouble(),
            usdtBinance = prefs.getFloat("usdt", 0f).toDouble(),
            fecha = prefs.getString("fecha", "Fecha no disponible") ?: ""
        )
    }
}