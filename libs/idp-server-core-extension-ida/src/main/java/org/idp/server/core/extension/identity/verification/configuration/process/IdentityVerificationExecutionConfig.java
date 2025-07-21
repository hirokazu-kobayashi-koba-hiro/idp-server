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

public class IdentityVerificationExecutionConfig
    implements HttpRequestExecutionConfigInterface, JsonReadable {
  String url;
  String method;
  String authType;
  OAuthAuthorizationConfiguration oauthAuthorization = new OAuthAuthorizationConfiguration();
  HmacAuthenticationConfiguration hmacAuthentication = new HmacAuthenticationConfiguration();
  Map<String, String> staticHeaders = new HashMap<>();
  Map<String, Object> staticBody = new HashMap<>();
  List<MappingRule> pathMappingRules = new ArrayList<>();
  List<MappingRule> headerMappingRules = new ArrayList<>();
  List<MappingRule> bodyMappingRules = new ArrayList<>();
  List<MappingRule> queryMappingRules = new ArrayList<>();

  public IdentityVerificationExecutionConfig() {}

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
  public HmacAuthenticationConfiguration hmacAuthentication() {
    if (hmacAuthentication == null) {
      return new HmacAuthenticationConfiguration();
    }
    return hmacAuthentication;
  }

  @Override
  public HttpRequestStaticHeaders httpRequestStaticHeaders() {
    return new HttpRequestStaticHeaders(staticHeaders);
  }

  @Override
  public HttpRequestMappingRules httpRequestPathMappingRules() {
    return new HttpRequestMappingRules(pathMappingRules);
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

  // FIXME to be more correct
  public boolean exists() {
    return url != null && !url.isEmpty();
  }
}
