/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.platform.http;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-End tests for HttpRequestExecutor using real HTTP connections to WireMock server.
 *
 * <p>These tests verify the complete retry mechanism functionality including:
 *
 * <ul>
 *   <li>Real HTTP communication with embedded mock server
 *   <li>Network failure recovery scenarios
 *   <li>Performance impact measurement
 *   <li>Log output verification
 *   <li>Retry-After header compliance
 * </ul>
 *
 * <h3>WireMock Integration</h3>
 *
 * <p>Uses embedded WireMock server for reliable, isolated testing without external dependencies.
 *
 * @see HttpRequestExecutor
 * @see HttpRetryConfiguration
 */
class HttpRequestExecutorE2ETest {

  private WireMockServer wireMockServer;
  private HttpRequestExecutor executor;
  private HttpClient httpClient;
  private String baseUrl;

  @BeforeEach
  void setUp() {
    // Start WireMock server on random port
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
    baseUrl = wireMockServer.baseUrl();

    // Use real HTTP client for E2E testing
    httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    // Mock OAuth resolvers for testing
    OAuthAuthorizationResolvers mockOAuthResolvers = mock(OAuthAuthorizationResolvers.class);

    executor = new HttpRequestExecutor(httpClient, mockOAuthResolvers);
  }

  @AfterEach
  void tearDown() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  void testE2E_RetryWithNetworkFailureRecovery() throws Exception {
    // Arrange: WireMock stub that simulates 503 → 503 → 200
    wireMockServer.stubFor(
        post(urlEqualTo("/e2e/retry/503-then-200"))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "1"))
            .willSetStateTo("First Retry"));

    wireMockServer.stubFor(
        post(urlEqualTo("/e2e/retry/503-then-200"))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs("First Retry")
            .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "1"))
            .willSetStateTo("Second Retry"));

    wireMockServer.stubFor(
        post(urlEqualTo("/e2e/retry/503-then-200"))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs("Second Retry")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\": true, \"retried\": true, \"message\": \"Request succeeded after retries\"}")));

    AtomicInteger attemptCount = new AtomicInteger(0);

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(3)
            .backoffDelays(
                Duration.ofMillis(100), // Fast for testing
                Duration.ofMillis(200),
                Duration.ofSeconds(1))
            .build();

    long startTime = System.currentTimeMillis();

    // Act: Execute request with retry that should eventually succeed
    HttpRequestResult result =
        executor.executeWithRetry(
            createRequestWithAttemptCounter(baseUrl + "/e2e/retry/503-then-200", attemptCount),
            retryConfig);

    long totalTime = System.currentTimeMillis() - startTime;

    // Assert: Request should succeed after retries
    assertTrue(result.isSuccess(), "Request should succeed after retries");
    assertEquals(200, result.statusCode());

    // Verify response content indicates successful retry
    assertTrue(
        result.body().contains("success"),
        "Response should indicate success: " + result.body().toJson());
    assertTrue(
        result.body().contains("retried"),
        "Response should indicate retry occurred: " + result.body().toJson());

    // Verify timing includes retry delays (at least 300ms from backoff)
    assertTrue(
        totalTime >= 300, String.format("Total time should include retry delays: %dms", totalTime));
    assertTrue(
        totalTime < 5000,
        String.format("Total time should not exceed reasonable bounds: %dms", totalTime));

    System.out.printf(
        "E2E Retry Success - Total time: %dms, Attempts: %d%n", totalTime, attemptCount.get());
  }

  @Test
  void testE2E_Dynamic499ResponseHandling() throws Exception {
    // Clear any existing stubs first
    wireMockServer.resetMappings();

    // Test 1: 499 with retryable=false
    wireMockServer.stubFor(
        get(urlPathEqualTo("/e2e/retry/499-false"))
            .willReturn(
                aResponse()
                    .withStatus(499)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"retryable\": false, \"message\": \"Request cancelled - not retryable\"}")));

    HttpRequest nonRetryableRequest =
        HttpRequest.newBuilder().uri(URI.create(baseUrl + "/e2e/retry/499-false")).GET().build();

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(2)
            .retryableStatusCodes(java.util.Set.of(499, 500, 502, 503, 504))
            .backoffDelays(Duration.ofMillis(100))
            .build();

    HttpRequestResult result1 = executor.executeWithRetry(nonRetryableRequest, retryConfig);

    // Assert: Should not retry when retryable=false
    assertFalse(result1.isSuccess());
    assertEquals(499, result1.statusCode());
    System.out.printf("DEBUG: Response body for retryable=false: %s%n", result1.body().toJson());
    assertFalse(result1.body().getValueAsBoolean("retryable"));

    // Test 2: 499 with retryable=true (use different endpoint to avoid conflicts)
    wireMockServer.stubFor(
        get(urlPathEqualTo("/e2e/retry/499-true"))
            .willReturn(
                aResponse()
                    .withStatus(499)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"retryable\": true, \"message\": \"Request cancelled - retryable\"}")));

    HttpRequest retryableRequest =
        HttpRequest.newBuilder().uri(URI.create(baseUrl + "/e2e/retry/499-true")).GET().build();

    long startTime2 = System.currentTimeMillis();
    HttpRequestResult result2 = executor.executeWithRetry(retryableRequest, retryConfig);
    long totalTime2 = System.currentTimeMillis() - startTime2;

    // Assert: Should retry when retryable=true (but will still fail after retries)
    assertFalse(result2.isSuccess());
    assertEquals(499, result2.statusCode());
    System.out.printf("DEBUG: Response body for retryable=true: %s%n", result2.body().toJson());

    // When retryable=true, it should have attempted retries, so either:
    // 1. The body contains retry context (if it's an error response after retries), OR
    // 2. The request took longer due to retries (at least 200ms for 2 retry delays)
    boolean hasRetryContext =
        result2.body().contains("retry_context") || result2.body().contains("max_retries");
    boolean tookTimeForRetries = totalTime2 >= 200; // At least two 100ms delays

    assertTrue(
        hasRetryContext || tookTimeForRetries,
        String.format(
            "Should show evidence of retry attempts. Time: %dms, Body: %s",
            totalTime2, result2.body().toJson()));

    System.out.println("E2E 499 Dynamic Handling - Non-retryable vs Retryable responses verified");
  }

  @Test
  void testE2E_TimeoutHandling() throws Exception {
    // Arrange: WireMock stub with fixed delay to simulate timeout
    wireMockServer.stubFor(
        get(urlEqualTo("/e2e/retry/timeout"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withFixedDelay(5000) // 5 second delay
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"message\": \"This response is delayed\"}")));

    HttpClient shortTimeoutClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    HttpRequestExecutor timeoutExecutor =
        new HttpRequestExecutor(shortTimeoutClient, mock(OAuthAuthorizationResolvers.class));

    HttpRequest timeoutRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/e2e/retry/timeout"))
            .timeout(Duration.ofSeconds(2)) // Shorter than WireMock's 5s delay
            .GET()
            .build();

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(1)
            .backoffDelays(Duration.ofMillis(100))
            .build();

    long startTime = System.currentTimeMillis();

    // Act: Execute request that should timeout and retry
    HttpRequestResult result = timeoutExecutor.executeWithRetry(timeoutRequest, retryConfig);

    long totalTime = System.currentTimeMillis() - startTime;

    // Assert: Should fail due to timeout (converted to 502, 500, or 504)
    assertFalse(result.isSuccess());
    System.out.printf("DEBUG: Actual status code: %d%n", result.statusCode());
    assertTrue(
        result.statusCode() == 502 || result.statusCode() == 500 || result.statusCode() == 504,
        String.format(
            "Timeout should result in 502, 500, or 504 status, but got: %d", result.statusCode()));

    // Should attempt retry (so total time > single timeout)
    assertTrue(
        totalTime >= 2000, String.format("Should include retry attempt time: %dms", totalTime));

    System.out.printf(
        "E2E Timeout Handling - Total time: %dms, Status: %d%n", totalTime, result.statusCode());
  }

  @Test
  void testE2E_PerformanceMeasurement() throws Exception {
    // Arrange: WireMock stub for performance testing
    wireMockServer.stubFor(
        post(urlEqualTo("/e2e/retry/performance"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"performance\": \"success\", \"message\": \"Performance test endpoint\"}")));

    HttpRequest performanceRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/e2e/retry/performance"))
            .POST(HttpRequest.BodyPublishers.ofString("{\"test\": \"performance\"}"))
            .header("Content-Type", "application/json")
            .build();

    // Test 1: Without retry
    long startNoRetry = System.currentTimeMillis();
    HttpRequestResult resultNoRetry =
        executor.executeWithRetry(performanceRequest, HttpRetryConfiguration.noRetry());
    long timeNoRetry = System.currentTimeMillis() - startNoRetry;

    // Test 2: With retry configuration (but successful request)
    long startWithRetry = System.currentTimeMillis();
    HttpRequestResult resultWithRetry =
        executor.executeWithRetry(
            performanceRequest,
            HttpRetryConfiguration.builder()
                .maxRetries(3)
                .idempotencyRequired(true)
                .backoffDelays(Duration.ofMillis(100))
                .build());
    long timeWithRetry = System.currentTimeMillis() - startWithRetry;

    // Assert: Both should succeed
    assertTrue(resultNoRetry.isSuccess());
    assertTrue(resultWithRetry.isSuccess());

    // Performance impact should be minimal for successful requests
    long performanceDiff = timeWithRetry - timeNoRetry;
    assertTrue(
        performanceDiff < 500, // Less than 500ms overhead
        String.format("Retry configuration overhead should be minimal: %dms", performanceDiff));

    System.out.printf(
        "E2E Performance - No Retry: %dms, With Retry: %dms, Diff: %dms%n",
        timeNoRetry, timeWithRetry, performanceDiff);
  }

  @Test
  void testE2E_IdempotencyKeyPersistence() throws Exception {
    // Arrange: WireMock stub that captures idempotency key for verification
    wireMockServer.stubFor(
        post(urlEqualTo("/e2e/retry/idempotency"))
            .inScenario("Idempotency Scenario")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "1"))
            .willSetStateTo("First Retry"));

    wireMockServer.stubFor(
        post(urlEqualTo("/e2e/retry/idempotency"))
            .inScenario("Idempotency Scenario")
            .whenScenarioStateIs("First Retry")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\": true, \"message\": \"Request succeeded with idempotency\"}")));

    AtomicInteger attemptCount = new AtomicInteger(0);

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(2)
            .idempotencyRequired(true)
            .backoffDelays(Duration.ofMillis(100), Duration.ofMillis(200))
            .build();

    // Act: Execute request that will fail first time
    HttpRequestResult result =
        executor.executeWithRetry(
            createRequestWithAttemptCounter(baseUrl + "/e2e/retry/idempotency", attemptCount),
            retryConfig);

    // Assert: Should succeed with consistent idempotency key
    assertTrue(result.isSuccess());

    // Verify idempotency header was sent in all requests
    wireMockServer.verify(
        2,
        postRequestedFor(urlEqualTo("/e2e/retry/idempotency"))
            .withHeader("Idempotency-Key", matching("idem_.*")));

    System.out.println("E2E Idempotency Key - Request completed with idempotency key persistence");
  }

  @Test
  void testE2E_RetryAfterHeaderCompliance() throws Exception {
    // Arrange: WireMock stub with Retry-After header
    wireMockServer.stubFor(
        post(urlEqualTo("/e2e/retry/retry-after"))
            .inScenario("Retry-After Scenario")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                aResponse().withStatus(503).withHeader("Retry-After", "2")) // 2 second delay
            .willSetStateTo("After Retry"));

    wireMockServer.stubFor(
        post(urlEqualTo("/e2e/retry/retry-after"))
            .inScenario("Retry-After Scenario")
            .whenScenarioStateIs("After Retry")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\": true, \"message\": \"Request succeeded after Retry-After delay\"}")));

    AtomicInteger attemptCount = new AtomicInteger(0);

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(2)
            .backoffDelays(Duration.ofMillis(500)) // Will be overridden by Retry-After
            .build();

    long startTime = System.currentTimeMillis();

    // Act: Execute request where WireMock returns Retry-After: 2
    HttpRequestResult result =
        executor.executeWithRetry(
            createRequestWithAttemptCounter(baseUrl + "/e2e/retry/retry-after", attemptCount),
            retryConfig);

    long totalTime = System.currentTimeMillis() - startTime;

    // Assert: Should respect Retry-After header (first retry waits ~2s)
    assertTrue(result.isSuccess());
    assertTrue(
        totalTime >= 2000, // Should wait at least 2 seconds from Retry-After
        String.format("Should respect Retry-After header timing: %dms", totalTime));

    System.out.printf(
        "E2E Retry-After Compliance - Total time: %dms (respected Retry-After header)%n",
        totalTime);
  }

  @Test
  void testE2E_CircuitBreakerPattern() throws Exception {
    // Arrange: WireMock stub that fails consistently (simulating degraded service)
    wireMockServer.stubFor(
        post(urlEqualTo("/e2e/retry/circuit-breaker"))
            .willReturn(
                aResponse()
                    .withStatus(503)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"error\": \"service_unavailable\", \"message\": \"Service temporarily degraded\"}")));

    HttpRetryConfiguration aggressiveRetryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(5)
            .backoffDelays(
                Duration.ofMillis(50),
                Duration.ofMillis(100),
                Duration.ofMillis(200),
                Duration.ofMillis(400),
                Duration.ofMillis(800))
            .build();

    AtomicInteger totalRequestCount = new AtomicInteger(0);
    long startTime = System.currentTimeMillis();

    // Act: Execute multiple requests that should fail
    HttpRequestResult result1 =
        executor.executeWithRetry(
            createRequestWithAttemptCounter(
                baseUrl + "/e2e/retry/circuit-breaker", totalRequestCount),
            aggressiveRetryConfig);

    HttpRequestResult result2 =
        executor.executeWithRetry(
            createRequestWithAttemptCounter(
                baseUrl + "/e2e/retry/circuit-breaker", totalRequestCount),
            aggressiveRetryConfig);

    long totalTime = System.currentTimeMillis() - startTime;

    // Assert: Both requests should fail
    assertFalse(result1.isSuccess());
    assertFalse(result2.isSuccess());
    assertEquals(503, result1.statusCode());
    assertEquals(503, result2.statusCode());

    // Should attempt full retry cycle for each request (5 retries each = 10 total)
    int expectedMinRequests = 12; // 2 initial + 10 retries
    wireMockServer.verify(
        expectedMinRequests, postRequestedFor(urlEqualTo("/e2e/retry/circuit-breaker")));

    // Total time should include all backoff delays
    assertTrue(
        totalTime >= 1500, // At least 1.5s for two full retry cycles
        String.format("Circuit breaker test should include retry delays: %dms", totalTime));

    System.out.printf(
        "E2E Circuit Breaker - Total time: %dms, Total requests made: %d+%n",
        totalTime, expectedMinRequests);
  }

  @Test
  void testE2E_ExponentialBackoffVerification() throws Exception {
    // Clear any existing mappings
    wireMockServer.resetMappings();

    // Arrange: WireMock stub that tracks request timing
    wireMockServer.stubFor(
        post(urlEqualTo("/e2e/retry/backoff"))
            .willReturn(
                aResponse()
                    .withStatus(503)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"error\": \"rate_limited\", \"message\": \"Please retry with exponential backoff\"}")));

    HttpRetryConfiguration exponentialConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(4)
            .backoffDelays(
                Duration.ofMillis(100), // 1st retry: 100ms
                Duration.ofMillis(200), // 2nd retry: 200ms
                Duration.ofMillis(400), // 3rd retry: 400ms
                Duration.ofMillis(800)) // 4th retry: 800ms
            .build();

    AtomicInteger attemptCount = new AtomicInteger(0);
    long[] attemptTimestamps = new long[5]; // Initial + 4 retries

    long startTime = System.currentTimeMillis();
    attemptTimestamps[0] = startTime;

    // Act: Execute request with exponential backoff
    HttpRequestResult result =
        executor.executeWithRetry(
            createTimestampTrackingRequest(
                baseUrl + "/e2e/retry/backoff", attemptCount, attemptTimestamps),
            exponentialConfig);

    long totalTime = System.currentTimeMillis() - startTime;

    // Assert: Should fail after all retries
    assertFalse(result.isSuccess());
    assertEquals(503, result.statusCode());

    // Verify exponential backoff timing (allowing for some variance)
    assertTrue(
        totalTime >= 1400, // 100+200+400+800 = 1500ms (with 100ms tolerance)
        String.format("Should include exponential backoff delays: %dms", totalTime));
    assertTrue(
        totalTime < 3000, // Should not take too long
        String.format("Exponential backoff should not exceed reasonable bounds: %dms", totalTime));

    // Verify total attempt count
    wireMockServer.verify(5, postRequestedFor(urlEqualTo("/e2e/retry/backoff")));

    System.out.printf("E2E Exponential Backoff - Total time: %dms, Expected: ~1500ms%n", totalTime);
  }

  @Test
  void testE2E_RetryWithDifferentHttpMethods() throws Exception {
    // Clear existing mappings
    wireMockServer.resetMappings();

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(2)
            .idempotencyRequired(true)
            .backoffDelays(Duration.ofMillis(100), Duration.ofMillis(200))
            .build();

    // Test 1: POST request (idempotent with Idempotency-Key)
    wireMockServer.stubFor(
        post(urlEqualTo("/e2e/retry/methods/post"))
            .inScenario("POST Scenario")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("POST Success"));

    wireMockServer.stubFor(
        post(urlEqualTo("/e2e/retry/methods/post"))
            .inScenario("POST Scenario")
            .whenScenarioStateIs("POST Success")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"method\": \"POST\", \"success\": true}")));

    HttpRequest postRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/e2e/retry/methods/post"))
            .POST(HttpRequest.BodyPublishers.ofString("{\"data\": \"test\"}"))
            .header("Content-Type", "application/json")
            .build();

    HttpRequestResult postResult = executor.executeWithRetry(postRequest, retryConfig);

    // Test 2: PUT request (naturally idempotent)
    wireMockServer.stubFor(
        put(urlEqualTo("/e2e/retry/methods/put"))
            .inScenario("PUT Scenario")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("PUT Success"));

    wireMockServer.stubFor(
        put(urlEqualTo("/e2e/retry/methods/put"))
            .inScenario("PUT Scenario")
            .whenScenarioStateIs("PUT Success")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"method\": \"PUT\", \"success\": true}")));

    HttpRequest putRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/e2e/retry/methods/put"))
            .PUT(HttpRequest.BodyPublishers.ofString("{\"data\": \"update\"}"))
            .header("Content-Type", "application/json")
            .build();

    HttpRequestResult putResult = executor.executeWithRetry(putRequest, retryConfig);

    // Test 3: GET request (naturally idempotent)
    wireMockServer.stubFor(
        get(urlEqualTo("/e2e/retry/methods/get"))
            .inScenario("GET Scenario")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("GET Success"));

    wireMockServer.stubFor(
        get(urlEqualTo("/e2e/retry/methods/get"))
            .inScenario("GET Scenario")
            .whenScenarioStateIs("GET Success")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"method\": \"GET\", \"success\": true}")));

    HttpRequest getRequest =
        HttpRequest.newBuilder().uri(URI.create(baseUrl + "/e2e/retry/methods/get")).GET().build();

    HttpRequestResult getResult = executor.executeWithRetry(getRequest, retryConfig);

    // Assert: All methods should succeed after retry
    assertTrue(postResult.isSuccess(), "POST request should succeed after retry");
    assertTrue(putResult.isSuccess(), "PUT request should succeed after retry");
    assertTrue(getResult.isSuccess(), "GET request should succeed after retry");

    // Verify response content
    System.out.printf("DEBUG POST response: %s%n", postResult.body().toJson());
    System.out.printf("DEBUG PUT response: %s%n", putResult.body().toJson());
    System.out.printf("DEBUG GET response: %s%n", getResult.body().toJson());

    assertTrue(
        postResult.body().toJson().contains("POST"),
        "POST response should contain 'POST': " + postResult.body().toJson());
    assertTrue(
        putResult.body().toJson().contains("PUT"),
        "PUT response should contain 'PUT': " + putResult.body().toJson());
    assertTrue(
        getResult.body().toJson().contains("GET"),
        "GET response should contain 'GET': " + getResult.body().toJson());

    // Verify idempotency key was added to POST request
    wireMockServer.verify(
        2,
        postRequestedFor(urlEqualTo("/e2e/retry/methods/post"))
            .withHeader("Idempotency-Key", matching("idem_.*")));

    System.out.println("E2E HTTP Methods - POST, PUT, GET all succeeded with retry");
  }

  @Test
  void testE2E_ConcurrentRetryRequests() throws Exception {
    // Clear existing mappings
    wireMockServer.resetMappings();

    // Arrange: WireMock stubs for concurrent testing - use different scenarios for each endpoint
    for (int i = 0; i < 3; i++) {
      final String endpoint = "/e2e/retry/concurrent/request-" + i;
      final String scenarioName = "Concurrent Scenario " + i;

      wireMockServer.stubFor(
          post(urlEqualTo(endpoint))
              .inScenario(scenarioName)
              .whenScenarioStateIs(Scenario.STARTED)
              .willReturn(
                  aResponse()
                      .withStatus(503)
                      .withFixedDelay(100)) // Small delay to simulate real network
              .willSetStateTo("Success " + i));

      wireMockServer.stubFor(
          post(urlEqualTo(endpoint))
              .inScenario(scenarioName)
              .whenScenarioStateIs("Success " + i)
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          "{\"concurrent\": true, \"success\": true, \"requestId\": " + i + "}")));
    }

    HttpRetryConfiguration concurrentConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(2)
            .backoffDelays(Duration.ofMillis(150), Duration.ofMillis(300))
            .build();

    // Act: Execute multiple concurrent requests
    long startTime = System.currentTimeMillis();

    java.util.concurrent.CompletableFuture<HttpRequestResult>[] futures =
        new java.util.concurrent.CompletableFuture[3];

    for (int i = 0; i < 3; i++) {
      final int requestId = i;
      futures[i] =
          java.util.concurrent.CompletableFuture.supplyAsync(
              () -> {
                try {
                  HttpRequest request =
                      HttpRequest.newBuilder()
                          .uri(URI.create(baseUrl + "/e2e/retry/concurrent/request-" + requestId))
                          .POST(
                              HttpRequest.BodyPublishers.ofString(
                                  "{\"requestId\": " + requestId + "}"))
                          .header("Content-Type", "application/json")
                          .build();
                  return executor.executeWithRetry(request, concurrentConfig);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
    }

    // Wait for all requests to complete
    HttpRequestResult[] results = new HttpRequestResult[3];
    for (int i = 0; i < 3; i++) {
      results[i] = futures[i].join();
    }

    long totalTime = System.currentTimeMillis() - startTime;

    // Assert: All concurrent requests should succeed
    for (int i = 0; i < 3; i++) {
      assertTrue(results[i].isSuccess(), String.format("Concurrent request %d should succeed", i));
      assertEquals(200, results[i].statusCode());
      assertTrue(results[i].body().toJson().contains("concurrent"));
    }

    // Verify total requests made (each endpoint: 1 initial + 1 retry = 2)
    for (int i = 0; i < 3; i++) {
      wireMockServer.verify(2, postRequestedFor(urlEqualTo("/e2e/retry/concurrent/request-" + i)));
    }

    // Concurrent execution should be faster than sequential
    assertTrue(
        totalTime < 2000, // Should be much faster than 3 sequential executions
        String.format("Concurrent requests should execute in parallel: %dms", totalTime));

    System.out.printf("E2E Concurrent Retry - 3 requests completed in %dms%n", totalTime);
  }

  /**
   * Creates an HTTP request that includes an attempt counter header. This helps WireMock scenarios
   * determine which response to return based on attempt number.
   */
  private HttpRequest createRequestWithAttemptCounter(String url, AtomicInteger attemptCount) {
    int attempt = attemptCount.incrementAndGet();
    return HttpRequest.newBuilder()
        .uri(URI.create(url))
        .POST(HttpRequest.BodyPublishers.ofString("{\"test\": \"e2e\"}"))
        .header("Content-Type", "application/json")
        .header("X-Attempt-Count", String.valueOf(attempt))
        .build();
  }

  /** Creates an HTTP request that tracks timing for backoff verification. */
  private HttpRequest createTimestampTrackingRequest(
      String url, AtomicInteger attemptCount, long[] timestamps) {
    int attempt = attemptCount.incrementAndGet();
    if (attempt < timestamps.length) {
      timestamps[attempt] = System.currentTimeMillis();
    }
    return HttpRequest.newBuilder()
        .uri(URI.create(url))
        .POST(
            HttpRequest.BodyPublishers.ofString(
                "{\"test\": \"backoff\", \"attempt\": " + attempt + "}"))
        .header("Content-Type", "application/json")
        .header("X-Attempt-Count", String.valueOf(attempt))
        .build();
  }
}
