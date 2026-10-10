package com.example.vault.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Vault : Screen("vault")
    data object Chat : Screen("chat")

    data object FolderDetail : Screen("folder/{folderId}") {
        fun createRoute(folderId: Long) = "folder/$folderId"
    }

    data object SaveDocument : Screen("save_document")

    data object ViewPdf : Screen("view_pdf/{documentId}?page={pageNumber}") {
        fun createRoute(documentId: Long, pageNumber: Int = 1) = "view_pdf/$documentId?page=$pageNumber"
    }

    data object ViewImage : Screen("view_image/{documentId}") {
        fun createRoute(documentId: Long) = "view_image/$documentId"
    }

    data object ViewText : Screen("view_text/{documentId}") {
        fun createRoute(documentId: Long) = "view_text/$documentId"
    }
}
