# RAG Knowledge Base Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a second chat view that answers questions about a fictional company by retrieving from its documents, so the app demonstrably looks facts up instead of guessing.

**Architecture:** Markdown documents in `src/main/resources/knowledge/` are split into one `Document` per `##` section, embedded through Ollama at startup, and stored in an in-memory `SimpleVectorStore`. A `QuestionAnswerAdvisor` on a per-view `ChatClient` retrieves the top matching sections and injects them into the prompt. The `ChatClient` goes into Vaadin's `SpringAILLMProvider`, so `AIOrchestrator` wiring stays identical to the existing `AiChatView`.

**Tech Stack:** Java 25, Spring Boot 4.1.1, Vaadin 25.2.8 (Flow + `vaadin-ai-components-flow`), Spring AI 2.0.1 (Ollama chat + embeddings, `SimpleVectorStore`, `QuestionAnswerAdvisor`), JUnit 5 + AssertJ + `SpringBrowserlessTest`.

**Spec:** `docs/superpowers/specs/2026-09-16-rag-knowledge-base-design.md`

## Global Constraints

- The fictional company is named exactly **Harborlight Systems Inc.** Never "AS", never any other suffix.
- All content — code, comments, documents, docs, commit messages — is in English.
- Build with the system `mvn`. There is no Maven wrapper in this tree and no `production` profile.
- Spring AI versions come from the BOM already in `pom.xml`. Never write an explicit `<version>` for a `org.springframework.ai` dependency.
- Every new package gets a `package-info.java` with JSpecify `@NullMarked`. Values that can be null carry an explicit `@Nullable`.
- Views are package-private classes. Component fields are `final` but package-private, because tests reach them directly.
- `mvn test` must never contact Ollama. No test sends a chat message or embeds through the real `OllamaEmbeddingModel`.
- Do not modify `src/main/java/io/github/avec112/ai/AiChatView.java`. It is the control case for the demo.
- Never edit anything under `src/main/frontend/generated/`.
- `@Menu` is all that navigation needs; `MainLayout` carries `@Layout` and picks views up automatically.

## API facts verified against the resolved jars

These were checked with `javap` and the Vaadin sources jar before this plan was written. Trust them over recall:

- `SpringAILLMProvider` has constructors `(ChatModel)` and `(ChatClient)`. Its `getPromptSpec` calls `chatClient.prompt()`, so **default advisors on the `ChatClient` are preserved**, and the orchestrator's system prompt arrives via `.system(...)` per request.
- With the `(ChatClient)` constructor, `hasManagedMemory` is `false`: the provider adds **no** chat memory and does **not** set the `ChatMemory.CONVERSATION_ID` advisor param. `BaseChatMemoryAdvisor.getConversationId` asserts that param is present, so the `ChatClient` must supply both the memory advisor and the param itself. Task 4 does this.
- `SimpleVectorStore.builder(EmbeddingModel)` → `SimpleVectorStoreBuilder` → `build()`.
- `QuestionAnswerAdvisor.builder(VectorStore).searchRequest(SearchRequest).build()`, in artifact `spring-ai-vector-store-advisor`, package `org.springframework.ai.chat.client.advisor.vectorstore`.
- `spring-ai-vector-store` is **not** on the classpath today; it arrives transitively with the advisor artifact added in Task 3.
- Ollama embedding autoconfiguration binds `spring.ai.ollama.embedding.model`; `spring.ai.ollama.init.pull-model-strategy` defaults to `NEVER`.
- `MessageWindowChatMemory.builder().maxMessages(int).build()` and `MessageChatMemoryAdvisor.builder(ChatMemory).build()`.

## File Structure

**Create:**

| Path | Responsibility |
|---|---|
| `src/main/java/io/github/avec112/rag/package-info.java` | `@NullMarked` for the feature package |
| `src/main/java/io/github/avec112/rag/KnowledgeDocumentReader.java` | Resolve `knowledge/*.md`, split into one `Document` per `##` section with metadata |
| `src/main/java/io/github/avec112/rag/KnowledgeBaseIngestor.java` | Read and add to the `VectorStore` at startup; log what was indexed |
| `src/main/java/io/github/avec112/rag/KnowledgeChatClientFactory.java` | Build one retrieval `ChatClient` per view instance |
| `src/main/java/io/github/avec112/rag/KnowledgeBaseConfig.java` | `VectorStore` and factory beans; retrieval constants; system prompt |
| `src/main/java/io/github/avec112/rag/ui/package-info.java` | `@NullMarked` for the UI package |
| `src/main/java/io/github/avec112/rag/ui/KnowledgeChatView.java` | `@Route("knowledge")` chat view |
| `src/main/resources/knowledge/*.md` | The five corpus documents |
| `src/test/resources/application.properties` | Keeps every test off the network |
| `src/test/resources/fixtures/sample-policy.md` | Reader fixture |
| `src/test/java/io/github/avec112/rag/*` | Tests and the deterministic fake embedding model |

**Modify:** `pom.xml` (one dependency), `src/main/resources/application.properties` (three properties), `README.md`, `CLAUDE.md`.

---

### Task 1: Markdown section reader

**Files:**
- Create: `src/main/java/io/github/avec112/rag/package-info.java`
- Create: `src/main/java/io/github/avec112/rag/KnowledgeDocumentReader.java`
- Create: `src/test/resources/fixtures/sample-policy.md`
- Test: `src/test/java/io/github/avec112/rag/KnowledgeDocumentReaderTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `public final class KnowledgeDocumentReader`
  - `public static final String METADATA_SOURCE = "source"`, `METADATA_TITLE = "title"`, `METADATA_SECTION = "section"`
  - `public static final int MAX_SECTION_CHARS = 2000`
  - `public KnowledgeDocumentReader(String locationPattern)`
  - `public List<Document> read()`
  - `static List<Document> parse(String fileName, String markdown)`

Why one document per `##` section rather than token windows: each section is one self-contained policy, the documents are small, and the section heading becomes both a retrieval signal and the citation the model can name.

