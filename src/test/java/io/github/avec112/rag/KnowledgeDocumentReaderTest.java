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
