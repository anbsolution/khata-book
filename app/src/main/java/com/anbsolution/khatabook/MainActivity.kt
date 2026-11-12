package com.anbsolution.khatabook

import android.os.Bundle
import com.anbsolution.khatabook.services.Appwrite
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RoyalBlue = Color(0xFF1746A2)
private val PageWhite = Color(0xFFF7F9FC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Appwrite.init(applicationContext)
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
    var selected by remember { mutableIntStateOf(0) }
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
                        icon = { Icon(icons[index], contentDescription = label) },
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
                else -> PlaceholderScreen("Settings")
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
fun SummaryCard(title: String, amount: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.DarkGray, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(amount, color = RoyalBlue, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title screen — implementation pending", color = Color.DarkGray)
    }
}