- [ ] **Step 1: Write the fixture**

Create `src/test/resources/fixtures/sample-policy.md`:

```markdown
# Sample Policy

This preamble explains the document.

## First Section

First section body.

## Second Section

Second section body.
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/io/github/avec112/rag/KnowledgeDocumentReaderTest.java`:

```java
package io.github.avec112.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeDocumentReaderTest {

    private static final String MARKDOWN = """
            # Sample Policy

            This preamble explains the document.

            ## First Section

            First section body.

            ## Second Section

            Second section body.
            """;

    @Test
    void parses_preamble_and_each_heading_into_its_own_document() {
        var documents = KnowledgeDocumentReader.parse("sample-policy.md", MARKDOWN);

        assertThat(documents).hasSize(3);
        assertThat(documents).extracting(d -> d.getMetadata().get(KnowledgeDocumentReader.METADATA_SECTION))
                .containsExactly("Overview", "First Section", "Second Section");
    }

    @Test
    void every_document_carries_source_title_and_section_metadata() {
        var documents = KnowledgeDocumentReader.parse("sample-policy.md", MARKDOWN);

        assertThat(documents).allSatisfy(document -> {
            assertThat(document.getMetadata().get(KnowledgeDocumentReader.METADATA_SOURCE)).isEqualTo("sample-policy.md");
            assertThat(document.getMetadata().get(KnowledgeDocumentReader.METADATA_TITLE)).isEqualTo("Sample Policy");
            assertThat(document.getMetadata().get(KnowledgeDocumentReader.METADATA_SECTION)).asString().isNotEmpty();
        });
    }

    @Test
    void document_text_repeats_title_and_section_so_the_chunk_is_self_describing() {
        var documents = KnowledgeDocumentReader.parse("sample-policy.md", MARKDOWN);

        assertThat(documents.get(1).getText())
                .startsWith("Sample Policy — First Section")
                .contains("First section body.");
    }

    @Test
    void sections_without_body_are_skipped() {
        var documents = KnowledgeDocumentReader.parse("empty.md", "# Title\n\n## Empty\n\n## Real\n\nBody.\n");

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getMetadata().get(KnowledgeDocumentReader.METADATA_SECTION)).isEqualTo("Real");
    }

    @Test
    void reads_every_markdown_file_matching_the_location_pattern() {
        var reader = new KnowledgeDocumentReader("classpath:fixtures/*.md");

        var documents = reader.read();

        assertThat(documents).isNotEmpty();
        assertThat(documents).allSatisfy(document ->
                assertThat(document.getMetadata().get(KnowledgeDocumentReader.METADATA_SOURCE)).isEqualTo("sample-policy.md"));
    }
}
```

- [ ] **Step 3: Run the test and verify it fails**

Run: `mvn test -Dtest=KnowledgeDocumentReaderTest`
Expected: FAIL — compilation error, `KnowledgeDocumentReader` does not exist.

- [ ] **Step 4: Write `package-info.java`**

Create `src/main/java/io/github/avec112/rag/package-info.java`:

```java
@NullMarked
package io.github.avec112.rag;

import org.jspecify.annotations.NullMarked;
```

- [ ] **Step 5: Write the reader**

Create `src/main/java/io/github/avec112/rag/KnowledgeDocumentReader.java`:

```java
package io.github.avec112.rag;

import org.springframework.ai.document.Document;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads Markdown documents and splits them into one {@link Document} per {@code ##} section.
 * Content before the first {@code ##} heading becomes an "Overview" section.
 */
public final class KnowledgeDocumentReader {

    public static final String METADATA_SOURCE = "source";
    public static final String METADATA_TITLE = "title";
    public static final String METADATA_SECTION = "section";

    /** A section longer than this is a signal the corpus needs splitting, not a reason to token-chunk. */
    public static final int MAX_SECTION_CHARS = 2000;

    private static final String OVERVIEW_SECTION = "Overview";

    private final String locationPattern;

    public KnowledgeDocumentReader(String locationPattern) {
        this.locationPattern = locationPattern;
    }

    public List<Document> read() {
        var resolver = new PathMatchingResourcePatternResolver();
        var documents = new ArrayList<Document>();
        try {
            for (var resource : resolver.getResources(locationPattern)) {
                var fileName = Objects.requireNonNull(resource.getFilename(),
                        () -> "Resource without a file name: " + resource);
                documents.addAll(parse(fileName, resource.getContentAsString(StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read knowledge documents from " + locationPattern, e);
        }
        return documents;
    }

    static List<Document> parse(String fileName, String markdown) {
        var lines = markdown.lines().toList();
        var title = lines.stream()
                .filter(line -> line.startsWith("# "))
                .findFirst()
                .map(line -> line.substring(2).strip())
                .orElse(fileName);

        var documents = new ArrayList<Document>();
        var section = OVERVIEW_SECTION;
        var body = new StringBuilder();
        for (var line : lines) {
            if (line.startsWith("## ")) {
                addSection(documents, fileName, title, section, body);
                section = line.substring(3).strip();
                body.setLength(0);
            } else if (!line.startsWith("# ")) {
                body.append(line).append('\n');
            }
        }
        addSection(documents, fileName, title, section, body);
        return documents;
    }

    private static void addSection(List<Document> documents, String fileName, String title, String section,
                                   StringBuilder body) {
        var text = body.toString().strip();
        if (text.isEmpty()) {
            return;
        }
        var content = "%s — %s%n%n%s".formatted(title, section, text);
        documents.add(new Document(content, Map.of(
                METADATA_SOURCE, fileName,
                METADATA_TITLE, title,
                METADATA_SECTION, section)));
    }
}
```

- [ ] **Step 6: Run the test and verify it passes**

