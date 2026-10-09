package com.example.vault.ui.settings

import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.example.vault.ui.theme.VaultDashboardBg

@Composable
fun SettingsScreen(
    onShowMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = ThemeState.isDarkState ?: isSystemInDarkTheme()

    // Dialog state
    var showThemeDialog by remember { mutableStateOf(false) }
    var showRagSpecsDialog by remember { mutableStateOf(false) }
    var showFaqDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }

    // RAG dynamic settings state
    var topKCitations by remember { mutableIntStateOf(3) }
    var includeImagePreviews by remember { mutableStateOf(true) }
    var ocrSensitivityHigh by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp)
    ) {
        // ==========================================
        // Top Header: "Settings" Title sticking to the left
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Vault v1.0.0 • On-Device AI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }

            // Status Indicator Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF16251E) else Color(0xFFEAF7F0),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color(0x333ECF8E) else Color(0x3316A34A)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(6.dp),
                        shape = CircleShape,
                        color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                    ) {}
                    Text(
                        text = "100% Local",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ==========================================
        // Section 1: General Preferences
        // ==========================================
        SettingsSectionHeader(title = "General")

        SettingsCardGroup(isDark = isDark) {
            // Theme / Appearance
            SettingsItemRow(
                icon = Icons.Outlined.DarkMode,
                title = "Appearance",
                subtitle = "Theme preference",
                trailingValue = when (ThemeState.isDarkState) {
                    true -> "Dark"
                    false -> "Light"
                    null -> "System"
                },
                isDark = isDark,
                onClick = { showThemeDialog = true }
            )

            SettingsDivider(isDark = isDark)

            // Notifications
            SettingsItemRow(
                icon = Icons.Outlined.Notifications,
                title = "Notifications",
                subtitle = "Scanner alerts and indexing",
                trailingValue = "Enabled",
                isDark = isDark,
                onClick = { onShowMessage("Notification settings are synchronized with system") }
            )

            SettingsDivider(isDark = isDark)

            // Storage & Data
            SettingsItemRow(
                icon = Icons.Outlined.Storage,
                title = "Storage & Data",
                subtitle = "Local files and app cache",
                trailingValue = "Manage",
                isDark = isDark,
                onClick = { showStorageDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // Section 2: AI & Knowledge (RAG)
        // ==========================================
        SettingsSectionHeader(title = "AI & Knowledge (RAG)")

        SettingsCardGroup(isDark = isDark) {
            // RAG Specifications
            SettingsItemRow(
                icon = Icons.Outlined.AutoAwesome,
                title = "RAG Specifications",
                subtitle = "Hybrid retrieval engine & on-device OCR pipeline",
                trailingValue = "View Specs",
                isDark = isDark,
                onClick = { showRagSpecsDialog = true }
            )

            SettingsDivider(isDark = isDark)

            // Retrieval Citations Parameter
            SettingsToggleRow(
                icon = Icons.Outlined.Tune,
                title = "Include Page Previews",
                subtitle = "Attach thumbnail cards in RAG assistant citations",
                checked = includeImagePreviews,
                isDark = isDark,
                onCheckedChange = {
                    includeImagePreviews = it
                    onShowMessage(if (it) "Citations will include page previews" else "Text-only citations enabled")
                }
            )

            SettingsDivider(isDark = isDark)

            // OCR Sensitivity Parameter
            SettingsToggleRow(
                icon = Icons.Outlined.Security,
                title = "High OCR Sensitivity",
                subtitle = "Thorough multilingual text matching over receipts",
                checked = ocrSensitivityHigh,
                isDark = isDark,
                onCheckedChange = {
                    ocrSensitivityHigh = it
                    onShowMessage(if (it) "High OCR sensitivity enabled" else "Balanced sensitivity enabled")
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // Section 3: Support & Help
        // ==========================================
        SettingsSectionHeader(title = "Support")

        SettingsCardGroup(isDark = isDark) {
            // FAQ
            SettingsItemRow(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                title = "FAQ",
                subtitle = "Frequently asked questions & guides",
                trailingValue = null,
                isDark = isDark,
                onClick = { showFaqDialog = true }
            )

            SettingsDivider(isDark = isDark)

            // Report an Issue
            SettingsItemRow(
                icon = Icons.Outlined.BugReport,
                title = "Report an issue",
                subtitle = "Submit bugs, crashes, or scanner feedback",
                trailingValue = null,
                isDark = isDark,
                onClick = { showReportDialog = true }
            )

            SettingsDivider(isDark = isDark)

            // Contact Support
            SettingsItemRow(
                icon = Icons.Outlined.SupportAgent,
                title = "Support",
                subtitle = "Contact the developer team & get help",
                trailingValue = null,
                isDark = isDark,
                onClick = { showSupportDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // Section 4: About
        // ==========================================
        SettingsSectionHeader(title = "About")

        SettingsCardGroup(isDark = isDark) {
            SettingsItemRow(
                icon = Icons.Outlined.Info,
                title = "About Vault",
                subtitle = "Privacy-first offline document intelligence",
                trailingValue = "v1.0.0",
                isDark = isDark,
                onClick = {
                    onShowMessage("Vault v1.0.0 • Powered by ML Kit & On-Device RAG")
                }
            )
        }
    }

    // ==========================================
    // Dialog 1: Theme / Appearance Selector
    // ==========================================
    if (showThemeDialog) {
        ThemeSelectionDialog(
            isDark = isDark,
            currentSelection = ThemeState.isDarkState,
            onDismiss = { showThemeDialog = false },
            onSelect = { selected ->
                ThemeState.isDarkState = selected
                showThemeDialog = false
            }
        )
    }

    // ==========================================
    // Dialog 2: RAG Specifications Modal
    // ==========================================
    if (showRagSpecsDialog) {
        RagSpecificationsDialog(
            isDark = isDark,
            topKCitations = topKCitations,
            onTopKChange = { topKCitations = it },
            onDismiss = { showRagSpecsDialog = false }
        )
    }

    // ==========================================
    // Dialog 3: FAQ Modal
    // ==========================================
    if (showFaqDialog) {
        FaqDialog(
            isDark = isDark,
            onDismiss = { showFaqDialog = false }
        )
    }

    // ==========================================
    // Dialog 4: Report an Issue Modal
    // ==========================================
    if (showReportDialog) {
        ReportIssueDialog(
            isDark = isDark,
            onDismiss = { showReportDialog = false },
            onSubmit = { category, description ->
                showReportDialog = false
                onShowMessage("Thank you! Report for '$category' received.")
            }
        )
    }

    // ==========================================
    // Dialog 5: Support Modal
    // ==========================================
    if (showSupportDialog) {
        SupportDialog(
            isDark = isDark,
            onDismiss = { showSupportDialog = false }
        )
    }

    // ==========================================
    // Dialog 6: Storage & Cache Modal
    // ==========================================
    if (showStorageDialog) {
        StorageDialog(
            isDark = isDark,
            onDismiss = { showStorageDialog = false },
            onClearCache = {
                showStorageDialog = false
                onShowMessage("Cache cleaned successfully")
            }
        )
    }
}

// ==========================================
// Reusable UI Components
// ==========================================

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsCardGroup(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isDark) VaultCardBorderDark else VaultCardBorderLight
        ),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingValue: String? = null,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!trailingValue.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isDark) Color(0xFF242424) else Color(0xFFF3F4F6)
            ) {
                Text(
                    text = trailingValue,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    isDark: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                uncheckedTrackColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE5E7EB)
            )
        )
    }
}

@Composable
private fun SettingsDivider(isDark: Boolean) {
    HorizontalDivider(
        thickness = 1.dp,
        color = if (isDark) Color(0xFF262626) else Color(0xFFF3F4F6),
        modifier = Modifier.padding(start = 52.dp, end = 16.dp)
    )
}

// ==========================================
// Dialog: Theme Selection
// ==========================================
@Composable
private fun ThemeSelectionDialog(
    isDark: Boolean,
    currentSelection: Boolean?,
    onDismiss: () -> Unit,
    onSelect: (Boolean?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose Theme",
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemeOptionRow(
                    title = "Dark Theme",
                    subtitle = "Sleek obsidian theme with emerald accents",
                    isSelected = currentSelection == true,
                    isDark = isDark,
                    onClick = { onSelect(true) }
                )
                ThemeOptionRow(
                    title = "Light Theme",
                    subtitle = "Clean daylight canvas",
                    isSelected = currentSelection == false,
                    isDark = isDark,
                    onClick = { onSelect(false) }
                )
                ThemeOptionRow(
                    title = "System Default",
                    subtitle = "Synchronize with Android system setting",
                    isSelected = currentSelection == null,
                    isDark = isDark,
                    onClick = { onSelect(null) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", fontFamily = Manrope, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) {
            if (isDark) Color(0x223ECF8E) else Color(0x1816A34A)
        } else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = if (isDark) SupabaseGreen else SupabaseGreenDarker
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = title,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontFamily = Manrope,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ==========================================
// Dialog: RAG Specifications Modal
// ==========================================
@Composable
private fun RagSpecificationsDialog(
    isDark: Boolean,
    topKCitations: Int,
    onTopKChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "RAG Architecture",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Vault features an on-device Retrieval-Augmented Generation (RAG) pipeline designed for speed, complete privacy, and zero data egress.",
                    fontFamily = Manrope,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                RagSpecPill(
                    label = "Retrieval Strategy",
                    value = "Hybrid Search (BM25 + Semantic Vector Scoring)",
                    isDark = isDark
                )
                RagSpecPill(
                    label = "OCR Text Engine",
                    value = "Google ML Kit On-Device Recognition v2",
                    isDark = isDark
                )
                RagSpecPill(
                    label = "Chunking Model",
                    value = "512 tokens / 64-token sliding window",
                    isDark = isDark
                )
                RagSpecPill(
                    label = "Context Window",
                    value = "4,096 tokens with dynamic document reranker",
                    isDark = isDark
                )
                RagSpecPill(
                    label = "Privacy Guarantee",
                    value = "100% On-Device • No telemetry • No cloud egress",
                    isDark = isDark
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Top-K Document Citations in Chat:",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(3, 5, 8).forEach { k ->
                        val isSelected = topKCitations == k
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onTopKChange(k) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) {
                                if (isDark) Color(0xFF16251E) else Color(0xFFEAF7F0)
                            } else {
                                if (isDark) Color(0xFF222222) else Color(0xFFF3F4F6)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) {
                                    if (isDark) SupabaseGreen else SupabaseGreenDarker
                                } else Color.Transparent
                            )
                        ) {
                            Text(
                                text = "Top $k",
                                fontFamily = Manrope,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.5.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = if (isSelected) {
                                    if (isDark) SupabaseGreen else SupabaseGreenDarker
                                } else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) SupabaseGreen else SupabaseGreenDarker
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Got it", fontFamily = Manrope, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    )
}

@Composable
private fun RagSpecPill(
    label: String,
    value: String,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (isDark) Color(0xFF1C1C1C) else Color(0xFFF9FAFB),
        border = BorderStroke(
            1.dp,
            if (isDark) VaultCardBorderDark else VaultCardBorderLight
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                text = label,
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (isDark) SupabaseGreen else SupabaseGreenDarker
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontFamily = Manrope,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ==========================================
// Dialog: FAQ Modal
// ==========================================
@Composable
private fun FaqDialog(
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val faqItems = listOf(
        "Are my documents uploaded to any server?" to "Never. Vault processes everything completely on-device. Images, PDFs, and extracted OCR text never leave your phone.",
        "How does the AI Assistant answer my questions?" to "Vault performs on-device Retrieval-Augmented Generation (RAG). It indexes text recognized from your files and searches matching passages whenever you ask a question.",
        "How do I rescan or recrop a document?" to "Open any document from the Recent Files or Folders view. Tap the Crop tool at the top to re-adjust boundary quad corners, then tap 'Crop & Rescan' for fresh OCR recognition.",
        "What formats are supported?" to "Vault supports multi-page PDFs, JPEG photos, PNG images, and WEBP formats taken with your camera or imported from storage.",
        "Can I organize documents into folders?" to "Yes! Tap '+ add folder' under My Folders on the Vault screen. You can move files between folders anytime."
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = null,
                    tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Frequently Asked Questions",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.5.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                faqItems.forEach { (question, answer) ->
                    FaqAccordionItem(
                        question = question,
                        answer = answer,
                        isDark = isDark
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontFamily = Manrope, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun FaqAccordionItem(
    question: String,
    answer: String,
    isDark: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(10.dp),
        color = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF9FAFB),
        border = BorderStroke(
            1.dp,
            if (isDark) VaultCardBorderDark else VaultCardBorderLight
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = question,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = answer,
                        fontFamily = Manrope,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// Dialog: Report an Issue
// ==========================================
@Composable
private fun ReportIssueDialog(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (category: String, description: String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Scanner & OCR") }
    var description by remember { mutableStateOf("") }
    val categories = listOf("Scanner & OCR", "AI Chat & RAG", "UI & Performance", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Report an Issue",
                fontFamily = Manrope,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select Category:",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.take(2).forEach { cat ->
                        CategoryChip(
                            title = cat,
                            isSelected = selectedCategory == cat,
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedCategory = cat }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.drop(2).forEach { cat ->
                        CategoryChip(
                            title = cat,
                            isSelected = selectedCategory == cat,
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedCategory = cat }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Description:",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = {
                        Text(
                            "What happened? Steps to reproduce...",
                            fontFamily = Manrope,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = "Device info (Android ${Build.VERSION.RELEASE}, ${Build.MODEL}) will be included automatically.",
                    fontFamily = Manrope,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedCategory, description.trim()) },
                enabled = description.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) SupabaseGreen else SupabaseGreenDarker
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Submit", fontFamily = Manrope, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = Manrope)
            }
        }
    )
}

@Composable
private fun CategoryChip(
    title: String,
    isSelected: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            if (isDark) Color(0xFF16251E) else Color(0xFFEAF7F0)
        } else {
            if (isDark) Color(0xFF222222) else Color(0xFFF3F4F6)
        },
        border = BorderStroke(
            1.dp,
            if (isSelected) {
                if (isDark) SupabaseGreen else SupabaseGreenDarker
            } else Color.Transparent
        )
    ) {
        Text(
            text = title,
            fontFamily = Manrope,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.5.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = if (isSelected) {
                if (isDark) SupabaseGreen else SupabaseGreenDarker
            } else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==========================================
// Dialog: Support
// ==========================================
@Composable
private fun SupportDialog(
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SupportAgent,
                    contentDescription = null,
                    tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Vault Support",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Need help or have questions regarding your document scanner or chat assistant?",
                    fontFamily = Manrope,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF9FAFB),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) VaultCardBorderDark else VaultCardBorderLight
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Email Support:",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "support@vaultapp.local",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "GitHub / Community:",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "github.com/LunabaLeeris/Vault",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) SupabaseGreen else SupabaseGreenDarker
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done", fontFamily = Manrope, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    )
}

// ==========================================
// Dialog: Storage & Cache
// ==========================================
@Composable
private fun StorageDialog(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onClearCache: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Storage & Cache",
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Vault stores all scanned documents, cropped bitmaps, and extracted text locally on your device.",
                    fontFamily = Manrope,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF9FAFB),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) VaultCardBorderDark else VaultCardBorderLight
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Database Type:", fontFamily = Manrope, fontSize = 12.5.sp)
                            Text("Room SQLite", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("OCR Engine:", fontFamily = Manrope, fontSize = 12.5.sp)
                            Text("ML Kit Offline", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onClearCache,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) SupabaseGreen else SupabaseGreenDarker
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Clean Cache", fontFamily = Manrope, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontFamily = Manrope)
            }
        }
    )
}
