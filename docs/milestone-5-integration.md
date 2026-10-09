# Milestone 5 integration guide

The `rag` branch adds a standalone local chat feature. The default demo uses three bundled documents and returns exact excerpts.
The LiteRT-LM adapter uses the same retrieval pipeline for local model answers after model provisioning.

## Run the first demo

1. Build and install the debug app from Android Studio.
2. Open Vault.
3. Enter `When is rent due?`.
4. Tap **Send**.
5. Tap the source card to open the lease page.

Other demo questions are `How long is the laptop warranty?` and `When is hotel check-in?`.
Use the **Search** selector to restrict retrieval to one document or folder.
Use **Refresh** after source or model changes.

Excerpt mode returns literal document text. It does not claim model inference or semantic understanding.

## Replace the dummy corpus

The connection points are `DocumentSource`, `RagDependencies`, and `ChatRoute`.
The document source owns access to current metadata and authoritative text.

1. Implement `DocumentSource` over the document and text repositories.
2. Return a consistent snapshot from `snapshot()`.
3. Preserve document and folder IDs from the authoritative database.
4. Map current filenames to `title`.
5. Map one-based page numbers to `TextPage.number`.
6. Map user-edited text to `TextPage.text`.
7. Increment `TextPage.revision` after a text edit or text deletion.
8. Set `TextPage.isEdited` for user-edited content.
9. Supply the adapter as `RagDependencies.source`.
10. Set `corpusLabel` to the label for the real vault.

Documents without extracted text can have an empty page list or empty page text.
Deleting extracted text retains the document metadata and original file. It contributes no retrieval chunks.
Deleting a document excludes it from the source snapshot.

The first question and **Refresh** synchronize the retrieval cache with the source snapshot.
A changed snapshot replaces the cache in one database transaction. Unchanged content does not require a replacement within the same pipeline instance.
A new pipeline rebuilds its cache from current authoritative content before retrieval.

The source adapter must return immutable data. Do not mutate a previously returned list or document in place.
For Room, read document metadata and text in one transaction to prevent mixed revisions.
File reads and repository operations must support background execution.
Revisions are positive. If the repository starts revisions at zero, add one consistently in the adapter.

## Connect the viewer

Call `ChatRoute` from the application navigation graph.
Supply `onOpenDocument(documentId, pageNumber)` to connect the Milestone 3 viewer.
The page number is one-based. Convert it once if the viewer uses zero-based indexes.

The application resolves the current document before the callback. The model never supplies a file path.
Deleting extracted text does not block an old source card from opening its original document through the viewer callback.
Without the callback, the route opens the bundled/current text page preview.
The preview labels edited text and revision changes. It does not claim an exact image highlight.
The real viewer must retain its own missing-file handling after navigation.

## Tune retrieval and inference

All initial tuning values live in `rag/RagConfig.kt`.

| Field | Default | Purpose |
| --- | --- | --- |
| `chunkChars` | 700 | Maximum chunk length within one page |
| `overlapChars` | 100 | Overlap between chunks on the same page |
| `maxRetrievedChunks` | 4 | Maximum retrieved evidence chunks |
| `maxContextChars` | 2,800 | Maximum total evidence characters |
| `maxQuestionChars` | 1,000 | Maximum question length |
| `maxQueryTerms` | 16 | Maximum unique query tokens |
| `modelContextTokens` | 2,048 | Model input plus output token capacity |
| `maxOutputTokens` | 256 | Maximum generated output tokens |
| `enableThinking` | false | Disable reasoning output for concise answers |
| `temperature`, `topK`, `topP` | 0.2, 10, 0.9 | Sampling values |
| `modelFileName` | `chat.litertlm` | Filename inside app-private model storage |

Pass a custom configuration to `RagDependencies` to change defaults at construction.
`RagPipeline.updateConfig()` accepts a new immutable configuration for future runtime settings.
A chunk-size or overlap change rebuilds chunks on the next refresh or question.
The current UI reads its question limit from the initial configuration. A future settings screen must update that UI limit too.

Character limits do not guarantee token counts. Test the chosen model with representative documents and languages.
Keyword retrieval can miss synonyms and paraphrases.

## Provision a local model

