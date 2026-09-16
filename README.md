# My Application

A Spring Boot + Vaadin project. Build your UI in pure Java — no HTML, no JavaScript.

> **New to Vaadin?** The 5-minute [Quickstart](https://vaadin.com/quickstart) walks you from here to your first running app, a live code change, and an AI-assisted edit with Copilot.

---

## Fastest start — no plugin needed

From the project folder:

```bash
mvn spring-boot:run
```

Maven 3.9+ and JDK 25 are required (the Maven wrapper is not included in this project). The app opens **http://localhost:8080** in your browser automatically; disable that with `vaadin.launch-browser=false` in `src/main/resources/application.properties`.

The first start takes ~30 seconds while Maven downloads dependencies. The start view is a **Chat Bot** — see [Choose your AI model](#choose-your-ai-model--local-or-cloud) below before you expect it to answer. The side nav also has a **Task List** demo at `/task-list`: a data grid (Description / Due Date / Creation Date), a Create button, and an empty-state message. When you see the nav drawer and those two views, you're running.

> **Port 8080 already in use?** Stop the other process, or set `server.port=8081` in `src/main/resources/application.properties` and open that port instead.
>
> **To stop the app:** press `Ctrl+C` in the terminal (or the red Stop button if you launched from your IDE).

---

## Choose your AI model — local or cloud

The start view of this app is a **Chat Bot** backed by [Spring AI](https://docs.spring.io/spring-ai/reference/). Before it can answer anything you have to tell it *which* model to talk to, and that choice is made in `src/main/resources/application.properties`.

The switch is the `spring.ai.model.chat` property: it decides which Spring AI auto-configuration is activated, and therefore which `ChatModel` bean gets injected into `AiChatView`.

### Option A — local model (Ollama)

This is what the project ships with. Install [Ollama](https://ollama.com/), pull a model, and keep it running:

```bash
ollama pull qwen3
ollama serve
```

Then in `src/main/resources/application.properties`:

```properties
spring.ai.model.chat=ollama
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.model=qwen3
```

**Where do the model names come from?** Not from any Java class. The string is passed verbatim to Ollama's REST API, so the valid values are exactly what `ollama list` prints — name plus tag, e.g. `mistral:latest` or `llama3.1:8b`. Dropping the tag works when `:latest` exists, so `mistral` is fine. Pull more from [ollama.com/library](https://ollama.com/library).

> **Two lists that are easy to confuse:**
>
> - `org.springframework.ai.model.SpringAIModels` lists **provider ids** — `ollama`, `openai`, `anthropic`, `deepseek` — and those are the values for `spring.ai.model.chat`. They are *not* model names. `SpringAIModels.DEEPSEEK` means "DeepSeek's own cloud API as a provider", not a model you can name here.
> - `org.springframework.ai.ollama.api.OllamaModel` holds convenience constants (`mistral`, `llama3.2`, `qwq`, …) but is **not** a whitelist — nothing validates against it. `deepseek-r1` is absent from that enum yet works fine as a model name.
>
> A wrong name is not rejected by Spring; Ollama answers `model '<name>' not found`.

If you leave `spring.ai.ollama.chat.model` unset, Spring AI falls back to **`mistral`** (`OllamaChatOptions` defaults to `OllamaModel.MISTRAL`), which is why the chat works out of the box once you have that model pulled.

> **The model must support tool calling.** `AIOrchestrator` registers a built-in `get_session_context` tool — if you don't call `withMetadata(...)`, `build()` installs a default context supplier, so the tool is sent on every request. Models without tool support reject it:
>
> ```
> 400 Bad Request from POST http://localhost:11434/api/chat
> registry.ollama.ai/library/deepseek-r1:latest does not support tools
> ```
>
> **`deepseek-r1` does not work with this chat view** for that reason, even though `ollama run deepseek-r1` works fine in a terminal. Check any model with `ollama show <model>` — the capabilities list must include `tools`:
>
> | Model | Capabilities | Works in this chat view |
> |---|---|---|
> | `qwen3` | completion, tools, thinking | yes |
> | `mistral` | completion, tools | yes |
> | `llama3.1:8b` | completion, tools | yes |
> | `deepseek-r1` | completion, thinking | **no** — 400 Bad Request |
>
> Note that `qwen3` and `deepseek-r1` are both reasoning models that think before answering. They differ only in `tools`, which is what decides whether they work here — the thinking phase was never the problem.
>
> This is a Vaadin `AIOrchestrator` requirement, **not** a Spring AI one — Spring AI itself talks to tool-less models happily. To use such a model, opt out of the session context tool when building the orchestrator:
>
> ```java
> AIOrchestrator.builder(provider, "You are a helpful assistant.")
>         .withMetadata(() -> null)   // no get_session_context tool
>         .withMessageList(messageList)
>         .withInput(messageInput)
>         .build();
> ```

Free, private, and offline — nothing leaves your machine.

### Option B — cloud model (OpenAI)

Cloud providers need a **dependency change as well as a property change**. Swap the Ollama starter in `pom.xml` for the OpenAI one:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

Then in `src/main/resources/application.properties`:

```properties
spring.ai.model.chat=openai
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.model=gpt-5
```

Export the key in your shell rather than writing it into the file — that way it never ends up in Git:

```bash
export OPENAI_API_KEY=sk-...
```

> **Keeping both starters on the classpath?** Then `spring.ai.model.chat` is mandatory. Both auto-configurations match when the property is absent, you end up with two `ChatModel` beans, and startup fails because the injection into `AiChatView` is ambiguous.

> The AI components used by the chat view are still experimental, so they are enabled through `src/main/resources/vaadin-featureflags.properties` (`com.vaadin.experimental.aiComponents=true`). Leave that flag in place.

---

## Ask the knowledge base (RAG)

The **Knowledge Base** view at `/knowledge` answers questions about a fictional company,
**Harborlight Systems Inc.**, whose handbook, time-off policy, IT security policy, travel rules and
onboarding guide live in `src/main/resources/knowledge/`. At startup each `##` section of those files
is embedded and stored in an in-memory vector store. When you ask a question, the eight most similar
sections are retrieved and placed in the prompt before the model answers — retrieval-augmented
generation, with no fine-tuning and no data leaving your machine.

This needs an embedding model in addition to the chat model, and **you must pull it yourself first**:

```bash
ollama pull nomic-embed-text
```

`spring.ai.ollama.init.pull-model-strategy=never` in `application.properties`, deliberately: the
alternative, `when_missing`, runs its model-existence check during bean initialization with no error
handling of its own, so with Ollama unreachable it throws at startup and takes the whole app down —
every view, not just the AI ones. Ingestion was confirmed against a real, locally running Ollama in
this environment: startup logged `Indexed 31 sections from 5 documents in 1807 ms`.

**The demo.** Ask the same question in `/chat-bot` and in `/knowledge`. The company is invented, so no
model can know these answers from pretraining — a correct, sourced answer is proof the retrieval
happened. The table below was captured by driving each view's `ChatClient` directly (the same beans
the UI uses) against a real, locally running `qwen3` and `nomic-embed-text`:

| Question | `/chat-bot` | `/knowledge` |
|---|---|---|
| How many vacation days do employees get? | Hedges, then guesses "between 10–20 vacation days per year" | **27** vacation days per calendar year (source: time-off-policy.md) |
| What is the VPN called? | Hedges: guesses it might be "a typo, a fictional reference, or a less-known provider" | **Lighthouse** (source: it-security-policy.md) |
| What is the mileage rate? | Quotes the 2023 US IRS public mileage rate (58.5 cents/mile) instead of Harborlight's own rate | **USD 0.62 per mile** (source: travel-and-expenses.md) |
| What is the annual bonus scheme? | *(not asked — this question is only exercised in `/knowledge`)* | Says the documents do not specify one and suggests asking HR or your team lead |

All four `/knowledge` answers are exactly what this demo is meant to show: a specific, sourced figure
the base model could not know, or an honest admission that the documents don't say. The citations are
literal filenames, matching the system prompt's `(source: time-off-policy.md)` example exactly — see
`KnowledgeDocumentReader`, which embeds the filename directly into each chunk's text so the model has
something to cite (`QuestionAnswerAdvisor` sends only `Document::getText` to the model; the source
metadata by itself never reaches the prompt).

**Questions about the corpus itself need tools, not retrieval.** Similarity search only ever attaches
the sections closest to the question asked, so "which documents do you have?" used to be answered from
whatever handful of excerpts happened to be attached — the assistant would confidently report three
documents when there are five. No amount of tuning fixes that: the other two files were never in front
of it. `KnowledgeTools` therefore registers two tools on the chat client:

- `list_documents` — every document with its title and section headings.
- `read_document(fileName)` — one whole document, for questions that need the full text rather than
  excerpts, such as summarising it.

**Tools alone were not enough, because calling them is the model's decision.** With only the tools
wired up, `qwen3` answered the listing question correctly when asked cold, but skipped the tool on a
follow-up — any question that looks answerable from the excerpts already in the window does — and then
reported four of the five documents, denying that `time-off-policy.md` existed at all. The excerpts it
held simply came from four files.

So the document list is now in **every** request: `KnowledgeChatClientFactory.systemPrompt()` appends
the manifest to the grounding prompt, rendering it with the same `list_documents` tool the model can
call, so the two can never disagree. The prompt states that the list is authoritative — a document on
it exists even when no excerpt from it is attached. `read_document` stays a tool because whole
documents are far too large to sit in every prompt; only the inventory is small enough to always
include, at roughly 250 tokens per request.

Verified against a real `qwen3`, in one conversation, in this order: "What is the VPN called?" →
Lighthouse (source: it-security-policy.md); "Which documents do you have?" → all five files with all
31 sections; "Is time-off-policy.md part of the knowledge base?" → "Yes", with an accurate summary of
its six sections. That third question is the one that previously got a denial.

Note this makes tool support a hard requirement for the chat model — though `AIOrchestrator` already
required it for its own `get_session_context` tool, so the set of usable models is unchanged.

One prompt detail earned its place: without an explicit instruction to reproduce the listing in full,
`qwen3` abbreviated it to three or four sections per document and blended it with the retrieved
excerpts. The system prompt now says not to shorten the list or merge it with the excerpts.

**Tuning.** `TOP_K` and `SIMILARITY_THRESHOLD` in
`src/main/java/io/github/avec112/rag/KnowledgeChatClientFactory.java` control how much context is
retrieved. Too high a threshold and good questions retrieve nothing; too low and every question
retrieves noise. Both were tuned against the demo questions above:

- `TOP_K` is `8`, not the original `4`. With `TOP_K = 4`, the VPN question failed: direct inspection of
  the vector store showed `it-security-policy.md`'s `## VPN Access` section ranks 7th of 31 by cosine
  similarity for "What is the Harborlight VPN called?" (score ≈ 0.50), so a window of 4 never reached
  it regardless of `SIMILARITY_THRESHOLD` — threshold only prunes *within* the selected window, it
  never enlarges it. Widening to 8 reaches rank 7 and fixed the VPN question, and the other three
  answers (including the bonus-scheme honest miss) held with no regressions.
- `SIMILARITY_THRESHOLD` is `0.3`, lowered from `0.5`. On this corpus it is currently inert: the
  sections that answer a question score 0.48–0.83, and nothing tested has been excluded by either
  value. It is kept low deliberately, as headroom for questions whose best match scores poorly — the
  awkward phrasings below land near 0.48.

**Measured and rejected: Nomic's task prefixes.** `nomic-embed-text` is trained with a task
instruction in front of its input (`search_document:` for stored passages, `search_query:` for the
question), and the obvious guess is that adding them would sharpen retrieval enough to bring `TOP_K`
back down to 4. It was implemented and measured against the live model, comparing the rank of the
correct section with and without prefixes:

| Query | Without | With |
|---|---|---|
| How many vacation days do employees get? | rank 1 (0.7901) | rank 1 (0.8283) |
| What is the mileage reimbursement rate? | rank 1 (0.7647) | rank 1 (0.8093) |
| How long is parental leave? | rank 1 (0.7909) | rank 1 (0.8286) |
| What is the hardware allowance? | rank 1 (0.7611) | rank 1 (0.8185) |
| What is the VPN called? | rank 3 (0.4819) | **rank 8** (0.5452) |

Prefixes lift every score by roughly 0.04–0.06 but change no rank that mattered, and they pushed the
one weak query from rank 3 to rank 8 — the last slot inside the window. Net negative, so the code was
removed rather than kept behind a flag.

**What actually explains the weak query: phrasing, not prefixes.** The same section, same corpus,
three phrasings:

| Query | Rank of `it-security-policy.md — VPN Access` |
|---|---|
| "What is the VPN **called**?" | 3 (top hit: `employee-handbook.md — Job Levels`) |
| "**Which VPN do we use for remote access?**" | **1** (0.6099) |
| "**VPN access**" | **1** (0.5622) |

`nomic-embed-text` handles descriptive queries well and "what is X called?" badly — that naming
phrasing pulls toward the Job Levels section, which is dense with titles and names. This is a
property of the small embedding model, and it is the real reason `TOP_K` is 8 rather than 4: the
window has to be wide enough to survive an awkwardly phrased question. A larger embedding model, or
query rewriting before retrieval, would be the next thing to try — not prefixes.

**Adding your own documents.** Drop a Markdown file with `#` and `##` headings into
`src/main/resources/knowledge/` and restart. Each `##` section becomes one retrievable chunk.

---

## Ask your AI assistant about Vaadin (optional)

If you use Claude Code, Cursor, or another AI coding assistant, connect it to the **Vaadin MCP server** so it answers against real Vaadin docs and the exact API of your installed version — instead of guessing from outdated training data.

```bash
# One-time setup — see https://vaadin.com/docs/latest/building-apps/mcp
```

A `.mcp.json` with the Vaadin docs server is already included and active in this project — no setup needed for Claude Code.

---

## Build for production

```bash
mvn package
java -jar target/*.jar
```

## Learn more

- [Vaadin Quickstart](https://vaadin.com/quickstart) — the 5-minute getting-started path
- [Components](https://vaadin.com/docs/latest/components) — 50+ UI components, all callable from Java
- [Vaadin Copilot](https://vaadin.com/docs/latest/tools/copilot) — visual + AI editing in the browser
- [Full documentation](https://vaadin.com/docs)
