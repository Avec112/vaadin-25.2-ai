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
