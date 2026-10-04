package com.example.wifichat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 100)
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                vm.init(applicationContext)
                WifiChatApp(vm)
            }
        }
    }
}

@Composable
fun WifiChatApp(viewModel: ChatViewModel) {
    val isConnected by viewModel.isConnected.collectAsState()
    var ipInput by remember { mutableStateOf("192.168.43.1") }

    if (!isConnected) {
        ConnectionScreen(ipInput, { ipInput = it }, { viewModel.startAsHost() }, { viewModel.startAsClient(ipInput) })
    } else {
        ChatScreen(viewModel)
    }
}

@Composable
fun ConnectionScreen(ipInput: String, onIpChange: (String) -> Unit, onStartHost: () -> Unit, onJoinClient: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("💬 Локальный мессенджер", color = Color.Cyan, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(40.dp))
        Button(onStartHost, Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(Color(0xFF00BCD4)), shape = RoundedCornerShape(16.dp)) {
            Text("Создать чат (Я Хост)", fontSize = 18.sp, color = Color.Black, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp)); Text("ИЛИ", color = Color.Gray); Spacer(Modifier.height(24.dp))
        OutlinedTextField(ipInput, onIpChange, label = { Text("IP адрес хоста") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.Cyan, focusedBorderColor = Color.Cyan), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(16.dp))
        Button(onJoinClient, Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(Color(0xFF2C2C2C)), shape = RoundedCornerShape(16.dp)) {
            Text("Подключиться", fontSize = 18.sp, color = Color.White)
        }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    
    // 1. СОСТОЯНИЕ СКРОЛЛА ДЛЯ АВТОПРОКРУТКИ
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 2. УНИВЕРСАЛЬНЫЙ ЛАУНЧЕР ДЛЯ ЛЮБЫХ ФАЙЛОВ
    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.sendFile(it) }
    }

    // 3. АВТОМАТИЧЕСКАЯ ПРОКРУТКА ВНИЗ ПРИ НОВОМ СООБЩЕНИИ
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(top = 40.dp)) {
        Box(Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(16.dp)) {
            Text("🟢 Подключено | Локальный чат", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // Передаем listState в LazyColumn
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 8.dp), 
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg -> MessageBubble(msg) }
        }

        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            
            // Кнопка теперь открывает любые файлы
            IconButton(onClick = { pickFileLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = Color.Cyan, modifier = Modifier.size(30.dp))
            }

            OutlinedTextField(
                value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение...", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.Cyan, focusedBorderColor = Color(0xFF2C2C2C), unfocusedBorderColor = Color(0xFF2C2C2C)),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { if (inputText.isNotBlank()) { viewModel.sendMessage(inputText); inputText = "" } },
                modifier = Modifier.size(50.dp).background(Color.Cyan, RoundedCornerShape(25.dp))
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier.widthIn(max = 260.dp).background(
                color = if (message.isMine) Color(0xFF00BCD4) else Color(0xFF2C2C2C),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (message.isMine) 16.dp else 4.dp, bottomEnd = if (message.isMine) 4.dp else 16.dp)
            ).padding(12.dp)
        ) {
            when {
                // Если это картинка (jpg, png, webp) - показываем изображение
                message.imageUri != null && (message.imageUri.endsWith(".jpg", true) || 
                                             message.imageUri.endsWith(".jpeg", true) || 
                                             message.imageUri.endsWith(".png", true) || 
                                             message.imageUri.endsWith(".webp", true)) -> {
                    Image(
                        painter = rememberAsyncImagePainter(File(message.imageUri)),
                        contentDescription = "Image",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                // Если это другой файл (pdf, apk, zip и т.д.) - показываем иконку и имя
                message.imageUri != null -> {
                    val fileName = File(message.imageUri).name
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Description, 
                            contentDescription = "File", 
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = fileName, 
                            color = Color.White, 
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Обычный текст
                else -> {
                    Text(text = message.text ?: "", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}
