package io.github.avec112.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.UUID;

/**
 * Builds the retrieval-augmented chat client for the knowledge base view.
 * Retrieval tuning lives here so there is one place to adjust it.
 */
public class KnowledgeChatClientFactory {

    /** How many sections are pulled into the prompt per question. */
    static final int TOP_K = 8;

    /** Below this cosine similarity a section is treated as irrelevant rather than weak evidence. */
    static final double SIMILARITY_THRESHOLD = 0.3;

    /** How many recent messages the client remembers before older turns are dropped. */
    static final int MEMORY_MAX_MESSAGES = 20;

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
        // Conversation isolation must not depend on the ChatMemory repository being per-instance:
        // a shared constant here would work today only because MessageWindowChatMemory.builder()
        // defaults to an in-memory, per-instance repository. Injecting an autoconfigured, shared
        // ChatMemoryRepository later would silently merge every user's conversation onto this one
        // id, with no test failing. A fresh id per client closes that gap regardless of which
        // repository backs the memory.
        var conversationId = UUID.randomUUID().toString();
        return ChatClient.builder(chatModel)
                .defaultAdvisors(advisors -> advisors
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build(), retrieval)
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .build();
    }
}
