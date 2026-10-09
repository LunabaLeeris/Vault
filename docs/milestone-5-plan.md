# Milestone 5 implementation plan

Status: implemented on `rag`. This document records the design. Integration details and check results are in `milestone-5-integration.md`.

This plan applies to the existing `rag` branch. The starting app contains the generated Compose screen.

The first result is a local chat screen. A question returns an answer, reference chunks, filenames, and page numbers. 

The reference specifies keyword retrieval with SQLite FTS4. The proposal therefore uses dummy document text rather than simulated semantic vectors.
Real documents replace the dummy source through one interface. The chat screen and pipeline remain the same.

The term “reference embeddings” refers to the replaceable retrieval corpus in this first version.
Dense embeddings require an embedding model and a different retrieval adapter. They remain a later feature under the project reference.

## First demonstration

The screen contains a question field, a Send button, an answer area, and source cards. ( no need to make the UI good, just make it functional)

Each source card shows the filename, page number, and retrieved text chunk.
A tap opens a bundled document preview. The Milestone 3 viewer replaces that preview after integration.

Example question: “When is rent due?”
Expected evidence: “Rent is due on the fifth day of each month.”
Expected reference: `Apartment lease.txt`, page 1, with its retrieved chunk.

The demonstration uses three sample documents: an apartment lease, a laptop warranty, and a conference itinerary.
The fixtures include multiple pages and one edited page to exercise citation metadata.

The first demonstration returns exact excerpts. Full Milestone 5 adds local model answers through the same pipeline.
An excerpt demonstration does not satisfy the reference requirement for local model inference.

## Proposed structure

```text
docs/
    milestone-5-plan.md
    milestone-5-integration.md

app/src/main/
    assets/rag/
        documents.json
        files/
            lease-page-1.txt
            lease-page-2.txt
            warranty-page-1.txt
            trip-page-1.txt
    java/com/example/vault/
        rag/
            RagPipeline.kt
            RagDependencies.kt
            RagConfig.kt
            model/
                RagModels.kt
            source/
                DocumentSource.kt
                AssetDocumentSource.kt
            index/
                PageChunker.kt
                KeywordQuery.kt
                RetrievalIndex.kt
                SqliteRetrievalIndex.kt
            generation/
                AnswerGenerator.kt
                EvidencePrompt.kt
                CitationChecker.kt
                ExcerptAnswerGenerator.kt
                LiteRtAnswerGenerator.kt
        ui/chat/
            ChatRoute.kt
            ChatScreen.kt
            ChatViewModel.kt
            ChatUiState.kt
            SourceCard.kt
            SourcePreview.kt

app/src/test/java/com/example/vault/rag/
    PageChunkerTest.kt
    KeywordQueryTest.kt
    CitationCheckerTest.kt
    RagPipelineTest.kt

app/src/androidTest/java/com/example/vault/rag/
    SqliteRetrievalIndexTest.kt
    ChatScreenTest.kt
    LocalModelTest.kt
```

These are logical packages inside the existing `app` module. Separate Gradle modules and a dependency injection framework are unnecessary for this version.
The tree lists feature files. `RagConfig.kt` holds the tuning values for retrieval and inference.

## Folder map

All Kotlin paths below start at `app/src/main/java/com/example/vault/`.

| Folder or file | Purpose | Features and boundary |
| --- | --- | --- |
| `rag/model/` | Shared RAG data contracts | Documents, pages, revisions, chunks, search scope, answers, and source references. No Android UI or database types. |
| `rag/source/` | Input from other milestones | `DocumentSource` supplies current documents and resolves stable document IDs. The dummy implementation reads bundled fixtures. |
| `rag/index/` | Local retrieval | Page chunker, query tokenizer, index interface, and private SQLite FTS4 implementation. Replaces stale chunks after edits or deletion. |
| `rag/generation/` | Answer production | Generator interface, evidence prompt, citation checks, excerpt generator, and LiteRT-LM adapter. |
| `rag/` | Pipeline and manual wiring | Load current content, synchronize index, retrieve bounded evidence, generate answer, and resolve references. |
| `ui/chat/` | Chat feature | Compose screen, ViewModel, loading/error/cancel state, scope selection, source cards, and source preview. |
| `assets/rag/` | Replaceable dummy corpus | JSON metadata with stable IDs, folder IDs, page numbers, and revision numbers. |
| `assets/rag/files/` | Dummy source files | Bundled page text. The preview opens these files without storage permissions. |
| `app/src/test/java/com/example/vault/rag/` | Pipeline checks | Chunk boundaries, query escaping, citation rejection, and evidence limits. |
| `app/src/androidTest/java/com/example/vault/rag/` | Android checks | Real SQLite search, scope filters, revision changes, deletion, and chat interaction. |
| `docs/` | Developer handoff | This plan and a future integration guide with contracts, examples, model setup, and remaining device checks. |
| `MainActivity.kt` | Temporary demo entry | Opens the standalone chat feature. Other developers can embed `ChatRoute` in their navigation graph. |
| `gradle/libs.versions.toml` and `app/build.gradle.kts` | Pinned dependencies | Add ViewModel/Compose support and LiteRT-LM without build-tool upgrades. |
| `app/src/main/AndroidManifest.xml` and `res/xml/` | Local data policy | Exclude document text, retrieval indexes, and model files from backup. |

