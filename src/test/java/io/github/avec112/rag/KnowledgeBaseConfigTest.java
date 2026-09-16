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
    void the_assembled_system_prompt_names_every_document_so_the_inventory_never_depends_on_a_tool_call() {
        assertThat(chatClientFactory.systemPrompt()).contains(
                "employee-handbook.md",
                "time-off-policy.md",
                "it-security-policy.md",
                "travel-and-expenses.md",
                "onboarding-guide.md");
    }

    @Test
    void the_system_prompt_grounds_the_assistant_in_the_documents() {
        assertThat(KnowledgeChatClientFactory.SYSTEM_PROMPT)
                .contains("Harborlight Systems Inc.")
                .contains("source");
    }
}
