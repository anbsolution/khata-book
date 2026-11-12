package com.anbsolution.khatabook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import com.anbsolution.khatabook.data.LedgerTransactionEntity
import com.anbsolution.khatabook.services.Appwrite
import com.anbsolution.khatabook.services.AuthRepository
import com.anbsolution.khatabook.services.PinLockStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

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
    val scope = rememberCoroutineScope()

    var signedIn by remember { mutableStateOf(false) }
    var checkingSession by remember { mutableStateOf(true) }
    var pinConfigured by remember { mutableStateOf(false) }
    var pinUnlocked by remember { mutableStateOf(false) }
    var recoveryError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        signedIn = try {
            authRepository.currentUser()
            pinConfigured = PinLockStore.hasPin(context)
            true
        } catch (_: Exception) {
            PinLockStore.clear(context)
            pinConfigured = false
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
                recoveryError = e.message ?: "Email login required. Logout failed; check your internet and retry."
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
                    pinConfigured = PinLockStore.hasPin(context)
                    pinUnlocked = false
                    recoveryError = null
                    signedIn = true
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
                onLogout = {
                    PinLockStore.clear(context)
                    pinConfigured = false
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
            color = Color.Gray,
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
                        error = if (e is io.appwrite.exceptions.AppwriteException) {
                            "Appwrite error: ${e.type} (HTTP ${e.code})\n${e.message ?: "No details"}"
                        } else {
                            "${e::class.java.simpleName}: ${e.message ?: "Authentication failed"}"
                        }
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
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
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

    val activeCustomer = selectedCustomer
    if (activeCustomer != null) {
        CustomerLedgerScreen(
            database = database,
            customer = activeCustomer,
            onBack = { selectedCustomer = null }
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
            Text("Customers", fontSize = 21.sp, fontWeight = FontWeight.Bold)
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

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        when {
            loading -> CircularProgressIndicator()
            customers.isEmpty() -> Text(
                "No customers found. Add your first customer.",
                color = Color.Gray
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(customers, key = { it.id }) { customer ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCustomer = customer },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                            Text(
                                "Open ledger  →",
                                color = RoyalBlue,
                                fontSize = 13.sp
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
                                            val count = dao.getCustomerTransactionCount(customer.id)
                                            if (count > 0) {
                                                error = "This customer has $count ledger entries. Delete is blocked to protect account history."
                                            } else {
                                                dao.deleteCustomer(customer)
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
                                if (old == null) dao.saveCustomer(item)
                                else dao.updateCustomer(item)
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

@Composable
fun CustomerLedgerScreen(
    database: KhataDatabase,
    customer: CustomerEntity,
    onBack: () -> Unit
) {
    val dao = remember { database.khataDao() }
    val scope = rememberCoroutineScope()

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

    suspend fun refresh() {
        transactions = dao.getCustomerTransactions(customer.id)
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onBack) {
                Text("← Back")
            }
            Column {
                Text(customer.name, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(customer.phone, color = Color.Gray, fontSize = 13.sp)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Current balance", color = Color.Gray)
                Text(
                    formatRupees(kotlin.math.abs(balance)),
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (balance >= 0) RoyalBlue else Color(0xFF16834A)
                )
                Text(
                    if (balance > 0) "Customer owes you"
                    else if (balance < 0) "You owe customer"
                    else "Account settled",
                    color = Color.DarkGray
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("You will get", color = Color.Gray, fontSize = 12.sp)
                        Text(formatRupees(udharTotal), color = RoyalBlue, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("You received", color = Color.Gray, fontSize = 12.sp)
                        Text(formatRupees(jamaTotal), color = Color(0xFF16834A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Button(
            onClick = {
                transactionType = "UDHAR"
                amount = ""
                note = ""
                error = null
                showDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Add transaction")
        }

        Text("Transaction history", fontWeight = FontWeight.Bold, fontSize = 18.sp)

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        when {
            loading -> CircularProgressIndicator()
            transactions.isEmpty() -> Text(
                "No transactions yet.",
                color = Color.Gray
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions, key = { it.id }) { item ->
                    val isUdhar = item.type == "UDHAR"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (isUdhar) "Udhar" else "Jama",
                                    fontWeight = FontWeight.Bold
                                )
                                if (item.note.isNotBlank()) {
                                    Text(item.note, color = Color.Gray, fontSize = 13.sp)
                                }
                                Text(
                                    java.text.DateFormat.getDateTimeInstance(
                                        java.text.DateFormat.MEDIUM,
                                        java.text.DateFormat.SHORT
                                    ).format(java.util.Date(item.createdAt)),
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                (if (isUdhar) "+" else "-") + formatRupees(item.amountPaise),
                                color = if (isUdhar) RoyalBlue else Color(0xFF16834A),
                                fontWeight = FontWeight.Bold
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
                                        dao.saveTransaction(transaction)
                                    } else {
                                        dao.updateTransaction(transaction)
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
                            dao.deleteTransaction(pendingDelete)
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
