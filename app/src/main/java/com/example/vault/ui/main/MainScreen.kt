package com.example.vault.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.example.vault.data.local.DocumentEntity
import com.example.vault.data.repository.PendingDocument
import com.example.vault.ui.chat.ChatScreen
import com.example.vault.ui.chat.ChatSession
import com.example.vault.ui.chat.ChatViewModel
import com.example.vault.ui.chat.DeleteChatDialog
import com.example.vault.ui.chat.RenameChatDialog
import com.example.vault.rag.model.SourceNavigationRequest
import com.example.vault.ui.chat.RightChatDrawer
import com.example.vault.ui.settings.SettingsScreen
import com.example.vault.ui.theme.ThemeState
import com.example.vault.ui.vault.VaultBottomNavigationBar
import com.example.vault.ui.vault.VaultScreen
import com.example.vault.ui.vault.VaultViewModel

@Composable
fun MainScreen(
    vaultViewModel: VaultViewModel,
    chatViewModel: ChatViewModel,
    onFolderClick: (Long) -> Unit,
    onStartSaveFlow: (PendingDocument) -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit,
    onOpenSource: (SourceNavigationRequest) -> Unit = {},
    initialTab: String = "Vault"
) {
    var currentTab by rememberSaveable { mutableStateOf(initialTab) }
    var isChatDrawerOpen by rememberSaveable { mutableStateOf(false) }

    val isDark = ThemeState.isDarkState ?: isSystemInDarkTheme()
    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val chatSessions by chatViewModel.chatSessions.collectAsState()
    val activeSessionId by chatViewModel.activeSessionId.collectAsState()
    val currentSession = remember(chatSessions, activeSessionId) {
        chatSessions.find { it.id == activeSessionId } ?: chatSessions.firstOrNull()
    }

    var chatToRename by remember { mutableStateOf<ChatSession?>(null) }
    var chatToDelete by remember { mutableStateOf<ChatSession?>(null) }

    // System Back priority:
    // 1. If drawer is open -> close drawer
    // 2. If on Chat or Settings -> return to Vault
    BackHandler(enabled = isChatDrawerOpen) {
        isChatDrawerOpen = false
    }

    BackHandler(enabled = !isChatDrawerOpen && currentTab != "Vault") {
        currentTab = "Vault"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // Bottom bar stays flush and stationary, hidden only when typing on Chat or when drawer is open
                if (!(currentTab == "Chat" && (isKeyboardOpen || isChatDrawerOpen))) {
                    VaultBottomNavigationBar(
                        isDark = isDark,
                        currentTab = currentTab,
                        onTabClick = { tabName ->
                            when (tabName) {
                                "Vault" -> currentTab = "Vault"
                                "Chat" -> currentTab = "Chat"
                                "Settings" -> currentTab = "Settings"
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        val tabOrder = mapOf("Vault" to 0, "Chat" to 1, "Settings" to 2)
                        val from = tabOrder[initialState] ?: 0
                        val to = tabOrder[targetState] ?: 0
                        if (to > from) {
                            (slideInHorizontally(animationSpec = tween(240)) { it } + fadeIn(animationSpec = tween(240)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(240)) { -it } + fadeOut(animationSpec = tween(240)))
                        } else {
                            (slideInHorizontally(animationSpec = tween(240)) { -it } + fadeIn(animationSpec = tween(240)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(240)) { it } + fadeOut(animationSpec = tween(240)))
                        }
                    },
                    label = "MainTabTransition"
                ) { tab ->
                    when (tab) {
                        "Vault" -> {
                            VaultScreen(
                                viewModel = vaultViewModel,
                                onFolderClick = onFolderClick,
                                onStartSaveFlow = onStartSaveFlow,
                                onOpenDocument = onOpenDocument,
                                onNavigateToChat = { currentTab = "Chat" },
                                onNavigateToSettings = { currentTab = "Settings" },
                                showBottomBar = false
                            )
                        }
                        "Chat" -> {
                            ChatScreen(
                                viewModel = chatViewModel,
                                onNavigateToVault = { currentTab = "Vault" },
                                onOpenDocument = onOpenDocument,
                                onOpenSource = onOpenSource,
                                onOpenDrawer = { isChatDrawerOpen = true },
                                onNavigateToSettings = { currentTab = "Settings" },
                                showBottomBar = false
                            )
                        }
                        "Settings" -> {
                            SettingsScreen(
                                onShowMessage = { msg -> vaultViewModel.showMessage(msg) }
                            )
                        }
                    }
                }
            }
        }

        // Full-screen Modal Scrim
        if (isChatDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isChatDrawerOpen = false }
            )
        }

        // Modal Drawer spanning full physical height
        AnimatedVisibility(
            visible = isChatDrawerOpen,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(280)
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(240)
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            RightChatDrawer(
                isDark = isDark,
                currentSession = currentSession,
                chatSessions = chatSessions,
                activeSessionId = activeSessionId,
                onClose = { isChatDrawerOpen = false },
                onNewChat = {
                    chatViewModel.createNewChat()
                    isChatDrawerOpen = false
                },
                onSelectChat = { sessionId ->
                    chatViewModel.selectChat(sessionId)
                    isChatDrawerOpen = false
                },
                onRenameChat = { session ->
                    chatToRename = session
                },
                onDeleteChat = { session ->
                    chatToDelete = session
                }
            )
        }

        // Dialog: Rename Chat
        chatToRename?.let { session ->
            RenameChatDialog(
                session = session,
                isDark = isDark,
                onDismiss = { chatToRename = null },
                onConfirm = { newName ->
                    chatViewModel.renameChat(session.id, newName)
                    chatToRename = null
                }
            )
        }

        // Dialog: Delete Chat Confirmation
        chatToDelete?.let { session ->
            DeleteChatDialog(
                session = session,
                onDismiss = { chatToDelete = null },
                onConfirm = {
                    chatViewModel.deleteChat(session.id)
                    chatToDelete = null
                }
            )
        }
    }
}
