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
