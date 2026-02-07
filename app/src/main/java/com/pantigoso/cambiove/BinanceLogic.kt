package com.pantigoso.cambiove

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class BinanceLogic {

    suspend fun obtenerPrecioUSDT(): Double = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://p2p.binance.com/bapi/c2c/v2/friendly/c2c/adv/search")
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.doOutput = true

            // CAMBIO CLAVE: "publisherType": "merchant" busca solo verificados (chulito amarillo)
            // Esto da un precio más estable y seguro, similar al que ves por defecto.
            val jsonInputString = """
                {
                    "page": 1,
                    "rows": 10,
                    "payTypes": [],
                    "asset": "USDT",
                    "tradeType": "BUY",
                    "fiat": "VES",
                    "publisherType": "merchant" 
                }
            """.trimIndent()

            val os = OutputStreamWriter(conn.outputStream)
            os.write(jsonInputString)
            os.flush()
            os.close()

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val data = json.getJSONArray("data")

                if (data.length() > 0) {
                    var sumaPrecios = 0.0
                    // Tomamos hasta 5 anuncios para hacer promedio, o los que haya si son menos
                    val cantidadAPromediar = if (data.length() < 5) data.length() else 5

                    for (i in 0 until cantidadAPromediar) {
                        val anuncio = data.getJSONObject(i)
                        val datosAdv = anuncio.getJSONObject("adv")
                        val precio = datosAdv.getString("price").toDouble()
                        sumaPrecios += precio
                    }

                    // Devolvemos el promedio
                    return@withContext sumaPrecios / cantidadAPromediar
                }
            }
            return@withContext 0.0

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext 0.0
        }
    }
}