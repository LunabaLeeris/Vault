package com.example.vault.ui.vault

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vault.ui.theme.SupabaseGreenDarker

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "New Folder",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = {
                    folderName = it
                    isError = false
                },
                label = { Text("Folder Name") },
                isError = isError,
                supportingText = if (isError) {
                    { Text("Folder name cannot be blank") }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SupabaseGreenDarker,
                    focusedLabelColor = SupabaseGreenDarker,
                    cursorColor = SupabaseGreenDarker
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = folderName.trim()
                    if (trimmed.isBlank()) {
                        isError = true
                    } else {
                        onConfirm(trimmed)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SupabaseGreenDarker,
                    contentColor = Color.White
                )
            ) {
                Text("Create", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun RenameDocumentDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (newTitle: String) -> Unit
) {
    var title by remember { mutableStateOf(currentTitle) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "Rename Document",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    isError = false
                },
                label = { Text("Document Title") },
                isError = isError,
                supportingText = if (isError) {
                    { Text("Title cannot be blank") }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SupabaseGreenDarker,
                    focusedLabelColor = SupabaseGreenDarker,
                    cursorColor = SupabaseGreenDarker
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = title.trim()
                    if (trimmed.isBlank()) {
                        isError = true
                    } else {
                        onConfirm(trimmed)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SupabaseGreenDarker,
                    contentColor = Color.White
                )
            ) {
                Text("Rename", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun DeleteDocumentDialog(
    documentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "Delete Document",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Text(
                "Are you sure you want to delete \"$documentTitle\"? This file will be permanently removed from your device.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
