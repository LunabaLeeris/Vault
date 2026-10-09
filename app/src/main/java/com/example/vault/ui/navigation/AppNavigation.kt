package com.example.vault.ui.navigation

import android.app.Application
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.vault.ui.editor.SaveDocumentScreen
import com.example.vault.ui.vault.FolderDetailScreen
import com.example.vault.ui.vault.FolderDetailViewModel
import com.example.vault.ui.vault.VaultScreen
import com.example.vault.ui.vault.VaultViewModel
import com.example.vault.ui.viewer.ImageViewerScreen
import com.example.vault.ui.viewer.PdfViewerScreen
import com.example.vault.ui.chat.ChatRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vaultViewModel: VaultViewModel = viewModel()
    val ragModel: VaultRagViewModel = viewModel()
    val navigationScope = rememberCoroutineScope()
    var sourceError by remember { mutableStateOf<String?>(null) }

    if (sourceError != null) AlertDialog(
        onDismissRequest = { sourceError = null },
        title = { Text("Source unavailable") },
        text = { Text(sourceError.orEmpty()) },
        confirmButton = { TextButton(onClick = { sourceError = null }) { Text("Close") } },
    )

    NavHost(
        navController = navController,
        startDestination = Screen.Vault.route,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(260)
            ) + fadeIn(animationSpec = tween(260))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(260)
            ) + fadeOut(animationSpec = tween(260))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(260)
            ) + fadeIn(animationSpec = tween(260))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(260)
            ) + fadeOut(animationSpec = tween(260))
        }
    ) {
        composable(Screen.Vault.route) {
            VaultScreen(
                viewModel = vaultViewModel,
                onFolderClick = { folderId ->
                    navController.navigate(Screen.FolderDetail.createRoute(folderId))
                },
                onStartSaveFlow = {
                    navController.navigate(Screen.SaveDocument.route)
                },
                onOpenChat = { navController.navigate(Screen.Chat.route) },
            )
        }

        composable(Screen.Chat.route) {
            ChatRoute(
                ragModel.dependencies,
                onNavigateBack = { navController.popBackStack() },
                onOpenDocument = { request ->
                    navigationScope.launch {
                        val doc = withContext(Dispatchers.IO) {
                            request.documentId.toLongOrNull()?.takeIf { it > 0 }?.let { ragModel.repository.findDocument(it) }
                                ?.takeIf { ragModel.repository.getFile(it.relativePath).isFile }
                        }
                        withContext(Dispatchers.Main.immediate) {
                            if (navController.currentDestination?.route != Screen.Chat.route) return@withContext
                            if (doc == null) sourceError = "The original document is no longer available on this device."
                            else if (doc.mimeType.contains("pdf", ignoreCase = true)) {
                                navController.navigate(Screen.ViewPdf.createRoute(doc.id, request.pageNumber, request.textRevision, request.isEdited))
                            } else navController.navigate(Screen.ViewImage.createRoute(doc.id, request.textRevision, request.isEdited))
                        }
                    }
                },
            )
        }

        composable(
            route = Screen.FolderDetail.route,
            arguments = listOf(navArgument("folderId") { type = NavType.LongType })
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getLong("folderId") ?: return@composable
            val folderDetailViewModel: FolderDetailViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FolderDetailViewModel(app, folderId) as T
                    }
                }
            )

            FolderDetailScreen(
                viewModel = folderDetailViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenDocument = { doc ->
                    if (doc.mimeType.contains("pdf", ignoreCase = true)) {
                        navController.navigate(Screen.ViewPdf.createRoute(doc.id))
                    } else {
                        navController.navigate(Screen.ViewImage.createRoute(doc.id))
                    }
                },
                onViewText = { docId ->
                    navController.navigate(Screen.ViewText.createRoute(docId))
                }
            )
        }

        composable(Screen.SaveDocument.route) {
            SaveDocumentScreen(
                onSaveSuccess = { folderId ->
                    navController.popBackStack(Screen.Vault.route, false)
                    navController.navigate(Screen.FolderDetail.createRoute(folderId))
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.ViewPdf.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.LongType },
                navArgument("pageNumber") { type = NavType.IntType; defaultValue = 1 },
                navArgument("revision") { type = NavType.LongType; defaultValue = 0L },
                navArgument("edited") { type = NavType.BoolType; defaultValue = false },
            )
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getLong("documentId") ?: return@composable
            PdfViewerScreen(
                documentId = documentId,
                initialPageNumber = backStackEntry.arguments?.getInt("pageNumber") ?: 1,
                expectedRevision = backStackEntry.arguments?.getLong("revision") ?: 0,
                sourceEdited = backStackEntry.arguments?.getBoolean("edited") ?: false,
                onNavigateBack = { navController.popBackStack() },
                onViewText = {
                    navController.navigate(Screen.ViewText.createRoute(documentId))
                }
            )
        }

        composable(
            route = Screen.ViewImage.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.LongType },
                navArgument("revision") { type = NavType.LongType; defaultValue = 0L },
                navArgument("edited") { type = NavType.BoolType; defaultValue = false },
            )
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getLong("documentId") ?: return@composable
            ImageViewerScreen(
                documentId = documentId,
                expectedRevision = backStackEntry.arguments?.getLong("revision") ?: 0,
                sourceEdited = backStackEntry.arguments?.getBoolean("edited") ?: false,
                onNavigateBack = { navController.popBackStack() },
                onViewText = {
                    navController.navigate(Screen.ViewText.createRoute(documentId))
                }
            )
        }

        composable(
            route = Screen.ViewText.route,
            arguments = listOf(navArgument("documentId") { type = NavType.LongType })
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getLong("documentId") ?: return@composable
            val documentTextViewModel: com.example.vault.ui.editor.DocumentTextViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return com.example.vault.ui.editor.DocumentTextViewModel(app, documentId) as T
                    }
                }
            )
            com.example.vault.ui.editor.DocumentTextScreen(
                viewModel = documentTextViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