Runtime files stay in app-private storage:

| Runtime location | Purpose |
| --- | --- |
| `databases/rag_index.db` | Disposable retrieval cache. Other milestones own the authoritative Room database. |
| `files/models/chat.litertlm` | Local chat model. Development provisioning comes first. An import screen can follow. |
| `cache/` | Temporary inference cache. |

## Modular features

| Module | Responsibility | Input | Output |
| --- | --- | --- | --- |
| Document source | Supply current authoritative content | Vault repository or fixture metadata | Documents with text pages and current revisions |
| Page chunker | Split each page without losing its source | Document text pages | Chunks with document ID, page, revision, and offsets |
| Retrieval index | Store and search local chunks | Current chunks, question, search scope | Ranked matching chunks |
| Pipeline | Coordinate retrieval and answer generation | Question and search scope | Answer with checked source references |
| Answer generator | Produce an answer from supplied evidence | Question and application-assigned evidence IDs | Excerpt text or local model text |
| Citation checker | Check cited IDs against supplied evidence | Generated text and allowed source IDs | Checked references or an unsupported-answer result |
| Chat ViewModel | Manage asynchronous screen state | Send, cancel, scope, refresh, and source actions | StateFlow with messages, loading, errors, and references |
| Chat UI | Collect questions and show results | Screen state | User actions and viewer navigation requests |

`RagDependencies` selects the source, index, and generator in one place.
`ChatRoute` accepts these dependencies and a viewer callback. It has no direct dependency on scanner, OCR, or explorer screens.

## Data contracts

| Contract | Required fields | Owner and rule |
| --- | --- | --- |
| Document | Stable document ID, title, folder ID, folder name | Milestones 2 and 3 own metadata. A rename never changes the ID. |
| Text page | One-based page number, authoritative text, revision, edited flag | Milestone 4 owns text. A text change increments its revision. |
| Reference chunk | Chunk ID, document ID, folder ID, title, page, revision, text, offsets, edited flag | Milestone 5 derives chunks from text pages. Chunks never cross pages. |
| Search scope | All documents, one folder ID, or one document ID | Application-controlled selection. Apply it before evidence enters the prompt. |
| Evidence | Answer-local source ID and reference chunk | Milestone 5 assigns IDs such as `S1` and `S2`. |
| Answer | Text, answer mode, checked references, optional notice | Generated citations resolve only to retrieved evidence. |
| Viewer request | Stable document ID and one-based page number | Milestone 3 resolves the current local file. Model text never supplies a file path. |

The document source exposes two operations: read the current snapshot and resolve a document by ID.
The retrieval index exposes two operations: replace the current chunks and search within a scope.
The generator exposes one operation: answer a question from supplied evidence.

The source snapshot includes documents with empty text pages. These documents remain visible but contribute no retrieval chunks.
The RAG layer neither copies original files nor owns the authoritative document records.

## Request flow and initial limits

```mermaid
flowchart LR
    A[Question and scope] --> B[Read current document source]
    B --> C[Chunk text and refresh FTS4 cache]
    C --> D[Retrieve relevant chunks]
    D --> E[Assign source IDs and bound context]
    E --> F[Excerpt or local model generator]
    F --> G[Check citations]
    G --> H[Answer and source cards]
    H --> I[Document ID and page callback]
```

Initial limits are tuning values, not guarantees of model token usage. (make these easy to changel like in a single file. This way we can also add this in a settings screen for the users to tweak)

| Concern | Initial choice |
| --- | --- |
| Chunk size | Up to 700 characters, with up to 100 characters of overlap within the same page |
| Retrieval size | Up to four chunks, ranked by query-term matches |
| Context size | Up to 2,800 characters of evidence, plus the question and instructions |
| Query syntax | Extract letter and number tokens. Exclude conversational stop words and raw FTS operators. |
| Conversation context | Each question retrieves independently. No prior chat text enters the model in the first version. |
| Refresh | Read the current source before each question. Replace the cache only after content or metadata changes. |
| Execution | Run source reads, indexing, retrieval, and inference outside the main thread |
| Cancellation | Cancel the active question and release its model conversation |

The model prompt identifies document text as evidence. It instructs the model to ignore instructions inside that evidence.
The model cites only assigned source IDs. The application supplies filenames and page numbers from its own records.
A valid source ID checks provenance. It does not prove that a generated claim follows from the evidence.

## Implementation sequence

| Step | Work | Result to check |
| --- | --- | --- |
| 1 | Define contracts and manual dependency wiring | Dummy and real sources can satisfy the same interface.  |
| 2 | Add the three dummy documents and source adapter | The app reads stable IDs, metadata, and pages offline. |
| 3 | Add chunking, FTS4 storage, and retrieval | A demo question returns relevant chunks with intact source metadata. |
| 4 | Add excerpt answers and the pipeline | Unsupported queries produce an evidence message instead of a fabricated answer. |
| 5 | Add the chat screen, ViewModel, and source preview | The user can send a question and open its reference. This is the first demonstration. |
| 6 | Add document and folder scope, refresh, and cancellation | The feature respects scope and responds to source changes. |
| 7 | Add LiteRT-LM CPU inference and citation checks | A provisioned local model answers through the existing pipeline. |
| 8 | Add the integration guide and complete checks | The other developer can replace the source and connect the viewer. |

