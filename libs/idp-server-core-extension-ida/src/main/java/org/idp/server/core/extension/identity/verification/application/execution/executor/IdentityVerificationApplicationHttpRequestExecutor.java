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

package org.idp.server.core.extension.identity.verification.application.execution.executor;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationApplicationExecutor;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationExecutionResult;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationExecutionStatus;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationExecutionConfig;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationHttpRequestConfig;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessConfiguration;
import org.idp.server.platform.http.*;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.platform.oauth.OAuthAuthorizationResolver;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;

public class IdentityVerificationApplicationHttpRequestExecutor
    implements IdentityVerificationApplicationExecutor {

  OAuthAuthorizationResolvers authorizationResolvers;
  HttpRequestExecutor httpRequestExecutor;

  public IdentityVerificationApplicationHttpRequestExecutor() {
    this.authorizationResolvers = new OAuthAuthorizationResolvers();
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
  }

  @Override
  public String type() {
    return "http_request";
  }

  @Override
  public IdentityVerificationExecutionResult execute(
      Map<String, Object> parameters,
      IdentityVerificationProcess processes,
      IdentityVerificationConfiguration verificationConfiguration) {
    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    IdentityVerificationExecutionConfig executionConfig = processConfig.execution();
    IdentityVerificationHttpRequestConfig httpRequestConfig = executionConfig.httpRequest();

    HttpRequestBaseParams httpRequestBaseParams = new HttpRequestBaseParams(parameters);

    Map<String, String> headers =
        new HashMap<>(httpRequestConfig.httpRequestStaticHeaders().toMap());

    switch (httpRequestConfig.httpRequestAuthType()) {
      case OAUTH2 -> {
        OAuthAuthorizationConfiguration oAuthAuthorizationConfig =
            verificationConfiguration.getOAuthAuthorizationConfig(processes);
        OAuthAuthorizationResolver resolver =
            authorizationResolvers.get(oAuthAuthorizationConfig.type());
        String accessToken = resolver.resolve(oAuthAuthorizationConfig);
        headers.put("Authorization", "Bearer " + accessToken);
      }
      case HMAC_SHA256 -> {
        HttpRequestStaticHeaders httpRequestStaticHeaders = new HttpRequestStaticHeaders(headers);
        HmacAuthenticationConfiguration hmacAuthenticationConfig =
            verificationConfiguration.getHmacAuthenticationConfig(processes);

        HttpRequestResult httpRequestResult =
            httpRequestExecutor.execute(
                httpRequestConfig.httpRequestUrl(),
                httpRequestConfig.httpMethod(),
                hmacAuthenticationConfig,
                httpRequestBaseParams,
                httpRequestStaticHeaders,
                httpRequestConfig.httpRequestStaticBody(),
                httpRequestConfig.httpRequestPathMappingRules(),
                httpRequestConfig.httpRequestHeaderMappingRules(),
                httpRequestConfig.httpRequestBodyMappingRules());

        return new IdentityVerificationExecutionResult(
            resolveStatus(httpRequestResult), resolveResult(httpRequestResult));
      }
    }

    HttpRequestResult httpRequestResult =
        httpRequestExecutor.executeWithDynamicMapping(
            httpRequestConfig.httpRequestUrl(),
            httpRequestConfig.httpMethod(),
            httpRequestBaseParams,
            new HttpRequestStaticHeaders(headers),
            httpRequestConfig.httpRequestStaticBody(),
            httpRequestConfig.httpRequestPathMappingRules(),
            httpRequestConfig.httpRequestHeaderMappingRules(),
            httpRequestConfig.httpRequestBodyMappingRules());

    return new IdentityVerificationExecutionResult(
        resolveStatus(httpRequestResult), resolveResult(httpRequestResult));
  }

  private IdentityVerificationExecutionStatus resolveStatus(HttpRequestResult httpRequestResult) {
    if (httpRequestResult.isClientError()) {
      return IdentityVerificationExecutionStatus.CLIENT_ERROR;
    }

    if (httpRequestResult.isServerError()) {
      return IdentityVerificationExecutionStatus.SERVER_ERROR;
    }

    return IdentityVerificationExecutionStatus.OK;
  }

  private Map<String, Object> resolveResult(HttpRequestResult httpRequestResult) {
    Map<String, Object> result = new HashMap<>();
    result.put("response_status", httpRequestResult.statusCode());
    result.put("response_headers", httpRequestResult.headers());
    result.put("response_body", httpRequestResult.body().toMap());

    return result;
  }
}
