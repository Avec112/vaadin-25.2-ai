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
