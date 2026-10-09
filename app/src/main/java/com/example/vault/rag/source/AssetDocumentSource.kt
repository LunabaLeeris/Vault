package com.example.vault.rag.source

import android.content.res.AssetManager
import com.example.vault.rag.model.RagDocument
import com.example.vault.rag.model.TextPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Replace this demo adapter with one over the vault's document/text repositories. */
class AssetDocumentSource(private val assets: AssetManager) : DocumentSource {
    override suspend fun snapshot(): List<RagDocument> = withContext(Dispatchers.IO) {
        val json = assets.open("rag/documents.json").bufferedReader().use { it.readText() }
        val entries = JSONArray(json)
        List(entries.length()) { index ->
            ensureActive()
            val entry = entries.getJSONObject(index)
            val pages = entry.getJSONArray("pages")
            RagDocument(
                id = entry.getString("id"),
                title = entry.getString("title"),
                folderId = entry.getString("folderId"),
                folderName = entry.getString("folderName"),
                pages = List(pages.length()) { pageIndex ->
                    val page = pages.getJSONObject(pageIndex)
                    TextPage(
                        number = page.getInt("number"),
                        text = assets.open(page.getString("textAsset")).bufferedReader().use { it.readText() },
                        revision = page.getLong("revision"),
                        isEdited = page.optBoolean("isEdited", false),
                    )
                },
            )
        }
    }
}
