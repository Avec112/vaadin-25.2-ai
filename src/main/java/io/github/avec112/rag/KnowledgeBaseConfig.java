package io.github.avec112.rag;

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
}
