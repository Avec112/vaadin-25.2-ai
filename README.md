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
ollama pull mistral
ollama serve
```

Then in `src/main/resources/application.properties`:

```properties
spring.ai.model.chat=ollama
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.model=mistral
```

If you leave `spring.ai.ollama.chat.model` unset, Spring AI falls back to **`mistral`** (`OllamaChatOptions` defaults to `OllamaModel.MISTRAL`), which is why the chat works out of the box once you have that model pulled.

> **The model must support tool calling.** `AIOrchestrator` registers a built-in `get_session_context` tool — if you don't call `withMetadata(...)`, `build()` installs a default context supplier, so the tool is sent on every request. Models without tool support reject it:
>
> ```
> 400 Bad Request from POST http://localhost:11434/api/chat
> registry.ollama.ai/library/deepseek-r1:latest does not support tools
> ```
>
> **`deepseek-r1` does not work with this chat view** for that reason, even though `ollama run deepseek-r1` works fine in a terminal. Pick a tool-capable model such as `mistral` or `llama3.1:8b`. Check with `ollama show <model>` — the capabilities list must include `tools`.
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