The adapter uses LiteRT-LM **0.15.0** and the CPU backend.
This version keeps the existing Kotlin compiler compatible. LiteRT-LM 0.17.1 uses incompatible Kotlin 2.4 metadata in this project.
The adapter uses the callback API through a cancellable coroutine.
The SDK Flow wrapper crashed during device inference because its channel ABI differs from its declared coroutine dependency.
The integration follows the [official Kotlin API](https://github.com/google-ai-edge/LiteRT-LM/tree/v0.15.0/kotlin).

The initial candidate is [Gemma 3 1B IT](https://huggingface.co/litert-community/Gemma3-1B-IT/tree/main).
Its files require the user's Hugging Face login and license acceptance.
Use a CPU-compatible `.litertlm` file, such as `gemma3-1b-it-int4.litertlm`.
The `.task` format and hardware-specific NPU artifacts do not satisfy this adapter's contract.

The supplied model for the device check is `app/src/models/gemma-4-E2B-it.litertlm`.
This folder is a developer input location. Gradle does not package it as an application asset.
Model files stay outside Git through the `*.litertlm` ignore rule.

| Supplied artifact | Value |
| --- | --- |
| Model | Gemma 4 E2B IT, supplied by the user |
| Size | 2,588,147,712 bytes |
| SHA-256 | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` |
| Device location | App-private `files/models/chat.litertlm` |

The app does not download model files or contact a model service.
The model must exist at `files/models/chat.litertlm` inside app-private storage.
Development provisioning uses ADB and the debug application's `run-as` permission.

From a terminal with `adb` on its path:

```powershell
adb push "C:\path\to\gemma3-1b-it-int4.litertlm" /data/local/tmp/vault-chat.litertlm
adb shell run-as com.example.vault mkdir -p files/models
adb shell run-as com.example.vault cp /data/local/tmp/vault-chat.litertlm files/models/chat.litertlm
adb shell rm /data/local/tmp/vault-chat.litertlm
```

1. Provision the compatible model file.
2. Tap **Refresh** in chat.
3. Enable **Use local model**.
4. Ask a demo question.
5. Check the answer and source cards.

The adapter initializes an engine per question and releases it afterward. A disk cache helps subsequent loads.
Cancellation stops the active model conversation. Engine initialization can finish before cancellation takes effect.
Model errors permit retry or explicit excerpt mode.
The pipeline discards generated answers with missing, unknown, or malformed source IDs and returns labeled excerpts.
Citation checks establish source identity. They do not prove factual entailment.

## Data ownership and shared files

| Area | Owner |
| --- | --- |
| Capture and original file storage | Milestone 2 |
| Document and folder metadata, explorer, viewer | Milestones 2 and 3 |
| OCR, edited text, text revisions | Milestone 4 |
| Disposable chunks, retrieval, generation, chat | Milestone 5 |

`databases/rag_index.db` is a disposable SQLite FTS4 cache.
It does not change the vault's authoritative Room schema.
A later Room FTS4 adapter can implement `RetrievalIndex` without a chat or generator change.
A later semantic adapter also needs a query embedding from the same model as its stored vectors.

Shared changes are limited to:

- `MainActivity.kt`: temporary demo entry for `ChatRoute`.
- `app/build.gradle.kts`: lifecycle Compose/ViewModel, LiteRT-LM, and coroutine test dependencies.
- `gradle/libs.versions.toml`: pinned versions for those dependencies.
- `AndroidManifest.xml`: disable automatic backup.
- `res/xml/backup_rules.xml` and `data_extraction_rules.xml`: exclude local app data from backup and transfer.

The app adds no network permission. Document contents do not enter application logs.
Chat messages survive activity recreation through the ViewModel. Process restart clears chat history.

## Checks

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Unit checks cover page boundaries, query syntax, citation rejection, context limits, refresh failure, deletion, and configuration changes.
Android checks cover real FTS4 retrieval, scope before limits, cache persistence, revision changes, and chat/source interaction.
`LocalModelTest` performs real inference and requires a checked lease-page citation. It skips only if no local model is provisioned.
Local model inference requires a compatible provisioned model and a physical device check.

Results on 9 October 2026:

- Debug application and Android test APK builds passed.
- All 11 unit checks passed, including 10 RAG checks and the generated example check.
- Android lint passed with warnings about dependency versions and generated resources. It reported no errors.
- Both SQLite integration checks passed on the connected RMX3941 phone with Android 15 and ARM64.
- The chat UI check passed after the phone was unlocked, including the question, answer, and source preview.
- The Gemma 4 E2B check passed with a generated rent answer and a checked lease-page reference.
- The final model and SQLite group passed all three checks with the callback adapter.
- The device feature checks cover four tests: chat interaction, local inference, and two SQLite tests.

The final model/SQLite group ran directly through AndroidJUnitRunner to preserve the provisioned model and application data.
Its output is in `build/rag-model-final.log`. The earlier successful chat check appears in `build/rag-device-final.log`.
That earlier run also records the SDK Flow crash, which the callback adapter corrects.
Build output is in `build-rag.log`.

The supplied model is installed on the connected phone. Enable **Use local model** to use it from chat.
The app keeps the existing minimum SDK of 26. Runtime checks used Android 15, not a separate Android 11 device.
