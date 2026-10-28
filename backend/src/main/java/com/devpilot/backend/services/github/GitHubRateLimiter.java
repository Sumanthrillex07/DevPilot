package com.devpilot.backend.services.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Inserts a configurable delay between consecutive GitHub API calls during indexing.
 *
 * <p><b>Why do we need this?</b><br>
 * GitHub enforces two types of rate limits:
 * <ul>
 *   <li><b>Primary limit</b>: 5,000 requests/hour per authenticated user (we check HTTP 429)</li>
 *   <li><b>Secondary limit</b>: abuse detection that triggers when you make too many
 *       requests in a short burst, even if you're under 5,000/hour. GitHub does not
 *       document the exact threshold — it's based on request frequency.</li>
 * </ul>
 *
 * <p>During repository indexing (Part 5), we fetch every file in the repo. A repo
 * with 500 files generates 500+ API requests in quick succession. Without a delay,
 * GitHub will temporarily block our OAuth token with a 403 and an "abuse detection"
 * error message.
 *
 * <p>A 50ms delay means we can fetch at most ~20 files/second. For a 500-file repo
 * this takes ~25 seconds — acceptable for a background job.
 *
 * <p>The delay is configurable via {@code DEVPILOT_GITHUB_API_DELAY_MS} so we can
 * tune it without a code change (e.g., set to 0 in tests).
 */
@Component
public class GitHubRateLimiter {

    private final long delayMs;

    public GitHubRateLimiter(
            @Value("${devpilot.github.api-delay-ms:50}") long delayMs) {
        this.delayMs = Math.max(0, delayMs);
    }

    /**
     * Sleeps for {@code delayMs} milliseconds.
     * Should be called between each file-content API request during indexing.
     *
     * @throws IllegalStateException if the thread is interrupted while sleeping
     */
    public void pause() {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Rate limiter interrupted", e);
        }
    }
}
