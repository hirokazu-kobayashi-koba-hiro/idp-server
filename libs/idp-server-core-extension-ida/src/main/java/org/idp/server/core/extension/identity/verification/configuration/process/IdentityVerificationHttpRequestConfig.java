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

package org.idp.server.core.extension.identity.verification.configuration.process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;

/**
 * Configuration for external HTTP API calls in identity verification process execution phase.
 *
 * <p>Supports response success criteria with customizable error status codes for fine-grained error
 * handling based on external API responses.
 *
 * <p>Example configuration with custom error status code:
 *
 * <pre>{@code
 * {
 *   "url": "https://api.example.com/verify",
 *   "method": "POST",
 *   "auth_type": "oauth2",
 *   "response_success_criteria": {
 *     "conditions": [
 *       {"path": "$.status", "operation": "eq", "value": "approved"},
 *       {"path": "$.error", "operation": "missing"}
 *     ],
 *     "match_mode": "ALL",
 *     "error_status_code": 422
 *   }
 * }
 * }</pre>
 *
 * <p>Common {@code error_status_code} patterns:
 *
 * <ul>
 *   <li>400/422: External API validation errors (input data issues)
 *   <li>401: Authentication failures from external service
 *   <li>403: Permission denied by external service
 *   <li>404: Resource not found in external system
 *   <li>502: External service errors (default if not specified)
 *   <li>503: External service temporarily unavailable
 * </ul>
 */
public class IdentityVerificationHttpRequestConfig
    implements HttpRequestExecutionConfigInterface, JsonReadable {
  String url;
  String method;
  String authType;
  OAuthAuthorizationConfiguration oauthAuthorization = new OAuthAuthorizationConfiguration();
  HmacAuthenticationConfig hmacAuthentication = new HmacAuthenticationConfig();
  List<MappingRule> pathMappingRules = new ArrayList<>();
  List<MappingRule> headerMappingRules = new ArrayList<>();
  List<MappingRule> bodyMappingRules = new ArrayList<>();
  List<MappingRule> queryMappingRules = new ArrayList<>();
  HttpRetryConfiguration retryConfiguration = HttpRetryConfiguration.noRetry();
  Integer requestTimeoutSeconds;
  ResponseSuccessCriteria responseSuccessCriteria;

  public IdentityVerificationHttpRequestConfig() {}

  @Override
  public HttpRequestUrl httpRequestUrl() {
    return new HttpRequestUrl(url);
  }

  @Override
  public HttpMethod httpMethod() {
    return HttpMethod.of(method);
  }

  public boolean isGetHttpMethod() {
    return httpMethod().equals(HttpMethod.GET);
  }

  @Override
  public HttpRequestAuthType httpRequestAuthType() {
    return HttpRequestAuthType.of(authType);
  }

  @Override
  public boolean hasOAuthAuthorization() {
    return oauthAuthorization != null && oauthAuthorization.exists();
  }

  @Override
  public OAuthAuthorizationConfiguration oauthAuthorization() {
    if (oauthAuthorization == null) {
      return new OAuthAuthorizationConfiguration();
    }
    return oauthAuthorization;
  }

  @Override
  public boolean hasHmacAuthentication() {
    return hmacAuthentication != null && hmacAuthentication.exists();
  }

  @Override
  public HmacAuthenticationConfig hmacAuthentication() {
    if (hmacAuthentication == null) {
      return new HmacAuthenticationConfig();
    }
    return hmacAuthentication;
  }

  @Override
  public HttpRequestMappingRules pathMappingRules() {
    return new HttpRequestMappingRules(pathMappingRules);
  }

  public boolean hasPathMappingRules() {
    return pathMappingRules != null && !pathMappingRules.isEmpty();
  }

  public List<Map<String, Object>> pathMappingRulesMap() {
    return pathMappingRules.stream().map(MappingRule::toMap).toList();
  }

  @Override
  public HttpRequestMappingRules headerMappingRules() {
    return new HttpRequestMappingRules(headerMappingRules);
  }

  public boolean hasHeaderMappingRules() {
    return headerMappingRules != null && !headerMappingRules.isEmpty();
  }

  public List<Map<String, Object>> headerMappingRulesMap() {
    return headerMappingRules.stream().map(MappingRule::toMap).toList();
  }

  @Override
  public HttpRequestMappingRules bodyMappingRules() {
    return new HttpRequestMappingRules(bodyMappingRules);
  }

  public boolean hasBodyMappingRules() {
    return bodyMappingRules != null && !bodyMappingRules.isEmpty();
  }

  public List<Map<String, Object>> bodyMappingRulesMap() {
    return bodyMappingRules.stream().map(MappingRule::toMap).toList();
  }

  @Override
  public HttpRequestMappingRules queryMappingRules() {
    return new HttpRequestMappingRules(queryMappingRules);
  }

  public boolean hasQueryMappingRules() {
    return queryMappingRules != null && !queryMappingRules.isEmpty();
  }

  public List<Map<String, Object>> queryMappingRulesMap() {
    return queryMappingRules.stream().map(MappingRule::toMap).toList();
  }

  @Override
  public boolean hasRetryConfiguration() {
    return retryConfiguration != null && retryConfiguration.maxRetries() > 0;
  }

  @Override
  public HttpRetryConfiguration retryConfiguration() {
    if (retryConfiguration == null) {
      return HttpRetryConfiguration.noRetry();
    }
    return retryConfiguration;
  }

  @Override
  public boolean hasRequestTimeout() {
    return requestTimeoutSeconds != null && requestTimeoutSeconds > 0;
  }

  @Override
  public int requestTimeoutSeconds() {
    return requestTimeoutSeconds != null ? requestTimeoutSeconds : 30;
  }

  public boolean hasResponseSuccessCriteria() {
    return responseSuccessCriteria != null
        && responseSuccessCriteria.conditions() != null
        && !responseSuccessCriteria.conditions().isEmpty();
  }

  public ResponseSuccessCriteria responseSuccessCriteria() {
    return responseSuccessCriteria;
  }

  public boolean exists() {
    return url != null && !url.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("url", url);
    map.put("method", method);
    map.put("auth_type", authType);
    if (hasOAuthAuthorization()) map.put("oauth_authorization", oauthAuthorization.toMap());
    if (hasHmacAuthentication()) map.put("hmac_authentication", hmacAuthentication.toMap());
    if (hasPathMappingRules()) map.put("path_mapping_rules", pathMappingRulesMap());
    if (hasHeaderMappingRules()) map.put("header_mapping_rules", headerMappingRulesMap());
    if (hasBodyMappingRules()) map.put("body_mapping_rules", bodyMappingRulesMap());
    if (hasQueryMappingRules()) map.put("query_mapping_rules", queryMappingRulesMap());
    if (hasRetryConfiguration()) map.put("retry_configuration", retryConfiguration.toMap());
    if (hasRequestTimeout()) map.put("request_timeout_seconds", requestTimeoutSeconds);
    if (hasResponseSuccessCriteria())
      map.put("response_success_criteria", responseSuccessCriteria.toMap());
    return map;
  }
}
