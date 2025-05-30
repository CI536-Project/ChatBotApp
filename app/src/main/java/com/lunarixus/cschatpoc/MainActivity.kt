package com.lunarixus.cschatpoc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunarixus.cschatpoc.handlers.SharedPreferenceHandler
import com.lunarixus.cschatpoc.handlers.WebHandler
import com.lunarixus.cschatpoc.ui.theme.CSChatPoCTheme
import dev.jeziellago.compose.markdowntext.MarkdownText

// MainActivity Class
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pass the application context to SharedPreferenceHandler
        SharedPreferenceHandler.init(applicationContext)

        setContent {
            CSChatPoCTheme {
                ChatScreen()
            }
        }
    }
}

// Main screen consisting of UI elements for the chat screen
// Updates live when the backend server responds
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    var messages by remember { mutableStateOf(listOf(Triple("Bot", "Welcome! I am the CI536 Laptop Bot, ask me any questions regarding laptops to begin.", false))) }
    var textState by remember { mutableStateOf(TextFieldValue("")) }
    val webHandler = remember { WebHandler() }
    var streamingMessage by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var thinkingText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    var showDialog by remember { mutableStateOf(false) }
    var connectionAvailable by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(SharedPreferenceHandler.getString("server_url", "http://0.0.0.0:5000")) }

    // Check connection once on start
    LaunchedEffect(Unit) {
        webHandler.testConnection { isConnected ->
            connectionAvailable = isConnected
        }
    }

    LaunchedEffect(messages, streamingMessage) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("CI536 - Laptop ChatBot") },
                actions = {
                    IconButton(onClick = {
                        showSettings = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        bottomBar = {
            ChatInputField(textState, onTextChange = { textState = it }) { message ->
                if (message.isNotEmpty()) {
                    messages = messages + Triple("You", message, true)
                    textState = TextFieldValue("")
                    streamingMessage = Triple("Bot", "", false)

                    webHandler.sendMessage(message, { actualResponse, thinking ->
                        thinkingText = thinking
                        streamingMessage = Triple("Bot", actualResponse, false)
                    }, {
                        if (streamingMessage != null) {
                            messages = messages + streamingMessage!!
                            streamingMessage = null
                            showDialog = false
                        }
                    })
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(8.dp)
                .verticalScroll(scrollState)
        ) {
            if (showSettings) {
                AlertDialog(
                    onDismissRequest = { showSettings = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showSettings = false

                            // Step 1: Add http:// if missing
                            var formattedUrl = if (serverUrlInput.startsWith("http://") && serverUrlInput.endsWith(":5000")) {
                                serverUrlInput
                            } else {
                                "http://$serverUrlInput:5000"
                            }

                            // Update global URL variable
                            SharedPreferenceHandler.putString("server_url", formattedUrl)
                            serverUrlInput = formattedUrl

                            // Test server connection again
                            webHandler.testConnection { success ->
                                connectionAvailable = success
                            }

                        }) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSettings = false }) {
                            Text("Cancel")
                        }
                    },
                    title = { Text("Settings") },
                    text = {
                        Column {
                            Text("Enter the server URL")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = serverUrlInput,
                                onValueChange = { serverUrlInput = it },
                                label = { Text("Server Address") },
                                singleLine = true
                            )
                        }
                    }
                )
            }

            // Show connection bubble if the server can't be contacted
            if (!connectionAvailable) {
                ConnectionBubble()
            }

            messages.forEach { (sender, message, isUser) ->
                ChatBubble(sender, message, isUser)
            }

            streamingMessage?.let { (sender, message, isUser) ->
                ChatBubble(
                    sender,
                    if (message.isEmpty()) "The bot is thinking of a response..." else message,
                    isUser,
                    showLoading = message.isEmpty(),
                    onClick = { showDialog = true }
                )
            }
        }

        if (showDialog && streamingMessage != null) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                confirmButton = {},
                title = { Text("Bot is thinking...") },
                text = {
                    Box(
                        modifier = Modifier
                            .heightIn(max = 500.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column {
                            Text(thinkingText)
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ConnectionBubble() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Connection Warning",
                tint = Color.Red,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Cannot connect to the server. Please check your connection.",
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// UI element for chat bubbles
@Composable
fun ChatBubble(sender: String, message: String, isUser: Boolean, showLoading: Boolean = false, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = sender,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 2.dp)
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.widthIn(min = 64.dp, max = 480.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (showLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = message, fontSize = 16.sp)
                    }
                } else {
                    MarkdownText(
                        markdown = message,
                    )
                }
            }
        }
    }
}

// UI element for text input
@Composable
fun ChatInputField(
    textState: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSend: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = textState,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(32.dp),
            placeholder = { Text("Type a message...") }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = { onSend(textState.text) }) {
            Text("Send")
        }
    }
}

// UI preview
@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    CSChatPoCTheme {
        ChatScreen()
    }
}

// Bubble element preview
@Preview(showBackground = true)
@Composable
fun ChatBubblePreview() {
    ChatBubble("You", "Hello, World!", true, false, onClick = {})
}