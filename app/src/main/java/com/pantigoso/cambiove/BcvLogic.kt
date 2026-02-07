package com.pantigoso.cambiove

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

data class TasasBcv(
    val dolar: Double,
    val euro: Double,
    val fecha: String // La fecha textual que pone el BCV
)

class BcvService {

    suspend fun obtenerTasas(): TasasBcv? = withContext(Dispatchers.IO) {
        try {
            // Ignorar certificados SSL para evitar bloqueos
            ignorarSeguridadSSL()

            // Conectar al sitio oficial
            val doc = Jsoup.connect("https://www.bcv.org.ve/")
                .userAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .timeout(30000)
                .ignoreContentType(true)
                .get()

            // Extraer Dólar (limpiando comas y puntos)
            val dolarTexto = doc.select("#dolar strong").text()
                .replace(".", "")   // Quitar punto de miles
                .replace(",", ".")  // Cambiar coma a punto decimal
                .replace("Bs.", "")
                .trim()

            // Extraer Euro
            val euroTexto = doc.select("#euro strong").text()
                .replace(".", "")
                .replace(",", ".")
                .replace("Bs.", "")
                .trim()

            // Extraer la fecha EXACTA que muestra el sitio (Ej: "Fecha Valor: Viernes, ...")
            var fechaSitio = doc.select(".date-display-single").first()?.text() ?: ""

            // Si está vacía o muy sucia, ponemos un texto por defecto
            if (fechaSitio.isEmpty()) {
                fechaSitio = "Fecha no disponible"
            }

            val tasaDolar = dolarTexto.toDoubleOrNull() ?: 0.0
            val tasaEuro = euroTexto.toDoubleOrNull() ?: 0.0

            TasasBcv(tasaDolar, tasaEuro, fechaSitio)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun ignorarSeguridadSSL() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            })
            val sc = SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        } catch (e: Exception) {}
    }
}