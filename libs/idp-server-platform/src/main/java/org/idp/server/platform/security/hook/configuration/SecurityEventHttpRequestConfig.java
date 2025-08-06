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

package org.idp.server.platform.security.hook.configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;

public class SecurityEventHttpRequestConfig
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

  public SecurityEventHttpRequestConfig() {}

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

  public boolean exists() {
    return url != null && !url.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("url", url);
    map.put("method", method);
    map.put("authType", authType);
    if (hasOAuthAuthorization()) map.put("oauth_authorization", oauthAuthorization.toMap());
    if (hasHmacAuthentication()) map.put("hmac_authentication", hmacAuthentication.toMap());
    if (hasPathMappingRules()) map.put("path_mapping_rules", pathMappingRulesMap());
    if (hasBodyMappingRules()) map.put("header_mapping_rules", headerMappingRulesMap());
    if (hasBodyMappingRules()) map.put("body_mapping_rules", bodyMappingRulesMap());
    if (hasQueryMappingRules()) map.put("query_mapping_rules", queryMappingRulesMap());
    return map;
  }
}
