package com.example.wifichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

// Модель сообщения
data class Message(val text: String, val isMine: Boolean)

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var outStream: PrintWriter? = null

    // --- ЛОГИКА СЕРВЕРА (ХОСТ) ---
    fun startAsHost() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(5000)
                val socket = serverSocket!!.accept()
                setupStreams(socket)
                
                withContext(Dispatchers.Main) {
                    _isConnected.value = true
                    addMessage("Друг подключился к чату!", false)
                }
                
                listenForMessages(socket)
            } catch (e: Exception) {
                e.printStackTrace()
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
                    addMessage("Вы подключились к хосту!", false)
                }
                
                listenForMessages(socket)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    addMessage("Ошибка подключения: ${e.message}", false)
                }
            }
        }
    }

    // --- ОБЩИЕ ФУНКЦИИ ---
    
    private fun setupStreams(socket: Socket) {
        outStream = PrintWriter(socket.getOutputStream(), true)
    }

    private suspend fun listenForMessages(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        try {
            while (true) {
                val message = reader.readLine() ?: break
                withContext(Dispatchers.Main) {
                    addMessage(message, false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            withContext(Dispatchers.Main) {
                _isConnected.value = false
                addMessage("Соединение разорвано.", false)
            }
        }
    }

    fun sendMessage(text: String) {
        addMessage(text, true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                outStream?.println(text)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun addMessage(text: String, isMine: Boolean) {
        _messages.value = _messages.value + Message(text, isMine)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            serverSocket?.close()
            clientSocket?.close()
            outStream?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