Run: `mvn test -Dtest=KnowledgeDocumentReaderTest`
Expected: PASS, 5 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/avec112/rag src/test/java/io/github/avec112/rag src/test/resources/fixtures
git commit -m "Add Markdown section reader for the knowledge corpus"
```

---

### Task 2: The Harborlight Systems corpus

**Files:**
- Create: `src/main/resources/knowledge/employee-handbook.md`
- Create: `src/main/resources/knowledge/time-off-policy.md`
- Create: `src/main/resources/knowledge/it-security-policy.md`
- Create: `src/main/resources/knowledge/travel-and-expenses.md`
- Create: `src/main/resources/knowledge/onboarding-guide.md`
- Test: `src/test/java/io/github/avec112/rag/KnowledgeCorpusTest.java`

**Interfaces:**
- Consumes: `KnowledgeDocumentReader.read()`, `MAX_SECTION_CHARS`, the three metadata keys.
- Produces: the corpus at `classpath:knowledge/*.md`. Later tasks and the README demo script depend on these exact facts: 27 vacation days, 3 Harborlight Days, USD 2,600 hardware allowance every 36 months, Lighthouse VPN, Harbor Key, 18 weeks parental leave, First Mate buddy program, USD 68 per diem, USD 0.62 per mile, Anchor Days on Tuesday and Thursday.

Every fact is stated in exactly one document, so a correct answer identifies a specific retrieval rather than a lucky paraphrase.

- [ ] **Step 1: Write the corpus guard test**

Create `src/test/java/io/github/avec112/rag/KnowledgeCorpusTest.java`:

```java
package io.github.avec112.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeCorpusTest {

    private final List<Document> corpus = new KnowledgeDocumentReader("classpath:knowledge/*.md").read();

    @Test
    void every_corpus_file_is_present() {
        assertThat(corpus).extracting(d -> d.getMetadata().get(KnowledgeDocumentReader.METADATA_SOURCE))
                .containsOnly("employee-handbook.md", "time-off-policy.md", "it-security-policy.md",
                        "travel-and-expenses.md", "onboarding-guide.md");
    }

    @Test
    void corpus_has_enough_sections_to_make_retrieval_meaningful() {
        assertThat(corpus).hasSizeGreaterThanOrEqualTo(20);
    }

    @Test
    void no_section_exceeds_the_chunk_cap() {
        assertThat(corpus).allSatisfy(document ->
                assertThat(document.getText()).hasSizeLessThanOrEqualTo(KnowledgeDocumentReader.MAX_SECTION_CHARS));
    }

    @Test
    void the_company_is_named_consistently() {
        assertThat(corpus).allSatisfy(document -> assertThat(document.getText()).doesNotContain("Harborlight Systems AS"));
        assertThat(corpusText()).contains("Harborlight Systems Inc.");
    }

    @Test
    void each_demo_fact_appears_in_exactly_one_section() {
        assertThatFactIsUnique("27 vacation days");
        assertThatFactIsUnique("USD 2,600");
        assertThatFactIsUnique("Lighthouse");
        assertThatFactIsUnique("18 weeks of parental leave");
        assertThatFactIsUnique("First Mate");
        assertThatFactIsUnique("USD 0.62 per mile");
    }

    private void assertThatFactIsUnique(String fact) {
        assertThat(corpus.stream().filter(d -> d.getText() != null && d.getText().contains(fact)).toList())
                .as("fact '%s' must appear in exactly one section", fact)
                .hasSize(1);
    }

    private String corpusText() {
        return corpus.stream().map(Document::getText).reduce("", (a, b) -> a + "\n" + b);
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `mvn test -Dtest=KnowledgeCorpusTest`
Expected: FAIL — no documents found, so `every_corpus_file_is_present` fails on an empty list.

- [ ] **Step 3: Write `employee-handbook.md`**

Create `src/main/resources/knowledge/employee-handbook.md`:

```markdown
# Harborlight Systems Employee Handbook

Harborlight Systems Inc. is a privately held maritime logistics software company founded in 2014.
The company has 184 employees and is headquartered in Portland, Maine, with a second office in Tampere, Finland.

## Working Hours

Core hours are 10:00 to 15:00 in each employee's local time zone. Outside core hours, employees choose
their own schedule as long as the weekly total is 37.5 hours. Overtime is approved in advance by the
team lead and compensated as time off in lieu at a 1:1 rate within the following quarter.

## Anchor Days

Harborlight runs a hybrid model. Tuesday and Thursday are Anchor Days: everyone assigned to an office
works on site those two days. The remaining three days are the employee's choice. Anchor Days are
suspended for the last two weeks of December.

## Working From Abroad

Employees may work from outside their country of employment for up to 12 weeks per calendar year,
in stretches of no more than 4 consecutive weeks. Requests go to People Ops at least 21 days ahead,
because tax registration in the destination country can take that long.

## Job Levels

Engineering has six levels, from HL1 (Associate Engineer) to HL6 (Principal Engineer). Promotion
decisions are made twice a year, in March and September, by a panel of three engineers at or above
the target level. Compensation bands are published internally at the start of each calendar year.

## Company Meetings

The all-hands meeting, called Harbor Call, runs on the first Wednesday of each month at 15:00 ET and
is recorded. Team retrospectives are every second Friday. The annual company gathering, Landfall,
takes place in September and attendance is expected but not mandatory.
```

- [ ] **Step 4: Write `time-off-policy.md`**

Create `src/main/resources/knowledge/time-off-policy.md`:

```markdown
# Harborlight Systems Time Off Policy

This policy covers vacation, public holidays, parental leave, sick leave and sabbaticals for all
permanent employees of Harborlight Systems Inc.

## Vacation

Every permanent employee receives 27 vacation days per calendar year, accrued monthly at 2.25 days
per month. In addition, the company closes for 3 Harborlight Days each year: the Friday before
Memorial Day, the first Monday of August, and December 27. Harborlight Days do not count against
the vacation balance.

## Carryover

Up to 10 unused vacation days carry over into the next calendar year and must be used before
March 31, after which they expire. Carryover beyond 10 days requires written approval from a
department head and is granted only when a project deadline prevented the employee from taking leave.

## Parental Leave

Birthing and non-birthing parents alike receive 18 weeks of parental leave at full pay, to be taken
within 12 months of birth or adoption. A further 6 weeks may be taken at full pay on a flexible
schedule within 24 months, in blocks of no less than one week. Parental leave does not reduce
vacation accrual.

## Sick Leave

Sick leave is not capped and is not deducted from vacation. Absences of more than 5 consecutive
working days require a medical certificate sent to People Ops. Employees caring for a sick child
under 12 may use up to 10 family care days per year.

## Sabbatical

After 5 years of continuous employment, an employee may take a paid sabbatical of 6 weeks. The
sabbatical is granted once per 5-year period, is requested at least 3 months in advance, and cannot
be split into shorter periods or exchanged for pay.
```

- [ ] **Step 5: Write `it-security-policy.md`**

Create `src/main/resources/knowledge/it-security-policy.md`:

```markdown
# Harborlight Systems IT and Security Policy

This policy applies to every device and account that touches Harborlight Systems Inc. data,
including personal phones used for work email.

## Hardware Allowance

Each employee receives a hardware allowance of USD 2,600, renewed every 36 months, covering a laptop
and one peripheral of their choice. Monitors, chairs and desks are ordered separately through Facilities
and do not count against the allowance. Devices are company property and are returned when employment ends.

## Device Security

All laptops use full-disk encryption and lock after 5 minutes of inactivity. Installing software from
outside the managed catalog requires an IT ticket. Company data may not be stored on removable media.
Laptops are patched automatically; employees restart at least once a week so patches apply.

## VPN Access

Remote access to internal systems goes through the company VPN, called Lighthouse. Lighthouse is
required for the admin console, the staging environment and the customer database, and is not needed
for email, chat or the documentation site. Sessions expire after 12 hours.

## Multi-Factor Authentication

Every account uses multi-factor authentication. The standard second factor is a hardware key called
Harbor Key, issued on the first day. SMS codes are not accepted. A lost Harbor Key is reported to IT
within 2 hours, and a temporary software factor is issued while a replacement ships.

## Phishing and Incidents

Suspected phishing is forwarded to security@harborlight.example and then deleted. Security incidents
are reported to the incident hotline at extension 4400, which is staffed 24 hours. No employee is ever
penalized for reporting an incident they caused; the penalty applies only to concealing one.

## Support Desk

The IT support desk is open 07:30 to 17:00 ET on business days, reachable in the #hl-support channel.
Response targets are 1 business hour for P1 (work stopped), 4 business hours for P2 (work impaired)
and 2 business days for P3. Outside those hours, only the incident hotline is staffed.
```

- [ ] **Step 6: Write `travel-and-expenses.md`**

Create `src/main/resources/knowledge/travel-and-expenses.md`:

```markdown
# Harborlight Systems Travel and Expenses

Rules for booking business travel and claiming expenses at Harborlight Systems Inc.

## Booking Travel

Flights and hotels are booked through the Voyager travel portal at least 14 days before departure.
Economy class is standard; premium economy is allowed on flights longer than 6 hours, and business
class requires VP approval. Hotel rates are capped at USD 240 per night in North America and
EUR 190 in Europe.

## Per Diem

Meals on business travel are covered by a per diem of USD 68 for domestic travel and USD 92 for
international travel, paid without receipts. The per diem is reduced by 25 percent for each meal
provided by a conference or customer. Alcohol is never covered by per diem.

## Receipts

Expenses above USD 35 require an itemized receipt uploaded to the expense tool within 30 days of the
expense date. Claims submitted after 60 days are not reimbursed except in documented cases of
extended leave. Reimbursement is paid with the next payroll run after approval.

## Mileage

Driving a personal vehicle for business is reimbursed at USD 0.62 per mile, which covers fuel,
insurance and wear. Parking and tolls are claimed separately with receipts. Commuting between home
and the employee's assigned office is never reimbursable.

## Client Entertainment

Client meals require the manager's approval in advance and the names of the attendees on the claim.
The cap is USD 120 per attendee. Gifts to clients above USD 50 require compliance review, and gifts
to public officials are prohibited regardless of value.
```

- [ ] **Step 7: Write `onboarding-guide.md`**

Create `src/main/resources/knowledge/onboarding-guide.md`:

```markdown
# Harborlight Systems Onboarding Guide

What a new employee at Harborlight Systems Inc. can expect during their first 90 days.

## First Day

The laptop, Harbor Key and accounts are ready before the start date; IT ships hardware to arrive 3
business days early. Day one is 10:00 to 15:00 and covers the welcome session, a tour of the systems,
and lunch with the team. No production access is granted on day one.

## First Mate Buddy Program

Every new employee is paired with a buddy through the First Mate program. The buddy is an experienced
colleague outside the new employee's reporting line, meets them daily for the first week and weekly
for the next 11, and answers the questions that feel too small to raise with a manager.

## First Week

By the end of week one the new employee has run the application locally, opened one pull request
however small, and met every member of their immediate team one to one. The team lead schedules a
30-minute end-of-week check-in.

## Checkpoints

Formal check-ins happen at 30, 60 and 90 days. The 30-day check-in is about tooling and access,
the 60-day about the first real deliverable, and the 90-day about scope and growth for the year
ahead. Probation ends at 6 months.

## Training Budget

Each employee has an annual training budget of USD 1,800 for courses, conferences and books,
approved by the team lead. The budget does not carry over between years. Conference travel is booked
through Voyager and follows the standard travel rules.
```

- [ ] **Step 8: Run the test and verify it passes**

Run: `mvn test -Dtest=KnowledgeCorpusTest`
Expected: PASS, 5 tests. If `corpus_has_enough_sections_to_make_retrieval_meaningful` fails, count the `##` headings — there are 26 across the five files, plus 5 Overview sections, so 31 documents.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/knowledge src/test/java/io/github/avec112/rag/KnowledgeCorpusTest.java
git commit -m "Add the Harborlight Systems dummy corpus"
```

---

### Task 3: Ingest the corpus into a vector store

**Files:**
- Modify: `pom.xml` (add one dependency to `<dependencies>`)
- Create: `src/main/java/io/github/avec112/rag/KnowledgeBaseIngestor.java`
- Create: `src/test/java/io/github/avec112/rag/DeterministicEmbeddingModel.java`
- Test: `src/test/java/io/github/avec112/rag/KnowledgeBaseIngestorTest.java`

**Interfaces:**
- Consumes: `KnowledgeDocumentReader(String)`, `read()`, `METADATA_SOURCE`.
- Produces:
  - `class KnowledgeBaseIngestor` (package-private, `@Component`)
  - `KnowledgeBaseIngestor(VectorStore vectorStore, String locationPattern, boolean ingestOnStartup)` — the Spring constructor, `@Autowired`
  - `KnowledgeBaseIngestor(VectorStore vectorStore, KnowledgeDocumentReader reader, boolean ingestOnStartup)` — the test constructor
  - `int ingest()` — adds every section to the store, returns how many
  - `void ingestOnStartup()` — `@EventListener(ApplicationReadyEvent.class)`, does nothing when the flag is off

The test embeds with a bag-of-words fake, not Ollama. That tests the plumbing — reading, metadata, storing, retrieving — and deliberately does not test semantic quality, which only a real embedding model has. Lexical overlap is enough to prove the right section comes back.

- [ ] **Step 1: Add the dependency**

In `pom.xml`, directly after the `spring-ai-starter-model-ollama` dependency, add:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vector-store-advisor</artifactId>
</dependency>
```

No `<version>` — the Spring AI BOM manages it. This also brings `spring-ai-vector-store` (and therefore `SimpleVectorStore`) onto the classpath transitively.

- [ ] **Step 2: Verify the dependency resolves**

Run: `mvn dependency:tree -Dincludes='org.springframework.ai:spring-ai-vector-store*'`
Expected: BUILD SUCCESS, with `spring-ai-vector-store-advisor:jar:2.0.1` and `spring-ai-vector-store:jar:2.0.1` listed.

- [ ] **Step 3: Write the deterministic fake embedding model**

Create `src/test/java/io/github/avec112/rag/DeterministicEmbeddingModel.java`:

```java
package io.github.avec112.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

/**
 * Offline stand-in for a real embedding model: a hashed bag-of-words vector, L2-normalized.
 * Similarity therefore tracks lexical overlap, which is deterministic and enough to verify that
 * ingestion, metadata and retrieval are wired correctly. It says nothing about semantic quality.
 */
class DeterministicEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 256;

    @Override
    public float[] embed(Document document) {
        return embed(Objects.requireNonNullElse(document.getText(), ""));
    }

    @Override
    public float[] embed(String text) {
        var vector = new float[DIMENSIONS];
        for (var token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!token.isEmpty()) {
                vector[Math.floorMod(token.hashCode(), DIMENSIONS)] += 1f;
            }
        }
        var norm = 0d;
        for (var value : vector) {
            norm += value * value;
        }
        if (norm == 0d) {
            vector[0] = 1f;
            return vector;
        }
        var length = (float) Math.sqrt(norm);
        for (var i = 0; i < vector.length; i++) {
            vector[i] /= length;
        }
        return vector;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        var instructions = request.getInstructions();
        var embeddings = new ArrayList<Embedding>(instructions.size());
        for (var i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(embed(instructions.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }
}
```

- [ ] **Step 4: Write the failing test**

Create `src/test/java/io/github/avec112/rag/KnowledgeBaseIngestorTest.java`:

```java
package io.github.avec112.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseIngestorTest {

    private VectorStore vectorStore;
    private KnowledgeBaseIngestor ingestor;

    @BeforeEach
    void setUp() {
        vectorStore = SimpleVectorStore.builder(new DeterministicEmbeddingModel()).build();
        ingestor = new KnowledgeBaseIngestor(vectorStore,
                new KnowledgeDocumentReader("classpath:knowledge/*.md"), true);
    }

    @Test
    void ingest_stores_every_section_of_the_corpus() {
        var stored = ingestor.ingest();

        assertThat(stored).isGreaterThanOrEqualTo(20);
    }

    @Test
    void a_question_about_vacation_retrieves_the_time_off_policy() {
        ingestor.ingest();

        var hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query("vacation days per calendar year")
                .topK(3)
                .similarityThresholdAll()
                .build());

        assertThat(hits).isNotNull();
        assertThat(hits.getFirst().getMetadata().get(KnowledgeDocumentReader.METADATA_SOURCE))
                .isEqualTo("time-off-policy.md");
    }

    @Test
    void a_question_about_the_vpn_retrieves_the_security_policy() {
        ingestor.ingest();

        var hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query("Lighthouse VPN remote access")
                .topK(3)
                .similarityThresholdAll()
                .build());

        assertThat(hits).isNotNull();
        assertThat(hits.getFirst().getMetadata().get(KnowledgeDocumentReader.METADATA_SOURCE))
                .isEqualTo("it-security-policy.md");
    }

    @Test
    void startup_ingestion_does_nothing_when_disabled() {
        var disabled = new KnowledgeBaseIngestor(vectorStore,
                new KnowledgeDocumentReader("classpath:knowledge/*.md"), false);

        disabled.ingestOnStartup();

        var hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query("vacation")
                .topK(1)
                .similarityThresholdAll()
                .build());
        assertThat(hits).isEmpty();
    }
}
```

- [ ] **Step 5: Run the test and verify it fails**

Run: `mvn test -Dtest=KnowledgeBaseIngestorTest`
Expected: FAIL — compilation error, `KnowledgeBaseIngestor` does not exist.

- [ ] **Step 6: Write the ingestor**

Create `src/main/java/io/github/avec112/rag/KnowledgeBaseIngestor.java`:

```java
package io.github.avec112.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Loads the knowledge corpus into the vector store once the application is up.
 * Embedding happens here, so this is the only place that talks to the embedding model at startup.
 */
