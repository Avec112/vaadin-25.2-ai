# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

Spring Boot 4.1.1 + Vaadin 25.2.8 (Flow, server-side Java UI) on Java 25, with Hibernate/JPA over an auto-configured in-memory H2 database. Generated from start.vaadin.com.

## Commands

The Maven wrapper (`mvnw`) has been deleted from this tree even though README.md still references it — use the system `mvn`.

```bash
mvn                                  # defaultGoal is spring-boot:run — dev mode on :8080, opens a browser
mvn test                             # all tests
mvn test -Dtest=TaskListViewTest     # one test class
mvn test -Dtest='TaskListViewTest#create_task_with_due_date'   # one test method
mvn package && java -jar target/*.jar # production build (frontend bundle included)
```

There is **no `production` profile**: `vaadin-maven-plugin:build-frontend` is bound unconditionally, so a plain `mvn package` already produces the optimized bundle.

## Use the Vaadin MCP server

`.mcp.json` wires up the Vaadin docs MCP server, and it is enabled in `.claude/settings.local.json`. Vaadin 25.2 ships API newer than model training data. Before writing against a component or asserting that a method does not exist, call `get_vaadin_primer` / `get_new_apis` / `get_java_symbol` for version `25.2` rather than relying on recall.

## Architecture

**Package-by-feature.** Each feature is a top-level package under `io.github.avec112` containing its entity, repository, and service, with UI in a nested `ui` subpackage (`examplefeature/` + `examplefeature/ui/`). `base/ui/` holds cross-cutting UI (`MainLayout`, `ViewTitle`). `examplefeature` is scaffolding meant to be deleted once real features exist.

**RAG feature.** `rag/` holds the knowledge base: `KnowledgeDocumentReader` splits
`src/main/resources/knowledge/*.md` into one document per `##` section, `KnowledgeBaseIngestor`
embeds them into an in-memory `SimpleVectorStore` on `ApplicationReadyEvent`, and
`KnowledgeChatClientFactory` builds a `ChatClient` with a `QuestionAnswerAdvisor` — one per view
instance, because the client owns the conversation memory. Ingestion is off in tests via
`app.rag.ingest-on-startup=false` in `src/test/resources/application.properties`; keep it that way,
because `mvn test` must never contact Ollama.

**Encapsulation boundary.** Repositories and their `@Transactional` boundaries stay package-private (`TaskRepository`); the `@Service` is the only public entry point; views take it via constructor injection. Views themselves are package-private classes.

**Routing and navigation are convention-driven.** `MainLayout` carries `@Layout`, so it wraps every route automatically — no per-view `layout =` attribute. The side nav is built from `MenuConfiguration.getMenuEntries()`, so annotating a view with `@Menu(order, icon, title)` is all that is needed for it to appear in the drawer. Icons ending in `.svg` resolve to files under `src/main/resources/META-INF/resources/icons/`.

**Nullness.** Every package has a `package-info.java` with JSpecify `@NullMarked`; fields and returns that may be null carry an explicit `@Nullable`. New packages need their own `package-info.java`.

**Grid data access.** Grids lazy-load through `setItems(query -> service.list(toSpringPageRequest(query)).stream())`, and repositories return `Slice` rather than `Page` to avoid the extra count query.

**Frontend.** `src/main/frontend/generated/` is entirely tool-generated and gitignored — never edit it. There are currently no hand-written TypeScript/React views, though `hilla-spring-boot-starter` is on the classpath if you add them.

**Styling.** Plain CSS files live in `src/main/resources/META-INF/resources/` and are attached with `@StyleSheet` (globally on `Application`, or per-component as on `ViewTitle`). Lumo is the theme; prefer its custom properties (`--lumo-*`, `--vaadin-*`) over hard-coded values.

## Testing

UI tests extend `com.vaadin.browserless.SpringBrowserlessTest` with `@SpringBootTest(webEnvironment = MOCK)` and `@Transactional` — full view interaction at unit-test speed, no browser or Selenium. The idiom is `navigate(SomeView.class)`, then `test(component).click()/.setValue()/.getCellText()`, and `$(Notification.class)` to query rendered components.

This is why view component fields are `final` but package-private rather than private: tests reach them directly. Keep that shape when adding views.

## Configuration notes

- `vaadin.allowed-packages` in `application.properties` lists the packages Vaadin scans; add new top-level base packages there or component scanning slows down / misses them.
- `spring.jpa.hibernate.ddl-auto=update` is fine for this scaffold but is called out in the file as unsuitable for production — Flyway is the intended replacement.
- `@Push` is enabled on `Application`, so server-initiated UI updates work, but background-thread updates still need `ui.access(...)`.
