package com.example.sleepguardian

import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SleepGuardianTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SleepGuardianApp()
                }
            }
        }
    }
}

@Composable
fun SleepGuardianApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }

    val startDestination = if (tokenManager.getToken() != null) "dashboard" else "login"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") { LoginScreen(navController, tokenManager) }
        composable("register") { RegisterScreen(navController) }
        composable("dashboard") { DashboardScreen(navController, tokenManager) }
        composable("history") { HistoryScreen(navController, tokenManager) }
    }
}

@Composable
private fun BackgroundGradient() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2B1549).copy(alpha = 0.95f),
                        SleepThemeBackground
                    ),
                    radius = 1400f
                )
            )
    )
}

@Composable
private fun NeonTitle(title: String, subtitle: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color(0xFF4A3B63))
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun MetricPill(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.42f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint)
            Spacer(modifier = Modifier.width(8.dp))
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LoginScreen(navController: NavController, tokenManager: TokenManager) {
    var email by remember { mutableStateOf(tokenManager.getSavedEmail()) }
    var password by remember { mutableStateOf(tokenManager.getSavedPassword()) }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundGradient()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = "Logo",
                    modifier = Modifier.padding(18.dp).size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "SleepGuardian",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Zadbaj o sen z odrobiną magii i dyscypliny.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            SectionCard {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Hasło") },
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            statusMessage = "Wypełnij wszystkie pola."
                            return@Button
                        }
                        isLoading = true
                        statusMessage = ""
                        coroutineScope.launch {
                            try {
                                val request = LoginRequest(email, password)
                                val response = RetrofitClient.apiService.login(request)
                                tokenManager.saveToken(response.token)
                                tokenManager.saveCredentials(email, password)
                                navController.navigate("dashboard") {
                                    popUpTo("login") { inclusive = true }
                                }
                            } catch (_: Exception) {
                                statusMessage = "Błąd logowania. Sprawdź dane lub połączenie."
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Zaloguj się", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = { navController.navigate("register") },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Nie masz konta? Zarejestruj się")
                }

                AnimatedVisibility(visible = statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(22.dp).size(44.dp),
                        strokeWidth = 4.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundGradient()

        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NeonTitle("Dołącz do nas", "Stwórz konto i odpal nocny tryb kontroli snu.")
            Spacer(modifier = Modifier.height(30.dp))

            SectionCard {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Hasło (min. 8 znaków)") },
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            statusMessage = "Wypełnij wszystkie pola."
                            return@Button
                        }
                        isLoading = true
                        statusMessage = ""
                        coroutineScope.launch {
                            try {
                                val request = LoginRequest(email, password)
                                val response = RetrofitClient.apiService.register(request)
                                statusMessage = response.message
                            } catch (_: Exception) {
                                statusMessage = "Błąd: ten e-mail może być już zajęty."
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    enabled = !isLoading
                ) {
                    Text("Utwórz konto", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(onClick = { navController.popBackStack() }, enabled = !isLoading) {
                    Text("Wróć do logowania")
                }

                AnimatedVisibility(visible = statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Widok historii sesji snu.
 * Pobiera i wyświetla zrealizowane sesje użytkownika, wyróżniając graficznie
 * te, które zostały przerwane (poddane) negatywnie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController, tokenManager: TokenManager) {
    val context = LocalContext.current
    var historyList by remember { mutableStateOf<List<SleepSessionItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val token = tokenManager.getToken()
                if (token != null) {
                    val response = RetrofitClient.apiService.getHistory("Bearer $token")
                    historyList = response.history
                }
            } catch (_: Exception) {
                errorMessage = "Nie udało się pobrać historii. Jesteś offline?"
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Historia sesji", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
                errorMessage.isNotEmpty() -> Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
                historyList.isEmpty() -> Text(
                    text = "Brak zarejestrowanych sesji snu.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(historyList) { session ->
                        val dateStr = session.start_time?.take(10) ?: "Nieznana data"
                        val prefs = context.getSharedPreferences("sleepguardian_prefs", Context.MODE_PRIVATE)
                        val isNegative = prefs.getBoolean("negative_session_${session.id}", false)

                        val statusText = when {
                            isNegative -> "Zakończona negatywnie"
                            session.end_time != null -> "Zakończona pomyślnie"
                            else -> "Niezakończona"
                        }
                        val statusColor = when {
                            isNegative -> SleepThemeError
                            session.end_time != null -> SleepThemeSuccess
                            else -> SleepThemeWarning
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                            elevation = CardDefaults.cardElevation(0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Cel snu: ${session.target_sleep_time} - ${session.target_wake_time}",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Status: $statusText",
                                    color = statusColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Główny panel użytkownika (Dashboard).
 * Obsługuje konfigurację harmonogramów snu, uruchamianie usług sprzętowych
 * oraz zapewnia stabilne zarządzanie cyklem życia sesji wraz z opcją poddania się (Surrender).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, tokenManager: TokenManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var sleepTime by remember { mutableStateOf("22:00") }
    var wakeTime by remember { mutableStateOf("06:00") }
    var isLoading by remember { mutableStateOf(false) }
    var currentStreak by remember { mutableIntStateOf(0) }
    var heartsRemaining by remember { mutableIntStateOf(0) }
    var isSessionActive by remember { mutableStateOf(tokenManager.getSessionId() != -1) }

    data class ModeDef(val name: String, val icon: ImageVector, val desc: String)
    val rigourModes = listOf(
        ModeDef("Narastająca Kurtyna", Icons.Default.VisibilityOff, "Zasłania ekran"),
        ModeDef("Test Kamienia", Icons.Default.Smartphone, "Wykrywa ruch"),
        ModeDef("Upierdliwy Komar", Icons.Default.NotificationsActive, "Dźwięk ostrzegawczy"),
        ModeDef("Latarnia Morska", Icons.Default.FlashlightOn, "Stroboskop LED")
    )
    var selectedMode by remember { mutableStateOf(rigourModes.first().name) }

    /**
     * Aktualizuje statystyki oparte na mechanizmach grywalizacji, preferując dane lokalne,
     * i próbuje dokonać synchronizacji z backendem w tle.
     */
    fun refreshStats() {
        coroutineScope.launch {
            val offlineManager = OfflineSyncManager(context)
            currentStreak = offlineManager.getCachedStreak()
            heartsRemaining = offlineManager.getCachedHearts()

            try {
                val token = tokenManager.getToken()
                if (token != null) {
                    offlineManager.syncAll(token)
                }
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(Unit) { refreshStats() }

    // Reaguje na przerwanie sesji narzucone sprzętowo (np. usługa działająca w tle)
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == "com.example.sleepguardian.SESSION_ABORTED") {
                    isSessionActive = false
                    refreshStats()
                    Toast.makeText(context, "Sesja została przerwana i odnotowana w historii.", Toast.LENGTH_LONG).show()
                }
            }
        }
        val filter = IntentFilter("com.example.sleepguardian.SESSION_ABORTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    /**
     * Obsługuje proces manualnego anulowania (poddania się) z poziomu panelu Dashboard.
     * Dodaje odpowiednie kary i aktualizuje lokalne stany, kończąc usługi sprzętowe.
     */
    fun handleSurrender() {
        isLoading = true
        coroutineScope.launch {
            val offlineManager = OfflineSyncManager(context)
            val token = tokenManager.getToken()
            val sessionId = tokenManager.getSessionId()
            val penaltyType = "Poddanie się z poziomu aplikacji"

            val prefs = context.getSharedPreferences("sleepguardian_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("negative_session_$sessionId", true).apply()

            offlineManager.recordFailedSession()
            offlineManager.recordSessionEnd()

            if (token != null) {
                if (sessionId == -2) {
                    offlineManager.addPenaltyToActiveOfflineSession(penaltyType)
                    offlineManager.endOfflineSessionAndQueue()
                } else if (sessionId > 0) {
                    try {
                        RetrofitClient.apiService.logPenalty("Bearer $token", sessionId, LogPenaltyRequest(penaltyType))
                        RetrofitClient.apiService.endSession("Bearer $token", sessionId)
                    } catch (e: Exception) {
                        offlineManager.queueStandalonePenalty(sessionId, penaltyType)
                        offlineManager.queueStandaloneEnd(sessionId)
                    }
                }
            }

            tokenManager.saveSessionId(-1)
            context.stopService(Intent(context, MosquitoService::class.java))
            context.stopService(Intent(context, LighthouseService::class.java))
            context.stopService(Intent(context, StoneTestService::class.java))
            context.stopService(Intent(context, CurtainService::class.java))

            refreshStats()
            isSessionActive = false
            isLoading = false
            Toast.makeText(context, "Mięczak! Straciłeś serce.", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundGradient()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            if (isSessionActive) "Trwa sesja snu" else "Profil snu",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        if (!isSessionActive) {
                            IconButton(onClick = { navController.navigate("history") }) {
                                Icon(Icons.Default.History, contentDescription = "Historia sesji")
                            }
                        }
                    },
                    actions = {
                        if (!isSessionActive) {
                            IconButton(onClick = {
                                tokenManager.clearToken()
                                navController.navigate("login") { popUpTo(0) { inclusive = true } }
                            }) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "Wyloguj")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricPill(
                        icon = Icons.Default.LocalFireDepartment,
                        label = "Streak",
                        value = "$currentStreak dni",
                        tint = SleepThemeWarning,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        icon = Icons.Default.Favorite,
                        label = "Serca",
                        value = "$heartsRemaining",
                        tint = SleepThemeError,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = !isSessionActive,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 })
                ) {
                    Column {
                        SectionCard {
                            Text(
                                "Harmonogram",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bedtime, contentDescription = null, tint = SleepThemeAccent)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("Sennność", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(sleepTime, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                TextButton(onClick = {
                                    TimePickerDialog(context, { _, hour, minute -> sleepTime = String.format("%02d:%02d", hour, minute) }, 22, 0, true).show()
                                }) { Text("Edytuj") }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = SleepThemeAccent)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("Pobudka", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(wakeTime, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                TextButton(onClick = {
                                    TimePickerDialog(context, { _, hour, minute -> wakeTime = String.format("%02d:%02d", hour, minute) }, 6, 0, true).show()
                                }) { Text("Edytuj") }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Metoda rygoru",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            for (i in rigourModes.indices step 2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    for (j in 0..1) {
                                        if (i + j < rigourModes.size) {
                                            val mode = rigourModes[i + j]
                                            val isSelected = selectedMode == mode.name
                                            Card(
                                                onClick = { selectedMode = mode.name },
                                                modifier = Modifier.weight(1f).aspectRatio(1.02f),
                                                shape = RoundedCornerShape(22.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                                ),
                                                border = BorderStroke(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize().padding(14.dp),
                                                    verticalArrangement = Arrangement.Center,
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(16.dp),
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                    ) {
                                                        Icon(
                                                            mode.icon,
                                                            contentDescription = mode.name,
                                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(10.dp).size(26.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    Text(
                                                        mode.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        textAlign = TextAlign.Center,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        mode.desc,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isSessionActive,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(40.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                modifier = Modifier.padding(18.dp).size(92.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(22.dp))
                        Text(
                            "Ochrona snu aktywna",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tryb: $selectedMode",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f)) // Zastąpienie stałego odstępu, aby przyciski opadały na dół ekranu

                if (isSessionActive) {
                    Button(
                        onClick = {
                            isLoading = true
                            coroutineScope.launch {
                                val offlineManager = OfflineSyncManager(context)
                                val token = "Bearer ${tokenManager.getToken()}"
                                val sessionId = tokenManager.getSessionId()

                                offlineManager.recordSuccessfulSession()
                                offlineManager.recordSessionEnd()

                                if (sessionId == -2) {
                                    offlineManager.endOfflineSessionAndQueue()
                                    tokenManager.saveSessionId(-1)
                                } else if (sessionId > 0) {
                                    try { RetrofitClient.apiService.endSession(token, sessionId) }
                                    catch (_: Exception) { offlineManager.queueStandaloneEnd(sessionId) }
                                    tokenManager.saveSessionId(-1)
                                }

                                context.stopService(Intent(context, MosquitoService::class.java))
                                context.stopService(Intent(context, LighthouseService::class.java))
                                context.stopService(Intent(context, StoneTestService::class.java))
                                context.stopService(Intent(context, CurtainService::class.java))

                                refreshStats()
                                isSessionActive = false
                                isLoading = false
                                Toast.makeText(context, "Sesja zakończona pomyślnie!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(62.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SleepThemeSuccess, contentColor = Color(0xFF07140C)),
                        enabled = !isLoading
                    ) { Text("Wstałem — zakończ sesję", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Użytkownik decyduje się na kapitulację wprost z ekranu głównego (wbudowany cooldown i straty)
                    OutlinedButton(
                        onClick = { handleSurrender() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, SleepThemeError.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SleepThemeError),
                        enabled = !isLoading
                    ) { Text("Poddaję się (-1 Serce)", style = MaterialTheme.typography.titleMedium) }

                } else {
                    Button(
                        onClick = {
                            val offlineManager = OfflineSyncManager(context)

                            // Weryfikacja blokady czasowej (Cooldown), zapobiegająca farmowaniu sesji/kar
                            if (!offlineManager.canStartNewSession()) {
                                Toast.makeText(context, "Musisz odczekać przed kolejną sesją!", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            isLoading = true
                            coroutineScope.launch {
                                val token = "Bearer ${tokenManager.getToken()}"
                                try {
                                    val request = StartSessionRequest(sleepTime, wakeTime)
                                    val response = RetrofitClient.apiService.startSession(token, request)
                                    tokenManager.saveSessionId(response.session_id)
                                } catch (_: Exception) {
                                    offlineManager.startOfflineSession(sleepTime, wakeTime)
                                    tokenManager.saveSessionId(-2)
                                } finally {
                                    isSessionActive = true
                                    when (selectedMode) {
                                        "Narastająca Kurtyna" -> {
                                            if (!Settings.canDrawOverlays(context)) {
                                                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                                            } else {
                                                ContextCompat.startForegroundService(context, Intent(context, CurtainService::class.java))
                                            }
                                        }
                                        "Test Kamienia" -> ContextCompat.startForegroundService(context, Intent(context, StoneTestService::class.java))
                                        "Upierdliwy Komar" -> ContextCompat.startForegroundService(context, Intent(context, MosquitoService::class.java))
                                        "Latarnia Morska" -> ContextCompat.startForegroundService(context, Intent(context, LighthouseService::class.java))
                                    }
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(62.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        enabled = !isLoading
                    ) { Text("Start", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                    CircularProgressIndicator(modifier = Modifier.padding(22.dp).size(44.dp), strokeWidth = 4.dp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}