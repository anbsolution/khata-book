package com.anbsolution.khatabook

import android.os.Bundle
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anbsolution.khatabook.data.KhataDatabaseProvider
import com.anbsolution.khatabook.data.KhataDatabase
import com.anbsolution.khatabook.data.BusinessEntity
import com.anbsolution.khatabook.data.CustomerEntity
import com.anbsolution.khatabook.data.LedgerTransactionEntity
import com.anbsolution.khatabook.domain.LedgerCalculator
import com.anbsolution.khatabook.domain.LedgerPeriod
import com.anbsolution.khatabook.services.AuthRepository
import com.anbsolution.khatabook.services.AppAppearanceMode
import com.anbsolution.khatabook.services.AppearancePreferenceStore
import com.anbsolution.khatabook.services.AutomaticBackupCadence
import com.anbsolution.khatabook.services.LocalAutomaticBackupFiles
import com.anbsolution.khatabook.services.LocalAutomaticBackupScheduler
import com.anbsolution.khatabook.services.LocalApiClient
import com.anbsolution.khatabook.services.LocalApiServer
import com.anbsolution.khatabook.services.PinLockStore
import com.anbsolution.khatabook.services.DeviceBindingStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast
import com.anbsolution.khatabook.services.normalizeIndianWhatsAppPhoneNumber
import com.anbsolution.khatabook.services.CustomerStatementPdf
import com.anbsolution.khatabook.services.ReportExportData
import com.anbsolution.khatabook.services.ReportOutstandingCustomer
import com.anbsolution.khatabook.services.ReportTransaction
import com.anbsolution.khatabook.services.ReportsCsv
import com.anbsolution.khatabook.services.ReportsPdf
import com.anbsolution.khatabook.services.StatementSharePreferenceStore
import com.anbsolution.khatabook.services.StatementShareTarget
import kotlinx.coroutines.Job

private data class KhataPalette(
    val royalBlue: Color,
    val pageBackground: Color,
    val primaryTint: Color,
    val mutedText: Color,
    val surfaceBorder: Color,
    val jamaGreen: Color,
    val jamaTint: Color,
    val warningText: Color
)

private val LightKhataPalette = KhataPalette(
    royalBlue = Color(0xFF174EA6),
    pageBackground = Color(0xFFF2F4F7),
    primaryTint = Color(0xFFE8EFFB),
    mutedText = Color(0xFF667085),
    surfaceBorder = Color(0xFFE5E7EB),
    jamaGreen = Color(0xFF16834A),
    jamaTint = Color(0xFFE9F5ED),
    warningText = Color(0xFF9A5B00)
)
private val DarkKhataPalette = KhataPalette(
    royalBlue = Color(0xFF9DBBFF),
    pageBackground = Color(0xFF0D1117),
    primaryTint = Color(0xFF1F3251),
    mutedText = Color(0xFFA0A8B5),
    surfaceBorder = Color(0xFF344054),
    jamaGreen = Color(0xFF72D19B),
    jamaTint = Color(0xFF163425),
    warningText = Color(0xFFF4C36A)
)
private val LocalKhataPalette = staticCompositionLocalOf { LightKhataPalette }
private val RoyalBlue: Color
    @Composable get() = LocalKhataPalette.current.royalBlue
private val PageWhite: Color
    @Composable get() = LocalKhataPalette.current.pageBackground
private val PrimaryTint: Color
    @Composable get() = LocalKhataPalette.current.primaryTint
private val MutedText: Color
    @Composable get() = LocalKhataPalette.current.mutedText
private val SurfaceBorder: Color
    @Composable get() = LocalKhataPalette.current.surfaceBorder
private val JamaGreen: Color
    @Composable get() = LocalKhataPalette.current.jamaGreen
private val JamaTint: Color
    @Composable get() = LocalKhataPalette.current.jamaTint
private val WarningText: Color
    @Composable get() = LocalKhataPalette.current.warningText

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LocalApiServer.start(applicationContext)
        KhataDatabaseProvider.get(applicationContext)
        LocalAutomaticBackupScheduler.ensureScheduled(applicationContext)

        setContent {
            val context = LocalContext.current
            var appearanceMode by remember {
                mutableStateOf(AppearancePreferenceStore.load(context))
            }
            var appearanceError by remember { mutableStateOf<String?>(null) }

            KhataBookTheme(appearanceMode) {
                KhataBookApp(
                    appearanceMode = appearanceMode,
                    appearanceError = appearanceError,
                    onAppearanceModeChanged = { mode ->
                        try {
                            AppearancePreferenceStore.save(context, mode)
                            appearanceMode = mode
                            appearanceError = null
                        } catch (e: Exception) {
                            appearanceError = e.message ?: "Unable to save appearance setting."
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun KhataBookTheme(
    mode: AppAppearanceMode,
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        AppAppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppAppearanceMode.LIGHT -> false
        AppAppearanceMode.DARK -> true
    }
    val palette = if (darkTheme) DarkKhataPalette else LightKhataPalette
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF174EA6),
            onPrimary = Color.White,
            secondary = Color(0xFFD4AF37),
            background = palette.pageBackground,
            surface = Color(0xFF151B25),
            onSurface = Color(0xFFE6EAF0),
            onSurfaceVariant = palette.mutedText,
            outline = palette.surfaceBorder,
            error = Color(0xFFFFB4AB)
        )
    } else {
        lightColorScheme(
            primary = palette.royalBlue,
            onPrimary = Color.White,
            secondary = Color(0xFFD4AF37),
            background = palette.pageBackground,
            surface = Color.White,
            onSurface = Color(0xFF17243A),
            onSurfaceVariant = palette.mutedText,
            outline = palette.surfaceBorder,
            error = Color(0xFFC62828)
        )
    }

    CompositionLocalProvider(LocalKhataPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(
                bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif)
            ),
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp)
            ),
            content = content
        )
    }
}

