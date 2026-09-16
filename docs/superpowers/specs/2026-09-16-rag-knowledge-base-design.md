# Design: RAG knowledge base over a fictional company's documents

Date: 2026-09-16
Status: Approved design, ready for an implementation plan

## Goal

Demonstrate that the LLM in this project can look things up in known data instead of
guessing. The demo is a second chat view backed by retrieval-augmented generation over a
small corpus of invented company documents.

The corpus is fictional on purpose. Every fact a question can hit is a fact no model can
know from pretraining, so a correct answer proves retrieval happened. The existing
`/chat-bot` view stays exactly as it is and becomes the control case: the same question
asked there produces a hedge or an invention, while `/knowledge` answers with the number
and names the file it came from.

## Scope

In scope:

- A dummy corpus of Markdown documents for **Harborlight Systems Inc.**
- Startup ingestion: read, split, embed, store.
- An in-memory vector store and a retrieval-augmented `ChatClient`.
- A new Vaadin view at `/knowledge` in the side nav.
- Tests that cover reading, splitting and retrieval without contacting Ollama.
- A README section explaining setup and a demo script.

Out of scope (possible follow-ups, not built now):

- Persisting embeddings between restarts, or any external vector database.
- Uploading or editing documents at runtime.
- Access control or per-user document visibility.
- Reranking, query rewriting, or multi-query retrieval.
- Rendering the retrieved chunks in the UI as a sources panel.

## The corpus

Location: `src/main/resources/knowledge/*.md`. Five documents, all in English:

| File | Covers |
|---|---|
| `employee-handbook.md` | Company facts, office locations, working hours, Anchor Days |
| `time-off-policy.md` | Vacation, parental leave, sick leave, sabbatical |
| `it-security-policy.md` | Devices, VPN, MFA, incident reporting, phishing |
| `travel-and-expenses.md` | Booking rules, per diem, mileage, receipt thresholds |
| `onboarding-guide.md` | First day, buddy program, 30/60/90-day checkpoints |

Each document is written as Markdown with a single `#` title and several `##` sections.

Every document plants specific, invented, checkable facts. A sample of what the demo will
ask about:

- 27 vacation days per year plus 3 company-wide "Harborlight Days".
- A USD 2,600 hardware allowance, renewed every 36 months.
- The VPN is called **Lighthouse**; MFA uses a hardware key called **Harbor Key**.
- Parental leave: 18 weeks at full pay, plus 6 flexible weeks within 24 months.
- Support desk hours 07:30-17:00 ET, 4 business hours response target for P2.
- Per diem USD 68 domestic; receipts required above USD 35; mileage USD 0.62/mile.
- Up to 12 weeks per year working from abroad; Anchor Days are Tuesday and Thursday.
- The onboarding buddy program is called **First Mate**; check-ins at 30, 60 and 90 days.

Facts are stated once, in one document, so a right answer identifies a specific retrieval
rather than a lucky paraphrase.

## Architecture

New package `io.github.avec112.rag`, following the project's package-by-feature layout,
with UI in `io.github.avec112.rag.ui`. Both packages get a `package-info.java` with
`@NullMarked`.

```
io.github.avec112.rag
├── package-info.java
├── KnowledgeDocumentReader   reads knowledge/*.md into Documents, one per ## section
├── KnowledgeBaseIngestor     reads, then adds to the VectorStore; returns a count
├── KnowledgeBaseConfig       VectorStore bean, retrieval ChatClient bean
└── ui
    ├── package-info.java
    └── KnowledgeChatView     @Route("knowledge"), package-private
```

### Data flow

1. **Startup.** `KnowledgeBaseIngestor` runs on `ApplicationReadyEvent`, guarded by the
   property `app.rag.ingest-on-startup` (default `true`).
2. **Read.** `KnowledgeDocumentReader` resolves `classpath:knowledge/*.md`, and for each
   file splits the content on `##` headings, producing one `Document` per section with
   metadata `source` (file name), `title` (the `#` heading) and `section` (the `##`
   heading). Section-level chunks beat token windows here: the documents are small, each
   section is one self-contained policy, and the metadata gives the model a citation
   handle. A test asserts no section exceeds a character cap, so the chunking stays honest
   as the corpus grows.
3. **Embed and store.** `vectorStore.add(documents)` embeds through `OllamaEmbeddingModel`
   and keeps the vectors in a `SimpleVectorStore`, in memory. Ingestion logs how many
   sections came from how many files, and how long it took.
4. **Ask.** `KnowledgeChatView` injects the retrieval `ChatClient`, wraps it in
   `SpringAILLMProvider`, and hands that to `AIOrchestrator` with `MessageList` and
   `MessageInput` — the same shape as `AiChatView`.
5. **Retrieve and answer.** `QuestionAnswerAdvisor` embeds the question, pulls the top-K
   sections from the store, and injects them into the prompt before the model sees it.

### Verified API surface

Checked against the jars this project resolves, not from memory:

