# DocVault: Implementation & Onboarding Plan

## 1. Project Overview & Context
DocVault is an Android local-first document management system built for rapid, reliable mobile capture, organization, and viewing. The application operates entirely offline on device storage without remote servers, cloud dependencies, or tracking.

The application runs on Android 11 (API 30) and newer, targeting modern Android standards (compileSdk 37, minSdk 26).

---

## 2. Core Architectural Decisions

### Principle 1: YAGNI & KISS
1. Manual Dependency Injection: A thread-safe repository singleton provides data services without complex Dagger/Hilt setup overhead.
2. Built-in Android Graphics: Image rendering and PDF display use standard Android framework APIs (`BitmapFactory` and `PdfRenderer`) rather than heavy external viewer libraries.
3. Strict Local Storage: Automatic cloud backup is disabled (`android:allowBackup="false"` in `AndroidManifest.xml`). All documents are stored in app-private directory space (`context.filesDir/documents/`).

### Principle 2: Two-Step Atomic Write
To avoid orphan database records pointing to missing files:
1. File write first: Raw bytes are copied from content URIs into internal storage under UUID filenames (`<UUID>.pdf` or `<UUID>.jpg`).
2. Database write second: Room SQLite metadata is committed only after the physical file stream closes successfully.
3. Automatic cleanup: If either step fails or the user cancels, incomplete temporary files are immediately removed from storage.

---

## 3. Technology Stack & Dependencies

1. Language: Kotlin 2.2.10
2. Build Tooling: Android Gradle Plugin (AGP) 9.4.1, Gradle 9.6.0, KSP 2.2.10-2.0.2
3. UI Framework: Jetpack Compose with Material 3 and Compose Navigation 2.8.5
4. Database: AndroidX Room 2.6.1 with KSP Kotlin code generation (`room.generateKotlin = true`)
5. Document Scanning: Google Play Services ML Kit Document Scanner 16.0.0
6. Pickers: Android Photo Picker (`PickVisualMedia`) and Storage Access Framework (`OpenDocument`)
7. PDF Rendering: Android Framework `android.graphics.pdf.PdfRenderer`
8. Concurrency & State: Kotlin Coroutines, `Dispatchers.IO`, and `StateFlow`

---

## 4. Data Architecture & Database Schema

### Database: `docvault.db`

#### Table: `folders`
1. `id`: Long, Primary Key (Autoincrement)
2. `name`: String, Unique Index (Case-insensitive check enforced in Repository)
3. `createdAt`: Long, Epoch Milliseconds

#### Table: `documents`
1. `id`: Long, Primary Key (Autoincrement)
2. `folderId`: Long, Foreign Key referencing `folders(id)` with `ON DELETE CASCADE`
3. `title`: String (Display title, editable without renaming the disk file)
4. `mimeType`: String (`application/pdf`, `image/jpeg`, `image/png`)
5. `relativePath`: String (Relative path from `filesDir`, e.g. `documents/3fa85f64-5717-4562-b3fc-2c963f66afa6.pdf`)
6. `createdAt`: Long, Epoch Milliseconds
7. `updatedAt`: Long, Epoch Milliseconds

#### File System Organization
Files are located at:
`context.filesDir / "documents" / "<UUID>.<ext>"`
Storage access requires no broad storage permissions on Android 11+.

---

## 5. Component Structure & Responsibilities

### Package: `com.example.vault.data.local`
1. `FolderEntity.kt`: Room entity defining the folders table.
2. `DocumentEntity.kt`: Room entity defining the documents table with foreign key relations.
3. `VaultDao.kt`: Room Data Access Object providing reactive `Flow` queries for folders with document counts, folder listings, and CRUD operations.
4. `VaultDatabase.kt`: Abstract Room database singleton pre-seeding the initial "Inbox" folder on creation.

### Package: `com.example.vault.data.repository`
1. `FileStorageManager.kt`: Manages physical file I/O, copies streams from content URIs, computes paths, and performs safe file deletions.
2. `VaultRepository.kt`: Coordinates business validation (blank checks, duplicate folder checks), orchestrates atomic saves, and provides shared reactive data flows.

### Package: `com.example.vault.scanner`
1. `DocumentScannerHelper.kt`: Wraps ML Kit `GmsDocumentScanner` configured in full mode with auto-crop, rotation, and gallery import enabled.

