package io.github.avec112.rag.ui;

import com.vaadin.flow.component.ai.orchestrator.AIOrchestrator;
import com.vaadin.flow.component.ai.provider.SpringAILLMProvider;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.github.avec112.base.ui.ViewTitle;
import io.github.avec112.rag.KnowledgeChatClientFactory;

@PageTitle("Knowledge Base")
@Route("knowledge")
@Menu(order = 2, icon = "vaadin:book", title = "Knowledge Base")
class KnowledgeChatView extends VerticalLayout {

    final MessageList messageList = new MessageList();
    final MessageInput messageInput = new MessageInput();
    final Paragraph caption = new Paragraph(
            "Ask about Harborlight Systems Inc. Answers come from the company documents bundled with this app, "
                    + "and the assistant names the file it used.");

    KnowledgeChatView(KnowledgeChatClientFactory chatClientFactory) {
        messageList.setSizeFull();
        messageList.setMarkdown(true);
        messageInput.setWidthFull();

        // One client per view instance: the client owns the conversation memory.
        var provider = new SpringAILLMProvider(chatClientFactory.create());

        AIOrchestrator.builder(provider, chatClientFactory.systemPrompt())
                .withAssistantName("Harborlight Assistant")
                .withMessageList(messageList)
                .withInput(messageInput)
                .build();

        add(new ViewTitle("Knowledge Base"), caption);
        addAndExpand(messageList);
        add(messageInput);
    }
}
