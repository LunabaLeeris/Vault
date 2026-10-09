package com.example.vault.data.local

import android.content.Context
import android.util.Log
import com.example.vault.ui.chat.ChatMessage
import com.example.vault.ui.chat.ChatSession
import com.example.vault.ui.chat.MessageSender
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object ChatStorage {

    private const val TAG = "ChatStorage"
    private const val FILE_NAME = "chat_sessions.json"
    private const val TMP_FILE_NAME = "chat_sessions.json.tmp"

    /**
     * Saves chat sessions and the active session ID to internal storage atomically.
     */
    @Synchronized
    fun saveChatData(
        context: Context,
        sessions: List<ChatSession>,
        activeSessionId: String
    ) {
        try {
            val rootObj = JSONObject()
            rootObj.put("activeSessionId", activeSessionId)

            val sessionsArray = JSONArray()
            for (session in sessions) {
                val sessionObj = JSONObject()
                sessionObj.put("id", session.id)
                sessionObj.put("title", session.title)
                sessionObj.put("updatedAt", session.updatedAt)

                val messagesArray = JSONArray()
                for (msg in session.messages) {
                    val msgObj = JSONObject()
                    msgObj.put("id", msg.id)
                    msgObj.put("sender", msg.sender.name)
                    msgObj.put("text", msg.text)
                    msgObj.put("timestamp", msg.timestamp)
                    msgObj.put("isLiked", msg.isLiked)

                    val docIdsArray = JSONArray()
                    for (doc in msg.relatedDocs) {
                        docIdsArray.put(doc.id)
                    }
                    msgObj.put("relatedDocIds", docIdsArray)

                    messagesArray.put(msgObj)
                }
                sessionObj.put("messages", messagesArray)
                sessionsArray.put(sessionObj)
            }
            rootObj.put("sessions", sessionsArray)

            val jsonString = rootObj.toString()
            val filesDir = context.filesDir
            val tmpFile = File(filesDir, TMP_FILE_NAME)
            val destFile = File(filesDir, FILE_NAME)

            // Write atomically to temp file first
            FileOutputStream(tmpFile).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    writer.write(jsonString)
                    writer.flush()
                }
            }

            // Atomic rename
            if (tmpFile.exists()) {
                if (destFile.exists()) {
                    destFile.delete()
                }
                tmpFile.renameTo(destFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving chat data to storage", e)
        }
    }

    /**
     * Loads persisted chat sessions and the active session ID from internal storage.
     * Optionally resolves related documents using [allDocsMap].
     */
    @Synchronized
    fun loadChatData(
        context: Context,
        allDocsMap: Map<Long, DocumentEntity> = emptyMap()
    ): Pair<List<ChatSession>, String?> {
        val destFile = File(context.filesDir, FILE_NAME)
        if (!destFile.exists()) {
            return Pair(emptyList(), null)
        }

        try {
            val jsonString = destFile.readText(StandardCharsets.UTF_8)
            if (jsonString.isBlank()) {
                return Pair(emptyList(), null)
            }

            val rootObj = JSONObject(jsonString)
            val activeSessionId = if (rootObj.has("activeSessionId")) rootObj.getString("activeSessionId") else null

            val sessionsArray = rootObj.optJSONArray("sessions") ?: return Pair(emptyList(), activeSessionId)
            val sessionsList = mutableListOf<ChatSession>()

            for (i in 0 until sessionsArray.length()) {
                val sessionObj = sessionsArray.optJSONObject(i) ?: continue
                val sessionId = sessionObj.optString("id")
                val title = sessionObj.optString("title", "Vault Assistant")
                val updatedAt = sessionObj.optLong("updatedAt", System.currentTimeMillis())

                val messagesArray = sessionObj.optJSONArray("messages")
                val messagesList = mutableListOf<ChatMessage>()

                if (messagesArray != null) {
                    for (j in 0 until messagesArray.length()) {
                        val msgObj = messagesArray.optJSONObject(j) ?: continue
                        val msgId = msgObj.optString("id")
                        val senderStr = msgObj.optString("sender", MessageSender.ASSISTANT.name)
                        val sender = try {
                            MessageSender.valueOf(senderStr)
                        } catch (_: Exception) {
                            MessageSender.ASSISTANT
                        }
                        val text = msgObj.optString("text", "")
                        val timestamp = msgObj.optLong("timestamp", System.currentTimeMillis())
                        val isLiked = msgObj.optBoolean("isLiked", false)

                        val relatedDocs = mutableListOf<DocumentEntity>()
                        val docIdsArray = msgObj.optJSONArray("relatedDocIds")
                        if (docIdsArray != null && allDocsMap.isNotEmpty()) {
                            for (k in 0 until docIdsArray.length()) {
                                val docId = docIdsArray.optLong(k)
                                allDocsMap[docId]?.let { relatedDocs.add(it) }
                            }
                        }

                        messagesList.add(
                            ChatMessage(
                                id = msgId,
                                sender = sender,
                                text = text,
                                timestamp = timestamp,
                                isLiked = isLiked,
                                relatedDocs = relatedDocs
                            )
                        )
                    }
                }

                sessionsList.add(
                    ChatSession(
                        id = sessionId,
                        title = title,
                        messages = messagesList,
                        updatedAt = updatedAt
                    )
                )
            }

            return Pair(sessionsList, activeSessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading chat data from storage", e)
            return Pair(emptyList(), null)
        }
    }
}
