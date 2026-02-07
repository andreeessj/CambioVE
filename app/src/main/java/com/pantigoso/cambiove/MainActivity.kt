package com.pantigoso.cambiove

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
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

    // Estado inicial: Cargar datos guardados (Offline)
    var tasas by remember { mutableStateOf(preferencias.obtenerTasasGuardadas()) }
    var cargando by remember { mutableStateOf(false) }
    var errorConexion by remember { mutableStateOf(false) }

    // Inputs
    var montoInputState by remember { mutableStateOf(TextFieldValue("")) }
    var resultadoNumerico by remember { mutableStateOf(0.0) }
    var esVesADivisa by remember { mutableStateOf(true) }
    var monedaSeleccionada by remember { mutableStateOf("USD") } // USD, EUR, USDT

    // Función que carga BCV y Binance al mismo tiempo
    fun actualizarDatos() {
        cargando = true
        errorConexion = false
        scope.launch {
            // Ejecutamos ambas consultas en paralelo para ser más rápidos
            val bcvDeferred = async { BcvService().obtenerTasas() } // <--- Clase de tu archivo BcvLogic.kt
            val binanceDeferred = async { BinanceLogic().obtenerPrecioUSDT() } // <--- Clase nueva

            val resultadoBcv = bcvDeferred.await()
            val resultadoBinance = binanceDeferred.await()

            if (resultadoBcv != null) {
                // Si Binance falla (devuelve 0.0) usamos el último valor guardado o 0.0
                val usdtSeguro = if (resultadoBinance > 0) resultadoBinance else (tasas?.usdtBinance ?: 0.0)

                val nuevasTasas = TasasGlobales(
                    dolarBcv = resultadoBcv.dolar,
                    euroBcv = resultadoBcv.euro,
                    usdtBinance = usdtSeguro,
                    fecha = resultadoBcv.fecha // Usamos la fecha que trae el BCV
                )

                // Actualizamos pantalla y guardamos en memoria
                tasas = nuevasTasas
                preferencias.guardarTasas(nuevasTasas)
                Toast.makeText(context, "Tasas actualizadas", Toast.LENGTH_SHORT).show()
            } else {
                errorConexion = true
                Toast.makeText(context, "Error de conexión BCV", Toast.LENGTH_SHORT).show()
            }
            cargando = false
        }
    }

    // Al iniciar, si no hay datos guardados, carga. Si hay, muestra los guardados y actualiza en fondo.
    LaunchedEffect(Unit) {
        if (tasas == null) cargando = true
        actualizarDatos()
    }

    fun recalcular(input: String, sentidoVes: Boolean, moneda: String) {
        val monto = input.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
        val tasa = when (moneda) {
            "USD" -> tasas?.dolarBcv
            "EUR" -> tasas?.euroBcv
            "USDT" -> tasas?.usdtBinance
            else -> 0.0
        }

        if (tasa != null && tasa > 0) {
            resultadoNumerico = if (sentidoVes) monto / tasa else monto * tasa
        } else {
            resultadoNumerico = 0.0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Cambio VE",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // FECHA
            if (tasas != null) {
                Text(
                    text = tasas!!.fecha,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    fontStyle = FontStyle.Italic
                )
            } else if (cargando) {
                Text("Actualizando...", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // TARJETA DE PRECIOS
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (tasas != null) {
                        // Dólar BCV
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("🇺🇸 BCV Dólar:", fontWeight = FontWeight.SemiBold)
                            Text("${formatoVenezuela(tasas!!.dolarBcv)} Bs.", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        // Euro BCV
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("🇪🇺 BCV Euro:", fontWeight = FontWeight.SemiBold)
                            Text("${formatoVenezuela(tasas!!.euroBcv)} Bs.", fontWeight = FontWeight.Bold)
                        }

                        Divider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                        // USDT Binance
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("₮ Binance USDT:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Text("${formatoVenezuela(tasas!!.usdtBinance)} Bs.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (cargando) {
                        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(30.dp))
                    } else {
                        Text("Sin datos. Conéctate a internet.", Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.error)
                    }

                    if (errorConexion && tasas != null) {
                        Text("Modo Offline", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.End))
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // INPUT
            OutlinedTextField(
                value = montoInputState,
                onValueChange = { input ->
                    val txt = formatearMontoMientrasEscribes(input.text)
                    montoInputState = TextFieldValue(txt, TextRange(txt.length))
                    recalcular(txt, esVesADivisa, monedaSeleccionada)
                },
                label = { Text("Monto a calcular") },
                prefix = {
                    val simbolo = when {
                        esVesADivisa -> "Bs."
                        monedaSeleccionada == "USD" -> "$"
                        monedaSeleccionada == "EUR" -> "€"
                        else -> "₮"
                    }
                    Text("$simbolo ", fontWeight = FontWeight.Bold)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 22.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // CHIPS DE SELECCIÓN (USD, EUR, USDT)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilterChip(
                    selected = monedaSeleccionada == "USD",
                    onClick = { monedaSeleccionada = "USD"; recalcular(montoInputState.text, esVesADivisa, "USD") },
                    label = { Text("USD ($)") }
                )
                FilterChip(
                    selected = monedaSeleccionada == "EUR",
                    onClick = { monedaSeleccionada = "EUR"; recalcular(montoInputState.text, esVesADivisa, "EUR") },
                    label = { Text("EUR (€)") }
                )
                FilterChip(
                    selected = monedaSeleccionada == "USDT",
                    onClick = { monedaSeleccionada = "USDT"; recalcular(montoInputState.text, esVesADivisa, "USDT") },
                    label = { Text("USDT (₮)") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // BOTÓN INVERTIR
            Button(
                onClick = {
                    val nuevo = !esVesADivisa
                    esVesADivisa = nuevo
                    recalcular(montoInputState.text, nuevo, monedaSeleccionada)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                val monedaNombre = if(monedaSeleccionada == "USDT") "USDT" else monedaSeleccionada
                val origen = if(esVesADivisa) "Bolívares" else monedaNombre
                val destino = if(esVesADivisa) monedaNombre else "Bolívares"
                Text("De $origen a $destino ↔️")
            }

            Spacer(modifier = Modifier.height(40.dp))

            // RESULTADO
            Text("Resultado:", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Text(
                text = "${formatoVenezuela(resultadoNumerico)} ${if(esVesADivisa) if(monedaSeleccionada=="USDT") "USDT" else monedaSeleccionada else "Bs."}",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                lineHeight = 44.sp
            )
        }

        // BOTONES FLOTANTES (SUPERIORES)
        IconButton(
            onClick = { actualizarDatos() },
            modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 16.dp),
            enabled = !cargando
        ) {
            if (cargando) CircularProgressIndicator(Modifier.size(24.dp))
            else Icon(Icons.Filled.Refresh, "Actualizar", tint = MaterialTheme.colorScheme.onBackground)
        }

        IconButton(
            onClick = onCambiarTema,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp)
        ) {
            Icon(if (modoOscuroActual) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Tema", tint = MaterialTheme.colorScheme.onBackground)
        }

        // CRÉDITOS
        Row(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
            Text("Creador: Andres Pantigoso (andresdavidps6@gmail.com)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
        }
    }
}

// --- TEMAS Y COLORES AZULES ---

private val LightColors = lightColorScheme(
    primary = Color(0xFF005FAC), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF555F71), onSecondary = Color.White,
    background = Color(0xFFFDFBFF), onBackground = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E2EC), onSurfaceVariant = Color(0xFF43474E),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C8FF), onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468C), onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBDC7DC), onSecondary = Color(0xFF273141),
    background = Color(0xFF1A1C1E), onBackground = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF44474F), onSurfaceVariant = Color(0xFFC4C6D0),
    error = Color(0xFFFFB4AB)
)

@Composable
fun CambioVeTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// --- UTILIDADES DE FORMATO ---

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
    val parteEntera = partes[0]
    val parteDecimal = if (partes.size > 1) "," + partes[1] else ""
    val symbols = DecimalFormatSymbols(Locale("es", "VE"))
    val df = DecimalFormat("#,###", symbols)
    val enteroFormateado = if (parteEntera.isNotEmpty()) {
        try { df.format(parteEntera.replace(".", "").toLong()) } catch (e: Exception) { parteEntera }
    } else { "" }
    return enteroFormateado + parteDecimal
}