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

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpRequestExecutorResponseCriteriaTest {

  @Mock private HttpClient mockHttpClient;

  @Mock private OAuthAuthorizationResolvers mockOAuthResolvers;

  @Mock private HttpResponse<String> mockHttpResponse;

  private HttpRequestExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new HttpRequestExecutor(mockHttpClient, mockOAuthResolvers);
  }

  @Test
  @DisplayName("execute evaluates success criteria on HTTP 200 response")
  void testExecuteEvaluatesSuccessCriteriaOnSuccess() throws Exception {
    // Arrange
    String responseBody = "{\"status\": \"success\", \"data\": {\"id\": \"123\"}}";
    when(mockHttpResponse.statusCode()).thenReturn(200);
    when(mockHttpResponse.body()).thenReturn(responseBody);
    when(mockHttpResponse.headers()).thenReturn(createHeaders());
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockHttpResponse);

    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.status";
    condition.operation = "eq";
    condition.value = "success";

    TestConfig config = new TestConfig();
    config.url = "http://example.com/api";
    config.method = "POST";
    config.responseSuccessCriteria =
        new ResponseSuccessCriteria(List.of(condition), ConditionMatchMode.ALL);

    HttpRequestBaseParams params = new HttpRequestBaseParams(Map.of());

    // Act
    HttpRequestResult result = executor.execute(config, params);

    // Assert
    assertTrue(result.isSuccess(), "Result should be successful when criteria match");
    assertEquals(200, result.statusCode());
  }

  @Test
  @DisplayName(
      "execute returns 502 with original response body when success criteria fails - error field"
          + " exists")
  void testExecuteReturns502WhenCriteriaFailsWithErrorField() throws Exception {
    // Arrange
    String responseBody = "{\"status\": \"success\", \"error\": \"verification_failed\"}";
    when(mockHttpResponse.statusCode()).thenReturn(200);
    when(mockHttpResponse.body()).thenReturn(responseBody);
    when(mockHttpResponse.headers()).thenReturn(createHeaders());
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockHttpResponse);

    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.error";
    condition.operation = "missing";
    condition.value = null;

    TestConfig config = new TestConfig();
    config.url = "http://example.com/api";
    config.method = "POST";
    config.responseSuccessCriteria =
        new ResponseSuccessCriteria(List.of(condition), ConditionMatchMode.ALL);

    HttpRequestBaseParams params = new HttpRequestBaseParams(Map.of());

    // Act
    HttpRequestResult result = executor.execute(config, params);

    // Assert
    assertEquals(502, result.statusCode(), "Should return 502 when criteria not met");
    // Body should preserve original response from external API
    assertTrue(result.body().contains("error"));
    assertEquals("verification_failed", result.body().getValueAsJsonNode("error").asText());
    assertEquals("success", result.body().getValueAsJsonNode("status").asText());
  }

  @Test
  @DisplayName(
      "execute returns 502 with original response body when success criteria fails - status"
          + " mismatch")
  void testExecuteReturns502WhenCriteriaFailsWithStatusMismatch() throws Exception {
    // Arrange
    String responseBody = "{\"status\": \"failed\"}";
    when(mockHttpResponse.statusCode()).thenReturn(200);
    when(mockHttpResponse.body()).thenReturn(responseBody);
    when(mockHttpResponse.headers()).thenReturn(createHeaders());
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockHttpResponse);

    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.status";
    condition.operation = "eq";
    condition.value = "success";

    TestConfig config = new TestConfig();
    config.url = "http://example.com/api";
    config.method = "POST";
    config.responseSuccessCriteria =
        new ResponseSuccessCriteria(List.of(condition), ConditionMatchMode.ALL);

    HttpRequestBaseParams params = new HttpRequestBaseParams(Map.of());

    // Act
    HttpRequestResult result = executor.execute(config, params);

    // Assert
    assertEquals(502, result.statusCode());
    // Body should preserve original response
    assertEquals("failed", result.body().getValueAsJsonNode("status").asText());
  }

  @Test
  @DisplayName("execute preserves original response body directly when criteria fails")
  void testExecutePreservesOriginalResponseWhenCriteriaFails() throws Exception {
    // Arrange
    String responseBody = "{\"status\": \"failed\", \"message\": \"Invalid request\"}";
    when(mockHttpResponse.statusCode()).thenReturn(200);
    when(mockHttpResponse.body()).thenReturn(responseBody);
    when(mockHttpResponse.headers()).thenReturn(createHeaders());
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockHttpResponse);

    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.status";
    condition.operation = "eq";
    condition.value = "success";

    TestConfig config = new TestConfig();
    config.url = "http://example.com/api";
    config.method = "POST";
    config.responseSuccessCriteria =
        new ResponseSuccessCriteria(List.of(condition), ConditionMatchMode.ALL);

    HttpRequestBaseParams params = new HttpRequestBaseParams(Map.of());

    // Act
    HttpRequestResult result = executor.execute(config, params);

    // Assert - original response body preserved directly (not wrapped)
    assertEquals(502, result.statusCode(), "Status code changed to 502");
    assertEquals("failed", result.body().getValueAsJsonNode("status").asText());
    assertEquals("Invalid request", result.body().getValueAsJsonNode("message").asText());
  }

  @Test
  @DisplayName("execute does not evaluate criteria when hasResponseSuccessCriteria is false")
  void testExecuteDoesNotEvaluateCriteriaWhenNotConfigured() throws Exception {
    // Arrange
    String responseBody = "{\"status\": \"failed\"}";
    when(mockHttpResponse.statusCode()).thenReturn(200);
    when(mockHttpResponse.body()).thenReturn(responseBody);
    when(mockHttpResponse.headers()).thenReturn(createHeaders());
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockHttpResponse);

    TestConfig config = new TestConfig();
    config.url = "http://example.com/api";
    config.method = "POST";
    config.responseSuccessCriteria = null; // No criteria

    HttpRequestBaseParams params = new HttpRequestBaseParams(Map.of());

    // Act
    HttpRequestResult result = executor.execute(config, params);

    // Assert
    assertTrue(result.isSuccess(), "Should be successful when no criteria configured");
    assertEquals(200, result.statusCode());
  }

  @Test
  @DisplayName("execute does not evaluate criteria on non-2xx responses")
  void testExecuteDoesNotEvaluateCriteriaOnErrorResponse() throws Exception {
    // Arrange
    String responseBody = "{\"error\": \"Server error\"}";
    when(mockHttpResponse.statusCode()).thenReturn(500);
    when(mockHttpResponse.body()).thenReturn(responseBody);
    when(mockHttpResponse.headers()).thenReturn(createHeaders());
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockHttpResponse);

    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.status";
    condition.operation = "eq";
    condition.value = "success";

    TestConfig config = new TestConfig();
    config.url = "http://example.com/api";
    config.method = "POST";
    config.responseSuccessCriteria =
        new ResponseSuccessCriteria(List.of(condition), ConditionMatchMode.ALL);

    HttpRequestBaseParams params = new HttpRequestBaseParams(Map.of());

    // Act
    HttpRequestResult result = executor.execute(config, params);

    // Assert
    assertEquals(500, result.statusCode(), "Should preserve original error status code");
    assertFalse(result.isSuccess());
  }

  @Test
  @DisplayName("execute returns configured error status code when criteria fails")
  void testExecuteReturnsConfiguredErrorStatusCode() throws Exception {
    // Arrange
    String responseBody = "{\"status\": \"failed\"}";
    when(mockHttpResponse.statusCode()).thenReturn(200);
    when(mockHttpResponse.body()).thenReturn(responseBody);
    when(mockHttpResponse.headers()).thenReturn(createHeaders());
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockHttpResponse);

    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.status";
    condition.operation = "eq";
    condition.value = "success";

    TestConfig config = new TestConfig();
    config.url = "http://example.com/api";
    config.method = "POST";
    config.responseSuccessCriteria =
        new ResponseSuccessCriteria(List.of(condition), ConditionMatchMode.ALL);
    config.responseSuccessCriteria.errorStatusCode = 400; // Custom error code

    HttpRequestBaseParams params = new HttpRequestBaseParams(Map.of());

    // Act
    HttpRequestResult result = executor.execute(config, params);

    // Assert
    assertEquals(400, result.statusCode(), "Should return configured error status code");
    // Body should preserve original response
    assertEquals("failed", result.body().getValueAsJsonNode("status").asText());
  }

  @Test
  @DisplayName("execute returns default 502 when no custom error status code configured")
  void testExecuteReturnsDefault502WhenNoCustomCode() throws Exception {
    // Arrange
    String responseBody = "{\"status\": \"failed\"}";
    when(mockHttpResponse.statusCode()).thenReturn(200);
    when(mockHttpResponse.body()).thenReturn(responseBody);
    when(mockHttpResponse.headers()).thenReturn(createHeaders());
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockHttpResponse);

    ResponseCondition condition = new ResponseCondition();
    condition.path = "$.status";
    condition.operation = "eq";
    condition.value = "success";

    TestConfig config = new TestConfig();
    config.url = "http://example.com/api";
    config.method = "POST";
    config.responseSuccessCriteria =
        new ResponseSuccessCriteria(List.of(condition), ConditionMatchMode.ALL);
    // No errorStatusCode set - should default to 502

    HttpRequestBaseParams params = new HttpRequestBaseParams(Map.of());

    // Act
    HttpRequestResult result = executor.execute(config, params);

    // Assert
    assertEquals(502, result.statusCode(), "Should return default 502 when not configured");
    // Body should preserve original response
    assertEquals("failed", result.body().getValueAsJsonNode("status").asText());
  }

  @Test
  @DisplayName("execute evaluates multiple conditions with ALL match mode")
  void testExecuteEvaluatesMultipleConditionsAllMode() throws Exception {
    // Arrange
    String responseBody = "{\"status\": \"success\", \"verified\": true}";
    when(mockHttpResponse.statusCode()).thenReturn(200);
    when(mockHttpResponse.body()).thenReturn(responseBody);
    when(mockHttpResponse.headers()).thenReturn(createHeaders());
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockHttpResponse);

    ResponseCondition condition1 = new ResponseCondition();
    condition1.path = "$.status";
    condition1.operation = "eq";
    condition1.value = "success";

    ResponseCondition condition2 = new ResponseCondition();
    condition2.path = "$.verified";
    condition2.operation = "eq";
    condition2.value = true;

    TestConfig config = new TestConfig();
    config.url = "http://example.com/api";
    config.method = "POST";
    config.responseSuccessCriteria =
        new ResponseSuccessCriteria(List.of(condition1, condition2), ConditionMatchMode.ALL);

    HttpRequestBaseParams params = new HttpRequestBaseParams(Map.of());

    // Act
    HttpRequestResult result = executor.execute(config, params);

    // Assert
    assertTrue(result.isSuccess());
    assertEquals(200, result.statusCode());
  }

  // Test helper class
  static class TestConfig implements HttpRequestExecutionConfigInterface {
    String url;
    String method;
    ResponseSuccessCriteria responseSuccessCriteria;

    @Override
    public HttpRequestUrl httpRequestUrl() {
      return new HttpRequestUrl(url);
    }

    @Override
    public HttpMethod httpMethod() {
      return HttpMethod.of(method);
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
      return new HmacAuthenticationConfig();
    }

    @Override
    public HttpRequestMappingRules pathMappingRules() {
      return new HttpRequestMappingRules(List.of());
    }

    @Override
    public HttpRequestMappingRules headerMappingRules() {
      return new HttpRequestMappingRules(List.of());
    }

    @Override
    public HttpRequestMappingRules bodyMappingRules() {
      return new HttpRequestMappingRules(List.of());
    }

    @Override
    public HttpRequestMappingRules queryMappingRules() {
      return new HttpRequestMappingRules(List.of());
    }

    @Override
    public boolean hasResponseSuccessCriteria() {
      return responseSuccessCriteria != null
          && responseSuccessCriteria.conditions() != null
          && !responseSuccessCriteria.conditions().isEmpty();
    }

    @Override
    public ResponseSuccessCriteria responseSuccessCriteria() {
      return responseSuccessCriteria != null
          ? responseSuccessCriteria
          : ResponseSuccessCriteria.empty();
    }
  }

  private java.net.http.HttpHeaders createHeaders() {
    return java.net.http.HttpHeaders.of(
        Map.of("Content-Type", List.of("application/json")), (k, v) -> true);
  }
}
