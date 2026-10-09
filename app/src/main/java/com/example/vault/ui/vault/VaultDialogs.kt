package com.example.vault.ui.vault

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
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
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
        title = { Text("Rename Document") },
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
                }
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
        title = { Text("Delete Document") },
        text = {
            Text("Are you sure you want to delete \"$documentTitle\"? This file will be permanently removed from your device.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
