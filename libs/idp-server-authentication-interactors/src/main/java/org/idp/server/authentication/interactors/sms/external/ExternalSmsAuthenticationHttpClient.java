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

package org.idp.server.authentication.interactors.sms.external;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.authentication.interactors.sms.SmsAuthenticationExecutionRequest;
import org.idp.server.platform.http.*;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.platform.oauth.OAuthAuthorizationResolver;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;

public class ExternalSmsAuthenticationHttpClient {

  OAuthAuthorizationResolvers authorizationResolvers;
  HttpRequestExecutor httpRequestExecutor;

  public ExternalSmsAuthenticationHttpClient() {
    this.authorizationResolvers = new OAuthAuthorizationResolvers();
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
  }

  public ExternalSmsAuthenticationHttpRequestResult execute(
      SmsAuthenticationExecutionRequest request,
      ExternalSmsAuthenticationExecutionConfiguration configuration) {

    HttpRequestResult executionResult =
        execute(new HttpRequestBaseParams(request.toMap()), configuration);

    return new ExternalSmsAuthenticationHttpRequestResult(executionResult);
  }

  private HttpRequestResult execute(
      HttpRequestBaseParams httpRequestBaseParams,
      ExternalSmsAuthenticationExecutionConfiguration configuration) {

    Map<String, String> headers = new HashMap<>(configuration.httpRequestHeaders().toMap());

    switch (configuration.httpRequestAuthType()) {
      case OAUTH2 -> {
        OAuthAuthorizationConfiguration oAuthAuthorizationConfig =
            configuration.oauthAuthorization();
        OAuthAuthorizationResolver resolver =
            authorizationResolvers.get(oAuthAuthorizationConfig.type());
        String accessToken = resolver.resolve(oAuthAuthorizationConfig);
        headers.put("Authorization", "Bearer " + accessToken);
      }
      case HMAC_SHA256 -> {
        HttpRequestStaticHeaders httpRequestStaticHeaders = new HttpRequestStaticHeaders(headers);
        HmacAuthenticationConfiguration hmacAuthenticationConfig =
            configuration.hmacAuthentication();

        return httpRequestExecutor.execute(
            configuration.httpRequestUrl(),
            configuration.httpMethod(),
            hmacAuthenticationConfig,
            httpRequestBaseParams,
            httpRequestStaticHeaders,
            configuration.httpRequestStaticBody(),
            configuration.httpRequestPathMappingRules(),
            configuration.httpRequestHeaderMappingRules(),
            configuration.httpRequestBodyMappingRules());
      }
    }

    return httpRequestExecutor.executeWithDynamicMapping(
        configuration.httpRequestUrl(),
        configuration.httpMethod(),
        httpRequestBaseParams,
        new HttpRequestStaticHeaders(headers),
        configuration.httpRequestStaticBody(),
        configuration.httpRequestPathMappingRules(),
        configuration.httpRequestHeaderMappingRules(),
        configuration.httpRequestHeaderMappingRules());
  }
}
