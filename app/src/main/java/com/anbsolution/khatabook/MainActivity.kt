package com.anbsolution.khatabook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
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
import com.anbsolution.khatabook.data.KhataDatabase
import com.anbsolution.khatabook.data.CustomerEntity
import com.anbsolution.khatabook.services.Appwrite
import com.anbsolution.khatabook.services.AuthRepository
import kotlinx.coroutines.launch
import java.util.UUID

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
    val context = LocalContext.current
    val database = remember { KhataDatabaseProvider.get(context) }
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
            database = database,
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
    database: KhataDatabase,
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
                1 -> CustomerScreen(database, authRepository)
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


@Composable
fun CustomerScreen(
    database: KhataDatabase,
    authRepository: AuthRepository
) {
    val dao = remember { database.khataDao() }
    val scope = rememberCoroutineScope()

    var businessId by remember { mutableStateOf<String?>(null) }
    var customers by remember { mutableStateOf<List<CustomerEntity>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var editingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val id = businessId ?: return
        customers = if (search.isBlank()) {
            dao.getCustomers(id)
        } else {
            dao.searchCustomers(id, search.trim())
        }
    }

    LaunchedEffect(Unit) {
        try {
            businessId = authRepository.currentUser().id
            refresh()
        } catch (e: Exception) {
            error = e.message ?: "Unable to load customers"
        } finally {
            loading = false
        }
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
            Text(
                "Customers",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = {
                editingCustomer = null
                customerName = ""
                customerPhone = ""
                showDialog = true
            }) {
                Text("+ Add")
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
            singleLine = true
        )

        if (error != null) {
            Text(
                error ?: "",
                color = MaterialTheme.colorScheme.error
            )
        }

        when {
            loading -> {
                CircularProgressIndicator()
            }

            customers.isEmpty() -> {
                Text(
                    "No customers found. Add your first customer.",
                    color = Color.Gray
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(customers, key = { it.id }) { customer ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    customer.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    customer.phone.ifBlank { "No phone number" },
                                    color = Color.Gray
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
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
                                                dao.deleteCustomer(customer)
                                                refresh()
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
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone
                        )
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
                                if (old == null) {
                                    dao.saveCustomer(item)
                                } else {
                                    dao.updateCustomer(item)
                                }
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
