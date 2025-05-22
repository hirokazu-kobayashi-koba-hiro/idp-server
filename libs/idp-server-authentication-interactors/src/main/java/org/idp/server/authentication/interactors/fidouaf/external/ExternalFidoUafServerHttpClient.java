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


package org.idp.server.authentication.interactors.fidouaf.external;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.authentication.interactors.fidouaf.FidoUafExecutionRequest;
import org.idp.server.basic.http.*;
import org.idp.server.basic.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.basic.oauth.OAuthAuthorizationResolver;
import org.idp.server.basic.oauth.OAuthAuthorizationResolvers;

public class ExternalFidoUafServerHttpClient {

  OAuthAuthorizationResolvers authorizationResolvers;
  HttpRequestExecutor httpRequestExecutor;

  public ExternalFidoUafServerHttpClient() {
    this.authorizationResolvers = new OAuthAuthorizationResolvers();
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
  }

  public ExternalFidoUafServerHttpRequestResult execute(
      FidoUafExecutionRequest request,
      ExternalFidoUafServerExecutionConfiguration configuration,
      OAuthAuthorizationConfiguration oAuthAuthorizationConfig) {

    HttpRequestHeaders httpRequestHeaders =
        createHttpRequestHeaders(configuration.httpRequestHeaders(), oAuthAuthorizationConfig);

    HttpRequestResult executionResult =
        httpRequestExecutor.execute(
            configuration.httpRequestUrl(),
            configuration.httpMethod(),
            httpRequestHeaders,
            new HttpRequestBaseParams(request.toMap()),
            configuration.httpRequestDynamicBodyKeys(),
            configuration.httpRequestStaticBody());

    return new ExternalFidoUafServerHttpRequestResult(executionResult);
  }

  private HttpRequestHeaders createHttpRequestHeaders(
      HttpRequestHeaders httpRequestHeaders,
      OAuthAuthorizationConfiguration oAuthAuthorizationConfig) {
    Map<String, String> values = new HashMap<>(httpRequestHeaders.toMap());

    if (oAuthAuthorizationConfig.exists()) {
      OAuthAuthorizationResolver resolver =
          authorizationResolvers.get(oAuthAuthorizationConfig.type());
      String accessToken = resolver.resolve(oAuthAuthorizationConfig);
      values.put("Authorization", "Bearer " + accessToken);
    }

    return new HttpRequestHeaders(values);
  }
}
