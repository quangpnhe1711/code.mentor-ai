package com.lvn.codementor.ai.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisFile;
import com.lvn.codementor.ai.review.config.ReviewAiProperties;
import com.lvn.codementor.ai.review.domain.ReviewFindingSeverity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleReviewAnalyzerClientTest {

    @Test
    void callsOpenAiCompatibleEndpointAndParsesStructuredFindings() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess(responseJson(), MediaType.APPLICATION_JSON));

        ReviewAiProperties properties = new ReviewAiProperties(
                "OPENAI_COMPATIBLE",
                "review-model",
                "review-ai-v1",
                "http://ai.test/v1",
                "test-key",
                5,
                500);
        OpenAiCompatibleReviewAnalyzerClient client =
                new OpenAiCompatibleReviewAnalyzerClient(properties, new ObjectMapper(), builder);

        ReviewAnalyzerResult result = client.analyze(List.of(new CodeAnalysisFile(
                "src/App.java",
                "class App { void run() { System.out.println(\"x\"); } }",
                0)));

        server.verify();
        assertThat(result.aiProvider()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.aiModel()).isEqualTo("review-model");
        assertThat(result.promptVersion()).isEqualTo("review-ai-v1");
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.filePath()).isEqualTo("src/App.java");
            assertThat(finding.lineStart()).isEqualTo(1);
            assertThat(finding.severity()).isEqualTo(ReviewFindingSeverity.LOW);
            assertThat(finding.ruleId()).isEqualTo("ai-debug-code");
        });
    }

    @Test
    void parsesFindingsWhenModelWrapsJsonInMarkdownFences() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(fencedResponseJson(), MediaType.APPLICATION_JSON));

        ReviewAiProperties properties = new ReviewAiProperties(
                "OPENAI_COMPATIBLE", "review-model", "review-ai-v1", "http://ai.test/v1", "test-key", 5, 500);
        OpenAiCompatibleReviewAnalyzerClient client =
                new OpenAiCompatibleReviewAnalyzerClient(properties, new ObjectMapper(), builder);

        ReviewAnalyzerResult result = client.analyze(List.of(new CodeAnalysisFile("src/App.java", "x", 0)));

        server.verify();
        assertThat(result.findings()).singleElement()
                .satisfies(finding -> assertThat(finding.ruleId()).isEqualTo("ai-debug-code"));
    }

    private static String fencedResponseJson() {
        return """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "```json\\n{\\"findings\\":[{\\"filePath\\":\\"src/App.java\\",\\"lineStart\\":1,\\"lineEnd\\":1,\\"severity\\":\\"LOW\\",\\"category\\":\\"DEBUG_CODE\\",\\"title\\":\\"Debug output\\",\\"description\\":\\"Debug output should not be committed.\\",\\"suggestion\\":\\"Use a logger.\\",\\"ruleId\\":\\"ai-debug-code\\",\\"confidence\\":0.82}]}\\n```"
                      }
                    }
                  ]
                }
                """;
    }

    private static String responseJson() {
        return """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"findings\\":[{\\"filePath\\":\\"src/App.java\\",\\"lineStart\\":1,\\"lineEnd\\":1,\\"severity\\":\\"LOW\\",\\"category\\":\\"DEBUG_CODE\\",\\"title\\":\\"Debug output\\",\\"description\\":\\"Debug output should not be committed.\\",\\"suggestion\\":\\"Use a logger.\\",\\"ruleId\\":\\"ai-debug-code\\",\\"confidence\\":0.82}]}"
                      }
                    }
                  ]
                }
                """;
    }
}
