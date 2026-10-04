package com.example.wifichat

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.webkit.MimeTypeMap
import coil.compose.rememberAsyncImagePainter
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
                AppNavigation(vm)
            }
        }
    }
}

// Навигация между экранами
@Composable
fun AppNavigation(viewModel: ChatViewModel) {
    var currentScreen by remember { mutableStateOf("connection") }

    when (currentScreen) {
        "connection" -> ConnectionScreen(
            viewModel = viewModel,
            onStartHost = { viewModel.startAsHost(); currentScreen = "chat" },
            onJoinClient = { ip -> viewModel.startAsClient(ip); currentScreen = "chat" },
            onOpenSettings = { currentScreen = "settings" }
        )
        "chat" -> ChatScreen(
            viewModel = viewModel,
            onBack = { currentScreen = "connection" },
            onOpenSettings = { currentScreen = "settings" }
        )
        "settings" -> SettingsScreen(
            viewModel = viewModel,
            onBack = { currentScreen = if (viewModel.isConnected.value) "chat" else "connection" }
        )
    }
}

// ===== ЭКРАН ПОДКЛЮЧЕНИЯ =====
@Composable
fun ConnectionScreen(viewModel: ChatViewModel, onStartHost: () -> Unit, onJoinClient: (String) -> Unit, onOpenSettings: () -> Unit) {
    var ipInput by remember { mutableStateOf("192.168.43.1") }

    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        
        // Аватарка и имя
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(path = viewModel.settings.avatarPath, size = 60)
            Spacer(Modifier.width(16.dp))
            Text(viewModel.settings.userName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(40.dp))
        Text("💬 Локальный мессенджер", color = Color.Cyan, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(40.dp))
        
        Button(onStartHost, Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(Color(0xFF00BCD4)), shape = RoundedCornerShape(16.dp)) {
            Text("Создать чат (Я Хост)", fontSize = 18.sp, color = Color.Black, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp)); Text("ИЛИ", color = Color.Gray); Spacer(Modifier.height(24.dp))
        OutlinedTextField(ipInput, { ipInput = it }, label = { Text("IP адрес хоста") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.Cyan, focusedBorderColor = Color.Cyan), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(16.dp))
        Button({ onJoinClient(ipInput) }, Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(Color(0xFF2C2C2C)), shape = RoundedCornerShape(16.dp)) {
            Text("Подключиться", fontSize = 18.sp, color = Color.White)
        }
        
        Spacer(Modifier.height(40.dp))
        TextButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.Cyan)
            Spacer(Modifier.width(8.dp))
            Text("Настройки", color = Color.Cyan, fontSize = 16.sp)
        }
    }
}

