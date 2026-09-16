package io.github.avec112.rag.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class KnowledgeChatViewTest extends SpringBrowserlessTest {

    @Test
    void view_renders_an_empty_markdown_message_list_and_an_input() {
        var view = navigate(KnowledgeChatView.class);

        assertThat(view.messageList.isMarkdown()).isTrue();
        assertThat(view.messageList.getItems()).isEmpty();
        assertThat(view.messageInput.isAttached()).isTrue();
    }

    @Test
    void view_names_the_company_whose_documents_are_loaded() {
        var view = navigate(KnowledgeChatView.class);

        assertThat(view.caption.getText()).contains("Harborlight Systems Inc.");
    }
}
