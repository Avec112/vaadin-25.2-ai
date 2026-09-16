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
