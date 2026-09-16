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
        assertThat(corpus).isNotEmpty().allSatisfy(document ->
                assertThat(document.getText()).hasSizeLessThanOrEqualTo(KnowledgeDocumentReader.MAX_SECTION_CHARS));
    }

    @Test
    void the_company_is_named_consistently() {
        assertThat(corpus).isNotEmpty().allSatisfy(document -> assertThat(document.getText()).doesNotContain("Harborlight Systems AS"));
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
        assertThatFactIsUnique("3 Harborlight Days");
        assertThatFactIsUnique("USD 68");
        assertThatFactIsUnique("Tuesday and Thursday are Anchor Days");
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