@Component
class KnowledgeBaseIngestor {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseIngestor.class);

    private final VectorStore vectorStore;
    private final KnowledgeDocumentReader reader;
    private final boolean ingestOnStartup;

    @Autowired
    KnowledgeBaseIngestor(VectorStore vectorStore,
                          @Value("${app.rag.documents-location:classpath:knowledge/*.md}") String locationPattern,
                          @Value("${app.rag.ingest-on-startup:true}") boolean ingestOnStartup) {
        this(vectorStore, new KnowledgeDocumentReader(locationPattern), ingestOnStartup);
    }

    KnowledgeBaseIngestor(VectorStore vectorStore, KnowledgeDocumentReader reader, boolean ingestOnStartup) {
        this.vectorStore = vectorStore;
        this.reader = reader;
        this.ingestOnStartup = ingestOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    void ingestOnStartup() {
        if (!ingestOnStartup) {
            log.info("Knowledge base ingestion is disabled (app.rag.ingest-on-startup=false)");
            return;
        }
        ingest();
    }

    int ingest() {
        var started = System.currentTimeMillis();
        var documents = reader.read();
        if (documents.isEmpty()) {
            log.warn("No knowledge documents found - the knowledge base will have nothing to retrieve");
            return 0;
        }
        vectorStore.add(documents);
        var sources = documents.stream()
                .map(document -> document.getMetadata().get(KnowledgeDocumentReader.METADATA_SOURCE))
                .distinct()
                .count();
        log.info("Indexed {} sections from {} documents in {} ms",
                documents.size(), sources, System.currentTimeMillis() - started);
        return documents.size();
    }
}
```

- [ ] **Step 7: Run the test and verify it passes**

Run: `mvn test -Dtest=KnowledgeBaseIngestorTest`
Expected: PASS, 4 tests. Nothing in this run contacts Ollama.

- [ ] **Step 8: Commit**

```bash
git add pom.xml src/main/java/io/github/avec112/rag/KnowledgeBaseIngestor.java src/test/java/io/github/avec112/rag
git commit -m "Ingest the knowledge corpus into an in-memory vector store"
```

---

### Task 4: Retrieval wiring and configuration

**Files:**
- Create: `src/main/java/io/github/avec112/rag/KnowledgeChatClientFactory.java`
- Create: `src/main/java/io/github/avec112/rag/KnowledgeBaseConfig.java`
- Modify: `src/main/resources/application.properties` (append the AI section)
- Create: `src/test/resources/application.properties`
- Test: `src/test/java/io/github/avec112/rag/KnowledgeBaseConfigTest.java`

**Interfaces:**
- Consumes: `VectorStore` from the store bean; `ChatModel` from the Ollama autoconfiguration.
- Produces:
  - `public class KnowledgeChatClientFactory`
  - `public static final String SYSTEM_PROMPT`
  - `public ChatClient create()` — a new client, with its own chat memory, per call
  - Beans: `VectorStore knowledgeVectorStore(EmbeddingModel)`, `KnowledgeChatClientFactory knowledgeChatClientFactory(ChatModel, VectorStore)`

**Why `create()` returns a new client every call:** the `ChatClient` carries the conversation memory. A shared singleton would mean every user of the app shares one conversation history. One client per view instance keeps conversations private. This is not an optimization — a singleton here is a data leak.

**Why the factory sets the conversation-id param:** `SpringAILLMProvider`'s `ChatClient` constructor leaves `hasManagedMemory` false, so the provider neither adds memory nor sets `ChatMemory.CONVERSATION_ID`. `BaseChatMemoryAdvisor.getConversationId` asserts that param is non-null, so without it every request fails. The factory supplies both.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/io/github/avec112/rag/KnowledgeBaseConfigTest.java`:

```java
package io.github.avec112.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class KnowledgeBaseConfigTest {

    @Autowired
    VectorStore vectorStore;

    @Autowired
    KnowledgeChatClientFactory chatClientFactory;

    @Value("${app.rag.ingest-on-startup}")
    boolean ingestOnStartup;

    @Test
    void the_vector_store_is_the_in_memory_one() {
        assertThat(vectorStore).isInstanceOf(SimpleVectorStore.class);
    }

    @Test
    void each_call_builds_a_separate_chat_client_so_conversations_stay_private() {
        var first = chatClientFactory.create();
        var second = chatClientFactory.create();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void tests_never_ingest_and_therefore_never_call_ollama() {
        assertThat(ingestOnStartup).isFalse();
    }

    @Test
    void the_system_prompt_grounds_the_assistant_in_the_documents() {
        assertThat(KnowledgeChatClientFactory.SYSTEM_PROMPT)
                .contains("Harborlight Systems Inc.")
                .contains("source");
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `mvn test -Dtest=KnowledgeBaseConfigTest`
Expected: FAIL — compilation error, `KnowledgeChatClientFactory` does not exist.

- [ ] **Step 3: Write the chat client factory**

Create `src/main/java/io/github/avec112/rag/KnowledgeChatClientFactory.java`:

```java
package io.github.avec112.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Builds the retrieval-augmented chat client for the knowledge base view.
 * Retrieval tuning lives here so there is one place to adjust it.
 */
public class KnowledgeChatClientFactory {

    /** How many sections are pulled into the prompt per question. */
    static final int TOP_K = 4;

    /** Below this cosine similarity a section is treated as irrelevant rather than weak evidence. */
    static final double SIMILARITY_THRESHOLD = 0.5;

    static final int MEMORY_MAX_MESSAGES = 20;

    private static final String CONVERSATION_ID = "default";

    public static final String SYSTEM_PROMPT = """
            You are the internal assistant for Harborlight Systems Inc.
            Answer only from the company documents provided to you in the context of each question.
            Name the source file you used, like this: (source: time-off-policy.md).
            If the context does not contain the answer, say that the company documents do not cover it
            and suggest who to ask. Never invent a policy, a number, or a document name.
            Keep answers short and concrete, and quote exact figures when the documents give them.
            """;

    private final ChatModel chatModel;
    private final VectorStore vectorStore;