- `com.vaadin.flow.component.ai.provider.SpringAILLMProvider` (Vaadin 25.2.8) has two
  constructors: `(ChatModel)` and `(ChatClient)`. The `ChatClient` constructor is what
  makes this design clean — advisors ride along with the client.
- `org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor`, in
  artifact `spring-ai-vector-store-advisor`, built via
  `QuestionAnswerAdvisor.builder(vectorStore).searchRequest(...).build()`.
- `org.springframework.ai.vectorstore.SimpleVectorStore`, built via
  `SimpleVectorStore.builder(embeddingModel).build()`.
- `org.springframework.ai.reader.TextReader` is already on the classpath in
  `spring-ai-commons`; no Markdown reader dependency is needed.
- Ollama embedding autoconfiguration binds `spring.ai.ollama.embedding.model`, and
  `spring.ai.ollama.init.pull-model-strategy` defaults to `NEVER`.

### Retrieval settings

`SearchRequest.builder().topK(4).similarityThreshold(0.5).build()`, tuned during
implementation against the demo questions. Both numbers live in `KnowledgeBaseConfig` as
named constants so tuning is one edit in one place.

### Grounding

The system message given to `AIOrchestrator` instructs the assistant to answer only from
the supplied Harborlight Systems documents, to name the source file it used, and to say
plainly that the handbook does not cover it rather than inventing an answer. The fallback
behaviour is part of the demo: asking "what is the bonus scheme?" should produce an honest
miss, which is what separates a grounded assistant from a confident one.

## Dependencies and configuration

One new dependency, version managed by the Spring AI BOM already in `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vector-store-advisor</artifactId>
</dependency>
```

It brings `spring-ai-vector-store` (and therefore `SimpleVectorStore`) transitively.

New properties in `application.properties`:

```properties
spring.ai.ollama.embedding.model=nomic-embed-text
spring.ai.ollama.init.pull-model-strategy=when_missing
app.rag.ingest-on-startup=true
```

`nomic-embed-text` is a ~270 MB pull. The `when_missing` pull strategy makes the first
start fetch it automatically; the README will still document `ollama pull nomic-embed-text`
for anyone who prefers to control that, and will warn that the first start is slower.

The chat model is unchanged (`qwen3`) and must still support tool calling, because
`AIOrchestrator` registers `get_session_context`. The embedding model has no such
requirement.

## UI

`KnowledgeChatView`, package-private, in `io.github.avec112.rag.ui`:

- `@Route("knowledge")`, `@PageTitle("Knowledge Base")`,
  `@Menu(order = 1, icon = "vaadin:book", title = "Knowledge Base")`.
- Same layout as `AiChatView`: `ViewTitle`, an expanding `MessageList` with Markdown on,
  and a full-width `MessageInput`.
- Component fields stay package-private and `final`, per the project's testing convention.
- A short caption under the title names the company whose documents are loaded, so the
  demo explains itself without narration.

`AiChatView` is not modified. `MainLayout` and navigation need no change — `@Menu` is
enough.

## Testing

The point of the tests is retrieval mechanics, not model output, so nothing in
`mvn test` may contact Ollama.

1. `KnowledgeDocumentReaderTest` — plain unit test over a fixture file: sections are split
   on `##`, metadata carries `source`, `title` and `section`, preamble before the first
   `##` is kept, and no chunk exceeds the character cap.
2. `KnowledgeBaseIngestorTest` — unit test with a deterministic fake `EmbeddingModel`
   (stable pseudo-random vectors derived from the text) feeding a real `SimpleVectorStore`.
   Asserts all five files are ingested, the section count is what the corpus contains, and
   a similarity search returns the section a planted fact lives in.
3. `KnowledgeChatViewTest` — extends `SpringBrowserlessTest`, navigates to the view and
   asserts the title and input render. No message is sent.

`src/test/resources/application.properties` is added with `app.rag.ingest-on-startup=false`
and `spring.ai.ollama.init.pull-model-strategy=never`, so the Spring context in every test
— including the existing `TaskListViewTest` and `TaskServiceTest` — boots without touching
Ollama. This is the one change that could break existing green tests if omitted.

## Documentation

A new README section, "Ask the knowledge base", placed after the model-selection section:
what RAG does here in three sentences, the embedding model requirement, and a demo script
of three questions to ask in `/chat-bot` and then in `/knowledge` for contrast. CLAUDE.md
gains a short note about the `rag` package and the corpus location.

## Risks

- **Advisors through `SpringAILLMProvider`.** The design assumes `SpringAILLMProvider`
  calls the injected `ChatClient` in a way that preserves its default advisors. This is
  the first thing implementation verifies, before any corpus is written. If advisors are
  dropped, the fallback is to retrieve explicitly in the view's provider wrapper and pass
  the retrieved sections in the system message — more code, same demo, no change to the
  corpus or the tests.
- **Similarity threshold.** Too high and good questions retrieve nothing; too low and
  every question retrieves noise. Tuned against the demo questions, with the constants
  kept together for a one-line adjustment.
- **In-memory store.** Re-embedding on every restart costs a few seconds for this corpus.
  Acceptable for a demo; swapping in pgvector later is one bean.
