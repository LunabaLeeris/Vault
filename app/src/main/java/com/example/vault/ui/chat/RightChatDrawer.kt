package com.example.vault.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vault.ui.theme.Manrope
import com.example.vault.ui.theme.SupabaseGreen
import com.example.vault.ui.theme.SupabaseGreenDarker
import com.example.vault.ui.theme.ThemeState
import com.example.vault.ui.theme.VaultCardBorderDark
import com.example.vault.ui.theme.VaultCardBorderLight

@Composable
fun RightChatDrawer(
    isDark: Boolean,
    currentSession: ChatSession?,
    chatSessions: List<ChatSession>,
    activeSessionId: String,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onRenameChat: (ChatSession) -> Unit,
    onDeleteChat: (ChatSession) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(310.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isDark) VaultCardBorderDark else VaultCardBorderLight
        ),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            // Drawer Header: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chat Menu",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose),
                    shape = CircleShape,
                    color = if (isDark) Color(0xFF222222) else Color(0xFFF3F4F6)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 1. Theme Section (Dark mode / Light mode)
            Text(
                text = "THEME",
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F4F6),
                border = BorderStroke(
                    1.dp,
                    if (isDark) VaultCardBorderDark else VaultCardBorderLight
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ThemeOptionPill(
                        label = "Light",
                        icon = Icons.Rounded.LightMode,
                        isSelected = !isDark,
                        isDark = isDark,
                        modifier = Modifier.weight(1f),
                        onClick = { ThemeState.isDarkState = false }
                    )
                    ThemeOptionPill(
                        label = "Dark",
                        icon = Icons.Rounded.DarkMode,
                        isSelected = isDark,
                        isDark = isDark,
                        modifier = Modifier.weight(1f),
                        onClick = { ThemeState.isDarkState = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Current Chat Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CURRENT CHAT",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = 0.8.sp
                )

                // "+ New Chat" Action Button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onNewChat),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) Color(0xFF16251E) else Color(0xFFEAF7F0),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) Color(0x333ECF8E) else Color(0x4416A34A)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New Chat",
                            tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "New Chat",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF16251E) else Color(0xFFEAF7F0),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color(0x443ECF8E) else Color(0x4416A34A)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                    ) {}

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSession?.title ?: "Vault Assistant",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${currentSession?.messages?.size ?: 0} messages • Active session",
                            fontFamily = Manrope,
                            fontSize = 11.sp,
                            color = (if (isDark) SupabaseGreen else SupabaseGreenDarker).copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. List of Saved Chats
            Text(
                text = "SAVED CHATS",
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(chatSessions, key = { it.id }) { session ->
                    ChatSessionRowItem(
                        session = session,
                        isActive = session.id == activeSessionId,
                        isDark = isDark,
                        onSelect = { onSelectChat(session.id) },
                        onRename = { onRenameChat(session) },
                        onDelete = { onDeleteChat(session) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatSessionRowItem(
    session: ChatSession,
    isActive: Boolean,
    isDark: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showItemMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) {
            if (isDark) Color(0x223ECF8E) else Color(0x1816A34A)
        } else {
            if (isDark) Color(0xFF1B1B1B) else Color(0xFFF9FAFB)
        },
        border = BorderStroke(
            1.dp,
            if (isActive) {
                if (isDark) Color(0x443ECF8E) else Color(0x4416A34A)
            } else {
                if (isDark) VaultCardBorderDark else VaultCardBorderLight
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isActive) Icons.Rounded.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = if (isActive) {
                    if (isDark) SupabaseGreen else SupabaseGreenDarker
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
                modifier = Modifier.size(17.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = session.title,
                fontFamily = Manrope,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Right 3-dots Menu Button
            Box {
                IconButton(
                    onClick = { showItemMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(17.dp)
                    )
                }

                DropdownMenu(
                    expanded = showItemMenu,
                    onDismissRequest = { showItemMenu = false }
                ) {
                    // Option 1: Rename
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Rename",
                                    fontFamily = Manrope,
                                    fontSize = 13.sp
                                )
                            }
                        },
                        onClick = {
                            showItemMenu = false
                            onRename()
                        }
                    )

                    // Option 2: Delete chat
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFEF4444)
                                )
                                Text(
                                    text = "Delete chat",
                                    fontFamily = Manrope,
                                    fontSize = 13.sp,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        },
                        onClick = {
                            showItemMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeOptionPill(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val activeBg = if (isDark) Color(0xFF282828) else Color.White
    val activeColor = if (isDark) SupabaseGreen else SupabaseGreenDarker

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) activeBg else Color.Transparent,
        shadowElevation = if (isSelected) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontFamily = Manrope,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.5.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun RenameChatDialog(
    session: ChatSession,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var renameText by remember(session) { mutableStateOf(session.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rename Chat",
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                label = { Text("Chat Title", fontFamily = Manrope) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(renameText.trim()) },
                enabled = renameText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) SupabaseGreen else SupabaseGreenDarker
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Save",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    fontFamily = Manrope,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
fun DeleteChatDialog(
    session: ChatSession,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Chat?",
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete \"${session.title}\"? This action cannot be undone.",
                fontFamily = Manrope,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Delete",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    fontFamily = Manrope,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