    KnowledgeChatClientFactory(ChatModel chatModel, VectorStore vectorStore) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
    }

    /**
     * Creates a chat client with its own conversation memory. Call this once per view instance:
     * a shared client would share one conversation history across every user of the application.
     */
    public ChatClient create() {
        var chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(MEMORY_MAX_MESSAGES)
                .build();
        var retrieval = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(TOP_K)
                        .similarityThreshold(SIMILARITY_THRESHOLD)
                        .build())
                .build();
        return ChatClient.builder(chatModel)
                .defaultAdvisors(advisors -> advisors
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build(), retrieval)
                        .param(ChatMemory.CONVERSATION_ID, CONVERSATION_ID))
                .build();
    }
}
```

- [ ] **Step 4: Write the configuration**

Create `src/main/java/io/github/avec112/rag/KnowledgeBaseConfig.java`:

```java
package io.github.avec112.rag;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class KnowledgeBaseConfig {

    /**
     * An in-memory store: the corpus is small and re-embedding on restart costs seconds.
     * Swapping in pgvector later is a change to this method alone.
     */
    @Bean
    VectorStore knowledgeVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    KnowledgeChatClientFactory knowledgeChatClientFactory(ChatModel chatModel, VectorStore vectorStore) {
        return new KnowledgeChatClientFactory(chatModel, vectorStore);
    }
}
```

- [ ] **Step 5: Add the runtime properties**

Append to `src/main/resources/application.properties`:

```properties
# Embeddings for the knowledge base (RAG). Any Ollama embedding model works;
# nomic-embed-text is ~270 MB and is pulled automatically by the init strategy below.
spring.ai.ollama.embedding.model=nomic-embed-text

# Pull missing models on startup instead of failing. The first start is slower because of it.
spring.ai.ollama.init.pull-model-strategy=when_missing

# Index src/main/resources/knowledge/*.md at startup. Turned off in tests.
app.rag.ingest-on-startup=true
```

- [ ] **Step 6: Keep tests off the network**

Create `src/test/resources/application.properties`:

```properties
# Tests must never contact Ollama. Ingestion is the only startup path that would,
# and the pull strategy is the other. Both are off here.
app.rag.ingest-on-startup=false
spring.ai.ollama.init.pull-model-strategy=never
```

- [ ] **Step 7: Run the new test and verify it passes**

Run: `mvn test -Dtest=KnowledgeBaseConfigTest`
Expected: PASS, 4 tests.

- [ ] **Step 8: Run the whole suite to prove the existing tests still pass**

Run: `mvn test`
Expected: PASS. `TaskListViewTest` and `TaskServiceTest` must be green — they now boot a context that contains the vector store bean, and Step 6 is what keeps them off the network.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/io/github/avec112/rag src/main/resources/application.properties src/test/resources/application.properties src/test/java/io/github/avec112/rag/KnowledgeBaseConfigTest.java
git commit -m "Wire retrieval-augmented chat client and knowledge base configuration"
```

---

### Task 5: The knowledge base view

**Files:**
- Create: `src/main/java/io/github/avec112/rag/ui/package-info.java`
- Create: `src/main/java/io/github/avec112/rag/ui/KnowledgeChatView.java`
- Test: `src/test/java/io/github/avec112/rag/ui/KnowledgeChatViewTest.java`

