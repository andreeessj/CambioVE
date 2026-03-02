package com.pantigoso.cambiove

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val sistemaOscuro = isSystemInDarkTheme()
            var esModoOscuro by remember { mutableStateOf(sistemaOscuro) }

            CambioVeTheme(darkTheme = esModoOscuro) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PantallaPrincipal(esModoOscuro) { esModoOscuro = !esModoOscuro }
                }
            }
        }
    }
}

@Composable
fun PantallaPrincipal(modoOscuroActual: Boolean, onCambiarTema: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencias = remember { Preferencias(context) }

    var tasas by remember { mutableStateOf(preferencias.obtenerTasasGuardadas()) }
    var cargando by remember { mutableStateOf(false) }

    var montoInputState by remember { mutableStateOf(TextFieldValue("")) }
    var resultadoNumerico by remember { mutableStateOf(0.0) }
    var esVesADivisa by remember { mutableStateOf(true) }
    var monedaSeleccionada by remember { mutableStateOf("USD") }

    fun actualizarDatos() {
        cargando = true
        scope.launch {
            val bcvDeferred = async { BcvService().obtenerTasas() }
            val binanceDeferred = async { BinanceLogic().obtenerPrecioUSDT() }

            val resultadoBcv = bcvDeferred.await()
            val resultadoBinance = binanceDeferred.await()

            if (resultadoBcv != null) {
                val usdtSeguro = if (resultadoBinance > 0) resultadoBinance else (tasas?.usdtBinance ?: 0.0)

                val nuevasTasas = TasasGlobales(
                    dolarBcv = resultadoBcv.dolar,
                    euroBcv = resultadoBcv.euro,
                    usdtBinance = usdtSeguro,
                    fecha = resultadoBcv.fecha
                )

                tasas = nuevasTasas
                preferencias.guardarTasas(nuevasTasas)
                Toast.makeText(context, "Tasas actualizadas", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Error al conectar", Toast.LENGTH_SHORT).show()
            }
            cargando = false
        }
    }

    LaunchedEffect(Unit) {
        if (tasas == null) actualizarDatos()
    }

    fun recalcular(input: String, sentidoVes: Boolean, moneda: String) {
        val monto = input.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
        val tasa = when (moneda) {
            "USD" -> tasas?.dolarBcv
            "EUR" -> tasas?.euroBcv
            "USDT" -> tasas?.usdtBinance
            else -> 0.0
        }
        resultadoNumerico = if (tasa != null && tasa > 0) {
            if (sentidoVes) monto / tasa else monto * tasa
        } else 0.0
    }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(top = 60.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Cambio VE", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text("(Yai's Version)", fontSize = 12.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)

            Spacer(modifier = Modifier.height(15.dp))

            // TARJETA DE TASAS (Sin porcentajes)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (tasas != null) {
                        Text(tasas!!.fecha, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontStyle = FontStyle.Italic)
                        Spacer(Modifier.height(10.dp))

                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("🇺🇸 BCV Dólar:", fontWeight = FontWeight.SemiBold)
                            Text("${formatoVenezuela(tasas!!.dolarBcv)} Bs.", fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("🇪🇺 BCV Euro:", fontWeight = FontWeight.SemiBold)
                            Text("${formatoVenezuela(tasas!!.euroBcv)} Bs.", fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("₮ Binance USDT:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Text("${formatoVenezuela(tasas!!.usdtBinance)} Bs.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                        val brechaBs = tasas!!.usdtBinance - tasas!!.dolarBcv
                        val brechaPct = if(tasas!!.dolarBcv > 0) (brechaBs/tasas!!.dolarBcv)*100 else 0.0
                        Text("Brecha (Dólar - USDT): ${formatoVenezuela(brechaPct)}% (+${formatoVenezuela(brechaBs)} Bs.)", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Medium)
                    } else {
                        CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ENTRADA
            OutlinedTextField(
                value = montoInputState,
                onValueChange = { input ->
                    val txt = formatearMontoMientrasEscribes(input.text)
                    montoInputState = TextFieldValue(txt, TextRange(txt.length))
                    recalcular(txt, esVesADivisa, monedaSeleccionada)
                },
                label = { Text("Monto a calcular") },
                prefix = { Text(if(esVesADivisa) "Bs. " else if(monedaSeleccionada=="USD") "$ " else if(monedaSeleccionada=="EUR") "€ " else "₮ ", fontWeight = FontWeight.Bold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilterChip(selected = monedaSeleccionada == "USD", onClick = { monedaSeleccionada = "USD"; recalcular(montoInputState.text, esVesADivisa, "USD") }, label = { Text("USD") })
                FilterChip(selected = monedaSeleccionada == "EUR", onClick = { monedaSeleccionada = "EUR"; recalcular(montoInputState.text, esVesADivisa, "EUR") }, label = { Text("EUR") })
                FilterChip(selected = monedaSeleccionada == "USDT", onClick = { monedaSeleccionada = "USDT"; recalcular(montoInputState.text, esVesADivisa, "USDT") }, label = { Text("USDT") })
            }

            Button(
                onClick = { esVesADivisa = !esVesADivisa; recalcular(montoInputState.text, esVesADivisa, monedaSeleccionada) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if(esVesADivisa) "Convertir a $monedaSeleccionada ↔️" else "Convertir a Bolívares ↔️")
            }

            // RESULTADO
            Spacer(modifier = Modifier.height(15.dp))
            Text("Resultado:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Text(
                text = "${formatoVenezuela(resultadoNumerico)} ${if(esVesADivisa) monedaSeleccionada else "Bs."}",
                fontSize = 38.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center
            )

            // DEDICATORIA
            Spacer(modifier = Modifier.height(50.dp))
            Text(
                text = "Te ama mucho: Andres Pantigoso ❤️",
                fontSize = 14.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }

        // ACCIONES
        IconButton(onClick = { actualizarDatos() }, modifier = Modifier.align(Alignment.TopStart).padding(top = 35.dp, start = 10.dp)) {
            if (cargando) CircularProgressIndicator(Modifier.size(20.dp)) else Icon(Icons.Filled.Refresh, "Refrescar")
        }
        IconButton(onClick = onCambiarTema, modifier = Modifier.align(Alignment.TopEnd).padding(top = 35.dp, end = 10.dp)) {
            Icon(if (modoOscuroActual) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Tema")
        }
    }
}

@Composable
fun CambioVeTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val light = lightColorScheme(primary = Color(0xFFFFD54F), onPrimary = Color.Black, background = Color(0xFFFFFBFF), surfaceVariant = Color(0xFFF0EAE2))
    val dark = darkColorScheme(primary = Color(0xFFFFD758), onPrimary = Color.Black, background = Color(0xFF121212), surfaceVariant = Color(0xFF252525))
    MaterialTheme(colorScheme = if (darkTheme) dark else light, content = content)
}

fun formatoVenezuela(monto: Double): String {
    val formato = NumberFormat.getNumberInstance(Locale("es", "VE"))
    formato.maximumFractionDigits = 2
    formato.minimumFractionDigits = 2
    return formato.format(monto)
}

fun formatearMontoMientrasEscribes(input: String): String {
    if (input.isEmpty()) return ""
    val soloNumerosYComa = input.filter { it.isDigit() || it == ',' }
    val partes = soloNumerosYComa.split(',')
    val symbols = DecimalFormatSymbols(Locale("es", "VE"))
    val df = DecimalFormat("#,###", symbols)
    val entero = if (partes[0].isNotEmpty()) try { df.format(partes[0].replace(".", "").toLong()) } catch (e: Exception) { partes[0] } else ""
    return entero + if (partes.size > 1) "," + partes[1] else ""
}