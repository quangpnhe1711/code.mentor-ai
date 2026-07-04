package com.lvn.codementor.ai.github.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.lvn.codementor.ai.github.application.GitHubPullRequestSummary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubHttpPullRequestClientTest {

    @Test
    void listPullRequestsFollowsGitHubPaginationLinks() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubHttpPullRequestClient client = new GitHubHttpPullRequestClient(builder.build());

        server.expect(requestTo("https://api.github.test/repos/octo/repo/pulls?state=open&per_page=100&sort=updated&direction=desc"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andRespond(withSuccess("""
                        [
                          {
                            "number": 1,
                            "title": "First PR",
                            "state": "open",
                            "draft": false,
                            "user": {"login": "alice"},
                            "head": {"ref": "feature/a", "sha": "aaa"},
                            "base": {"ref": "main", "sha": "bbb"},
                            "html_url": "https://github.com/octo/repo/pull/1",
                            "created_at": "2026-07-01T00:00:00Z",
                            "updated_at": "2026-07-01T01:00:00Z"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK,
                                "<https://api.github.test/repos/octo/repo/pulls?state=open&per_page=100&sort=updated&direction=desc&page=2>; rel=\"next\""));
        server.expect(requestTo("https://api.github.test/repos/octo/repo/pulls?state=open&per_page=100&sort=updated&direction=desc&page=2"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andRespond(withSuccess("""
                        [
                          {
                            "number": 2,
                            "title": "Second PR",
                            "state": "open",
                            "draft": true,
                            "user": {"login": "bob"},
                            "head": {"ref": "feature/b", "sha": "ccc"},
                            "base": {"ref": "main", "sha": "ddd"},
                            "html_url": "https://github.com/octo/repo/pull/2",
                            "created_at": "2026-07-02T00:00:00Z",
                            "updated_at": "2026-07-02T01:00:00Z"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<GitHubPullRequestSummary> pullRequests =
                client.listPullRequests("access-token", "octo", "repo");

        assertThat(pullRequests).extracting(GitHubPullRequestSummary::number).containsExactly(1L, 2L);
        assertThat(pullRequests.get(1).draft()).isTrue();
        assertThat(pullRequests.get(1).authorLogin()).isEqualTo("bob");
        server.verify();
    }
}
