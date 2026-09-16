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
import io.github.avec112.base.ui.ViewTitle;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.springframework.ai.chat.model.ChatModel;

@PageTitle("Chat Bot")
@Route("")
@RouteAlias("chat-bot")
@Menu(order = 0, icon = "icons/robot-line-icon.svg", title = "Chat Bot")
public class AiChatView extends VerticalLayout {
    public AiChatView(ChatModel chatModel) {
        // Create UI components
        var messageList = new MessageList();
        messageList.setSizeFull();
        var messageInput = new MessageInput();
        messageInput.setWidthFull();

        // Create the LLM provider
        var provider = new SpringAILLMProvider(chatModel);

        // Wire everything together
        String model = chatModel.getOptions().getModel();
        model = model != null? model: "Mistral";
        String modelName = StringUtils.capitalize(model);
        AIOrchestrator.builder(provider,
                        "You are a helpful assistant.")
                .withAssistantName(modelName)
                .withMessageList(messageList)
                .withInput(messageInput)
                .build();

        // Add UI components to the layout
        add(new ViewTitle("Chat Bot"));
        addAndExpand(messageList);
        add(messageInput);
    }
}
