package com.example.vault.scanner

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

object DocumentScannerHelper {

    fun createScanner(): GmsDocumentScanner {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        return GmsDocumentScanning.getClient(options)
    }

    fun startScan(
        activity: Activity,
        onIntentReady: (IntentSenderRequest) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        try {
            val scanner = createScanner()
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender: IntentSender ->
                    val request = IntentSenderRequest.Builder(intentSender).build()
                    onIntentReady(request)
                }
                .addOnFailureListener { error ->
                    onError(error)
                }
        } catch (e: Throwable) {
            onError(e)
        }
    }

    fun parseResult(data: Intent?): GmsDocumentScanningResult? {
        return GmsDocumentScanningResult.fromActivityResultIntent(data)
    }
}