### Package: `com.example.vault.ui.vault`
1. `VaultViewModel.kt`: Exposes folder list StateFlow and handles folder creation actions.
2. `VaultScreen.kt`: Main landing view displaying all folders, folder document counts, the floating action button to scan documents, and quick-import buttons.
3. `FolderDetailViewModel.kt`: Supplies document listings and handles document renaming and deletion.
4. `FolderDetailScreen.kt`: Displays document cards inside a folder with open, rename, and delete options.
5. `VaultDialogs.kt`: Reusable composable dialogs for folder creation, document renaming, and deletion confirmation.

### Package: `com.example.vault.ui.editor`
1. `SaveDocumentViewModel.kt`: Background-renders instant visual previews for incoming images and PDFs, validates titles, and executes file saves.
2. `SaveDocumentScreen.kt`: Document review screen where users review previews, edit titles, pick destination folders, create folders inline, and save or cancel.

### Package: `com.example.vault.ui.viewer`
1. `PdfViewerScreen.kt`: Renders PDF pages off the main thread via `PdfRenderer`. Provides Previous/Next controls, page counters, and cleans up memory on exit.
2. `ImageViewerScreen.kt`: Decodes image files from disk and supports touch-based pinch-to-zoom and panning.

### Package: `com.example.vault.ui.navigation`
1. `NavRoutes.kt`: Type-safe navigation route definitions (`vault`, `folder/{folderId}`, `save_document`, `view_pdf/{documentId}`, `view_image/{documentId}`).
2. `AppNavigation.kt`: Central `NavHost` connecting screens and injecting ViewModel dependencies.

---

## 6. End-to-End Operational Flows

### Flow A: Document Capture & Save
1. User taps "Scan Document" on VaultScreen (or "Import Image" / "Import PDF").
2. The scanner or picker returns a content URI to MainActivity.
3. The URI and MIME type are set as a `PendingDocument` in the repository and the app navigates to SaveDocumentScreen.
4. SaveDocumentScreen generates an instant preview (page 1 for PDFs, bitmap for images) using a background dispatcher.
5. User enters a non-blank title and selects a folder (or creates a new folder inline via "+ New").
6. User taps "Save".
7. `FileStorageManager` copies the content stream into `filesDir/documents/<UUID>.<ext>`.
8. `VaultDao` inserts the document record with the resulting relative path.
9. Navigation automatically routes the user directly to the destination folder detail view.

### Flow B: Document Browsing & Viewing
1. User taps a folder on VaultScreen.
2. App routes to `FolderDetailScreen`, querying `VaultDao.getDocumentsInFolder(folderId)`.
3. Tapping a PDF document opens `PdfViewerScreen`.
4. `PdfViewerScreen` opens the file via `ParcelFileDescriptor`, reads page count, and renders page 0 into a Bitmap.
5. Tapping "Next" renders the subsequent page and recycles the old page bitmap.
6. When exiting the viewer, `DisposableEffect` closes both `PdfRenderer` and `ParcelFileDescriptor`.

### Flow C: Renaming & Deletion
1. Rename: User changes the title via dialog. `VaultDao.updateDocumentTitle` updates the database record without touching the file on disk.
2. Delete: User confirms deletion. `VaultDao.deleteDocument` removes the row, and `FileStorageManager.deleteFile` deletes the binary file from disk.

---

## 7. Developer Onboarding & Verification Guide

### Building the Project
Run the debug assembly task using the Gradle wrapper:
```powershell
.\gradlew.bat assembleDebug
```

Run unit tests:
```powershell
.\gradlew.bat testDebugUnitTest
```

Install on a connected device:
```powershell
.\gradlew.bat installDebug
```

### Manual Verification Checklist
1. Default Folder: Confirm "Inbox" exists on clean install.
2. Unique Names: Try creating a duplicate folder name and confirm rejection.
3. Blank Title: Confirm that blank titles cannot be saved or renamed.
4. PDF Navigation: Open a multi-page PDF and verify Previous/Next paging.
5. Image Zoom: Open an image and test pinch-to-zoom and dragging.
6. Cascade Delete: Delete a folder and ensure all contained documents are cleared.
