package io.github.avec112.ai;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.ai.orchestrator.AIOrchestrator;
import com.vaadin.flow.component.ai.provider.SpringAILLMProvider;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import org.springframework.ai.chat.model.ChatModel;

@PageTitle("Chat Bot")
@Route("")
@RouteAlias("chat-bot")
@Menu(order = 0, icon = "icons/robot-line-icon.svg", title = "Chat Bot")
public class AiChatView extends Composite<VerticalLayout> {
    public AiChatView(ChatModel chatModel) {
        // Create UI components
        var messageList = new MessageList();
        messageList.setSizeFull();
        var messageInput = new MessageInput();
        messageInput.setWidthFull();

        // Create the LLM provider
        var provider = new SpringAILLMProvider(chatModel);

        // Wire everything together
        AIOrchestrator.builder(provider,
                        "You are a helpful assistant.")
                .withMessageList(messageList)
                .withInput(messageInput)
                .build();

        // Add UI components to the layout
        getContent().addAndExpand(messageList);
        getContent().add(messageInput);
    }
}
