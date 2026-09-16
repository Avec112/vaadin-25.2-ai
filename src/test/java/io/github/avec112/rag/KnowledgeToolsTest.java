package io.github.avec112.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeToolsTest {

    private final KnowledgeTools tools = new KnowledgeTools(
            new KnowledgeDocumentReader("classpath:knowledge/*.md"));

    @Test
    void list_documents_names_every_document_in_the_corpus() {
        var listing = tools.listDocuments();

        assertThat(listing).contains(
                "employee-handbook.md",
                "time-off-policy.md",
                "it-security-policy.md",
                "travel-and-expenses.md",
                "onboarding-guide.md");
    }

    @Test
    void list_documents_names_the_sections_of_each_document() {
        var listing = tools.listDocuments();

        assertThat(listing).contains("Anchor Days", "Parental Leave", "VPN Access", "Mileage", "First Week");
    }

    @Test
    void read_document_returns_the_whole_document_not_an_excerpt() {
        var document = tools.readDocument("employee-handbook.md");

        assertThat(document)
                .contains("# Harborlight Systems Employee Handbook")
                .contains("## Working Hours")
                .contains("## Company Meetings")
                .contains("Tuesday and Thursday are Anchor Days");
    }

    @Test
    void read_document_tolerates_a_name_the_model_paraphrases() {
        assertThat(tools.readDocument("employee-handbook")).contains("## Working Hours");
        assertThat(tools.readDocument("  Employee-Handbook.MD  ")).contains("## Working Hours");
    }

    @Test
    void read_document_answers_an_unknown_name_with_the_valid_ones() {
        var answer = tools.readDocument("salary-bands.md");

        assertThat(answer)
                .contains("salary-bands.md")
                .contains("employee-handbook.md")
                .contains("onboarding-guide.md");
    }

    @Test
    void a_missing_corpus_degrades_to_a_message_rather_than_throwing() {
        var broken = new KnowledgeTools(new KnowledgeDocumentReader("classpath:no-such-directory/*.md"));

        assertThat(broken.listDocuments()).isNotBlank();
        assertThat(broken.readDocument("employee-handbook.md")).isNotBlank();
    }
}
