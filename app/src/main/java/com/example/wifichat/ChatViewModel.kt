package com.example.wifichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

// Модель сообщения для UI
data class Message(val text: String, val isMine: Boolean)

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var outStream: PrintWriter? = null
    
    // Флаг, чтобы бот не отвечал, если подключился реальный человек
    private var isBotActive = false

    // --- ЛОГИКА СЕРВЕРА (ХОСТ) + ТЕСТОВЫЙ БОТ ---
    fun startAsHost() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(5000)
                
                // Сразу переключаем экран на чат
                withContext(Dispatchers.Main) {
                    _isConnected.value = true
                    addMessage("Режим Хоста активирован. Ожидание подключения...", false)
                }

                // Запускаем ожидание реального подключения в отдельном потоке
                launch {
                    try {
                        val socket = serverSocket!!.accept()
                        // Если кто-то реально подключился, отключаем бота
                        isBotActive = false 
                        
                        setupStreams(socket)
                        withContext(Dispatchers.Main) {
                            addMessage("✅ Друг реально подключился по Wi-Fi!", false)
                        }
                        listenForMessages(socket)
                    } catch (e: Exception) {
                        // Игнорируем ошибку, если сокет закрылся принудительно
                    }
                }

                // === ТЕСТОВЫЙ РЕЖИМ (ЭХО-БОТ) ===
                // Ждем 3 секунды. Если никто не подключился, включаем бота для проверки UI
                delay(3000)
                
                // Проверяем, не подключился ли кто-то за эти 3 секунды
                if (clientSocket == null && outStream == null) {
                    isBotActive = true
                    withContext(Dispatchers.Main) {
                        addMessage("🤖 Тестовый бот: Никого нет рядом. Я здесь! Пиши сообщения, я буду отвечать, чтобы ты проверил дизайн.", false)
                    }
                    
                    // Запускаем слежение за твоими сообщениями
                    launch {
                        messages.collect { list ->
                            if (!isBotActive) return@collect // Если бот выключен, выходим
                            
                            val lastMsg = list.lastOrNull()
                            if (lastMsg != null && lastMsg.isMine) {
                                delay(800) // Небольшая задержка для реалистичности "печатания"
                                withContext(Dispatchers.Main) {
                                    addMessage("🤖 Бот: Ты написал '${lastMsg.text}'. Интерфейс работает отлично!", false)
                                }
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    addMessage("Ошибка сервера: ${e.message}", false)
                }
            }
        }
    }

    // --- ЛОГИКА КЛИЕНТА (ДРУГ) ---
    fun startAsClient(hostIp: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = Socket(hostIp, 5000)
                clientSocket = socket
                setupStreams(socket)
                
                withContext(Dispatchers.Main) {
                    _isConnected.value = true
                    addMessage("✅ Вы подключились к хосту!", false)
                }
                
                listenForMessages(socket)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _isConnected.value = false
                    addMessage("❌ Ошибка подключения: ${e.message}", false)
                }
            }
        }
    }

    // --- ОБЩИЕ ФУНКЦИИ ---
    
    // Настраиваем потоки чтения и записи для сокета
    private fun setupStreams(socket: Socket) {
        outStream = PrintWriter(socket.getOutputStream(), true)
    }

    // Бесконечный цикл чтения входящих сообщений по сети
    private suspend fun listenForMessages(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        try {
            while (true) {
                val message = reader.readLine() ?: break // Если null - соединение разорвано
                withContext(Dispatchers.Main) {
                    addMessage(message, false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            withContext(Dispatchers.Main) {
                _isConnected.value = false
                addMessage("⚠️ Соединение разорвано.", false)
            }
        }
    }

    // Отправка сообщения
    fun sendMessage(text: String) {
        // Добавляем в свой UI сразу
        addMessage(text, true)
        
        // Отправляем по сети (если есть реальное подключение)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                outStream?.println(text)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Обновление списка сообщений
    private fun addMessage(text: String, isMine: Boolean) {
        _messages.value = _messages.value + Message(text, isMine)
    }

    // Очистка ресурсов при закрытии приложения
    override fun onCleared() {
        super.onCleared()
        isBotActive = false
        try {
            serverSocket?.close()
            clientSocket?.close()
            outStream?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
