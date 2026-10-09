package com.example.vault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.vault.rag.RagDependencies
import com.example.vault.ui.chat.ChatRoute
import com.example.vault.ui.theme.VaultTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dependencies = RagDependencies(applicationContext)
        setContent { VaultTheme { ChatRoute(dependencies) } }
    }
}
