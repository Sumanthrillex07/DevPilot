package com.devpilot.backend.services.github;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around GitHub's REST API v3.
 *
 * <p><b>Why RestClient (not RestTemplate or WebClient)?</b><br>
 * {@link RestClient} is the synchronous, non-reactive HTTP client introduced in Spring 6.1
 * as the modern replacement for {@code RestTemplate}. It has a cleaner fluent API and is
 * appropriate here because our indexing jobs run in their own threads — we don't need
 * reactive streams for this use case.
 *
 * <p><b>Authentication:</b><br>
 * Every request sends the user's decrypted GitHub access token as
 * {@code Authorization: Bearer <token>}. This gives us the same permissions as the user —
 * we can access their private repos and organisation repos, subject to the scopes they
 * granted ({@code read:user,repo}).
 *
 * <p><b>Pagination:</b><br>
 * GitHub paginates repository lists at max 100 per page. {@link #listUserRepos} loops
 * up to 10 pages (1,000 repos). If a user has more than 1,000 repos, we stop at 1,000 —
 * an acceptable trade-off for a personal assistant tool.
 *
 * <p><b>File content decoding:</b><br>
 * GitHub returns file content Base64-encoded with line-breaks. {@link #getFileContent}
 * strips whitespace before decoding, then returns the content as a UTF-8 string.
 */
@Service
@RequiredArgsConstructor
public class GithubApiClient {

    private static final String API_BASE = "https://api.github.com";

    // ParameterizedTypeReference lets us deserialise generic List<Map<String,Object>>
    // which would otherwise be erased at runtime by Java's type erasure.
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_MAP =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {};

    // Spring injects a pre-configured RestClient.Builder with base settings from application.yml
    private final RestClient.Builder restClientBuilder;

    /**
     * Lists all repositories accessible to the authenticated user.
     *
     * <p>We use {@code affiliation=owner,collaborator,organization_member} to include
     * repos from all of the user's orgs, not just their own repos. This matches what
     * users see on github.com.
     *
     * @param accessToken Decrypted GitHub access token
     * @return List of raw repo attribute maps from GitHub's JSON
     */
    public List<Map<String, Object>> listUserRepos(String accessToken) {
        List<Map<String, Object>> all = new ArrayList<>();
        int page = 1;

        // GitHub paginates at 100/page; we cap at 10 pages = 1,000 repos
        while (page <= 10) {
            final int currentPage = page;
            List<Map<String, Object>> pageRepos = client(accessToken)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/repos")
                            .queryParam("affiliation", "owner,collaborator,organization_member")
                            .queryParam("sort", "updated")
                            .queryParam("per_page", 100)
                            .queryParam("page", currentPage)
                            .build())
                    .retrieve()
                    .body(LIST_MAP);

            if (pageRepos == null || pageRepos.isEmpty()) {
                break; // No more pages
            }
            all.addAll(pageRepos);

            // If we got fewer than 100 results, we've reached the last page
            if (pageRepos.size() < 100) {
                break;
            }
            page++;
        }
        return all;
    }

    /**
     * Fetches the recursive file tree of a repository branch.
     *
     * <p>The {@code ?recursive=1} parameter makes GitHub return all files at all depths
     * in a single request, rather than requiring us to traverse directories. This is
     * important for performance — a repo with nested folders would require dozens of
     * requests without recursion.
     *
     * @param accessToken Decrypted GitHub access token
     * @param owner       Repository owner login (e.g. "octocat")
     * @param repo        Repository name (e.g. "hello-world")
     * @param branch      Branch name (e.g. "main")
     * @return Map containing "tree" (list of file entries) and "truncated" (boolean)
     */
    public Map<String, Object> getRepoTree(String accessToken, String owner, String repo, String branch) {
        return client(accessToken)
                .get()
                .uri("/repos/{owner}/{repo}/git/trees/{branch}?recursive=1", owner, repo, branch)
                .retrieve()
                .body(MAP);
    }

    /**
     * Downloads and decodes the content of a single file.
     *
     * <p>GitHub's Contents API returns file data Base64-encoded (with newlines inserted
     * every 60 characters for historical reasons). We strip all whitespace before decoding.
     *
     * @param accessToken Decrypted GitHub access token
     * @param owner       Repository owner login
     * @param repo        Repository name
     * @param path        File path within the repo (e.g. "src/main/App.java")
     * @return Decoded UTF-8 file content, or {@code null} if the file couldn't be retrieved
     */
    public String getFileContent(String accessToken, String owner, String repo, String path) {
        Map<String, Object> body = client(accessToken)
                .get()
                .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                .retrieve()
                .body(MAP);

        if (body == null) {
            return null;
        }

        Object encoding = body.get("encoding");
        Object content = body.get("content");

        if (content == null) {
            return null;
        }

        if ("base64".equals(String.valueOf(encoding))) {
            // Strip newlines/spaces that GitHub inserts into the Base64 payload
            String raw = String.valueOf(content).replaceAll("\\s", "");
            return new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
        }

        return String.valueOf(content);
    }

    // ---- private helpers ----

    /**
     * Builds a per-request RestClient with the user's access token.
     *
     * <p>We build a new client per call (not per user) because {@code RestClient} is
     * cheap to construct — it's a thin wrapper around an HttpClient connection pool
     * that is shared via the injected {@code RestClient.Builder}.
     */
    private RestClient client(String accessToken) {
        return restClientBuilder
                .baseUrl(API_BASE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "DevPilot/1.0")
                .build();
    }
}