**Interfaces:**
- Consumes: `KnowledgeChatClientFactory.create()`, `KnowledgeChatClientFactory.SYSTEM_PROMPT`, `io.github.avec112.base.ui.ViewTitle`.
- Produces: `class KnowledgeChatView extends VerticalLayout` at route `knowledge`, with package-private final fields `messageList` and `messageInput`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/io/github/avec112/rag/ui/KnowledgeChatViewTest.java`:

```java
package io.github.avec112.rag.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class KnowledgeChatViewTest extends SpringBrowserlessTest {

    @Test
    void view_renders_an_empty_markdown_message_list_and_an_input() {
        var view = navigate(KnowledgeChatView.class);

        assertThat(view.messageList.isMarkdown()).isTrue();
        assertThat(view.messageList.getItems()).isEmpty();
        assertThat(view.messageInput.isVisible()).isTrue();
    }

    @Test
    void view_names_the_company_whose_documents_are_loaded() {
        var view = navigate(KnowledgeChatView.class);

        assertThat(view.caption.getText()).contains("Harborlight Systems Inc.");
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `mvn test -Dtest=KnowledgeChatViewTest`
Expected: FAIL — compilation error, `KnowledgeChatView` does not exist.

- [ ] **Step 3: Write `package-info.java`**

Create `src/main/java/io/github/avec112/rag/ui/package-info.java`:

```java
@NullMarked
package io.github.avec112.rag.ui;

import org.jspecify.annotations.NullMarked;
```

- [ ] **Step 4: Write the view**

Create `src/main/java/io/github/avec112/rag/ui/KnowledgeChatView.java`:

```java
package io.github.avec112.rag.ui;

import com.vaadin.flow.component.ai.orchestrator.AIOrchestrator;
import com.vaadin.flow.component.ai.provider.SpringAILLMProvider;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.github.avec112.base.ui.ViewTitle;
import io.github.avec112.rag.KnowledgeChatClientFactory;

@PageTitle("Knowledge Base")
@Route("knowledge")
@Menu(order = 2, icon = "vaadin:book", title = "Knowledge Base")
class KnowledgeChatView extends VerticalLayout {

    final MessageList messageList = new MessageList();
    final MessageInput messageInput = new MessageInput();
    final Paragraph caption = new Paragraph(
            "Ask about Harborlight Systems Inc. Answers come from the company documents bundled with this app, "
                    + "and the assistant names the file it used.");

    KnowledgeChatView(KnowledgeChatClientFactory chatClientFactory) {
        messageList.setSizeFull();
        messageList.setMarkdown(true);
        messageInput.setWidthFull();

        // One client per view instance: the client owns the conversation memory.
        var provider = new SpringAILLMProvider(chatClientFactory.create());

        AIOrchestrator.builder(provider, KnowledgeChatClientFactory.SYSTEM_PROMPT)
                .withAssistantName("Harborlight Assistant")
                .withMessageList(messageList)
                .withInput(messageInput)
                .build();

        add(new ViewTitle("Knowledge Base"), caption);
        addAndExpand(messageList);
        add(messageInput);
    }
}
```

- [ ] **Step 5: Run the test and verify it passes**

Run: `mvn test -Dtest=KnowledgeChatViewTest`
Expected: PASS, 2 tests. No LLM call happens — building the orchestrator does not talk to the model.

- [ ] **Step 6: Run the whole suite**

Run: `mvn test`
Expected: PASS, all classes.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/avec112/rag/ui src/test/java/io/github/avec112/rag/ui
git commit -m "Add the knowledge base chat view"
```

---

### Task 6: Documentation and end-to-end verification

**Files:**
- Modify: `README.md` (new section after "Choose your AI model")
- Modify: `CLAUDE.md` (a note in Architecture)

**Interfaces:**
- Consumes: everything built in Tasks 1-5.
- Produces: no code.

This task is where the demo is confirmed to actually work against a real model. Everything before it was verified without Ollama.

- [ ] **Step 1: Pull the embedding model**

Run: `ollama pull nomic-embed-text`
Expected: the model is downloaded (~270 MB). `ollama list` then shows `nomic-embed-text:latest`.

- [ ] **Step 2: Start the app and confirm ingestion**

Run: `mvn`
Expected: the log contains a line like `Indexed 30 sections from 5 documents in 4210 ms`. If it instead warns `No knowledge documents found`, the corpus is not on the classpath — check that the files are under `src/main/resources/knowledge/`.

- [ ] **Step 3: Ask the control questions in the plain chat bot**

Open http://localhost:8080/chat-bot and ask:

1. `How many vacation days do employees at Harborlight Systems get?`
2. `What is the Harborlight VPN called?`
3. `What is the mileage reimbursement rate?`

Expected: hedging or invention. This is the "before" half of the demo — note what it says.

- [ ] **Step 4: Ask the same questions in the knowledge base**

Open http://localhost:8080/knowledge and ask the same three questions.

Expected: `27 vacation days` plus the 3 Harborlight Days (source: time-off-policy.md); `Lighthouse` (source: it-security-policy.md); `USD 0.62 per mile` (source: travel-and-expenses.md).

If answers are vague or the assistant says the documents do not cover it, lower `SIMILARITY_THRESHOLD` in `KnowledgeChatClientFactory` to `0.3` and retry. If answers pull in unrelated sections, raise it toward `0.6`. Record the value that works.

- [ ] **Step 5: Confirm the honest miss**

Ask in the knowledge base: `What is the annual bonus scheme?`

Expected: the assistant says the company documents do not cover it, rather than inventing a scheme. If it invents one, the system prompt is not being applied — check that `AIOrchestrator.builder(provider, SYSTEM_PROMPT)` still carries the prompt.

- [ ] **Step 6: Write the README section**

In `README.md`, after the "Choose your AI model — local or cloud" section and before the next `---`, add:

````markdown
## Ask the knowledge base (RAG)

The **Knowledge Base** view at `/knowledge` answers questions about a fictional company,
**Harborlight Systems Inc.**, whose handbook, time-off policy, IT security policy, travel rules and
onboarding guide live in `src/main/resources/knowledge/`. At startup each `##` section of those files
is embedded and stored in an in-memory vector store. When you ask a question, the four most similar
sections are retrieved and placed in the prompt before the model answers — retrieval-augmented
generation, with no fine-tuning and no data leaving your machine.

This needs an embedding model in addition to the chat model:

```bash
ollama pull nomic-embed-text
```

`spring.ai.ollama.init.pull-model-strategy=when_missing` in `application.properties` pulls it
automatically on first start, so the manual pull is optional — it just makes the first start faster.

**The demo.** Ask the same question in `/chat-bot` and in `/knowledge`:

| Question | `/chat-bot` | `/knowledge` |
|---|---|---|
| How many vacation days do employees get? | Guesses, or refuses | 27, plus 3 Harborlight Days (source: time-off-policy.md) |
| What is the VPN called? | Invents a name | Lighthouse (source: it-security-policy.md) |
| What is the mileage rate? | Quotes a public figure | USD 0.62 per mile (source: travel-and-expenses.md) |
| What is the annual bonus scheme? | Invents one | Says the documents do not cover it |

The company is invented, so no model can know these answers from pretraining. A correct answer is
proof the lookup happened.

**Tuning.** `TOP_K` and `SIMILARITY_THRESHOLD` in
`src/main/java/io/github/avec112/rag/KnowledgeChatClientFactory.java` control how much context is
retrieved. Too high a threshold and good questions retrieve nothing; too low and every question
retrieves noise.

**Adding your own documents.** Drop a Markdown file with `#` and `##` headings into
`src/main/resources/knowledge/` and restart. Each `##` section becomes one retrievable chunk.
````

- [ ] **Step 7: Add the CLAUDE.md note**

In `CLAUDE.md`, in the **Architecture** section, after the "Package-by-feature" paragraph, add:

```markdown
**RAG feature.** `rag/` holds the knowledge base: `KnowledgeDocumentReader` splits
`src/main/resources/knowledge/*.md` into one document per `##` section, `KnowledgeBaseIngestor`
embeds them into an in-memory `SimpleVectorStore` on `ApplicationReadyEvent`, and
`KnowledgeChatClientFactory` builds a `ChatClient` with a `QuestionAnswerAdvisor` — one per view
instance, because the client owns the conversation memory. Ingestion is off in tests via
`app.rag.ingest-on-startup=false` in `src/test/resources/application.properties`; keep it that way,
because `mvn test` must never contact Ollama.
```

- [ ] **Step 8: Run the full suite one last time**

Run: `mvn test`
Expected: PASS, every test class.

- [ ] **Step 9: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "Document the RAG knowledge base demo"
```
