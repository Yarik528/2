package com.example.wifichat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

data class Message(
    val text: String?,
    val isMine: Boolean,
    val filePath: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0L
)

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var outputStream: OutputStream? = null

    private var isBotActive = false
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ===== ОТПРАВКА ЛЮБОГО ФАЙЛА (фото, видео, документы) =====
    fun sendFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = getFileName(uri)
                val localFile = File(appContext.filesDir, fileName)

                // Сохраняем у себя, чтобы показать в чате
                val size = appContext.contentResolver.openInputStream(uri)?.use { input ->
                    localFile.outputStream().use { out -> input.copyTo(out) }
                    localFile.length()
                } ?: return@launch

                withContext(Dispatchers.Main) {
                    addMessage(null, true, localFile.absolutePath, fileName, size)
                }

                if (outputStream != null) {
                    // Реальная отправка: заголовок + байты частями (не грузим всё в память)
                    val header = "[FILE]$fileName|$size\n"
                    outputStream?.write(header.toByteArray(Charsets.UTF_8))
                    localFile.inputStream().use { input ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            outputStream?.write(buf, 0, n)
                        }
                    }
                    outputStream?.flush()
                } else if (isBotActive) {
                    delay(1000)
                    withContext(Dispatchers.Main) {
                        addMessage("🤖 Бот: получил '${fileName}' (${formatSize(size)}). Лови обратно!", false, localFile.absolutePath, fileName, size)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name: String? = null
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0)
        }
        return sanitize(name ?: "file_${System.currentTimeMillis()}")
    }

    private fun sanitize(name: String): String =
        name.replace("|", "_").replace("\n", "_").replace("\r", "_").replace("/", "_")

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
        else -> String.format("%.1f МБ", bytes / (1024.0 * 1024.0))
    }

    // ===== СЕРВЕР (ХОСТ) =====
    fun startAsHost() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(5000)
                withContext(Dispatchers.Main) {
                    _isConnected.value = true
                    addMessage("Режим Хоста. Ожидание...", false)
                }

                launch {
                    try {
                        val socket = serverSocket!!.accept()
                        isBotActive = false
                        setupStreams(socket)
                        withContext(Dispatchers.Main) { addMessage("✅ Друг подключился!", false) }
                        listenForMessages(socket)
                    } catch (_: Exception) {}
                }

                delay(3000)
                if (clientSocket == null && outputStream == null) {
                    isBotActive = true
                    withContext(Dispatchers.Main) {
                        addMessage("🤖 Бот активен. Можешь слать текст и любые файлы!", false)
                    }
                    launch {
                        messages.collect { list ->
                            if (!isBotActive) return@collect
                            val lastMsg = list.lastOrNull()
                            if (lastMsg != null && lastMsg.isMine && lastMsg.text != null) {
                                delay(800)
                                withContext(Dispatchers.Main) {
                                    addMessage("🤖 Бот: Ты написал '${lastMsg.text}'.", false)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ===== КЛИЕНТ =====
    fun startAsClient(hostIp: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = Socket(hostIp, 5000)
                clientSocket = socket
                setupStreams(socket)
                withContext(Dispatchers.Main) {
                    _isConnected.value = true
                    addMessage("✅ Подключено к хосту!", false)
                }
                listenForMessages(socket)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isConnected.value = false
                    addMessage("❌ Ошибка: ${e.message}", false)
                }
            }
        }
    }

    private fun setupStreams(socket: Socket) {
        outputStream = BufferedOutputStream(socket.getOutputStream())
    }

    // Читаем строку ПОБАЙТОВО (без BufferedReader), чтобы не ломать передачу файлов
    private fun readLineRaw(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buffer.size() == 0) null else buffer.toString("UTF-8")
            if (b == '\n'.code) return buffer.toString("UTF-8")
            buffer.write(b)
        }
    }

    // ===== ПРИЕМ СЕТИ: ТЕКСТ И ФАЙЛЫ =====
    private suspend fun listenForMessages(socket: Socket) {
        val input = socket.getInputStream()
        try {
            while (true) {
                val line = readLineRaw(input) ?: break

                if (line.startsWith("[FILE]")) {
                    val info = line.removePrefix("[FILE]").split("|")
                    val fileName = info[0]
                    var remaining = info[1].toLong()

                    val file = File(appContext.filesDir, fileName)
                    file.outputStream().use { out ->
                        val buf = ByteArray(8192)
                        while (remaining > 0) {
                            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                            if (n == -1) break
                            out.write(buf, 0, n)
                            remaining -= n
                        }
                    }
                    withContext(Dispatchers.Main) {
                        addMessage(null, false, file.absolutePath, fileName, file.length())
                    }
                } else {
                    withContext(Dispatchers.Main) { addMessage(line, false) }
                }
            }
        } catch (_: Exception) {
        } finally {
            withContext(Dispatchers.Main) {
                _isConnected.value = false
                addMessage("⚠️ Соединение разорвано.", false)
            }
        }
    }

    fun sendMessage(text: String) {
        addMessage(text, true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                outputStream?.write((text + "\n").toByteArray(Charsets.UTF_8))
                outputStream?.flush()
            } catch (_: Exception) {}
        }
    }

    private fun addMessage(text: String?, isMine: Boolean, filePath: String? = null, fileName: String? = null, fileSize: Long = 0L) {
        _messages.value = _messages.value + Message(text, isMine, filePath, fileName, fileSize)
    }

    override fun onCleared() {
        super.onCleared()
        isBotActive = false
        try { serverSocket?.close(); clientSocket?.close(); outputStream?.close() } catch (_: Exception) {}
    }
}
