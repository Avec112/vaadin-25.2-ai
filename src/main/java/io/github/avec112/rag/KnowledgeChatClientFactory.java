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

            The excerpts attached to a question are only the sections retrieved for that question,
            never the whole knowledge base. Never describe them as the complete set of documents.
            The complete list of documents is given below, and it is authoritative: a document on
            that list exists even when no excerpt from it is attached to the question, and a document
            missing from it does not exist. Never say you cannot confirm whether a listed document
            exists, and never describe the attached excerpts as the full set of documents. When you
            list documents, reproduce every document and every section, without shortening the list
            and without merging it with the attached excerpts.
            Call read_document when a question needs a whole document rather than excerpts, such as
            summarising one. Call list_documents only if you need the list again.
            """;

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final KnowledgeTools knowledgeTools;

    KnowledgeChatClientFactory(ChatModel chatModel, VectorStore vectorStore, KnowledgeTools knowledgeTools) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.knowledgeTools = knowledgeTools;
    }

    /**
     * The grounding prompt with the corpus manifest appended.
     * <p>
     * The manifest is not a convenience: whether the model calls {@code list_documents} is its own
     * decision, and it skips the call whenever a question looks answerable from the excerpts it
     * already holds - which is exactly what a follow-up naming one file looks like. It then reports
     * the four or five files those excerpts came from as though they were the whole corpus. Putting
     * the list in every request makes the inventory present rather than merely available. It is
     * rendered by the same tool the model can call, so the two can never disagree.
     */
    public String systemPrompt() {
        return "%s%n%s".formatted(SYSTEM_PROMPT, knowledgeTools.listDocuments());
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
                // Retrieval answers questions about content; these answer questions about the
                // corpus itself, which similarity search structurally cannot.
                .defaultTools(knowledgeTools)
                .build();
    }
}
