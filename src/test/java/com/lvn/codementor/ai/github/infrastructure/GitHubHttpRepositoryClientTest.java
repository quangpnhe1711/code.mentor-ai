package com.lvn.codementor.ai.github.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.lvn.codementor.ai.github.application.GitHubRepositorySummary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubHttpRepositoryClientTest {

    @Test
    void listRepositoriesFollowsGitHubPaginationLinks() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubHttpRepositoryClient client = new GitHubHttpRepositoryClient(builder.build());

        server.expect(requestTo("https://api.github.test/user/repos?per_page=100&sort=updated"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andRespond(withSuccess("""
                        [
                          {
                            "id": 1,
                            "name": "alpha",
                            "full_name": "octo/alpha",
                            "owner": {"login": "octo"},
                            "private": false,
                            "default_branch": "main",
                            "html_url": "https://github.com/octo/alpha"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK,
                                "<https://api.github.test/user/repos?per_page=100&sort=updated&page=2>; rel=\"next\", "
                                        + "<https://api.github.test/user/repos?per_page=100&sort=updated&page=2>; rel=\"last\""));
        server.expect(requestTo("https://api.github.test/user/repos?per_page=100&sort=updated&page=2"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andRespond(withSuccess("""
                        [
                          {
                            "id": 2,
                            "name": "beta",
                            "full_name": "octo/beta",
                            "owner": {"login": "octo"},
                            "private": true,
                            "default_branch": "trunk",
                            "html_url": "https://github.com/octo/beta"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<GitHubRepositorySummary> repositories = client.listRepositories("access-token");

        assertThat(repositories).extracting(GitHubRepositorySummary::externalRepoId).containsExactly("1", "2");
        assertThat(repositories.get(1).visibility()).isEqualTo("PRIVATE");
        assertThat(repositories.get(1).isPrivate()).isTrue();
        server.verify();
    }
}
