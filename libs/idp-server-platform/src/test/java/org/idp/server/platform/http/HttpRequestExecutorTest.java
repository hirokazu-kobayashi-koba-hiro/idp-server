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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HttpRequestExecutorTest {

  @Mock private HttpClient httpClient;
  @Mock private OAuthAuthorizationResolvers oAuthAuthorizationResolvers;
  @Mock private HttpResponse<String> httpResponse;

  private HttpRequestExecutor executor;
  private HttpRequest testRequest;

  @BeforeEach
  void setUp() {
    executor = new HttpRequestExecutor(httpClient, oAuthAuthorizationResolvers);
    testRequest = HttpRequest.newBuilder().uri(URI.create("https://example.com/api")).GET().build();
  }

  @Test
  void testRetryAfterHeaderParsing_Seconds() throws Exception {
    // Arrange: Response with Retry-After: 5 seconds
    when(httpResponse.statusCode()).thenReturn(503);
    when(httpResponse.headers())
        .thenReturn(
            java.net.http.HttpHeaders.of(Map.of("Retry-After", List.of("5")), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse)
        .thenReturn(createSuccessResponse());

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder().maxRetries(1).backoffDelays(Duration.ofSeconds(1)).build();

    long startTime = System.currentTimeMillis();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    long elapsed = System.currentTimeMillis() - startTime;

    // Assert: Should wait approximately 5 seconds
    assertTrue(elapsed >= 4500, "Should wait at least 4.5 seconds for Retry-After");
    assertTrue(elapsed < 6000, "Should not wait more than 6 seconds");
    assertTrue(result.isSuccess());
  }

  @Test
  void testRetryAfterHeaderParsing_HttpDate_Future() throws Exception {
    // Arrange: Response with Retry-After HTTP-date (5 seconds in future)
    Instant futureTime = Instant.now().plusSeconds(5);
    String httpDate =
        DateTimeFormatter.RFC_1123_DATE_TIME.format(futureTime.atOffset(ZoneOffset.UTC));

    when(httpResponse.statusCode()).thenReturn(503);
    when(httpResponse.headers())
        .thenReturn(
            java.net.http.HttpHeaders.of(Map.of("Retry-After", List.of(httpDate)), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse)
        .thenReturn(createSuccessResponse());

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder().maxRetries(1).backoffDelays(Duration.ofSeconds(1)).build();

    long startTime = System.currentTimeMillis();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    long elapsed = System.currentTimeMillis() - startTime;

    // Assert: Current implementation logs HTTP-date but doesn't parse it
    // Should fall back to configured backoff (1 second)
    assertTrue(elapsed >= 900, "Should wait at least 0.9 seconds for backoff");
    assertTrue(elapsed < 2000, "Should not wait more than 2 seconds");
    assertTrue(result.isSuccess());
  }

  @Test
  void testRetryAfterHeaderParsing_HttpDate_Past() throws Exception {
    // Arrange: Response with Retry-After HTTP-date (in the past)
    String pastHttpDate = "Fri, 31 Dec 1999 23:59:59 GMT";

    when(httpResponse.statusCode()).thenReturn(503);
    when(httpResponse.headers())
        .thenReturn(
            java.net.http.HttpHeaders.of(
                Map.of("Retry-After", List.of(pastHttpDate)), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse)
        .thenReturn(createSuccessResponse());

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder().maxRetries(1).backoffDelays(Duration.ofSeconds(1)).build();

    long startTime = System.currentTimeMillis();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    long elapsed = System.currentTimeMillis() - startTime;

    // Assert: Should fall back to configured backoff (not parse HTTP-date)
    assertTrue(elapsed >= 900, "Should wait at least 0.9 seconds for backoff");
    assertTrue(elapsed < 2000, "Should not wait more than 2 seconds");
    assertTrue(result.isSuccess());
  }

  @Test
  void test499Retryable_WithRetryableTrue() throws Exception {
    // Arrange: 499 response with retryable=true in JSON
    String responseBody = "{\"retryable\": true, \"message\": \"Client closed request\"}";

    when(httpResponse.statusCode()).thenReturn(499);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn(responseBody);

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse)
        .thenReturn(createSuccessResponse());

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(1)
            .retryableStatusCodes(java.util.Set.of(499, 500, 502, 503, 504))
            .backoffDelays(Duration.ofMillis(100))
            .build();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    // Assert: Should retry and succeed
    assertTrue(result.isSuccess());
    verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void test499Retryable_WithRetryableFalse() throws Exception {
    // Arrange: 499 response with retryable=false in JSON
    String responseBody = "{\"retryable\": false, \"message\": \"Client closed request\"}";

    when(httpResponse.statusCode()).thenReturn(499);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn(responseBody);

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(1)
            .retryableStatusCodes(java.util.Set.of(499, 500, 502, 503, 504))
            .backoffDelays(Duration.ofMillis(100))
            .build();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    // Assert: Should not retry (retryable=false in response)
    assertFalse(result.isSuccess());
    assertEquals(499, result.statusCode());
    verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void test499NonJsonResponse_ShouldNotRetry() throws Exception {
    // Arrange: 499 response with non-JSON body (empty JSON to avoid parsing errors)
    String responseBody = "{}";

    when(httpResponse.statusCode()).thenReturn(499);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn(responseBody);

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(1)
            .retryableStatusCodes(java.util.Set.of(499, 500, 502, 503, 504))
            .backoffDelays(Duration.ofMillis(100))
            .build();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    // Assert: Should not retry (no retryable field, assume not retryable)
    assertFalse(result.isSuccess());
    assertEquals(499, result.statusCode());
    verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void test499EmptyResponse_ShouldNotRetry() throws Exception {
    // Arrange: 499 response with empty body
    when(httpResponse.statusCode()).thenReturn(499);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn("");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(1)
            .retryableStatusCodes(java.util.Set.of(499, 500, 502, 503, 504))
            .backoffDelays(Duration.ofMillis(100))
            .build();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    // Assert: Should not retry (empty response, assume not retryable)
    assertFalse(result.isSuccess());
    assertEquals(499, result.statusCode());
    verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void test499ActualNonJsonResponse_ShouldReturnGenericError() throws Exception {
    // Arrange: 499 response with actual non-JSON body (causes parsing error)
    String responseBody = "Client closed connection - not JSON";

    when(httpResponse.statusCode()).thenReturn(499);
    when(httpResponse.body()).thenReturn(responseBody);

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(1)
            .retryableStatusCodes(java.util.Set.of(499, 500, 502, 503, 504))
            .backoffDelays(Duration.ofMillis(100))
            .build();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    // Assert: JSON parsing error should convert to 500 status code
    assertFalse(result.isSuccess());
    assertEquals(500, result.statusCode()); // JSON parsing error converts to 500
    verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void testCaseInsensitiveRetryAfterHeader() throws Exception {
    // Arrange: Response with lowercase "retry-after" header
    when(httpResponse.statusCode()).thenReturn(503);
    when(httpResponse.headers())
        .thenReturn(
            java.net.http.HttpHeaders.of(Map.of("retry-after", List.of("3")), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse)
        .thenReturn(createSuccessResponse());

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder().maxRetries(1).backoffDelays(Duration.ofSeconds(1)).build();

    long startTime = System.currentTimeMillis();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    long elapsed = System.currentTimeMillis() - startTime;

    // Assert: Should wait approximately 3 seconds (case-insensitive header matching)
    assertTrue(elapsed >= 2500, "Should wait at least 2.5 seconds for Retry-After");
    assertTrue(elapsed < 4000, "Should not wait more than 4 seconds");
    assertTrue(result.isSuccess());
  }

  @Test
  void testMachineReadableRetryInfo_InErrorResponse() throws Exception {
    // Arrange: IOException during request
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("Connection refused"));

    HttpRetryConfiguration retryConfig = HttpRetryConfiguration.noRetry();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    // Assert: Should contain error info but retryable=false since no retries configured
    assertFalse(result.isSuccess());
    assertEquals(502, result.statusCode());

    // Check the response indicates max retries exceeded (since no retries configured)
    assertTrue(result.body().contains("error"));
    assertEquals("max_retries_exceeded", result.body().getValueOrEmptyAsString("error"));
    assertFalse(result.body().getValueAsBoolean("retryable"));
  }

  @Test
  void testExecuteWithConfigurationRetrySupport() throws Exception {
    // Arrange: Configuration with retry settings
    HttpRequestExecutionConfigInterface configWithRetry = new TestConfigWithRetry();
    HttpRequestBaseParams params = mock(HttpRequestBaseParams.class);

    // Mock a 503 response followed by success
    when(httpResponse.statusCode()).thenReturn(503);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse)
        .thenReturn(createSuccessResponse());

    // Act
    HttpRequestResult result = executor.execute(configWithRetry, params);

    // Assert: Should retry and succeed
    assertTrue(result.isSuccess());
    verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void testExecuteWithConfigurationNoRetrySupport() throws Exception {
    // Arrange: Configuration without retry settings (default behavior)
    HttpRequestExecutionConfigInterface configNoRetry = new TestConfigNoRetry();
    HttpRequestBaseParams params = mock(HttpRequestBaseParams.class);

    // Mock a 503 response
    when(httpResponse.statusCode()).thenReturn(503);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    // Act
    HttpRequestResult result = executor.execute(configNoRetry, params);

    // Assert: Should not retry (backward compatibility)
    assertFalse(result.isSuccess());
    assertEquals(503, result.statusCode());
    verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void testBackoffDelayAccuracy_SingleRetry() throws Exception {
    // Arrange: Single retry with 2 second backoff
    Duration expectedDelay = Duration.ofSeconds(2);
    when(httpResponse.statusCode()).thenReturn(503);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse)
        .thenReturn(createSuccessResponse());

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder().maxRetries(1).backoffDelays(expectedDelay).build();

    long startTime = System.currentTimeMillis();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    long elapsed = System.currentTimeMillis() - startTime;
    long expectedMinMs = expectedDelay.toMillis() - 100; // 100ms tolerance
    long expectedMaxMs = expectedDelay.toMillis() + 500; // 500ms tolerance

    // Assert: Delay accuracy within tolerance
    assertTrue(
        elapsed >= expectedMinMs,
        String.format("Expected delay >= %dms, got %dms", expectedMinMs, elapsed));
    assertTrue(
        elapsed <= expectedMaxMs,
        String.format("Expected delay <= %dms, got %dms", expectedMaxMs, elapsed));
    assertTrue(result.isSuccess());
    verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void testBackoffDelayAccuracy_MultipleRetries() throws Exception {
    // Arrange: Multiple retries with exponential backoff
    Duration[] delays = {
      Duration.ofMillis(500), // 1st retry: 500ms
      Duration.ofSeconds(1), // 2nd retry: 1s
      Duration.ofSeconds(2) // 3rd retry: 2s
    };

    when(httpResponse.statusCode()).thenReturn(503);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse)
        .thenReturn(httpResponse)
        .thenReturn(httpResponse)
        .thenReturn(createSuccessResponse());

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder().maxRetries(3).backoffDelays(delays).build();

    long startTime = System.currentTimeMillis();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    long elapsed = System.currentTimeMillis() - startTime;
    // Total expected: 500ms + 1000ms + 2000ms = 3500ms
    long expectedTotal = 3500;
    long tolerance = 1000; // 1s tolerance for test execution overhead

    // Assert: Total delay accuracy
    assertTrue(
        elapsed >= expectedTotal - tolerance,
        String.format(
            "Expected total delay >= %dms, got %dms", expectedTotal - tolerance, elapsed));
    assertTrue(
        elapsed <= expectedTotal + tolerance,
        String.format(
            "Expected total delay <= %dms, got %dms", expectedTotal + tolerance, elapsed));
    assertTrue(result.isSuccess());
    verify(httpClient, times(4)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void testBackoffDelayAccuracy_MaxRetriesExceeded() throws Exception {
    // Arrange: Max retries exceeded, verify all delays applied
    Duration[] delays = {Duration.ofMillis(200), Duration.ofMillis(400)};

    when(httpResponse.statusCode()).thenReturn(503);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder().maxRetries(2).backoffDelays(delays).build();

    long startTime = System.currentTimeMillis();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    long elapsed = System.currentTimeMillis() - startTime;
    // Total expected: 200ms + 400ms = 600ms
    long expectedTotal = 600;
    long tolerance = 300; // 300ms tolerance

    // Assert: Failed after all retries, but delays were applied
    assertFalse(result.isSuccess());
    assertTrue(
        elapsed >= expectedTotal - tolerance,
        String.format(
            "Expected total delay >= %dms, got %dms", expectedTotal - tolerance, elapsed));
    assertTrue(
        elapsed <= expectedTotal + tolerance,
        String.format(
            "Expected total delay <= %dms, got %dms", expectedTotal + tolerance, elapsed));
    verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void testIdempotencyKeyGeneration_UniqueKeys() throws Exception {
    // Arrange: Multiple requests with different URIs requiring idempotency
    HttpRequest request1 =
        HttpRequest.newBuilder().uri(URI.create("https://example.com/api/v1")).GET().build();
    HttpRequest request2 =
        HttpRequest.newBuilder().uri(URI.create("https://example.com/api/v2")).GET().build();

    when(httpResponse.statusCode()).thenReturn(503);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse)
        .thenReturn(createSuccessResponse())
        .thenReturn(httpResponse)
        .thenReturn(createSuccessResponse());

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(1)
            .idempotencyRequired(true) // Enable idempotency key generation
            .backoffDelays(Duration.ofMillis(10))
            .build();

    // Act: Execute requests and capture idempotency keys
    executor.executeWithRetry(request1, retryConfig);
    executor.executeWithRetry(request2, retryConfig);

    // Assert: Should have 4 total calls (2 initial + 2 retries)
    verify(httpClient, times(4)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

    // Extract sent requests to verify idempotency key headers
    var capturedRequests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient, atLeast(2))
        .send(capturedRequests.capture(), any(HttpResponse.BodyHandler.class));

    var requests = capturedRequests.getAllValues();
    assertTrue(requests.size() >= 2, "Should have captured at least 2 requests");

    // Check that idempotency keys are present when idempotency is required
    boolean foundIdempotencyKey = false;
    for (HttpRequest req : requests) {
      var idempotencyHeaders = req.headers().allValues("Idempotency-Key");
      if (!idempotencyHeaders.isEmpty()) {
        String idempotencyKey = idempotencyHeaders.get(0);
        assertTrue(
            idempotencyKey.startsWith("idem_"),
            "Idempotency key should start with 'idem_': " + idempotencyKey);
        assertTrue(
            idempotencyKey.length() > 15,
            "Idempotency key should be sufficiently long: " + idempotencyKey);
        foundIdempotencyKey = true;
      }
    }
    assertTrue(foundIdempotencyKey, "Should have found at least one idempotency key");
  }

  @Test
  void testIdempotencyKeyConsistency_SameRequestSameKey() throws Exception {
    // Arrange: Same request retried multiple times should use same idempotency key
    when(httpResponse.statusCode()).thenReturn(503);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse)
        .thenReturn(httpResponse)
        .thenReturn(createSuccessResponse());

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(2)
            .idempotencyRequired(true) // Enable idempotency key generation
            .backoffDelays(Duration.ofMillis(10), Duration.ofMillis(20))
            .build();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    // Assert: All retry attempts should use the same idempotency key
    var capturedRequests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient, times(3))
        .send(capturedRequests.capture(), any(HttpResponse.BodyHandler.class));

    var requests = capturedRequests.getAllValues();
    String firstIdempotencyKey = null;

    for (HttpRequest req : requests) {
      var idempotencyHeaders = req.headers().allValues("Idempotency-Key");
      if (!idempotencyHeaders.isEmpty()) {
        String currentKey = idempotencyHeaders.get(0);
        if (firstIdempotencyKey == null) {
          firstIdempotencyKey = currentKey;
        } else {
          assertEquals(
              firstIdempotencyKey,
              currentKey,
              "All retry attempts should use the same idempotency key");
        }
      }
    }

    assertNotNull(firstIdempotencyKey, "Should have generated an idempotency key");
    assertTrue(result.isSuccess());
  }

  @Test
  void testIdempotencyKeyFormat_ValidStructure() throws Exception {
    // Arrange
    when(httpResponse.statusCode()).thenReturn(503);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse)
        .thenReturn(createSuccessResponse());

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(1)
            .idempotencyRequired(true) // Enable idempotency key generation
            .backoffDelays(Duration.ofMillis(10))
            .build();

    // Act
    executor.executeWithRetry(testRequest, retryConfig);

    // Assert: Verify idempotency key format
    var capturedRequests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient, atLeast(1))
        .send(capturedRequests.capture(), any(HttpResponse.BodyHandler.class));

    var requests = capturedRequests.getAllValues();
    boolean foundValidKey = false;

    for (HttpRequest req : requests) {
      var idempotencyHeaders = req.headers().allValues("Idempotency-Key");
      if (!idempotencyHeaders.isEmpty()) {
        String idempotencyKey = idempotencyHeaders.get(0);

        // Verify format: idem_{hash}_{uuid8}
        String[] parts = idempotencyKey.split("_");
        assertEquals(3, parts.length, "Idempotency key should have 3 parts separated by '_'");
        assertEquals("idem", parts[0], "First part should be 'idem'");
        assertTrue(parts[1].length() > 0, "Hash part should not be empty");
        assertEquals(8, parts[2].length(), "UUID part should be 8 characters");

        // Verify UUID part is hexadecimal
        assertTrue(
            parts[2].matches("[0-9a-f]{8}"),
            "UUID part should be 8 hexadecimal characters: " + parts[2]);

        foundValidKey = true;
        break;
      }
    }

    assertTrue(foundValidKey, "Should have found at least one valid idempotency key");
  }

  @Test
  void testIdempotencyKeyAdded_WhenIdempotencyRequired() throws Exception {
    // Arrange: Request with idempotency required should always get idempotency key
    @SuppressWarnings("unchecked")
    HttpResponse<String> successResponse = mock(HttpResponse.class);
    when(successResponse.statusCode()).thenReturn(200);
    when(successResponse.headers())
        .thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(successResponse.body()).thenReturn("{\"success\": true}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(successResponse);

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(1)
            .idempotencyRequired(true) // Require idempotency key
            .backoffDelays(Duration.ofMillis(10))
            .build();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    // Assert: Idempotency key should be added when idempotency is required
    var capturedRequests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient, times(1))
        .send(capturedRequests.capture(), any(HttpResponse.BodyHandler.class));

    HttpRequest sentRequest = capturedRequests.getValue();
    var idempotencyHeaders = sentRequest.headers().allValues("Idempotency-Key");

    assertFalse(
        idempotencyHeaders.isEmpty(),
        "Idempotency key should be added when idempotency is required");

    String idempotencyKey = idempotencyHeaders.get(0);
    assertTrue(
        idempotencyKey.startsWith("idem_"),
        "Idempotency key should start with 'idem_': " + idempotencyKey);
    assertTrue(result.isSuccess());
  }

  @Test
  void testIdempotencyKeyNotAdded_WhenNotRequired() throws Exception {
    // Arrange: Request without idempotency requirement should not get idempotency key
    @SuppressWarnings("unchecked")
    HttpResponse<String> successResponse = mock(HttpResponse.class);
    when(successResponse.statusCode()).thenReturn(200);
    when(successResponse.headers())
        .thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(successResponse.body()).thenReturn("{\"success\": true}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(successResponse);

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(1)
            .idempotencyRequired(false) // Do not require idempotency key
            .backoffDelays(Duration.ofMillis(10))
            .build();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    // Assert: No idempotency key should be added when not required
    var capturedRequests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient, times(1))
        .send(capturedRequests.capture(), any(HttpResponse.BodyHandler.class));

    HttpRequest sentRequest = capturedRequests.getValue();
    var idempotencyHeaders = sentRequest.headers().allValues("Idempotency-Key");

    assertTrue(
        idempotencyHeaders.isEmpty(), "Idempotency key should not be added when not required");
    assertTrue(result.isSuccess());
  }

  @Test
  void testRetryDecisionLogic_StatusCodeNotInRetryableSet() throws Exception {
    // Arrange: 404 not in retryable status codes
    when(httpResponse.statusCode()).thenReturn(404);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{\"error\": \"Not found\"}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(2)
            .retryableStatusCodes(java.util.Set.of(500, 502, 503, 504)) // 404 not included
            .backoffDelays(Duration.ofMillis(10), Duration.ofMillis(20))
            .build();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    // Assert: Should not retry 404 errors
    assertFalse(result.isSuccess());
    assertEquals(404, result.statusCode());
    verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void testRetryDecisionLogic_IdempotencyRequired_NonIdempotentMethod() throws Exception {
    // Arrange: POST request with idempotency required but no Idempotency-Key
    HttpRequest postRequest =
        HttpRequest.newBuilder()
            .uri(URI.create("https://example.com/api"))
            .POST(HttpRequest.BodyPublishers.ofString("{\"data\": \"test\"}"))
            .build();

    when(httpResponse.statusCode()).thenReturn(503);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(1)
            .idempotencyRequired(true) // Require idempotency key
            .backoffDelays(Duration.ofMillis(10))
            .build();

    // Act
    HttpRequestResult result = executor.executeWithRetry(postRequest, retryConfig);

    // Assert: Should retry because idempotency key is automatically generated
    verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

    // Verify idempotency key was added for retry attempts
    var capturedRequests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient, atLeast(1))
        .send(capturedRequests.capture(), any(HttpResponse.BodyHandler.class));

    boolean foundIdempotencyKey = false;
    for (HttpRequest req : capturedRequests.getAllValues()) {
      var idempotencyHeaders = req.headers().allValues("Idempotency-Key");
      if (!idempotencyHeaders.isEmpty()) {
        foundIdempotencyKey = true;
        break;
      }
    }
    assertTrue(foundIdempotencyKey, "Idempotency key should be generated for retry attempts");
  }

  @Test
  void testRetryDecisionLogic_ExceptionTypes_RetryableVsNonRetryable() throws Exception {
    // Test 1: IOException (retryable by default)
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new java.io.IOException("Connection timeout"))
        .thenReturn(createSuccessResponse());

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder().maxRetries(1).backoffDelays(Duration.ofMillis(10)).build();

    HttpRequestResult result1 = executor.executeWithRetry(testRequest, retryConfig);
    assertTrue(result1.isSuccess(), "IOException should be retryable and eventually succeed");

    // Reset mock for test 2
    reset(httpClient);

    // Test 2: IllegalArgumentException (not retryable by default)
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IllegalArgumentException("Invalid request"));

    HttpRequestResult result2 = executor.executeWithRetry(testRequest, retryConfig);
    assertFalse(result2.isSuccess(), "IllegalArgumentException should not be retryable");
    assertEquals(500, result2.statusCode(), "Non-retryable exceptions should return 500");
  }

  @Test
  void testRetryDecisionLogic_CustomRetryableExceptions() throws Exception {
    // Arrange: Custom exception configuration
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new java.net.SocketTimeoutException("Custom timeout"))
        .thenReturn(createSuccessResponse());

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(1)
            .retryableExceptions(java.util.Set.of(java.net.SocketTimeoutException.class))
            .backoffDelays(Duration.ofMillis(10))
            .build();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    // Assert: Custom retryable exception should be retried
    assertTrue(result.isSuccess());
    verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void testRetryDecisionLogic_EmptyRetryConfiguration_NoRetries() throws Exception {
    // Arrange: Empty retry configuration (maxRetries = 0)
    when(httpResponse.statusCode()).thenReturn(503);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    HttpRetryConfiguration retryConfig = HttpRetryConfiguration.noRetry();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    // Assert: No retries should be attempted
    assertFalse(result.isSuccess());
    assertEquals(503, result.statusCode());
    verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void testRetryDecisionLogic_MixedConditions_StatusCodeAndException() throws Exception {
    // Arrange: Mix of retryable status and exception scenarios
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new java.io.IOException("Network error")) // Retryable exception
        .thenReturn(createErrorResponse(502)) // Retryable status
        .thenReturn(createSuccessResponse()); // Success

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(2)
            .backoffDelays(Duration.ofMillis(10), Duration.ofMillis(20))
            .build();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    // Assert: Should retry both exception and error status, then succeed
    assertTrue(result.isSuccess());
    verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void testRetryDecisionLogic_EdgeCase_ZeroBackoffDelay() throws Exception {
    // Arrange: Zero backoff delay (immediate retry)
    when(httpResponse.statusCode()).thenReturn(503);
    when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(httpResponse.body()).thenReturn("{}");

    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse)
        .thenReturn(createSuccessResponse());

    HttpRetryConfiguration retryConfig =
        HttpRetryConfiguration.builder()
            .maxRetries(1)
            .backoffDelays(Duration.ZERO) // Zero delay
            .build();

    long startTime = System.currentTimeMillis();

    // Act
    HttpRequestResult result = executor.executeWithRetry(testRequest, retryConfig);

    long elapsed = System.currentTimeMillis() - startTime;

    // Assert: Should complete quickly with zero delay
    assertTrue(result.isSuccess());
    assertTrue(elapsed < 100, "Zero backoff delay should complete quickly: " + elapsed + "ms");
    verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  private HttpResponse<String> createErrorResponse(int statusCode) {
    @SuppressWarnings("unchecked")
    HttpResponse<String> errorResponse = mock(HttpResponse.class);
    when(errorResponse.statusCode()).thenReturn(statusCode);
    when(errorResponse.headers())
        .thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(errorResponse.body()).thenReturn("{\"error\": \"Server error\"}");
    return errorResponse;
  }

  private HttpResponse<String> createSuccessResponse() {
    @SuppressWarnings("unchecked")
    HttpResponse<String> successResponse = mock(HttpResponse.class);
    when(successResponse.statusCode()).thenReturn(200);
    when(successResponse.headers())
        .thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
    when(successResponse.body()).thenReturn("{\"success\": true}");
    return successResponse;
  }

  // Test implementation of HttpRequestExecutionConfigInterface with retry
  private static class TestConfigWithRetry implements HttpRequestExecutionConfigInterface {
    @Override
    public HttpRequestUrl httpRequestUrl() {
      return new HttpRequestUrl("https://example.com/api");
    }

    @Override
    public HttpMethod httpMethod() {
      return HttpMethod.POST;
    }

    @Override
    public HttpRequestAuthType httpRequestAuthType() {
      return HttpRequestAuthType.NONE;
    }

    @Override
    public boolean hasOAuthAuthorization() {
      return false;
    }

    @Override
    public org.idp.server.platform.oauth.OAuthAuthorizationConfiguration oauthAuthorization() {
      return null;
    }

    @Override
    public boolean hasHmacAuthentication() {
      return false;
    }

    @Override
    public HmacAuthenticationConfig hmacAuthentication() {
      return null;
    }

    @Override
    public HttpRequestMappingRules pathMappingRules() {
      return new HttpRequestMappingRules();
    }

    @Override
    public HttpRequestMappingRules headerMappingRules() {
      return new HttpRequestMappingRules();
    }

    @Override
    public HttpRequestMappingRules bodyMappingRules() {
      return new HttpRequestMappingRules();
    }

    @Override
    public HttpRequestMappingRules queryMappingRules() {
      return new HttpRequestMappingRules();
    }

    @Override
    public boolean hasRetryConfiguration() {
      return true;
    }

    @Override
    public HttpRetryConfiguration retryConfiguration() {
      return HttpRetryConfiguration.builder()
          .maxRetries(1)
          .backoffDelays(Duration.ofMillis(100))
          .build();
    }
  }

  // Test implementation of HttpRequestExecutionConfigInterface without retry
  private static class TestConfigNoRetry implements HttpRequestExecutionConfigInterface {
    @Override
    public HttpRequestUrl httpRequestUrl() {
      return new HttpRequestUrl("https://example.com/api");
    }

    @Override
    public HttpMethod httpMethod() {
      return HttpMethod.POST;
    }

    @Override
    public HttpRequestAuthType httpRequestAuthType() {
      return HttpRequestAuthType.NONE;
    }

    @Override
    public boolean hasOAuthAuthorization() {
      return false;
    }

    @Override
    public org.idp.server.platform.oauth.OAuthAuthorizationConfiguration oauthAuthorization() {
      return null;
    }

    @Override
    public boolean hasHmacAuthentication() {
      return false;
    }

    @Override
    public HmacAuthenticationConfig hmacAuthentication() {
      return null;
    }

    @Override
    public HttpRequestMappingRules pathMappingRules() {
      return new HttpRequestMappingRules();
    }

    @Override
    public HttpRequestMappingRules headerMappingRules() {
      return new HttpRequestMappingRules();
    }

    @Override
    public HttpRequestMappingRules bodyMappingRules() {
      return new HttpRequestMappingRules();
    }

    @Override
    public HttpRequestMappingRules queryMappingRules() {
      return new HttpRequestMappingRules();
    }

    // Uses default implementation: hasRetryConfiguration() returns false
  }
}
