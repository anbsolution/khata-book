package com.anbsolution.khatabook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anbsolution.khatabook.data.KhataDatabaseProvider
import com.anbsolution.khatabook.services.Appwrite
import com.anbsolution.khatabook.services.AuthRepository
import kotlinx.coroutines.launch

private val RoyalBlue = Color(0xFF1746A2)
private val PageWhite = Color(0xFFF7F9FC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Appwrite.init(applicationContext)
        KhataDatabaseProvider.get(applicationContext)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = RoyalBlue,
                    background = PageWhite,
                    surface = Color.White
                )
            ) {
                KhataBookApp()
            }
        }
    }
}

@Composable
fun KhataBookApp() {
    val authRepository = remember { AuthRepository() }
    var signedIn by remember { mutableStateOf(false) }
    var checkingSession by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        signedIn = try {
            authRepository.currentUser()
            true
        } catch (_: Exception) {
            false
        }
        checkingSession = false
    }

    if (checkingSession) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = RoyalBlue)
        }
    } else if (!signedIn) {
        AuthScreen(
            authRepository = authRepository,
            onAuthenticated = { signedIn = true }
        )
    } else {
        MainDashboard(
            authRepository = authRepository,
            onLogout = { signedIn = false }
        )
    }
}

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onAuthenticated: () -> Unit
) {
    var isRegister by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
            if (isRegister) "Create your business account"
            else "Sign in to your account",
            color = Color.DarkGray
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
                        }
                        authRepository.login(
                            email = email.trim(),
                            password = password
                        )
                        authRepository.currentUser()
                        onAuthenticated()
                    } catch (e: Exception) {
                        error = e.message ?: "Authentication failed"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy &&
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
                Text(if (isRegister) "Create Account" else "Login")
            }
        }

        TextButton(
            onClick = {
                isRegister = !isRegister
                error = null
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isRegister) "Already have an account? Login"
                else "New user? Create account"
            )
        }

        Text(
            "Your account data is protected by your sign-in.",
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

@Composable
fun MainDashboard(
    authRepository: AuthRepository,
    onLogout: () -> Unit
) {
    var selected by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
            NavigationBar(containerColor = Color.White) {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = {
                            Icon(icons[index], contentDescription = label)
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {
            Text(
                "Khata Book",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = RoyalBlue
            )
            Spacer(Modifier.height(20.dp))

            when (selected) {
                0 -> HomeScreen()
                1 -> PlaceholderScreen("Customers")
                2 -> PlaceholderScreen("Reports")
                else -> {
                    Text("Settings", fontSize = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    if (error != null) {
                        Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    authRepository.logout()
                                    onLogout()
                                } catch (e: Exception) {
                                    error = e.message ?: "Logout failed"
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
}

@Composable
fun HomeScreen() {
    Text("Business overview", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(14.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryCard("You will get", "₹0", Modifier.weight(1f))
        SummaryCard("You will give", "₹0", Modifier.weight(1f))
    }

    Spacer(Modifier.height(16.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Recent activity", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Your transactions will appear here.")
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.DarkGray, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                amount,
                color = RoyalBlue,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("$title screen — implementation pending", color = Color.DarkGray)
    }
}
