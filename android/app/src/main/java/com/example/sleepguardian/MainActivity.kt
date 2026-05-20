package com.example.sleepguardian

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/**
 * Main application activity initializing the Compose UI toolkit and Material Design 3 theme.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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

/**
 * Root composable function orchestrating navigation between application screens.
 */
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

/**
 * Provides the UI for user authentication with credential retention and network state overlay.
 */
@Composable
fun LoginScreen(navController: NavController, tokenManager: TokenManager) {
    var email by remember { mutableStateOf(tokenManager.getSavedEmail()) }
    var password by remember { mutableStateOf(tokenManager.getSavedPassword()) }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = "Logo",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "SleepGuardian",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(48.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email Icon") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Hasło") },
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password Icon") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(32.dp))

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
                        } catch (e: Exception) {
                            statusMessage = "Błąd logowania. Sprawdź dane."
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading
            ) {
                Text("Zaloguj się", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { navController.navigate("register") }, enabled = !isLoading) {
                Text("Nie masz konta? Zarejestruj się", color = MaterialTheme.colorScheme.secondary)
            }

            if (statusMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = statusMessage, color = MaterialTheme.colorScheme.error)
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Provides the UI for registering a new user account.
 */
@Composable
fun RegisterScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Dołącz do nas",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(48.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email Icon") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Hasło (min. 8 znaków)") },
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password Icon") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(32.dp))

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
                        } catch (e: Exception) {
                            statusMessage = "Błąd: Ten e-mail może być już zajęty."
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading
            ) {
                Text("Utwórz konto", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { navController.popBackStack() }, enabled = !isLoading) {
                Text("Wróć do logowania", color = MaterialTheme.colorScheme.secondary)
            }

            if (statusMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = statusMessage, color = MaterialTheme.colorScheme.primary)
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Screen dedicated to fetching and displaying historical sleep sessions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController, tokenManager: TokenManager) {
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
            } catch (e: Exception) {
                errorMessage = "Nie udało się pobrać historii."
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Historia Sesji", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage.isNotEmpty()) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
            } else if (historyList.isEmpty()) {
                Text(text = "Brak zarejestrowanych sesji snu.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(historyList) { session ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                val dateStr = session.start_time?.take(10) ?: "Nieznana data"
                                Text(text = "Sesja z dnia: $dateStr", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Cel snu: ${session.target_sleep_time} - ${session.target_wake_time}", style = MaterialTheme.typography.bodyMedium)

                                val statusText = if (session.end_time != null) "Zakończona pomyślnie" else "Niezakończona"
                                val statusColor = if (session.end_time != null) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Status: $statusText", style = MaterialTheme.typography.bodySmall, color = statusColor, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Main dashboard view for configuring sleep schedules and executing penalties.
 * Manages State-Driven UI for Active Session vs Configuration Mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, tokenManager: TokenManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var sleepTime by remember { mutableStateOf("22:00") }
    var wakeTime by remember { mutableStateOf("06:00") }
    var isLoading by remember { mutableStateOf(false) }

    var currentStreak by remember { mutableIntStateOf(0) }
    var heartsRemaining by remember { mutableIntStateOf(0) }

    var isSessionActive by remember { mutableStateOf(tokenManager.getSessionId() != -1) }

    data class ModeDef(val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val desc: String)
    val rigourModes = listOf(
        ModeDef("Narastająca Kurtyna", Icons.Default.VisibilityOff, "Zasłania ekran"),
        ModeDef("Test Kamienia", Icons.Default.Smartphone, "Wykrywa ruch"),
        ModeDef("Upierdliwy Komar", Icons.Default.NotificationsActive, "Pisk 9kHz"),
        ModeDef("Latarnia Morska", Icons.Default.FlashlightOn, "Stroboskop LED")
    )
    var selectedMode by remember { mutableStateOf(rigourModes[0].name) }

    fun refreshStats() {
        coroutineScope.launch {
            try {
                val token = tokenManager.getToken()
                if (token != null) {
                    val stats = RetrofitClient.apiService.getStats("Bearer $token")
                    currentStreak = stats.current_streak
                    heartsRemaining = stats.hearts
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshStats()
    }

    fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            context,
            { _, hour, minute -> onTimeSelected(String.format("%02d:%02d", hour, minute)) },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(if (isSessionActive) "Trwa Sesja Snu" else "Profil Snu", fontWeight = FontWeight.Medium) },
                    navigationIcon = {
                        if (!isSessionActive) {
                            IconButton(onClick = { navController.navigate("history") }) {
                                Icon(Icons.Default.History, contentDescription = "Historia Sesji", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    actions = {
                        if (!isSessionActive) {
                            IconButton(
                                onClick = {
                                    tokenManager.clearToken()
                                    navController.navigate("login") { popUpTo(0) { inclusive = true } }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Wyloguj", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFFF3E0),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = "Streak", tint = Color(0xFFFF9800))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$currentStreak Dni",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFFEBEE),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = "Serca", tint = Color(0xFFE53935))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$heartsRemaining",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = !isSessionActive,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text("Harmonogram", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Sennność: $sleepTime", style = MaterialTheme.typography.bodyLarge)
                                    }
                                    TextButton(onClick = { showTimePicker { sleepTime = it } }) { Text("Edytuj") }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Pobudka: $wakeTime", style = MaterialTheme.typography.bodyLarge)
                                    }
                                    TextButton(onClick = { showTimePicker { wakeTime = it } }) { Text("Edytuj") }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Metoda Rygoru",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            for (i in rigourModes.indices step 2) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    for (j in 0..1) {
                                        if (i + j < rigourModes.size) {
                                            val mode = rigourModes[i + j]
                                            val isSelected = selectedMode == mode.name

                                            OutlinedCard(
                                                onClick = { selectedMode = mode.name },
                                                modifier = Modifier.weight(1f).aspectRatio(1.1f),
                                                shape = RoundedCornerShape(20.dp),
                                                colors = CardDefaults.outlinedCardColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                                ),
                                                border = CardDefaults.outlinedCardBorder(
                                                    (if (isSelected) 2.dp else 1.dp) != 0.dp
                                                ).copy(brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize().padding(12.dp),
                                                    verticalArrangement = Arrangement.Center,
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Icon(imageVector = mode.icon, contentDescription = mode.name, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(text = mode.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(text = mode.desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                                }
                                            }
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
                    enter = fadeIn() + slideInVertically { height -> height / 2 },
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            modifier = Modifier.size(120.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Ochrona Snu Aktywna",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Odłóż telefon. Rygor: $selectedMode",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        if (isSessionActive) {
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    val token = "Bearer ${tokenManager.getToken()}"
                                    val sessionId = tokenManager.getSessionId()
                                    if (sessionId != -1) {
                                        RetrofitClient.apiService.endSession(token, sessionId)
                                        tokenManager.saveSessionId(-1)
                                    }

                                    context.stopService(Intent(context, MosquitoService::class.java))
                                    context.stopService(Intent(context, LighthouseService::class.java))
                                    context.stopService(Intent(context, StoneTestService::class.java))
                                    context.stopService(Intent(context, CurtainService::class.java))

                                    refreshStats()
                                    isSessionActive = false
                                    Toast.makeText(context, "Sesja zakończona. Dzień dobry!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Błąd podczas zamykania sesji.", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        } else {
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    val token = "Bearer ${tokenManager.getToken()}"
                                    val request = StartSessionRequest(sleepTime, wakeTime)
                                    val response = RetrofitClient.apiService.startSession(token, request)

                                    tokenManager.saveSessionId(response.session_id)
                                    isSessionActive = true

                                    when (selectedMode) {
                                        "Narastająca Kurtyna" -> {
                                            if (!Settings.canDrawOverlays(context)) {
                                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                                context.startActivity(intent)
                                            } else {
                                                ContextCompat.startForegroundService(context, Intent(context, CurtainService::class.java))
                                            }
                                        }
                                        "Test Kamienia" -> ContextCompat.startForegroundService(context, Intent(context, StoneTestService::class.java))
                                        "Upierdliwy Komar" -> ContextCompat.startForegroundService(context, Intent(context, MosquitoService::class.java))
                                        "Latarnia Morska" -> ContextCompat.startForegroundService(context, Intent(context, LighthouseService::class.java))
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Błąd serwera. Sprawdź połączenie.", Toast.LENGTH_LONG).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(bottom = 8.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSessionActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                    ),
                    enabled = !isLoading
                ) {
                    Text(
                        text = if (isSessionActive) "WSTAŁEM (Zakończ)" else "Zaczynamy",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}