package com.example.wifichat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.net.ServerSocket
import java.net.Socket

data class Message(val text: String?, val isMine: Boolean, val imageUri: String? = null)

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    
    // Используем потоки байтов напрямую, а не PrintWriter
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    
    private var isBotActive = false
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // --- ОТПРАВКА ФАЙЛА ---
    fun sendFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStreamFile = appContext.contentResolver.openInputStream(uri) ?: return@launch
                val bytes = inputStreamFile.use { it.readBytes() }
                
                // Получаем имя файла
                var fileName = "photo_${System.currentTimeMillis()}.jpg"
                appContext.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) fileName = cursor.getString(0)
                }

                // 1. Показываем у себя
                withContext(Dispatchers.Main) {
                    // Сохраняем локально, чтобы отобразить
                    val localFile = saveFileFromBytes(bytes, fileName)
                    addMessage(null, true, localFile.absolutePath)
                }

                // 2. Отправляем по сети
                if (outStream != null) {
                    val header = "[FILE]$fileName|${bytes.size}\n"
                    outputStream?.write(header.toByteArray(Charsets.UTF_8))
                    outputStream?.write(bytes)
                    outputStream?.flush()
                } else if (isBotActive) {
                    // Эмуляция бота: возвращаем картинку обратно через 1 сек
                    delay(1000)
                    withContext(Dispatchers.Main) {
                        val botFile = saveFileFromBytes(bytes, "bot_$fileName")
                        addMessage("🤖 Бот: Классное фото! Лови обратно.", false, botFile.absolutePath)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Сохранение байтов во внутреннюю память приложения
    private fun saveFileFromBytes(bytes: ByteArray, fileName: String): File {
        val file = File(appContext.filesDir, fileName)
        file.outputStream().use { it.write(bytes) }
        return file
    }

    // --- ЛОГИКА СЕРВЕРА (ХОСТ) ---
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
                        addMessage("🤖 Бот активен. Можешь слать текст и картинки!", false)
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

    // --- ЛОГИКА КЛИЕНТА ---
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
        inputStream = BufferedInputStream(socket.getInputStream())
    }

    // --- ЧТЕНИЕ СЕТИ (ТЕКСТ И ФАЙЛЫ) ---
    private suspend fun listenForMessages(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(inputStream))
        try {
            while (true) {
                val line = reader.readLine() ?: break
                
                if (line.startsWith("[FILE]")) {
                    // Формат: [FILE]name.jpg|12345
                    val info = line.removePrefix("[FILE]").split("|")
                    val fileName = info[0]
                    val size = info[1].toInt()
                    
                    // Читаем ровно size байт
                    val buffer = ByteArray(size)
                    var bytesRead = 0
                    while (bytesRead < size) {
                        val result = inputStream?.read(buffer, bytesRead, size - bytesRead) ?: -1
                        if (result == -1) break
                        bytesRead += result
                    }
                    
                    val file = saveFileFromBytes(buffer, fileName)
                    withContext(Dispatchers.Main) {
                        addMessage(null, false, file.absolutePath)
                    }
                } else {
                    // Обычный текст
                    withContext(Dispatchers.Main) {
                        addMessage(line, false)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            withContext(Dispatchers.Main) {
                _isConnected.value = false
                addMessage(" Соединение разорвано.", false)
            }
        }
    }

    fun sendMessage(text: String) {
        addMessage(text, true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (outputStream != null) {
                    outputStream?.write((text + "\n").toByteArray(Charsets.UTF_8))
                    outputStream?.flush()
                }
            } catch (_: Exception) {}
        }
    }

    private fun addMessage(text: String?, isMine: Boolean, imagePath: String? = null) {
        _messages.value = _messages.value + Message(text, isMine, imagePath)
    }

    override fun onCleared() {
        super.onCleared()
        isBotActive = false
        try { serverSocket?.close(); clientSocket?.close(); outputStream?.close() } catch (_: Exception) {}
    }
}
