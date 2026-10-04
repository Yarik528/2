package com.example.wifichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Используем темную тему Material 3
            MaterialTheme(colorScheme = darkColorScheme()) {
                WifiChatApp()
            }
        }
    }
}

@Composable
fun WifiChatApp(viewModel: ChatViewModel = viewModel()) {
    val isConnected by viewModel.isConnected.collectAsState()
    var ipInput by remember { mutableStateOf("192.168.43.1") } // Стандартный IP точки доступа Android

    if (!isConnected) {
        // ЭКРАН ПОДКЛЮЧЕНИЯ
        ConnectionScreen(
            ipInput = ipInput,
            onIpChange = { ipInput = it },
            onStartHost = { viewModel.startAsHost() },
            onJoinClient = { viewModel.startAsClient(ipInput) }
        )
    } else {
        // ЭКРАН ЧАТА
        ChatScreen(viewModel = viewModel)
    }
}

@Composable
fun ConnectionScreen(
    ipInput: String,
    onIpChange: (String) -> Unit,
    onStartHost: () -> Unit,
    onJoinClient: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📡 WiFi Mesh Chat",
            color = Color.Cyan,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(40.dp))

        // Кнопка создания сервера
        Button(
            onClick = onStartHost,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Создать чат (Я Хост)", fontSize = 18.sp, color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("ИЛИ", color = Color.Gray, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(24.dp))

        // Поле ввода IP
        OutlinedTextField(
            value = ipInput,
            onValueChange = onIpChange,
            label = { Text("IP адрес хоста") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.Cyan,
                focusedBorderColor = Color.Cyan
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка подключения
        Button(
            onClick = onJoinClient,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Подключиться", fontSize = 18.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(40.dp))
        Text(
            "Инструкция:\n1. Включи точку доступа Wi-Fi на телефоне.\n2. Друг подключается к твоему Wi-Fi.\n3. Жми 'Создать чат'.\n4. Друг жмет 'Подключиться' (IP обычно 192.168.43.1).",
            color = Color.Gray,
            fontSize = 14.sp
        )
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsState()
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(top = 40.dp)
    ) {
        // Заголовок
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(16.dp)
        ) {
            Text(
                text = "🟢 Подключено | Локальный чат",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Список сообщений
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                MessageBubble(msg)
            }
        }

        // Поле ввода
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение...", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.Cyan,
                    focusedBorderColor = Color(0xFF2C2C2C),
                    unfocusedBorderColor = Color(0xFF2C2C2C)
                ),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(50.dp)
                    .background(Color.Cyan, RoundedCornerShape(25.dp))
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .background(
                    color = if (message.isMine) Color(0xFF00BCD4) else Color(0xFF2C2C2C),
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (message.isMine) 16.dp else 4.dp,
                        bottomEnd = if (message.isMine) 4.dp else 16.dp
                    )
                )
                .padding(12.dp)
        ) {
            Text(text = message.text, color = Color.White, fontSize = 16.sp)
        }
    }
}