@Composable
fun KhataBookApp(
    appearanceMode: AppAppearanceMode,
    appearanceError: String?,
    onAppearanceModeChanged: (AppAppearanceMode) -> Unit
) {
    val context = LocalContext.current
    val database = remember { KhataDatabaseProvider.get(context) }
    val api = remember(context) { LocalApiClient(context) }
    val authRepository = remember(api) { AuthRepository(api) }
    val scope = rememberCoroutineScope()

    var signedIn by remember { mutableStateOf(false) }
    var checkingSession by remember { mutableStateOf(true) }
    var pinConfigured by remember { mutableStateOf(false) }
    var pinUnlocked by remember { mutableStateOf(false) }
    var recoveryError by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, signedIn, pinConfigured, pinUnlocked) {
        var lockJob: Job? = null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (signedIn && pinConfigured && pinUnlocked) {
                        lockJob?.cancel()
                        lockJob = scope.launch {
                            delay(60_000)
                            pinUnlocked = false
                        }
                    }
                }
                Lifecycle.Event.ON_START -> {
                    lockJob?.cancel()
                    lockJob = null
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            lockJob?.cancel()
        }
    }

    LaunchedEffect(signedIn, pinUnlocked) {
        if (signedIn && pinUnlocked && LocalAutomaticBackupFiles.latest(context) == null) {
            LocalAutomaticBackupScheduler.createInitialBackup(context)
        }
    }

    LaunchedEffect(Unit) {
        signedIn = try {
            authRepository.currentUser()
            val hasLocalPin = PinLockStore.hasPin(context)
            if (hasLocalPin && !DeviceBindingStore.verify(context)) {
                // Existing PIN without a valid installation key requires email reauthentication.
                pinConfigured = false
                false
            } else {
                if (!hasLocalPin && !DeviceBindingStore.isRegistered(context)) {
                    DeviceBindingStore.register(context)
                }
                pinConfigured = hasLocalPin
                true
            }
        } catch (_: Exception) {
            // Network/session lookup errors must never erase local PIN or device keys.
            pinConfigured = PinLockStore.hasPin(context)
            false
        }
        checkingSession = false
    }

    fun startEmailRecovery() {
        recoveryError = null
        scope.launch {
            try {
                authRepository.logout()
                PinLockStore.clear(context)
                pinConfigured = false
                pinUnlocked = false
                signedIn = false
            } catch (e: Exception) {
                recoveryError = e.message ?: "Unable to reset the local PIN."
            }
        }
    }

    when {
        checkingSession -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RoyalBlue)
            }
        }

        !signedIn -> {
            AuthScreen(
                authRepository = authRepository,
                onAuthenticated = {
                    scope.launch {
                        try {
                            if (!DeviceBindingStore.verify(context)) {
                                PinLockStore.clear(context)
                                DeviceBindingStore.clear(context)
                                DeviceBindingStore.register(context)
                            }
                            pinConfigured = PinLockStore.hasPin(context)
                            pinUnlocked = false
                            recoveryError = null
                            signedIn = true
                        } catch (e: Exception) {
                            recoveryError = e.message ?: "Unable to register this device securely."
                        }
                    }
                }
            )
        }

        !pinUnlocked && !pinConfigured -> {
            PinSetupScreen(
                onPinCreated = {
                    pinConfigured = true
                    pinUnlocked = true
                }
            )
        }

        !pinUnlocked -> {
            PinUnlockScreen(
                recoveryError = recoveryError,
                onUnlocked = { pinUnlocked = true },
                onForgotPin = { startEmailRecovery() }
            )
        }

        else -> {
            MainDashboard(
                database = database,
                authRepository = authRepository,
                appearanceMode = appearanceMode,
                appearanceError = appearanceError,
                onAppearanceModeChanged = onAppearanceModeChanged,
                onLogout = {
                    pinConfigured = PinLockStore.hasPin(context)
                    pinUnlocked = false
                    signedIn = false
                }
            )
        }
    }
}

