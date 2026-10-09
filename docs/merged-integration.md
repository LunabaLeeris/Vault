# Vault: combined application

The `merged` branch combines the `ui` and `rag` histories.
The vault remains the start screen. Its Chat action opens local RAG over the actual saved documents.

## Data flow

Capture or import -> app-private original -> page OCR -> Room raw/edited text -> page chunks -> FTS4 -> local answer -> source viewer.

`VaultRepository` owns original-file metadata and text publication.
`DocumentSnapshot` reads documents, folders, and pages in one Room transaction.
`RoomDocumentSource` maps this snapshot to the existing RAG contracts.
Production never falls back to sample assets for an empty vault.
Sample fixtures live in the Android test assets.

`DocumentPageEntity.content` selects `editedText` whenever it is non-null, including an empty string.
Raw OCR remains available separately. Repeated extraction preserves existing edits and page IDs.
An extraction result cannot replace text after another edit or deletion changes the captured revision.
Extraction failure retains existing text and the original file.

## Persistence and migration

The authoritative database remains `vault.db`.
Version 2 adds `documents.textRevision`, with an initial database value of 1.
Migration `MIGRATION_1_2` preserves IDs, folders, document paths, OCR, and edited text.
The application no longer uses destructive migration fallback.
Both schema versions are recorded in `app/schemas/`.

The document-wide text revision increments in the transaction that publishes OCR, saves an edit, or deletes extracted text.
It survives deletion of page rows. Renaming a document does not increment the text revision.
RAG pages use the current document counter, so any text change conservatively invalidates all its chunks.

Original files keep the existing `files/documents/<UUID>.<extension>` location.
The integration does not move or rename the stored originals.
Folder deletion now cleans up its owned document files after the database cascade.

## Retrieval and answers

The retrieval cache remains the separate, disposable `rag_index.db`.
`RagSyncCoordinator` observes committed Room changes and refreshes it.
Every question reads a fresh Room snapshot before search, independently of that background refresh.
If a refresh fails, the question reports an error instead of querying old evidence.

The pipeline checks document IDs, scope, page revisions, and text again before publishing an answer.
Changes during generation discard the old result and ask the user to repeat the question.
Citation checks resolve only application-assigned source IDs.
They establish source identity, not proof that every generated claim follows from the evidence.

Defaults remain in `RagConfig.kt`:

- Up to 700 characters per chunk and 100 characters of page-local overlap.
- Up to four retrieved chunks and 2,800 evidence characters.
- Local CPU inference with disabled thinking.
- Exact excerpts when model citations are absent or invalid.

This MVP uses keyword retrieval. It does not create semantic vectors.

## Chat and navigation

1. Capture or import a document and save it into a folder.
2. Leave extraction enabled, or extract text later from the document text screen.
3. Open Chat from the vault toolbar.
4. Select all documents, a folder, or one document.
5. Enable **Use local model** for generated answers.
6. Ask a question.
7. Open a source card to view its original image or cited PDF page.

`SourceNavigationRequest` carries the stable document ID, one-based page number, text revision, and edited-text flag.
PDF routes convert the page number to a zero-based renderer index inside the viewer.
Rendering and page navigation run outside the main thread.
The viewer labels edited evidence and revisions that changed after an answer.
An old reference can still open its original document after extracted text is deleted.
Returning from the viewer retains chat history on the navigation back stack.

## Local model and privacy

The existing model remains in app-private `files/models/chat.litertlm`.
Model weights remain outside Git and outside the APK.
The adapter keeps LiteRT-LM 0.15.0 and its cancellable callback bridge.
The SDK Flow wrapper is not used because its declared coroutine dependency caused a runtime ABI error.

Both `INTERNET` and `ACCESS_NETWORK_STATE` permissions are explicitly excluded from the merged application manifest.
This also removes permissions introduced by transitive ML Kit dependencies.
OCR uses the bundled Latin recognizer. Retrieval, text storage, and inference remain local.
Scanner components still require initial setup through Google Play services.
Backup and transfer rules exclude the vault databases, originals, and model.

## Build compatibility

The integration retains AGP, Gradle, Kotlin, KSP, Room, and the existing application ID.
Kotlin and Java bytecode targets both use JVM 11.
Lifecycle components use 2.9.4. Navigation retains the UI branch's 2.8.5 version.
Source lookup runs on IO, followed by explicit main-thread navigation.

## Checks

Build and checks:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

For a provisioned model and an unlocked phone, use update installs to preserve data:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.example.vault.test/androidx.test.runner.AndroidJUnitRunner
```

Device tests create only uniquely identified test documents or isolated databases.
Cleanup targets their owned records and files. It does not clear the existing vault or model.

Current results:

- Debug build, test APK build, and lint pass.
- All 12 unit checks pass, including changes during generation.
- All 11 device checks pass together on the connected Android 15 ARM64 phone.
- These checks cover migration, real image/PDF OCR, authoritative edits, empty edits, stale extraction, cache refresh, and local model citations.
- The cited-page UI check passes, including page 3 and chat history after returning from the viewer.

Runtime logs remain under the ignored `build/` directory and `build-merged.log`.
The final device run is recorded in `build/merged-final-device-tests.log`.
Scanner/camera interaction retains the existing UI implementation. Automated capture inputs use generated images and PDFs.
The original project checkout, local IDE change, and model source file remain unchanged.
