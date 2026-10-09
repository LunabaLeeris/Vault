package com.example.vault.ui.navigation

sealed class Screen(val route: String) {
    data object Vault : Screen("vault")
    data object Chat : Screen("chat")

    data object FolderDetail : Screen("folder/{folderId}") {
        fun createRoute(folderId: Long) = "folder/$folderId"
    }

    data object SaveDocument : Screen("save_document")

    data object ViewPdf : Screen("view_pdf/{documentId}?page={pageNumber}&revision={revision}&edited={edited}") {
        fun createRoute(documentId: Long, pageNumber: Int = 1, revision: Long = 0, edited: Boolean = false) =
            "view_pdf/$documentId?page=$pageNumber&revision=$revision&edited=$edited"
    }

    data object ViewImage : Screen("view_image/{documentId}?revision={revision}&edited={edited}") {
        fun createRoute(documentId: Long, revision: Long = 0, edited: Boolean = false) =
            "view_image/$documentId?revision=$revision&edited=$edited"
    }

    data object ViewText : Screen("view_text/{documentId}") {
        fun createRoute(documentId: Long) = "view_text/$documentId"
    }
}
