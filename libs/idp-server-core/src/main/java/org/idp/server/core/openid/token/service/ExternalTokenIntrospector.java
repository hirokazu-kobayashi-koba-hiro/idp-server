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

package org.idp.server.core.openid.token.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.idp.server.core.openid.oauth.configuration.client.AvailableFederation;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.platform.http.HttpRequestBaseParams;
import org.idp.server.platform.http.HttpRequestExecutionConfig;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * ExternalTokenIntrospector
 *
 * <p>Introspects opaque tokens at an external IdP's introspection endpoint. Used by Token Exchange
 * (RFC 8693) when the subject_token is not a JWT.
 */
public class ExternalTokenIntrospector {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(ExternalTokenIntrospector.class);

  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public ExternalTokenIntrospector(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public ExternalIntrospectionResult introspect(String token, AvailableFederation federation) {
    if (!federation.hasIntrospectionEndpoint()) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format(
              "Federation '%s' does not have an introspection endpoint configured for opaque token validation",
              federation.issuer()));
    }

    try {
      HttpRequest request = buildIntrospectionRequest(token, federation);

      HttpRequestResult result = httpRequestExecutor.execute(request);

      if (result.isClientError() || result.isServerError()) {
        log.error(
            "External introspection failed. endpoint={}, issuer={}, statusCode={}, response={}",
            federation.introspectionEndpoint(),
            federation.issuer(),
            result.statusCode(),
            result.body());
        throw new TokenBadRequestException(
            "invalid_grant",
            String.format(
                "External introspection failed at '%s': HTTP %d",
                federation.introspectionEndpoint(), result.statusCode()));
      }

      String responseBody = result.body().toString();
      log.debug(
          "External introspection succeeded. endpoint={}, issuer={}",
          federation.introspectionEndpoint(),
          federation.issuer());
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = jsonConverter.read(responseBody, Map.class);

      return new ExternalIntrospectionResult(responseMap);

    } catch (TokenBadRequestException e) {
      throw e;
    } catch (Exception e) {
      log.error("Error introspecting token at external IdP", e);
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format(
              "Failed to introspect token at '%s': %s",
              federation.introspectionEndpoint(), e.getMessage()));
    }
  }

  /**
   * Fetches additional user information from the external IdP using configured HTTP requests. The
   * access_token is passed as a base parameter so that mapping rules can reference it (e.g. for
   * Authorization header).
   *
   * @param accessToken the subject_token (opaque access token)
   * @param federation the federation configuration containing userinfoHttpRequests
   * @return merged claims from all HTTP responses, or empty map if no requests configured
   */
  public Map<String, Object> fetchUserinfo(String accessToken, AvailableFederation federation) {
    if (!federation.hasUserinfoHttpRequests()) {
      return Collections.emptyMap();
    }

    List<HttpRequestExecutionConfig> configs = federation.userinfoHttpRequests();
    Map<String, Object> param = new HashMap<>();
    param.put("request_body", Map.of("access_token", accessToken));

    List<HttpRequestResult> httpRequestResults = new ArrayList<>();
    for (HttpRequestExecutionConfig config : configs) {
      HttpRequestBaseParams baseParams = new HttpRequestBaseParams(param);
      HttpRequestResult result = httpRequestExecutor.execute(config, baseParams);

      if (result.isClientError() || result.isServerError()) {
        log.warn(
            "Userinfo HTTP request failed. issuer={}, statusCode={}",
            federation.issuer(),
            result.statusCode());
        return Collections.emptyMap();
      }

      httpRequestResults.add(result);
      param.put(
          "execution_http_requests",
          httpRequestResults.stream().map(HttpRequestResult::toMap).toList());
    }

    Map<String, Object> results = new HashMap<>();
    results.put(
        "userinfo_execution_http_requests",
        httpRequestResults.stream().map(HttpRequestResult::toMap).toList());
    return results;
  }

  public static class ExternalIntrospectionResult {
    Map<String, Object> claims;

    public ExternalIntrospectionResult(Map<String, Object> claims) {
      this.claims = claims;
    }

    public boolean isActive() {
      Object active = claims.get("active");
      return Boolean.TRUE.equals(active);
    }

    public String subject() {
      Object sub = claims.get("sub");
      return sub != null ? sub.toString() : null;
    }

    public String issuer() {
      Object iss = claims.get("iss");
      return iss != null ? iss.toString() : null;
    }

    public boolean hasSubject() {
      return subject() != null && !subject().isEmpty();
    }

    public Map<String, Object> claims() {
      return claims;
    }
  }

  private HttpRequest buildIntrospectionRequest(String token, AvailableFederation federation) {
    String body =
        "token="
            + URLEncoder.encode(token, StandardCharsets.UTF_8)
            + "&token_type_hint=access_token";

    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(federation.introspectionEndpoint()))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json");

    if (federation.isIntrospectionBasicAuth()) {
      String credentials =
          federation.introspectionClientId() + ":" + federation.introspectionClientSecret();
      String basicAuth =
          "Basic "
              + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
      builder.header("Authorization", basicAuth);
    } else {
      body +=
          "&client_id="
              + URLEncoder.encode(federation.introspectionClientId(), StandardCharsets.UTF_8)
              + "&client_secret="
              + URLEncoder.encode(federation.introspectionClientSecret(), StandardCharsets.UTF_8);
    }

    return builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
  }
}