// ===== ЭКРАН ЧАТА =====
@Composable
fun ChatScreen(viewModel: ChatViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val messages by viewModel.messages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && viewModel.settings.autoScrollEnabled) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.sendFile(it) }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(top = 40.dp)) {
        // Верхняя панель
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("🟢 Локальный чат", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg -> MessageBubble(msg, viewModel.settings) }
        }

        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { pickFileLauncher.launch("*/*") }) {
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

// ===== ЭКРАН НАСТРОЕК =====
@Composable
fun SettingsScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val settings = viewModel.settings
    var nameInput by remember { mutableStateOf(settings.userName) }
    val context = LocalContext.current

    val pickAvatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            // Сохраняем аватарку локально
            val file = File(context.filesDir, "avatar.jpg")
            context.contentResolver.openInputStream(it)?.use { input ->
                file.outputStream().use { out -> input.copyTo(out) }
            }
            settings.avatarPath = file.absolutePath
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF121212))) {
        // Верхняя панель
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("⚙️ Настройки", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            
            // Аватарка
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarImage(path = settings.avatarPath, size = 80)
                Spacer(Modifier.width(16.dp))
                Button(onClick = { pickAvatarLauncher.launch("image/*") }, colors = ButtonDefaults.buttonColors(Color(0xFF2C2C2C))) {
                    Text("Выбрать фото", color = Color.White)
                }
            }

            Divider(color = Color.DarkGray)

            // Имя
            Text("Имя пользователя", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = nameInput, onValueChange = { nameInput = it },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.Cyan, focusedBorderColor = Color.Cyan),
                shape = RoundedCornerShape(12.dp)
            )
            Button(onClick = { settings.userName = nameInput }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(Color(0xFF00BCD4))) {
                Text("Сохранить имя", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Divider(color = Color.DarkGray)

            // Цвет пузырей
            Text("Цвет сообщений", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val colors = listOf(Color(0xFF00BCD4), Color(0xFF4CAF50), Color(0xFFFF5722), Color(0xFF9C27B0), Color(0xFF2196F3))
                colors.forEachIndexed { index, color ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(color).clickable { settings.bubbleColorIndex = index }.then(
                            if (settings.bubbleColorIndex == index) Modifier.border(3.dp, Color.White, CircleShape) else Modifier
                        )
                    )
                }
            }

            Divider(color = Color.DarkGray)

            // Размер текста
            Text("Размер текста", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Маленький", "Средний", "Большой").forEachIndexed { index, label ->
                    FilterChip(
                        selected = settings.textSizeIndex == index,
                        onClick = { settings.textSizeIndex = index },
                        label = { Text(label, color = if (settings.textSizeIndex == index) Color.Black else Color.White) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF2C2C2C), selectedContainerColor = Color.Cyan)
                    )
                }
            }

            Divider(color = Color.DarkGray)

            // Переключатели
            SwitchRow("Вибрация при получении", settings.vibrationEnabled) { settings.vibrationEnabled = it }
            SwitchRow("Звук при получении", settings.soundEnabled) { settings.soundEnabled = it }
            SwitchRow("Автопрокрутка чата", settings.autoScrollEnabled) { settings.autoScrollEnabled = it }

            Divider(color = Color.DarkGray)

            // Очистить историю
            Button(
                onClick = { viewModel.clearHistory() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(Color(0xFFD32F2F))
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Очистить историю чата", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 16.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.Cyan, checkedTrackColor = Color(0xFF00BCD4).copy(alpha = 0.5f)))
    }
}

// ===== КОМПОНЕНТЫ =====

@Composable
fun AvatarImage(path: String?, size: Int) {
    if (path != null && File(path).exists()) {
        Image(
            painter = rememberAsyncImagePainter(File(path)),
            contentDescription = "Avatar",
            modifier = Modifier.size(size.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF2C2C2C)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size((size / 2).dp))
        }
    }
}

@Composable
fun MessageBubble(message: Message, settings: SettingsManager) {
    val context = LocalContext.current
    val bubbleColor = if (message.isMine) settings.getBubbleColor() else Color(0xFF2C2C2C)
    val textSize = settings.getTextSize().sp

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier.widthIn(max = 260.dp).background(
                color = bubbleColor,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (message.isMine) 16.dp else 4.dp, bottomEnd = if (message.isMine) 4.dp else 16.dp)
            ).padding(12.dp)
        ) {
            when {
                message.filePath != null && isImage(message.filePath) -> {
                    Image(
                        painter = rememberAsyncImagePainter(File(message.filePath)),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                message.filePath != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { openFile(context, message) }) {
                        Text(if (isVideo(message.filePath)) "🎬" else "📄", fontSize = 28.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(message.fileName ?: "Файл", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(formatFileSize(message.fileSize), color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                }
                else -> Text(text = message.text ?: "", color = Color.White, fontSize = textSize)
            }
        }
    }
}

// ===== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ =====

fun isImage(path: String): Boolean {
    val ext = path.substringAfterLast('.', "").lowercase()
    return ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
}

fun isVideo(path: String): Boolean {
    val ext = path.substringAfterLast('.', "").lowercase()
    return ext in listOf("mp4", "mkv", "webm", "3gp", "mov", "avi")
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> String.format("%.1f МБ", bytes / (1024.0 * 1024.0))
}

fun openFile(context: Context, message: Message) {
    try {
        val file = File(message.filePath ?: return)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Открыть файл"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
