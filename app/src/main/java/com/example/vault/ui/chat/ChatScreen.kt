package com.example.vault.ui.chat

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vault.data.local.DocumentEntity
import com.example.vault.rag.DocumentIntent
import com.example.vault.rag.model.SourceNavigationRequest
import com.example.vault.ui.theme.Manrope
import com.example.vault.ui.theme.SupabaseGreen
import com.example.vault.ui.theme.SupabaseGreenDarker
import com.example.vault.ui.theme.ThemeState
import com.example.vault.ui.theme.VaultCardBorderDark
import com.example.vault.ui.theme.VaultCardBorderLight
import com.example.vault.ui.vault.VaultBottomNavigationBar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToVault: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit = {},
    onOpenSource: (SourceNavigationRequest) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    showBottomBar: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = ThemeState.isDarkState ?: isSystemInDarkTheme()

    val messages by viewModel.messages.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val selectedIntent by viewModel.selectedIntent.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var inputText by remember { mutableStateOf("") }
    var intentMenuExpanded by remember { mutableStateOf(false) }

    // Text to Speech
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var speakingMessageId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context) {
        var initializedTts: TextToSpeech? = null
        initializedTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                initializedTts?.language = Locale.getDefault()
            }
        }
        initializedTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                scope.launch { speakingMessageId = null }
            }
            override fun onError(utteranceId: String?) {
                scope.launch { speakingMessageId = null }
            }
        })
        tts = initializedTts
        onDispose {
            initializedTts.stop()
            initializedTts.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.userFeedbackEvent.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Scroll to bottom whenever new messages arrive
    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1 + if (isThinking) 1 else 0)
        }
    }

    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // Auto-scroll to latest message when keyboard pops up
    LaunchedEffect(isKeyboardOpen) {
        if (isKeyboardOpen && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1 + if (isThinking) 1 else 0)
        }
    }

    fun handleSend() {
        val query = inputText.trim()
        val intent = selectedIntent
        if (query.isNotBlank() && intent != null) {
            inputText = ""
            viewModel.sendMessage(query, intent)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar && !isKeyboardOpen) {
                VaultBottomNavigationBar(
                    isDark = isDark,
                    currentTab = "Chat",
                    onTabClick = { tabName ->
                        when (tabName) {
                            "Vault" -> onNavigateToVault()
                            "Chat" -> { /* Already on Chat */ }
                            "Settings" -> onNavigateToSettings()
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .imePadding()
            ) {
                // ==========================================
                // Top Header: "Chat" Title Sticking to the Left + Right Drawer Button
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chat",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = Manrope,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Right Drawer Menu Button
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                keyboardController?.hide()
                                onOpenDrawer()
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isDark) VaultCardBorderDark else VaultCardBorderLight
                        ),
                        shadowElevation = 1.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = "Open Chat Menu",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // ==========================================
                // Messages List
                // ==========================================
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        if (message.sender == MessageSender.USER) {
                            UserMessageBubble(
                                message = message,
                                isDark = isDark
                            )
                        } else {
                            AssistantMessageCard(
                                message = message,
                                isDark = isDark,
                                isSpeaking = speakingMessageId == message.id,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(message.text))
                                    viewModel.emitToast("Copied to clipboard")
                                },
                                onToggleLike = {
                                    viewModel.toggleLike(message.id)
                                },
                                onToggleSpeak = {
                                    if (speakingMessageId == message.id) {
                                        tts?.stop()
                                        speakingMessageId = null
                                    } else {
                                        tts?.stop()
                                        speakingMessageId = message.id
                                        tts?.speak(
                                            message.text,
                                            TextToSpeech.QUEUE_FLUSH,
                                            null,
                                            message.id
                                        )
                                    }
                                },
                                onRegenerate = {
                                    tts?.stop()
                                    speakingMessageId = null
                                    viewModel.regenerateLastMessage()
                                },
                                onOpenDocument = onOpenDocument,
                                onOpenSource = onOpenSource
                            )
                        }
                    }

                    // Thinking Indicator Bubble
                    if (isThinking) {
                        item {
                            ThinkingBubble(isDark = isDark)
                        }
                    }

                    // Suggested Prompts when conversation is short
                    if (messages.size <= 1 && !isThinking) {
                        item {
                            SuggestedPromptsRow(
                                isDark = isDark,
                                onSelectPrompt = { prompt, intent ->
                                    viewModel.sendMessage(prompt, intent)
                                }
                            )
                        }
                    }
                }

                // ==========================================
                // Solid Docked Chat Input Bar / Intent Selector
                // ==========================================
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isDark) VaultCardBorderDark else VaultCardBorderLight
                    ),
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Top Intent Header Indicator if an intent is selected
                        if (selectedIntent != null) {
                            val intentLabel = when (selectedIntent) {
                                DocumentIntent.LIST_DOCUMENTS -> "list related documents given a context"
                                DocumentIntent.LOOKUP_FACT -> "Find a fact from the source documents"
                                else -> "Selected intent"
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) Color(0xFF172B20) else Color(0xFFEAF8F0),
                                border = BorderStroke(
                                    1.dp,
                                    if (isDark) Color(0x553ECF8E) else Color(0x4416A34A)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (selectedIntent == DocumentIntent.LIST_DOCUMENTS) Icons.Rounded.Description else Icons.Rounded.Search,
                                            contentDescription = null,
                                            tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = intentLabel,
                                            fontFamily = Manrope,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.setSelectedIntent(null) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Change Intent",
                                            tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (selectedIntent == null) {
                            // Chat box before becoming an input: Button "specify intent before chatting"
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .clickable { intentMenuExpanded = true },
                                    shape = RoundedCornerShape(24.dp),
                                    color = if (isDark) Color(0xFF222222) else Color(0xFFF3F4F6),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDark) Color(0x333ECF8E) else Color(0x3316A34A)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AutoAwesome,
                                                contentDescription = null,
                                                tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Specify intent before chatting",
                                                fontFamily = Manrope,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = "Open Intent List",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = intentMenuExpanded,
                                    onDismissRequest = { intentMenuExpanded = false },
                                    modifier = Modifier
                                        .background(if (isDark) Color(0xFF1E1E1E) else Color.White)
                                        .widthIn(min = 290.dp)
                                ) {
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Description,
                                                contentDescription = null,
                                                tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        text = {
                                            Text(
                                                "list related documents given a context",
                                                fontFamily = Manrope,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            viewModel.setSelectedIntent(DocumentIntent.LIST_DOCUMENTS)
                                            intentMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Search,
                                                contentDescription = null,
                                                tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        text = {
                                            Text(
                                                "Find a fact from the source documents",
                                                fontFamily = Manrope,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            viewModel.setSelectedIntent(DocumentIntent.LOOKUP_FACT)
                                            intentMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        } else {
                            // Chat box becomes input text
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val placeholderText = when (selectedIntent) {
                                    DocumentIntent.LIST_DOCUMENTS -> "list me my ID's"
                                    DocumentIntent.LOOKUP_FACT -> "when was my most recent mcdonalds receipt?"
                                    else -> "Type a message.."
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    color = if (isDark) Color(0xFF222222) else Color(0xFFF3F4F6),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDark) Color(0x333ECF8E) else Color(0x3316A34A)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BasicTextField(
                                            value = inputText,
                                            onValueChange = { inputText = it },
                                            modifier = Modifier.weight(1f),
                                            textStyle = TextStyle(
                                                fontFamily = Manrope,
                                                fontSize = 14.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(if (isDark) SupabaseGreen else SupabaseGreenDarker),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                            keyboardActions = KeyboardActions(onSend = { handleSend() }),
                                            decorationBox = { innerTextField ->
                                                if (inputText.isEmpty()) {
                                                    Text(
                                                        text = placeholderText,
                                                        fontFamily = Manrope,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        )
                                    }
                                }

                                val canSend = inputText.isNotBlank() && selectedIntent != null
                                Surface(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = canSend) { handleSend() },
                                    shape = CircleShape,
                                    color = if (canSend) {
                                        if (isDark) SupabaseGreen else SupabaseGreenDarker
                                    } else {
                                        if (isDark) Color(0xFF222222) else Color(0xFFE5E7EB)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (canSend) {
                                            Color.Transparent
                                        } else {
                                            if (isDark) Color(0x22FFFFFF) else Color(0x11000000)
                                        }
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Send,
                                            contentDescription = "Send",
                                            tint = if (canSend) {
                                                Color.White
                                            } else {
                                                if (isDark) Color(0xFF666666) else Color(0xFF9CA3AF)
                                            },
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ==========================================
// User Message Bubble (Right Side)
// ==========================================
@Composable
private fun UserMessageBubble(
    message: ChatMessage,
    isDark: Boolean
) {
    val intent = message.intent ?: message.intentPlan?.intent
    val intentLabel = when (intent) {
        DocumentIntent.LOOKUP_FACT -> "fact finding"
        DocumentIntent.LIST_DOCUMENTS -> "list similar documents"
        else -> null
    }
    val formattedTime = remember(message.timestamp) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 290.dp),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 4.dp
            ),
            color = if (isDark) Color(0xFF16A34A) else Color(0xFF15803D),
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp,
                    lineHeight = 20.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (intentLabel != null) {
                        Text(
                            text = intentLabel,
                            fontFamily = Manrope,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.5.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "•",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = formattedTime,
                        fontFamily = Manrope,
                        fontSize = 10.5.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

// ==========================================
// Assistant Message Card (Left Side)
// ==========================================
@Composable
private fun AssistantMessageCard(
    message: ChatMessage,
    isDark: Boolean,
    isSpeaking: Boolean,
    onCopy: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleSpeak: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit,
    onOpenSource: (SourceNavigationRequest) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 330.dp),
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            ),
            color = if (isDark) Color(0xFF1C1C1C) else Color(0xFFFFFFFF),
            border = BorderStroke(
                1.dp,
                if (isDark) VaultCardBorderDark else VaultCardBorderLight
            ),
            shadowElevation = 3.dp
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)
            ) {
                // Assistant Text with Superscript Citations
                Text(
                    text = formatCitationSuperscript(message.text, isDark),
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.5.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Citing Related Documents with Citations on each card
                if (message.relatedDocs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Related Documents:",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(message.relatedDocs) { doc ->
                            val isPdf = doc.mimeType.contains("pdf", ignoreCase = true)
                            val matchingEvidences = message.groundedAnswer?.references?.filter { it.chunk.documentId == doc.id.toString() } ?: emptyList()
                            val citations = matchingEvidences.map { it.sourceId }.distinct()
                            val firstEvidence = matchingEvidences.firstOrNull()

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDark) Color(0xFF252525) else Color(0xFFF1F5F3),
                                border = BorderStroke(
                                    1.dp,
                                    if (isDark) Color(0x333ECF8E) else Color(0x3316A34A)
                                ),
                                modifier = Modifier.clickable {
                                    if (firstEvidence != null) {
                                        onOpenSource(
                                            SourceNavigationRequest(
                                                documentId = firstEvidence.chunk.documentId,
                                                pageNumber = firstEvidence.chunk.pageNumber,
                                                textRevision = firstEvidence.chunk.revision,
                                                isEdited = firstEvidence.chunk.isEdited
                                            )
                                        )
                                    } else {
                                        onOpenDocument(doc)
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPdf) Icons.Rounded.PictureAsPdf else Icons.Rounded.Description,
                                        contentDescription = null,
                                        tint = if (isPdf) Color(0xFFDC2626) else (if (isDark) SupabaseGreen else SupabaseGreenDarker),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = doc.title,
                                        fontFamily = Manrope,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (citations.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isDark) Color(0xFF1B382A) else Color(0xFFDCFCE7),
                                            border = BorderStroke(0.5.dp, if (isDark) Color(0x663ECF8E) else Color(0x6616A34A))
                                        ) {
                                            Text(
                                                text = citations.joinToString(", ") { "[$it]" },
                                                fontFamily = Manrope,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(
                    color = if (isDark) Color(0x22FFFFFF) else Color(0x11000000),
                    thickness = 0.5.dp
                )

                // Action Buttons Row (Copy, Thumbs Up, Speaker, Regenerate)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 1. Copy Button
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy text",
                                tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // 2. Thumbs Up / Like Button
                        IconButton(
                            onClick = onToggleLike,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ThumbUp,
                                contentDescription = "Like response",
                                tint = if (message.isLiked) {
                                    if (isDark) SupabaseGreen else SupabaseGreenDarker
                                } else {
                                    (if (isDark) SupabaseGreen else SupabaseGreenDarker).copy(alpha = 0.65f)
                                },
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // 3. Speaker / Audio Button
                        IconButton(
                            onClick = onToggleSpeak,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = "Read aloud",
                                tint = if (isSpeaking) {
                                    Color(0xFF38BDF8)
                                } else {
                                    if (isDark) SupabaseGreen else SupabaseGreenDarker
                                },
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // 4. Regenerate Button on far right
                    IconButton(
                        onClick = onRegenerate,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Regenerate",
                            tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// Superscript Citation Formatter
// ==========================================
@Composable
private fun formatCitationSuperscript(text: String, isDark: Boolean): AnnotatedString {
    val citationColor = if (isDark) SupabaseGreen else SupabaseGreenDarker
    val regex = Regex("\\[(S?\\d+(?:,\\s*S?\\d+)*)\\]")
    return remember(text, isDark) {
        buildAnnotatedString {
            var lastIndex = 0
            for (match in regex.findAll(text)) {
                if (match.range.first > lastIndex) {
                    append(text.substring(lastIndex, match.range.first))
                }
                withStyle(
                    SpanStyle(
                        baselineShift = BaselineShift.Superscript,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = citationColor
                    )
                ) {
                    append(match.value)
                }
                lastIndex = match.range.last + 1
            }
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }
}

// ==========================================
// Thinking Animation Bubble
// ==========================================
@Composable
private fun ThinkingBubble(isDark: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDark) Color(0xFF1E1E1E) else Color.White,
            border = BorderStroke(
                1.dp,
                if (isDark) VaultCardBorderDark else VaultCardBorderLight
            ),
            modifier = Modifier.padding(start = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            (if (isDark) SupabaseGreen else SupabaseGreenDarker).copy(alpha = dotAlpha)
                        )
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            (if (isDark) SupabaseGreen else SupabaseGreenDarker).copy(alpha = 1f - dotAlpha * 0.5f)
                        )
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            (if (isDark) SupabaseGreen else SupabaseGreenDarker).copy(alpha = dotAlpha)
                        )
                )
            }
        }
    }
}

// ==========================================
// Quick Suggestion Prompts
// ==========================================
private data class SuggestedPrompt(
    val title: String,
    val intent: DocumentIntent,
    val prompt: String
)

@Composable
private fun SuggestedPromptsRow(
    isDark: Boolean,
    onSelectPrompt: (String, DocumentIntent) -> Unit
) {
    val suggestions = listOf(
        SuggestedPrompt("📄 List all documents related to receipts", DocumentIntent.LIST_DOCUMENTS, "list all documents related to receipts"),
        SuggestedPrompt("🔍 When was my most recent receipt paid?", DocumentIntent.LOOKUP_FACT, "when was my most recent receipt paid?"),
        SuggestedPrompt("📄 List all identification cards", DocumentIntent.LIST_DOCUMENTS, "list all documents that are identification cards"),
        SuggestedPrompt("🔍 What are the key terms in my contract?", DocumentIntent.LOOKUP_FACT, "what are the key terms in my contract?")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Suggested Questions",
            fontFamily = Manrope,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(10.dp))
        suggestions.forEach { item ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF1E1E1E) else Color.White,
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color(0x333ECF8E) else Color(0x3316A34A)
                ),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelectPrompt(item.prompt, item.intent) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        fontFamily = Manrope,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