The default excerpt generator gives a working demonstration without a large model download.
The screen labels excerpt answers and model answers separately. Excerpt mode does not claim language-model inference.
The LiteRT-LM adapter follows the [official Android API](https://developers.google.com/edge/litert-lm/android).
Gemma 3 1B in compatible `.litertlm` format remains the initial model candidate.

## Connections to other milestones

| Producer | Data or feature supplied | Milestone 5 connection |
| --- | --- | --- |
| Milestone 1 | Application entry and dependency wiring | Embed `ChatRoute` and construct the RAG dependencies. |
| Milestone 2 | Document ID, title, folder ID, and owned file | Map the document repository to `DocumentSource`. |
| Milestone 3 | Current metadata and document viewer | Resolve the current title and handle the document/page callback. |
| Milestone 4 | Authoritative edited text, page number, revision | Map text pages to the source contract. Increment revisions after text changes. |

The source returns a current snapshot. A refresh replaces retrieval entries atomically.
Empty or deleted extracted text contributes no chunks. The original document remains available through the document repository.
Document IDs stay stable after renames. References carry the text revision and an edited-text label.

The initial cache uses Android SQLite to avoid ownership conflicts with the Room schema under parallel development.
`RetrievalIndex` permits a later Room FTS4 adapter. A later semantic adapter can replace retrieval independently of the screen and generator.

### Dummy-to-real replacement

1. Implement a source adapter over the document and text repositories from the other milestones.
2. Map stable document IDs and folder IDs without generating replacements.
3. Map authoritative edited text, one-based pages, and current revisions.
4. Select this adapter in `RagDependencies` instead of `AssetDocumentSource`.
5. Connect the viewer callback to the Milestone 3 document route.
6. Refresh the index and run the integration checks.

No chat screen or answer-generator change is required for this replacement.
If future milestones supply dense vectors, those vectors also need model identity, dimensions, and a query embedding from the same model.
That future adapter replaces `RetrievalIndex`. Document and citation contracts stay intact.

### Parallel development boundaries

The `rag` branch owns `rag/`, `ui/chat/`, demo assets, and RAG checks.
Milestones 2 and 3 retain ownership of capture, file storage, the document repository, and the viewer.
Milestone 4 retains ownership of OCR and edited text.

Keep shared-file changes limited to the chat entry, required dependencies, and backup policy.
The integration guide lists each shared-file change so the other developer can merge it deliberately.
Keep the demo entry separate from `ChatScreen`. The final application can call the same route from its navigation graph.

## Failure behavior

| Condition | Expected behavior |
| --- | --- |
| No extracted text exists | Show an empty corpus message and explain that documents need extracted text. |
| No keyword match exists | Say that no relevant evidence was found in the documents. |
| A local model is absent | Label the result as excerpts and retain the source cards. |
| Model initialization or inference fails | Show a model error and allow an explicit retry or excerpt mode. |
| Generated source IDs are unknown or absent | Discard the generated answer and show checked excerpts with a notice. |
| A referenced document was deleted | Show an unavailable source message. |
| Text changed after the answer | Open the current page and label the revision change. |
| Metadata changed after the answer | Resolve the current document title before opening the source. |
| Index refresh fails | Stop the question and show an error. Never silently query stale content. |
| The user cancels | Stop the question, clear loading state, and permit another question. |

Original documents remain usable through the explorer when AI processing fails.
No document contents enter logs or telemetry. The workflow requires no network permission.

## Deferred features

- Dense embeddings, vector databases, and semantic reranking.
- Chat-driven edits, deletion, or autonomous file operations.
- Persistent chat history and context from previous questions.
- Exact highlights on scanned pages.
- A model download manager or automatic model installation.
- GPU acceleration before CPU inference works on the demo phone.
- Changes to scanner, OCR, document storage, or viewer implementation.

## Acceptance checks

- The app opens the chat screen on Android 11 or later.
- A demo question returns relevant text, a filename, page number, and reference chunk.
- A source card opens its document page preview.
- A document or folder selection limits retrieval to that scope.
- An unrelated question reports insufficient document evidence.
- A rename, text edit, text deletion, or document deletion does not leave stale retrieval entries after refresh.
- Work runs outside the main thread and supports cancellation.
- The workflow uses no network service or remote storage.
- A compatible local model can use the same retrieval pipeline after provisioning.
- Restarting the app preserves its retrieval cache and provisioned model. Chat history starts empty.

The first demonstration is complete after the excerpt chat and source preview work with fixtures.
Full Milestone 5 is complete after local model answers and source navigation pass checks on the Android 11+ demo phone.
The exact LiteRT-LM version, model artifact, memory use, and response latency require that phone check before acceptance.

The implementation follows this plan after user approval. The integration guide records the resulting behavior and remaining device checks.
