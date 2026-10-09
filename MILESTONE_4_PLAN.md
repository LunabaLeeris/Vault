# VAULT: Milestone 4 Implementation Plan
## Local Text Extraction, Editing, and RAG Pipeline Preparation

---

## 1. Overview & Context
This document defines the implementation plan for Milestone 4 of the VAULT mobile application.

The goal of Milestone 4 is to add on-device Optical Character Recognition (OCR) to documents, allow users to view, edit, and delete extracted text independently of original document files, and prepare the database structure for Milestone 5 (Local Keyword RAG with SQLite FTS4).

All operations remain 100% offline and execute locally on Android 11+ devices.

---

## 2. Rebranding: DocVault to Vault
In accordance with the project specification:
1. Update `app_name` in `strings.xml` to "Vault".
2. Update all UI TopAppBar headers from "DocVault" to "Vault".
3. Update local database naming to `vault.db`.

---

## 3. Technology Stack & Dependencies
1. Language: Kotlin 2.2.10
2. UI: Jetpack Compose with Material 3
3. Database: AndroidX Room with SQLite and FTS4 support
4. Text Recognition: Google ML Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`) bundled for Latin script (English and Latin alphabets). Runs entirely on-device with no network calls.
5. PDF Rendering: Android Framework `android.graphics.pdf.PdfRenderer` (built into Android since API 21).

---

## 4. Data Architecture & Schema

### Entity: `DocumentPageEntity`
Represents an individual page of a document and its extracted text:
1. `id`: Long (Primary Key, autoincrement)
2. `documentId`: Long (Foreign Key referencing `documents(id)` with `ON DELETE CASCADE`)
3. `pageNumber`: Int (1-indexed page index)
4. `rawText`: String (Original text emitted by the OCR engine)
5. `editedText`: String? (User-modified text; null if not modified)
6. `updatedAt`: Long (Timestamp of last user modification)

### Milestone 5 RAG Bridge
1. The authoritative content for search is `editedText ?: rawText`.
2. Each text segment retains its exact `documentId` and `pageNumber`, providing the exact citation data needed for chatbot answer references in Milestone 5.
3. Deleting extracted text removes the `DocumentPageEntity` rows while keeping the physical PDF or image file completely intact.

---

## 5. Local OCR Pipeline (`ocr/TextRecognitionHelper.kt`)

### Image Processing
1. Reads bitmap from app-private storage or content URI using `InputImage.fromFilePath` or `InputImage.fromBitmap`.
2. Processes image through `TextRecognizer` on `Dispatchers.IO`.
3. Returns extracted text block.

### PDF Processing
1. Opens PDF file using `ParcelFileDescriptor` and `PdfRenderer`.
2. Iterates sequentially through each page from 0 to `pageCount - 1`.
3. Renders each page into an in-memory Bitmap.
4. Feeds bitmap into `TextRecognizer`.
5. Records page text and page number (1-indexed).
6. Recycles bitmap immediately to keep memory usage low on phone hardware.
7. Closes `PdfRenderer` and `ParcelFileDescriptor`.

---

## 6. User Interface & Workflows

### Flow 1: Text Extraction During Document Save
1. In `SaveDocumentScreen.kt`, user sees an "Extract Text (OCR)" toggle or action button.
2. If selected, OCR executes during the save operation.
3. Extracted pages are saved into Room alongside the document record.

### Flow 2: Dedicated Text Viewer & Editor (`DocumentTextScreen.kt`)
1. Accessible from document cards in `FolderDetailScreen.kt` or from inside `PdfViewerScreen.kt` and `ImageViewerScreen.kt`.
2. Displays pages with page headers ("Page 1 of 3").
3. Each page text is editable inside an `OutlinedTextField`.
4. Action: "Save Changes" commits modified text to `editedText`.
5. Action: "Delete Extracted Text" clears the text records from Room without deleting the physical image or PDF file.
6. If a document has not been scanned for text yet, shows an "Extract Text Now" button to run OCR on demand.

---

## 7. Implementation Steps

1. Step 1: Add ML Kit Text Recognition dependency to `gradle/libs.versions.toml` and `app/build.gradle.kts`. Update app name strings to "Vault".
2. Step 2: Create `DocumentPageEntity.kt` and update `VaultDao.kt` with text extraction queries and mutations.
3. Step 3: Implement `TextRecognitionHelper.kt` in the `ocr` package for single images and multi-page PDFs.
4. Step 4: Update `VaultRepository.kt` with text extraction, update, and deletion methods.
5. Step 5: Implement `DocumentTextScreen.kt` and `DocumentTextViewModel.kt` with editing, saving, and deletion workflows.
6. Step 6: Connect navigation routes in `AppNavigation.kt` and add action buttons in viewers and folder document cards.
7. Step 7: Verify compilation via `.\gradlew.bat assembleDebug` and test with `.\gradlew.bat testDebugUnitTest`.
