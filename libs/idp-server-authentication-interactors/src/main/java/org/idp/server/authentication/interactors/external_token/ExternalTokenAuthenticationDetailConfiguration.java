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

package org.idp.server.authentication.interactors.external_token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;

public class ExternalTokenAuthenticationDetailConfiguration
    implements HttpRequestExecutionConfigInterface, JsonReadable {
  String url;
  String method;
  String authType;
  OAuthAuthorizationConfiguration oauthAuthorization = new OAuthAuthorizationConfiguration();
  HmacAuthenticationConfiguration hmacAuthentication = new HmacAuthenticationConfiguration();
  Map<String, String> headers = new HashMap<>();
  List<String> dynamicBodyKeys = new ArrayList<>();
  Map<String, Object> staticBody = new HashMap<>();
  List<MappingRule> headerMappingRules = new ArrayList<>();
  List<MappingRule> bodyMappingRules = new ArrayList<>();
  List<MappingRule> queryMappingRules = new ArrayList<>();
  List<MappingRule> userinfoMappingRules;

  public ExternalTokenAuthenticationDetailConfiguration() {}

  public HttpRequestUrl httpRequestUrl() {
    return new HttpRequestUrl(url);
  }

  public HttpMethod httpMethod() {
    return HttpMethod.of(method);
  }

  public boolean isGetHttpMethod() {
    return httpMethod().isGet();
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
    return oauthAuthorization;
  }

  @Override
  public boolean hasHmacAuthentication() {
    return hmacAuthentication != null && hmacAuthentication.exists();
  }

  @Override
  public HmacAuthenticationConfiguration hmacAuthentication() {
    return hmacAuthentication;
  }

  @Override
  public HttpRequestStaticHeaders httpRequestHeaders() {
    return new HttpRequestStaticHeaders(headers);
  }

  @Override
  public boolean hasDynamicBodyKeys() {
    return dynamicBodyKeys != null && !dynamicBodyKeys.isEmpty();
  }

  @Override
  public HttpRequestDynamicBodyKeys httpRequestDynamicBodyKeys() {
    return new HttpRequestDynamicBodyKeys(dynamicBodyKeys);
  }

  @Override
  public HttpRequestStaticBody httpRequestStaticBody() {
    return new HttpRequestStaticBody(staticBody);
  }

  @Override
  public HttpRequestMappingRules httpRequestHeaderMappingRules() {
    return new HttpRequestMappingRules(headerMappingRules);
  }

  @Override
  public HttpRequestMappingRules httpRequestBodyMappingRules() {
    return new HttpRequestMappingRules(bodyMappingRules);
  }

  @Override
  public HttpRequestMappingRules httpRequestQueryMappingRules() {
    return new HttpRequestMappingRules(queryMappingRules);
  }

  public List<MappingRule> userinfoMappingRules() {
    return userinfoMappingRules;
  }
}
