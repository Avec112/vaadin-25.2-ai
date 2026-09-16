package io.github.avec112.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tools the model can call when a question is about the knowledge base itself rather than its
 * content.
 * <p>
 * Retrieval only ever attaches the sections most similar to the current question, so questions
 * like "which documents do you have?" or "summarise the whole handbook" are unanswerable from
 * that context alone - the model would answer from the handful of excerpts it happens to hold
 * and report them as if they were the whole corpus. These tools give it the shelf as well as
 * the page.
 */
class KnowledgeTools {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeTools.class);

    private final KnowledgeDocumentReader reader;

    KnowledgeTools(KnowledgeDocumentReader reader) {
        this.reader = reader;
    }

    @Tool(name = "list_documents", description = """
            List every document in the Harborlight Systems knowledge base, with its title and its
            section headings. Call this when asked which documents exist, how many there are, or
            what the knowledge base covers.""")
    String listDocuments() {
        Map<String, DocumentSummary> documents;
        try {
            documents = index();
        } catch (RuntimeException e) {
            log.error("Could not list knowledge documents", e);
            return "The knowledge base could not be read, so its documents cannot be listed.";
        }
        if (documents.isEmpty()) {
            return "The knowledge base contains no documents.";
        }
        var listing = documents.values().stream()
                .map(document -> "- %s — %s%n  Sections: %s".formatted(
                        document.fileName(), document.title(), String.join(", ", document.sections())))
                .collect(Collectors.joining("\n"));
        return "The knowledge base contains %d documents:%n%n%s".formatted(documents.size(), listing);
    }

    @Tool(name = "read_document", description = """
            Read one whole document from the Harborlight Systems knowledge base by its file name,
            for example employee-handbook.md. Call this when a question needs a whole document
            rather than a few excerpts, such as summarising one or listing everything it covers.""")
    String readDocument(@ToolParam(description = "File name of the document, for example time-off-policy.md")
                        String fileName) {
        Map<String, String> documents;
        try {
            documents = reader.readRaw();
        } catch (RuntimeException e) {
            log.error("Could not read knowledge document {}", fileName, e);
            return "The knowledge base could not be read, so '%s' cannot be opened.".formatted(fileName);
        }
        var match = documents.keySet().stream()
                .filter(candidate -> matches(candidate, fileName))
                .findFirst();
        if (match.isEmpty()) {
            // A tool that throws tells the model nothing it can act on. Naming the documents that
            // do exist lets it retry with a name that works.
            return "There is no document named '%s'. The knowledge base contains: %s."
                    .formatted(fileName, String.join(", ", documents.keySet()));
        }
        return documents.get(match.get());
    }

    /** Models paraphrase file names, so accept a missing extension and any casing. */
    private static boolean matches(String candidate, String requested) {
        var wanted = requested == null ? "" : requested.strip().toLowerCase(Locale.ROOT);
        var actual = candidate.toLowerCase(Locale.ROOT);
        return actual.equals(wanted) || actual.equals(wanted + ".md");
    }

    /** Groups the parsed sections back into one entry per file, in corpus order. */
    private Map<String, DocumentSummary> index() {
        var documents = new LinkedHashMap<String, DocumentSummary>();
        for (var section : reader.read()) {
            var metadata = section.getMetadata();
            var fileName = String.valueOf(metadata.get(KnowledgeDocumentReader.METADATA_SOURCE));
            var title = String.valueOf(metadata.get(KnowledgeDocumentReader.METADATA_TITLE));
            documents.computeIfAbsent(fileName, name -> new DocumentSummary(name, title, new ArrayList<>()))
                    .sections()
                    .add(String.valueOf(metadata.get(KnowledgeDocumentReader.METADATA_SECTION)));
        }
        return documents;
    }

    private record DocumentSummary(String fileName, String title, List<String> sections) {
    }
}