@Composable
private fun PinSetupScreen(
    onPinCreated: () -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Create your 4-digit PIN",
            color = RoyalBlue,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text("This PIN unlocks Khata Book on this device.")
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= 4 && it.all(Char::isDigit)) {
                    pin = it
                    error = null
                }
            },
            label = { Text("Enter 4-digit PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmPin,
            onValueChange = {
                if (it.length <= 4 && it.all(Char::isDigit)) {
                    confirmPin = it
                    error = null
                }
            },
            label = { Text("Confirm 4-digit PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            enabled = !busy && pin.length == 4 && confirmPin.length == 4,
            onClick = {
                if (pin != confirmPin) {
                    error = "PINs do not match. Try again."
                } else {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            PinLockStore.setPin(context, pin)
                            pin = ""
                            confirmPin = ""
                            onPinCreated()
                        } catch (e: Exception) {
                            error = e.message ?: "Unable to save PIN securely."
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(if (busy) "Saving..." else "Save PIN")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Your PIN is stored securely on this device. It is not your account password.",
            color = MutedText,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun PinUnlockScreen(
    recoveryError: String?,
    onUnlocked: () -> Unit,
    onForgotPin: () -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var remainingSeconds by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(remainingSeconds) {
        if (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds -= 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Welcome back",
            color = RoyalBlue,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text("Enter your 4-digit PIN to unlock Khata Book.")
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= 4 && it.all(Char::isDigit)) {
                    pin = it
                    message = null
                }
            },
            label = { Text("4-digit PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )

        recoveryError?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        if (remainingSeconds > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Too many incorrect attempts. Try again in $remainingSeconds seconds.",
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            enabled = !busy && remainingSeconds == 0 && pin.length == 4,
            onClick = {
                busy = true
                scope.launch {
                    when (val result = PinLockStore.verifyPin(context, pin)) {
                        is PinLockStore.VerifyResult.Success -> {
                            pin = ""
                            message = null
                            onUnlocked()
                        }
                        is PinLockStore.VerifyResult.Incorrect -> {
                            pin = ""
                            message = "Incorrect PIN. ${result.attemptsRemaining} attempts remaining."
                        }
                        is PinLockStore.VerifyResult.Locked -> {
                            pin = ""
                            remainingSeconds = result.secondsRemaining
                            message = null
                        }
                        is PinLockStore.VerifyResult.StorageError -> {
                            pin = ""
                            message = "Secure PIN data is unavailable. Use Forgot PIN to sign in again."
                        }
                    }
                    busy = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(if (busy) "Checking..." else "Unlock")
        }

        val activity = context as? FragmentActivity
        val biometricAvailable = BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (activity != null && biometricAvailable) {
            OutlinedButton(
                onClick = {
                    val prompt = BiometricPrompt(
                        activity,
                        ContextCompat.getMainExecutor(context),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(
                                result: BiometricPrompt.AuthenticationResult
                            ) {
                                pin = ""
                                message = null
                                onUnlocked()
                            }

                            override fun onAuthenticationError(
                                errorCode: Int,
                                errString: CharSequence
                            ) {
                                message = "Biometric unavailable. You can unlock with your PIN."
                            }

                            override fun onAuthenticationFailed() {
                                message = "Fingerprint not recognized. Try again or use your PIN."
                            }
                        }
                    )
                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock Khata Book")
                        .setSubtitle("Verify your fingerprint or supported strong biometric")
                        .setDescription("Your biometric data stays with Android; Khata Book does not receive it.")
                        .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG
                        )
                        .setNegativeButtonText("Use PIN")
                        .build()
                    prompt.authenticate(promptInfo)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Unlock with fingerprint")
            }
        }

        TextButton(
            onClick = onForgotPin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Forgot PIN? Sign in with email")
        }
    }
}

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onAuthenticated: () -> Unit
) {
    var isRegister by remember { mutableStateOf(true) }
    var hasLegacyData by remember { mutableStateOf(false) }
    var statusLoaded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(authRepository) {
        try {
            val status = authRepository.status()
            isRegister = !status.configured
            hasLegacyData = status.hasLegacyData
            statusLoaded = true
        } catch (e: Exception) {
            error = e.message ?: "Unable to check local account status."
            statusLoaded = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Khata Book",
            color = RoyalBlue,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                !statusLoaded -> "Checking this device..."
                isRegister && hasLegacyData -> "Set a new local password for your existing email"
                isRegister -> "Create your local account"
                else -> "Sign in to your local account"
            },
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(28.dp))

        if (isRegister) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email address") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error ?: "", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                error = null
                busy = true
                scope.launch {
                    try {
                        if (isRegister) {
                            authRepository.register(
                                name = name.trim(),
                                email = email.trim(),
                                password = password
                            )
                        } else {
                            authRepository.login(
                                email = email.trim(),
                                password = password
                            )
                        }
                        authRepository.currentUser()
                        onAuthenticated()
                    } catch (e: Exception) {
                        error = e.message ?: "Authentication failed."
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = statusLoaded && !busy &&
                email.isNotBlank() &&
                password.isNotBlank() &&
                (!isRegister || name.isNotBlank()),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(if (isRegister) "Set Local Password" else "Sign In")
            }
        }

        Text(
            if (hasLegacyData) {
                "Your existing ledger stays on this device. Passwords cannot be migrated; choose a new local password."
            } else {
                "Your account and ledger are stored privately on this device."
            },
            color = MutedText,
            fontSize = 12.sp
        )
    }
}

@Composable
fun MainDashboard(
    database: KhataDatabase,
    authRepository: AuthRepository,
    appearanceMode: AppAppearanceMode,
    appearanceError: String?,
    onAppearanceModeChanged: (AppAppearanceMode) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val api = remember(context) { LocalApiClient(context) }
    var selected by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var accountId by remember { mutableStateOf<String?>(null) }
    var businessName by remember { mutableStateOf("My Business") }
    var profileChecked by remember { mutableStateOf(false) }
    var needsBusinessSetup by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val id = authRepository.currentUser().id
            accountId = id
            val profile = api.getBusiness()
            needsBusinessSetup = profile == null
            businessName = profile?.businessName
                ?.takeIf(String::isNotBlank)
                ?: "My Business"
        } catch (e: Exception) {
            error = e.message ?: "Unable to load your business profile"
        } finally {
            profileChecked = true
        }
    }

    val id = accountId
    if (profileChecked && needsBusinessSetup && id != null) {
        BusinessSetupScreen(
            database = database,
            accountId = id,
            onSaved = { name ->
                businessName = name
                needsBusinessSetup = false
            }
        )
        return
    }

    val tabs = listOf("Home", "Customers", "Reports", "Settings")
    val icons = listOf(
        Icons.Default.Home,
        Icons.Default.People,
        Icons.Default.Assessment,
        Icons.Default.Settings
    )

    Scaffold(
        containerColor = PageWhite,
        bottomBar = {
            Column {
                HorizontalDivider(color = SurfaceBorder, thickness = 0.5.dp)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    tabs.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = {
                                Icon(icons[index], contentDescription = label)
                            },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = RoyalBlue,
                                selectedTextColor = RoyalBlue,
                                unselectedIconColor = MutedText,
                                unselectedTextColor = MutedText,
                                indicatorColor = PrimaryTint
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = PrimaryTint
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = RoyalBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column {
                    Text(
                        "Khata Book",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        businessName,
                        fontSize = 13.sp,
                        color = MutedText
                    )
                }
            }
            Spacer(Modifier.height(22.dp))

            if (id != null) error?.let { ErrorBanner(it) }

            when {
                !profileChecked && error == null -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                id == null -> ErrorBanner(error ?: "Unable to load account profile")
                else -> DashboardTabContent(
                    selected = selected,
                    database = database,
                    accountId = id,
                    businessName = businessName,
                    authRepository = authRepository,
                    appearanceMode = appearanceMode,
                    appearanceError = appearanceError,
                    onAppearanceModeChanged = onAppearanceModeChanged,
                    onBusinessNameChanged = { businessName = it },
                    onLogout = onLogout,
                    onLogoutError = { error = it }
                )
            }
        }
    }
}

@Composable
private fun DashboardTabContent(
    selected: Int,
    database: KhataDatabase,
    accountId: String,
    businessName: String,
    authRepository: AuthRepository,
    appearanceMode: AppAppearanceMode,
    appearanceError: String?,
    onAppearanceModeChanged: (AppAppearanceMode) -> Unit,
    onBusinessNameChanged: (String) -> Unit,
    onLogout: () -> Unit,
    onLogoutError: (String) -> Unit
) {
    when (selected) {
        0 -> HomeScreen(database, accountId)
        1 -> CustomerScreen(database, accountId, businessName)
        2 -> ReportsScreen(database, accountId, businessName)
        else -> {
            val scope = rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                AppearancePreferenceSection(
                    mode = appearanceMode,
                    error = appearanceError,
                    onModeSelected = onAppearanceModeChanged
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("App lock", fontWeight = FontWeight.SemiBold)
                        Text(
                            "The app locks behind your PIN after it stays in the background for one minute.",
                            color = MutedText,
                            fontSize = 13.sp
                        )
                    }
                }
                BusinessProfileSection(
                    database = database,
                    accountId = accountId,
                    businessName = businessName,
                    onBusinessNameChanged = onBusinessNameChanged
                )
                StatementSharingPreferenceSection()
                AutomaticBackupSection()
                KhataBackupSection(database)
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                authRepository.logout()
                                onLogout()
                            } catch (e: Exception) {
                                onLogoutError(e.message ?: "Logout failed")
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (busy) "Please wait..." else "Logout")
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun BusinessSetupScreen(
    database: KhataDatabase,
    accountId: String,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val api = remember(context) { LocalApiClient(context) }
    val scope = rememberCoroutineScope()
    var businessName by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Set up your business", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Add a business name to personalize your Khata Book.",
                    color = MutedText,
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = businessName,
                    onValueChange = {
                        businessName = it
                        error = null
                    },
                    label = { Text("Business name") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        val name = businessName.trim()
                        if (name.isBlank()) {
                            error = "Enter a business name."
                        } else {
                            scope.launch {
                                saving = true
                                error = null
                                try {
                                    api.saveBusiness(
                                        BusinessEntity(
                                            id = UUID.randomUUID().toString(),
                                            accountId = accountId,
                                            businessName = name,
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )
                                    onSaved(name)
                                } catch (e: Exception) {
                                    error = e.message ?: "Unable to save business profile."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                    enabled = businessName.isNotBlank() && !saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (saving) "Saving..." else "Continue")
                }
            }
        }
    }
}

@Composable
fun HomeScreen(database: KhataDatabase, businessId: String?) {
    val context = LocalContext.current
    val api = remember(context) { LocalApiClient(context) }
    val scope = rememberCoroutineScope()
    var customers by remember { mutableStateOf<List<CustomerEntity>>(emptyList()) }
    var transactions by remember { mutableStateOf<List<LedgerTransactionEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val id = businessId ?: return
        val refreshedCustomers = api.getCustomers(id)
        val refreshedTransactions = api.getBusinessTransactions(id)
        customers = refreshedCustomers
        transactions = refreshedTransactions
        error = null
    }

    LaunchedEffect(businessId) {
        if (businessId == null) return@LaunchedEffect
        loading = true
        try {
            refresh()
        } catch (e: Exception) {
            error = e.message ?: "Unable to load business activity"
        } finally {
            loading = false
        }
    }

    val balances = remember(transactions) {
        transactions
            .groupBy { it.customerId }
            .mapValues { (_, entries) ->
                entries.sumOf {
                    when (it.type) {
                        "UDHAR" -> it.amountPaise
                        "JAMA" -> -it.amountPaise
                        else -> 0L
                    }
                }
            }
            .values
    }
    val totalOutstanding = balances.filter { it > 0L }.sum()
    val totalAdvance = balances.filter { it < 0L }.sumOf { kotlin.math.abs(it) }
    val customerNames = remember(customers) { customers.associate { it.id to it.name } }
    val now = System.currentTimeMillis()
    val todayStart = LedgerCalculator.periodStartMillis(LedgerPeriod.TODAY, now)
    val todayTransactions = transactions.filter { it.createdAt in todayStart..now }
    val todayUdhar = todayTransactions.filter { it.type == "UDHAR" }.sumOf { it.amountPaise }
    val todayJama = todayTransactions.filter { it.type == "JAMA" }.sumOf { it.amountPaise }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Business overview", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Your balances at a glance", color = MutedText, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))

        when {
            loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            refresh()
                        } catch (e: Exception) {
                            error = e.message ?: "Unable to load business activity"
                        } finally {
                            loading = false
                        }
                    }
                }) {
                    Text("Retry")
                }
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard("You will get", formatRupees(totalOutstanding), Modifier.weight(1f))
                    SummaryCard("You owe", formatRupees(totalAdvance), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Today's Udhar", color = MutedText, fontSize = 12.sp)
                            Text(
                                formatRupees(todayUdhar),
                                color = RoyalBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Today's Jama", color = MutedText, fontSize = 12.sp)
                            Text(
                                formatRupees(todayJama),
                                color = JamaGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "${customers.size} customers · ${transactions.size} transactions",
                    color = MutedText,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Recent activity", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (transactions.isNotEmpty()) {
                        Text("${transactions.size} entries", color = MutedText, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (transactions.isEmpty()) {
                    EmptyStateCard("No transactions yet", "New ledger activity will appear here.")
                } else {
                    transactions.take(5).forEach { transaction ->
                        TransactionSummaryRow(
                            transaction = transaction,
                            customerName = customerNames[transaction.customerId] ?: "Customer"
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionSummaryRow(
    transaction: LedgerTransactionEntity,
    customerName: String
) {
    val isUdhar = transaction.type == "UDHAR"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(customerName, fontWeight = FontWeight.SemiBold)
                Text(
                    "${if (isUdhar) "Udhar" else "Jama"} · " +
                        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                            .format(java.util.Date(transaction.createdAt)),
                    color = MutedText,
                    fontSize = 12.sp
                )
            }
            Text(
                (if (isUdhar) "+" else "-") + formatRupees(transaction.amountPaise),
                color = if (isUdhar) RoyalBlue else JamaGreen,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Receipt, contentDescription = null, tint = MutedText)
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = MutedText, fontSize = 13.sp)
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    amount: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(17.dp)) {
            Text(title, color = MutedText, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                amount,
                color = RoyalBlue,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private enum class ReportExportFormat {
    PDF,
    CSV
}

private suspend fun saveReportsExport(
    context: Context,
    uri: Uri,
    data: ReportExportData,
    format: ReportExportFormat
) {
    withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri)
            ?: error("Could not open the selected destination.")
        output.use {
            when (format) {
                ReportExportFormat.PDF -> ReportsPdf.write(it, data)
                ReportExportFormat.CSV -> {
                    it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    it.write(ReportsCsv.render(data).toByteArray(Charsets.UTF_8))
                }
            }
        }
    }
}

@Composable
fun ReportsScreen(
    database: KhataDatabase,
    businessId: String?,
    businessName: String
) {
    val context = LocalContext.current
    val api = remember(context) { LocalApiClient(context) }
    val scope = rememberCoroutineScope()
    var customers by remember { mutableStateOf<List<CustomerEntity>>(emptyList()) }
    var transactions by remember { mutableStateOf<List<LedgerTransactionEntity>>(emptyList()) }
    var selectedRange by remember { mutableIntStateOf(2) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingReportExport by remember { mutableStateOf<ReportExportData?>(null) }
    var exportingReport by remember { mutableStateOf(false) }
    var reportExportMessage by remember { mutableStateOf<String?>(null) }
    var reportExportFailed by remember { mutableStateOf(false) }
    val ranges = listOf("Today", "Last 7 days", "This month", "All time")

    suspend fun refresh() {
        val id = businessId ?: return
        val refreshedCustomers = api.getCustomers(id)
        val refreshedTransactions = api.getBusinessTransactions(id)
        customers = refreshedCustomers
        transactions = refreshedTransactions
        error = null
    }

    LaunchedEffect(businessId) {
        if (businessId == null) return@LaunchedEffect
        loading = true
        try {
            refresh()
        } catch (e: Exception) {
            error = e.message ?: "Unable to load reports"
        } finally {
            loading = false
        }
    }

    val now = System.currentTimeMillis()
    val period = when (selectedRange) {
        0 -> LedgerPeriod.TODAY
        1 -> LedgerPeriod.LAST_7_DAYS
        2 -> LedgerPeriod.THIS_MONTH
        else -> LedgerPeriod.ALL_TIME
    }
    val periodStart = LedgerCalculator.periodStartMillis(period, now)
    val periodTransactions = transactions.filter {
        LedgerCalculator.isWithinPeriod(it.createdAt, periodStart, now)
    }
    val udharTotal = periodTransactions.filter { it.type == "UDHAR" }.sumOf { it.amountPaise }
    val jamaTotal = periodTransactions.filter { it.type == "JAMA" }.sumOf { it.amountPaise }
    val customerNames = remember(customers) { customers.associate { it.id to it.name } }
    val outstandingCustomers = remember(transactions, customerNames) {
        transactions
            .groupBy { it.customerId }
            .mapValues { (_, entries) ->
                entries.sumOf {
                    when (it.type) {
                        "UDHAR" -> it.amountPaise
                        "JAMA" -> -it.amountPaise
                        else -> 0L
                    }
                }
            }
            .filterValues { it > 0L }
            .entries
            .sortedByDescending { it.value }
            .take(5)
    }

    fun createReportExportData(): ReportExportData {
        val allOutstandingCustomers = transactions
            .groupBy { it.customerId }
            .mapNotNull { (customerId, entries) ->
                val balancePaise = entries.sumOf {
                    when (it.type) {
                        "UDHAR" -> it.amountPaise
                        "JAMA" -> -it.amountPaise
                        else -> 0L
                    }
                }
                if (balancePaise > 0L) {
                    ReportOutstandingCustomer(
                        customerName = customerNames[customerId] ?: "Customer",
                        balancePaise = balancePaise
                    )
                } else {
                    null
                }
            }
            .sortedByDescending { it.balancePaise }

        return ReportExportData(
            businessName = businessName,
            periodLabel = ranges[selectedRange],
            generatedAt = System.currentTimeMillis(),
            totalUdharPaise = udharTotal,
            totalJamaPaise = jamaTotal,
            outstandingCustomers = allOutstandingCustomers,
            transactions = periodTransactions.map { transaction ->
                ReportTransaction(
                    createdAt = transaction.createdAt,
                    customerName = customerNames[transaction.customerId] ?: "Customer",
                    type = transaction.type,
                    amountPaise = transaction.amountPaise,
                    note = transaction.note
                )
            }
        )
    }

    fun completeReportExport(uri: Uri?, format: ReportExportFormat) {
        val exportData = pendingReportExport
        pendingReportExport = null
        if (uri == null) {
            exportingReport = false
            reportExportMessage = null
            return
        }
        if (exportData == null) {
            exportingReport = false
            reportExportFailed = true
            reportExportMessage = "Could not prepare the report for export."
            return
        }

        scope.launch {
            try {
                saveReportsExport(context, uri, exportData, format)
                reportExportFailed = false
                reportExportMessage = "Report saved successfully."
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                reportExportFailed = true
                reportExportMessage = e.message ?: "Report export failed."
            } finally {
                exportingReport = false
            }
        }
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        completeReportExport(uri, ReportExportFormat.PDF)
    }
    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        completeReportExport(uri, ReportExportFormat.CSV)
    }

    fun launchReportExport(format: ReportExportFormat) {
        reportExportMessage = "Choose a destination to save the report."
        reportExportFailed = false
        exportingReport = true
        pendingReportExport = createReportExportData()
        val periodFileName = ranges[selectedRange]
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        val date = java.text.SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            .format(java.util.Date())
        val filename = "khata_report_${periodFileName}_$date"
        try {
            when (format) {
                ReportExportFormat.PDF -> pdfExportLauncher.launch("$filename.pdf")
                ReportExportFormat.CSV -> csvExportLauncher.launch("$filename.csv")
            }
        } catch (e: ActivityNotFoundException) {
            pendingReportExport = null
            exportingReport = false
            reportExportFailed = true
            reportExportMessage = e.message ?: "No file picker is available to save the report."
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Reports", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text("A clear view of your ledger activity", color = MutedText, fontSize = 13.sp)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Report period", color = MutedText, fontSize = 13.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ranges.forEachIndexed { index, label ->
                        FilterChip(
                            selected = selectedRange == index,
                            onClick = { selectedRange = index },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { launchReportExport(ReportExportFormat.PDF) },
                        enabled = !loading && error == null && !exportingReport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Export PDF")
                    }
                    OutlinedButton(
                        onClick = { launchReportExport(ReportExportFormat.CSV) },
                        enabled = !loading && error == null && !exportingReport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Export CSV")
                    }
                }
                reportExportMessage?.let { message ->
                    Text(
                        message,
                        color = if (reportExportFailed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MutedText
                        },
                        fontSize = 12.sp
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    loading -> Text("Loading report data...", color = MutedText)
                    error != null -> Text(
                        "Report totals are unavailable.",
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryCard("Udhar", formatRupees(udharTotal), Modifier.weight(1f))
                            SummaryCard("Jama", formatRupees(jamaTotal), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryCard(
                                if (udharTotal >= jamaTotal) "Period due" else "Period advance",
                                formatRupees(kotlin.math.abs(udharTotal - jamaTotal)),
                                Modifier.weight(1f)
                            )
                            SummaryCard("Entries", periodTransactions.size.toString(), Modifier.weight(1f))
                        }
                        Text(
                            "Based on ${periodTransactions.size} ledger entries",
                            color = MutedText,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        if (error != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            try {
                                refresh()
                            } catch (e: Exception) {
                                error = e.message ?: "Unable to load reports"
                            } finally {
                                loading = false
                            }
                        }
                    }) {
                        Text("Retry")
                    }
                }
            }
        }
        item {
            Text(
                "Current outstanding",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        when {
            loading -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> Unit
            outstandingCustomers.isEmpty() -> item {
                EmptyStateCard("All settled", "No customers have an outstanding balance.")
            }
            else -> items(outstandingCustomers, key = { "balance-${it.key}" }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                customerNames[entry.key] ?: "Customer",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("Outstanding", color = MutedText, fontSize = 12.sp)
                        }
                        Text(
                            formatRupees(entry.value),
                            color = RoyalBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Transactions", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (!loading) Text("${periodTransactions.size} entries", color = MutedText, fontSize = 12.sp)
            }
        }
        if (loading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (error == null && periodTransactions.isEmpty()) {
            item { EmptyStateCard("No activity in this period", "Choose another period to review ledger entries.") }
        } else if (error == null) {
            items(periodTransactions.take(30), key = { "transaction-${it.id}" }) { transaction ->
                TransactionSummaryRow(
                    transaction = transaction,
                    customerName = customerNames[transaction.customerId] ?: "Customer"
                )
            }
            if (periodTransactions.size > 30) {
                item {
                    Text(
                        "Showing the 30 most recent entries.",
                        color = MutedText,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BusinessProfileSection(
    database: KhataDatabase,
    accountId: String?,
    businessName: String,
    onBusinessNameChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val api = remember(context) { LocalApiClient(context) }
    val scope = rememberCoroutineScope()
    var name by remember(accountId, businessName) { mutableStateOf(businessName) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Business profile", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("This name appears in your app header.", color = MutedText, fontSize = 13.sp)
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    status = null
                },
                label = { Text("Business name") },
                singleLine = true,
                enabled = accountId != null && !saving,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val id = accountId ?: return@Button
                    scope.launch {
                        saving = true
                        status = null
                        try {
                            val existing = api.getBusiness()
                            val updatedName = name.trim()
                            api.saveBusiness(
                                BusinessEntity(
                                    id = existing?.id ?: UUID.randomUUID().toString(),
                                    accountId = id,
                                    businessName = updatedName,
                                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                                )
                            )
                            onBusinessNameChanged(updatedName)
                            status = "Business profile saved."
                            statusIsError = false
                        } catch (e: Exception) {
                            status = e.message ?: "Unable to save business profile."
                            statusIsError = true
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = accountId != null && name.isNotBlank() &&
                    name.trim() != businessName && !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saving) "Saving..." else "Save business name")
            }
            status?.let {
                Text(
                    it,
                    color = if (statusIsError) MaterialTheme.colorScheme.error else JamaGreen,
                    fontSize = 13.sp
                )
            }
        }
    }
}



@Composable
fun CustomerScreen(
    database: KhataDatabase,
    businessId: String?,
    businessName: String
) {
    val context = LocalContext.current
    val api = remember(context) { LocalApiClient(context) }
    val scope = rememberCoroutineScope()

    var customers by remember { mutableStateOf<List<CustomerEntity>>(emptyList()) }
    var businessTransactions by remember {
        mutableStateOf<List<LedgerTransactionEntity>>(emptyList())
    }
    var search by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var editingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val id = businessId ?: return
        val refreshedCustomers = if (search.isBlank()) {
            api.getCustomers(id)
        } else {
            api.searchCustomers(id, search.trim())
        }
        val refreshedTransactions = api.getBusinessTransactions(id)
        customers = refreshedCustomers
        businessTransactions = refreshedTransactions
        error = null
    }

    LaunchedEffect(businessId) {
        if (businessId == null) return@LaunchedEffect
        try {
            refresh()
        } catch (e: Exception) {
            error = e.message ?: "Unable to load customers"
        } finally {
            loading = false
        }
    }

    val customerBalances = remember(businessTransactions) {
        businessTransactions
            .groupBy { it.customerId }
            .mapValues { (_, entries) ->
                entries.sumOf {
                    when (it.type) {
                        "UDHAR" -> it.amountPaise
                        "JAMA" -> -it.amountPaise
                        else -> 0L
                    }
                }
            }
    }

    val activeCustomer = selectedCustomer
    if (activeCustomer != null) {
        CustomerLedgerScreen(
            database = database,
            customer = activeCustomer,
            businessName = businessName,
            onBack = {
                selectedCustomer = null
                scope.launch {
                    try {
                        refresh()
                    } catch (e: Exception) {
                        error = e.message ?: "Unable to refresh customer balances"
                    }
                }
            }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Customers", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = {
                    editingCustomer = null
                    customerName = ""
                    customerPhone = ""
                    showDialog = true
                },
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { value ->
                search = value
                scope.launch {
                    try {
                        refresh()
                    } catch (e: Exception) {
                        error = e.message ?: "Search failed"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search name or phone") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MutedText)
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        when {
            loading -> CircularProgressIndicator()
            error != null -> Column {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = {
                    scope.launch {
                        loading = true
                        try {
                            refresh()
                        } catch (e: Exception) {
                            error = e.message ?: "Unable to load customers"
                        } finally {
                            loading = false
                        }
                    }
                }) {
                    Text("Retry")
                }
            }
            customers.isEmpty() -> Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        tint = MutedText,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "No customers found. Add your first customer.",
                        color = MutedText,
                        fontSize = 14.sp
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(customers, key = { it.id }) { customer ->
                    val balance = customerBalances[customer.id] ?: 0L
                    val status = when {
                        balance > 0L -> "Outstanding"
                        balance < 0L -> "Advance"
                        else -> "Settled"
                    }
                    val statusColor = when {
                        balance > 0L -> RoyalBlue
                        balance < 0L -> JamaGreen
                        else -> MutedText
                    }
                    val statusSurface = when {
                        balance > 0L -> PrimaryTint
                        balance < 0L -> JamaTint
                        else -> PageWhite
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCustomer = customer },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = CircleShape,
                                    color = PrimaryTint
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            customer.name.take(1).uppercase(Locale.getDefault()),
                                            color = RoyalBlue,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        customer.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            customer.phone.ifBlank { "No phone number" },
                                            color = MutedText,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(50.dp),
                                            color = statusSurface
                                        ) {
                                            Text(
                                                status,
                                                modifier = Modifier.padding(
                                                    horizontal = 8.dp,
                                                    vertical = 4.dp
                                                ),
                                                color = statusColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    Text(
                                        "Balance ${formatRupees(kotlin.math.abs(balance))}",
                                        color = statusColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Open ledger",
                                    tint = MutedText
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 12.dp),
                                color = SurfaceBorder
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Open ledger",
                                    color = RoyalBlue,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    editingCustomer = customer
                                    customerName = customer.name
                                    customerPhone = customer.phone
                                    showDialog = true
                                }) {
                                    Text("Edit")
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            val count = api.getCustomerTransactionCount(customer.id)
                                            if (count > 0) {
                                                error = "This customer has $count ledger entries. Delete is blocked to protect account history."
                                            } else {
                                                api.deleteCustomer(customer)
                                                refresh()
                                                error = null
                                            }
                                        } catch (e: Exception) {
                                            error = e.message ?: "Delete failed"
                                        }
                                    }
                                }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(if (editingCustomer == null) "Add Customer" else "Edit Customer")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Customer name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("Mobile number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = customerName.isNotBlank() && businessId != null,
                    onClick = {
                        scope.launch {
                            try {
                                val old = editingCustomer
                                val item = CustomerEntity(
                                    id = old?.id ?: UUID.randomUUID().toString(),
                                    businessId = businessId!!,
                                    name = customerName.trim(),
                                    phone = customerPhone.trim(),
                                    createdAt = old?.createdAt ?: System.currentTimeMillis()
                                )
                                if (old == null) api.saveCustomer(item)
                                else api.updateCustomer(item)
                                showDialog = false
                                refresh()
                            } catch (e: Exception) {
                                error = e.message ?: "Unable to save customer"
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatRupees(amountPaise: Long): String {
    val rupees = amountPaise / 100
    val paise = kotlin.math.abs(amountPaise % 100)
    return "₹$rupees.${paise.toString().padStart(2, '0')}"
}

private fun createCustomerStatementMessage(
    businessName: String,
    customer: CustomerEntity,
    transactions: List<LedgerTransactionEntity>
): String {
    val udharTotal = transactions.filter { it.type == "UDHAR" }.sumOf { it.amountPaise }
    val jamaTotal = transactions.filter { it.type == "JAMA" }.sumOf { it.amountPaise }
    val balance = udharTotal - jamaTotal
    val safeBusinessName = businessName.replace(Regex("[\\r\\n]+"), " ").trim()
    val safeCustomerName = customer.name.replace(Regex("[\\r\\n]+"), " ").trim()

    return buildString {
        appendLine("Namaste $safeCustomerName,")
        appendLine("Statement from ${safeBusinessName.ifBlank { "Khata Book" }}")
        appendLine()
        appendLine("Total Udhar: ${formatRupees(udharTotal)}")
        appendLine("Total Jama: ${formatRupees(jamaTotal)}")
        appendLine("Current balance: ${formatRupees(kotlin.math.abs(balance))}")
        appendLine(
            when {
                balance > 0L -> "Outstanding to business"
                balance < 0L -> "Advance with customer"
                else -> "Account settled"
            }
        )
        if (transactions.isNotEmpty()) {
            appendLine()
            appendLine("Recent entries")
            transactions.take(5).forEach { transaction ->
                val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                    .format(java.util.Date(transaction.createdAt))
                val type = if (transaction.type == "UDHAR") "Udhar" else "Jama"
                appendLine("$date · $type · ${formatRupees(transaction.amountPaise)}")
            }
        }
    }
}

private fun openCustomerStatementShare(
    context: Context,
    whatsappPhone: String?,
    message: String,
    shareTarget: StatementShareTarget
) {
    if (whatsappPhone != null && shareTarget.whatsappPackages.isNotEmpty()) {
        val uri = Uri.parse("https://wa.me/$whatsappPhone?text=${Uri.encode(message)}")
        shareTarget.whatsappPackages.forEach { packageName ->
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).setPackage(packageName)
                )
                return
            } catch (_: ActivityNotFoundException) {
                // Try WhatsApp Business or the system share sheet.
            }
        }
    }

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(
        Intent.createChooser(sendIntent, "Share customer statement")
    )
}

private fun shareActionLabel(
    phone: String?,
    shareTarget: StatementShareTarget
): String = when {
    phone == null -> "Share"
    shareTarget == StatementShareTarget.SYSTEM_SHARE -> "Open share sheet"
    shareTarget == StatementShareTarget.WHATSAPP_BUSINESS -> "Continue to WhatsApp Business"
    else -> "Continue to WhatsApp"
}

@Composable
fun CustomerLedgerScreen(
    database: KhataDatabase,
    customer: CustomerEntity,
    businessName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val api = remember(context) { LocalApiClient(context) }
    val scope = rememberCoroutineScope()
    var selectedShareTarget by remember(context) {
        mutableStateOf(StatementSharePreferenceStore.load(context))
    }

    var transactions by remember {
        mutableStateOf<List<LedgerTransactionEntity>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    var transactionType by remember { mutableStateOf("UDHAR") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<LedgerTransactionEntity?>(null) }
    var deletingTransaction by remember { mutableStateOf<LedgerTransactionEntity?>(null) }
    var showShareDialog by remember { mutableStateOf(false) }
    var shareMessage by remember { mutableStateOf("") }
    var shareError by remember { mutableStateOf<String?>(null) }
    var pendingPdfTransactions by remember {
        mutableStateOf<List<LedgerTransactionEntity>?>(null)
    }
    var exportingPdf by remember { mutableStateOf(false) }
    var pdfExportMessage by remember { mutableStateOf<String?>(null) }
    var pdfExportFailed by remember { mutableStateOf(false) }
    var transactionSearchQuery by remember(customer.id) { mutableStateOf("") }
    var selectedTransactionPeriod by remember(customer.id) {
        mutableStateOf(LedgerPeriod.ALL_TIME)
    }
    val ledgerPeriods = listOf(
        LedgerPeriod.TODAY to "Today",
        LedgerPeriod.LAST_7_DAYS to "Last 7 days",
        LedgerPeriod.THIS_MONTH to "This month",
        LedgerPeriod.ALL_TIME to "All time"
    )
    val ledgerNow = System.currentTimeMillis()
    val ledgerPeriodStart = LedgerCalculator.periodStartMillis(
        selectedTransactionPeriod,
        ledgerNow
    )
    val visibleTransactions = transactions.filter {
        LedgerCalculator.isWithinPeriod(it.createdAt, ledgerPeriodStart, ledgerNow) &&
            LedgerCalculator.matchesTransactionSearch(
                transactionSearchQuery,
                it.note,
                it.type,
                it.amountPaise
            )
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val statementTransactions = pendingPdfTransactions
        pendingPdfTransactions = null
        if (uri == null) {
            exportingPdf = false
        } else if (statementTransactions == null) {
            exportingPdf = false
            pdfExportFailed = true
            pdfExportMessage = "Could not prepare the customer statement."
        } else {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: error("Could not open the selected destination.")
                        output.use {
                            CustomerStatementPdf.write(
                                output = it,
                                businessName = businessName,
                                customer = customer,
                                transactions = statementTransactions
                            )
                        }
                    }
                    pdfExportFailed = false
                    pdfExportMessage = "PDF statement saved successfully."
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    pdfExportFailed = true
                    pdfExportMessage = e.message ?: "PDF statement export failed."
                } finally {
                    exportingPdf = false
                }
            }
        }
    }

    suspend fun refresh() {
        transactions = api.getCustomerTransactions(customer.id)
        error = null
    }

    LaunchedEffect(customer.id) {
        try {
            refresh()
        } catch (e: Exception) {
            error = e.message ?: "Unable to load ledger"
        } finally {
            loading = false
        }
    }

    val udharTotal = transactions
        .filter { it.type == "UDHAR" }
        .sumOf { it.amountPaise }

    val jamaTotal = transactions
        .filter { it.type == "JAMA" }
        .sumOf { it.amountPaise }

    val balance = udharTotal - jamaTotal

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = RoyalBlue
                )
            }
            Column {
                Text(customer.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(customer.phone, color = MutedText, fontSize = 13.sp)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Current balance", color = MutedText, fontSize = 14.sp)
                Text(
                    formatRupees(kotlin.math.abs(balance)),
                    fontSize = 31.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (balance >= 0) RoyalBlue else JamaGreen
                )
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = if (balance < 0) JamaTint else PrimaryTint
                ) {
                    Text(
                        if (balance > 0) "Customer owes you"
                        else if (balance < 0) "You owe customer"
                        else "Account settled",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        color = if (balance < 0) JamaGreen else RoyalBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(18.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = PageWhite
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("You will get", color = MutedText, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                formatRupees(udharTotal),
                                color = RoyalBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("You received", color = MutedText, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                formatRupees(jamaTotal),
                                color = JamaGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    transactionType = "UDHAR"
                    amount = ""
                    note = ""
                    error = null
                    showDialog = true
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add transaction")
            }
            OutlinedButton(
                onClick = {
                    pdfExportMessage = null
                    pdfExportFailed = false
                    exportingPdf = true
                    pendingPdfTransactions = transactions.toList()
                    val filename = customer.name
                        .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                        .take(40)
                        .ifBlank { "customer" } +
                        "_statement_" +
                        java.text.SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                            .format(java.util.Date()) +
                        ".pdf"
                    pdfLauncher.launch(filename)
                },
                enabled = !loading && error == null && !exportingPdf,
                modifier = Modifier.size(52.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (exportingPdf) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF statement")
                }
            }
            OutlinedButton(
                onClick = {
                    selectedShareTarget = StatementSharePreferenceStore.load(context)
                    shareMessage = createCustomerStatementMessage(
                        businessName = businessName,
                        customer = customer,
                        transactions = transactions
                    )
                    shareError = null
                    showShareDialog = true
                },
                enabled = !loading && error == null && !exportingPdf,
                modifier = Modifier.size(52.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share statement")
            }
        }
        pdfExportMessage?.let {
            Text(
                it,
                color = if (pdfExportFailed) MaterialTheme.colorScheme.error else JamaGreen,
                fontSize = 13.sp
            )
        }

        Text("Transaction history", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)

        OutlinedTextField(
            value = transactionSearchQuery,
            onValueChange = { transactionSearchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search notes, amounts or type") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MutedText)
            },
            trailingIcon = {
                if (transactionSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { transactionSearchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ledgerPeriods.forEach { (period, label) ->
                FilterChip(
                    selected = selectedTransactionPeriod == period,
                    onClick = { selectedTransactionPeriod = period },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        when {
            loading -> CircularProgressIndicator()
            transactions.isEmpty() -> Text(
                "No transactions yet.",
                color = MutedText
            )
            visibleTransactions.isEmpty() -> Text(
                if (transactionSearchQuery.isBlank()) {
                    "No transactions in this period."
                } else {
                    "No matching transactions in this period."
                },
                color = MutedText
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(visibleTransactions, key = { it.id }) { item ->
                    val isUdhar = item.type == "UDHAR"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = CircleShape,
                                    color = if (isUdhar) PrimaryTint else JamaTint
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (isUdhar) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            tint = if (isUdhar) RoyalBlue else JamaGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (isUdhar) "Udhar" else "Jama",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (item.note.isNotBlank()) {
                                        Text(item.note, color = MutedText, fontSize = 13.sp)
                                    }
                                    Text(
                                        java.text.DateFormat.getDateTimeInstance(
                                            java.text.DateFormat.MEDIUM,
                                            java.text.DateFormat.SHORT
                                        ).format(java.util.Date(item.createdAt)),
                                        color = MutedText,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Text(
                                (if (isUdhar) "+" else "-") + formatRupees(item.amountPaise),
                                color = if (isUdhar) RoyalBlue else JamaGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                editingTransaction = item
                                transactionType = item.type
                                amount = BigDecimal.valueOf(item.amountPaise, 2).toPlainString()
                                note = item.note
                                error = null
                                showDialog = true
                            }) {
                                Text("Edit")
                            }
                            TextButton(onClick = {
                                deletingTransaction = item
                            }) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!saving) {
                    showDialog = false
                    editingTransaction = null
                    error = null
                }
            },
            title = {
                Text(if (editingTransaction == null) "Add transaction" else "Edit transaction")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = transactionType == "UDHAR",
                            onClick = { transactionType = "UDHAR" },
                            label = { Text("Udhar") }
                        )
                        FilterChip(
                            selected = transactionType == "JAMA",
                            onClick = { transactionType = "JAMA" },
                            label = { Text("Jama") }
                        )
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (₹)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        )
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (optional)") },
                        minLines = 2
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !saving && amount.isNotBlank(),
                    onClick = {
                        val paise = try {
                            BigDecimal(amount.trim())
                                .movePointRight(2)
                                .setScale(0, RoundingMode.HALF_UP)
                                .longValueExact()
                        } catch (_: Exception) {
                            0L
                        }

                        if (paise <= 0L) {
                            error = "Enter a valid amount greater than zero"
                        } else {
                            saving = true
                            error = null
                            scope.launch {
                                try {
                                    val existing = editingTransaction
                                    val transaction = LedgerTransactionEntity(
                                        id = existing?.id ?: UUID.randomUUID().toString(),
                                        businessId = customer.businessId,
                                        customerId = customer.id,
                                        type = transactionType,
                                        amountPaise = paise,
                                        note = note.trim(),
                                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                                        synced = false
                                    )
                                    if (existing == null) {
                                        api.saveTransaction(transaction)
                                    } else {
                                        api.updateTransaction(transaction)
                                    }
                                    refresh()
                                    showDialog = false
                                    editingTransaction = null
                                    amount = ""
                                    note = ""
                                } catch (e: Exception) {
                                    error = e.message ?: "Unable to save transaction"
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    }
                ) {
                    Text(if (saving) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        editingTransaction = null
                        error = null
                    },
                    enabled = !saving
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showShareDialog) {
        val whatsappPhone = normalizeIndianWhatsAppPhoneNumber(customer.phone)
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("Review statement") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "To: ${customer.name} · " +
                            customer.phone.ifBlank { "No phone number saved" },
                        color = MutedText,
                        fontSize = 13.sp
                    )
                    if (whatsappPhone == null) {
                        Text(
                            "A valid Indian mobile number is not saved. Continue to share using another app.",
                            color = WarningText,
                            fontSize = 12.sp
                        )
                    } else if (selectedShareTarget == StatementShareTarget.SYSTEM_SHARE) {
                        Text(
                            "The Android share sheet is selected in Settings.",
                            color = MutedText,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedTextField(
                        value = shareMessage,
                        onValueChange = { shareMessage = it },
                        label = { Text("Message") },
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    shareError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = shareMessage.isNotBlank(),
                    onClick = {
                        shareError = null
                        try {
                            openCustomerStatementShare(
                                context = context,
                                whatsappPhone = whatsappPhone,
                                message = shareMessage,
                                shareTarget = selectedShareTarget
                            )
                            showShareDialog = false
                        } catch (e: ActivityNotFoundException) {
                            shareError = e.message ?: "No compatible app is available to share this statement."
                        }
                    }
                ) {
                    Text(shareActionLabel(whatsappPhone, selectedShareTarget))
                }
            },
            dismissButton = {
                TextButton(onClick = { showShareDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val pendingDelete = deletingTransaction
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { deletingTransaction = null },
            title = { Text("Delete transaction?") },
            text = {
                Text(
                    "Delete ${pendingDelete.type.lowercase()} entry of " +
                        formatRupees(pendingDelete.amountPaise) +
                        "? This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            api.deleteTransaction(pendingDelete)
                            refresh()
                            deletingTransaction = null
                            error = null
                        } catch (e: Exception) {
                            error = e.message ?: "Unable to delete transaction"
                            deletingTransaction = null
                        }
                    }
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTransaction = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
private fun StatementSharingPreferenceSection() {
    val context = LocalContext.current
    var selectedTarget by remember(context) {
        mutableStateOf(StatementSharePreferenceStore.load(context))
    }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    fun selectTarget(target: StatementShareTarget) {
        if (target == selectedTarget) return
        try {
            StatementSharePreferenceStore.save(context, target)
            selectedTarget = target
            status = "Sharing preference saved."
            statusIsError = false
        } catch (e: Exception) {
            status = e.message ?: "Unable to save sharing preference."
            statusIsError = true
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Statement sharing", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Choose how reviewed customer statements open.",
                color = MutedText,
                fontSize = 13.sp
            )
            StatementShareTarget.entries.forEach { target ->
                val description = when (target) {
                    StatementShareTarget.AUTOMATIC ->
                        "Try WhatsApp, then WhatsApp Business; otherwise use the share sheet."
                    StatementShareTarget.WHATSAPP ->
                        "Open WhatsApp when installed; otherwise use the share sheet."
                    StatementShareTarget.WHATSAPP_BUSINESS ->
                        "Open WhatsApp Business when installed; otherwise use the share sheet."
                    StatementShareTarget.SYSTEM_SHARE ->
                        "Choose an app from the Android share sheet each time."
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectTarget(target) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedTarget == target,
                        onClick = { selectTarget(target) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(target.label, fontWeight = FontWeight.Medium)
                        Text(description, color = MutedText, fontSize = 12.sp)
                    }
                }
            }
            status?.let {
                Text(
                    it,
                    color = if (statusIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        JamaGreen
                    },
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun AppearancePreferenceSection(
    mode: AppAppearanceMode,
    error: String?,
    onModeSelected: (AppAppearanceMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Appearance", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            AppAppearanceMode.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onModeSelected(option) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mode == option,
                        onClick = { onModeSelected(option) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(option.label)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun AutomaticBackupSection() {
    val context = LocalContext.current
    val api = remember(context) { LocalApiClient(context) }
    val scope = rememberCoroutineScope()
    var latestBackup by remember(context) {
        mutableStateOf(LocalAutomaticBackupFiles.latest(context))
    }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Automatic local backups", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Encrypted daily and weekly copies are stored privately on this device. " +
                    "The newest 7 of each are kept; these copies do not survive uninstall.",
                color = MutedText,
                fontSize = 13.sp
            )
            Text(
                latestBackup?.let {
                    val date = java.text.DateFormat.getDateTimeInstance(
                        java.text.DateFormat.MEDIUM,
                        java.text.DateFormat.SHORT
                    ).format(java.util.Date(it.lastModified()))
                    "Latest copy: $date"
                } ?: "No automatic backup has completed yet.",
                color = MutedText,
                fontSize = 12.sp
            )
            OutlinedButton(
                enabled = !busy && latestBackup != null,
                modifier = Modifier.fillMaxWidth(),
                onClick = { confirmRestore = true }
            ) {
                Text(if (busy) "Please wait..." else "Restore Latest Automatic Backup")
            }
            status?.let { Text(it, color = if (it.startsWith("Restore completed")) JamaGreen else MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore automatic backup?") },
            text = {
                Text("This replaces the current local ledger with the latest encrypted automatic copy.")
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val file = latestBackup ?: return@TextButton
                        confirmRestore = false
                        busy = true
                        status = null
                        scope.launch {
                            try {
                                val bytes = withContext(Dispatchers.IO) {
                                    LocalAutomaticBackupFiles.read(file)
                                }
                                api.restoreAutomaticBackup(bytes)
                                status = "Restore completed."
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                status = e.message ?: "Automatic backup restore failed."
                            } finally {
                                busy = false
                                latestBackup = LocalAutomaticBackupFiles.latest(context)
                            }
                        }
                    }
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun KhataBackupSection(database: KhataDatabase) {
    val context = LocalContext.current
    val api = remember(context) { LocalApiClient(context) }
    val scope = rememberCoroutineScope()
    var createPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    val savePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: error("Could not open the selected destination.")
                    }
                    status = "Encrypted backup saved successfully."
                } catch (e: Exception) {
                    status = e.message ?: "Backup export failed."
                } finally {
                    pendingBytes = null
                    busy = false
                }
            }
        } else {
            pendingBytes = null
            busy = false
        }
    }

    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                total += n
                                require(total <= 50 * 1024 * 1024) { "Backup file is too large." }
                                out.write(buffer, 0, n)
                            }
                            out.toByteArray()
                        } ?: error("Could not read selected backup.")
                    }
                    api.restoreBackup(bytes, restorePassword)
                    status = "Restore completed. Local data has been replaced."
                    restorePassword = ""
                } catch (e: Exception) {
                    status = e.message ?: "Restore failed. Existing data was not changed."
                } finally {
                    busy = false
                }
            }
        } else {
            busy = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Secure Backup & Restore", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Encrypted .khata backup for this or another device.", color = MutedText, fontSize = 13.sp)
            OutlinedTextField(
                value = createPassword, onValueChange = { createPassword = it },
                label = { Text("New backup password (12+ characters)") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmPassword, onValueChange = { confirmPassword = it },
                label = { Text("Confirm backup password") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = !busy && createPassword.length >= 12 && createPassword == confirmPassword,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        busy = true
                        status = null
                        try {
                            val data = api.createBackup(createPassword)
                            pendingBytes = data
                            createPassword = ""
                            confirmPassword = ""
                            savePicker.launch("khata-book-backup.khata")
                        } catch (e: Exception) {
                            status = e.message ?: "Could not create backup."
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "Please wait..." else "Create Encrypted Backup") }

            HorizontalDivider()
            OutlinedTextField(
                value = restorePassword, onValueChange = { restorePassword = it },
                label = { Text("Backup password to restore") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                enabled = !busy && restorePassword.length >= 12,
                modifier = Modifier.fillMaxWidth(),
                onClick = { confirmRestore = true }
            ) { Text("Choose Backup & Restore") }
            Text(
                "Restore replaces current local data. Keep a separate backup first. " +
                    "A forgotten backup password cannot be recovered.",
                color = WarningText, fontSize = 12.sp
            )
            status?.let { Text(it, color = if (
                it.contains("failed", true) || it.contains("wrong", true) ||
                it.contains("invalid", true) || it.contains("could not", true)
            ) MaterialTheme.colorScheme.error else JamaGreen) }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Replace local data?") },
            text = { Text("All businesses, customers, and transactions on this device will be replaced.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    busy = true
                    restorePicker.launch(arrayOf("*/*"))
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Cancel") }
            }
        )
    }
}
