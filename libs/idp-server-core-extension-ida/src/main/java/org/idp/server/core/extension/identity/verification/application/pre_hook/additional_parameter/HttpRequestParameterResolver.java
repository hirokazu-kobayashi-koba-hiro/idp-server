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

package org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfig;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationApplicationRequest;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.platform.oauth.OAuthAuthorizationResolver;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;
import org.idp.server.platform.security.type.RequestAttributes;

// TODO to be more readable and handling error
public class HttpRequestParameterResolver implements AdditionalRequestParameterResolver {

  OAuthAuthorizationResolvers authorizationResolvers;
  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public HttpRequestParameterResolver() {
    this.authorizationResolvers = new OAuthAuthorizationResolvers();
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public String type() {
    return "http_request";
  }

  @Override
  public Map<String, Object> resolve(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfig additionalParameterConfig) {

    HttpRequestBaseParams baseParams = resolveBaseParams(request, requestAttributes);

    AdditionalParameterHttpRequestConfiguration configuration =
        jsonConverter.read(
            additionalParameterConfig.details(), AdditionalParameterHttpRequestConfiguration.class);

    HttpRequestResult executionResult = execute(baseParams, configuration);

    JsonNodeWrapper body = executionResult.body();
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("additional_parameter_http.header", executionResult.headers());
    parameters.put("additional_parameter_http.body", body.toMap());

    return parameters;
  }

  private HttpRequestBaseParams resolveBaseParams(
      IdentityVerificationApplicationRequest request, RequestAttributes requestAttributes) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("body", request.toMap());
    parameters.put("attributes", requestAttributes.toMap());

    return new HttpRequestBaseParams(parameters);
  }

  private HttpRequestResult execute(
      HttpRequestBaseParams httpRequestBaseParams, HttpRequestExecutionConfigInterface config) {

    Map<String, String> headers = new HashMap<>(config.httpRequestStaticHeaders().toMap());

    switch (config.httpRequestAuthType()) {
      case OAUTH2 -> {
        OAuthAuthorizationConfiguration oAuthAuthorizationConfig = config.oauthAuthorization();
        OAuthAuthorizationResolver resolver =
            authorizationResolvers.get(oAuthAuthorizationConfig.type());
        String accessToken = resolver.resolve(oAuthAuthorizationConfig);
        headers.put("Authorization", "Bearer " + accessToken);
      }
      case HMAC_SHA256 -> {
        HttpRequestStaticHeaders httpRequestStaticHeaders = new HttpRequestStaticHeaders(headers);
        HmacAuthenticationConfiguration hmacAuthenticationConfig = config.hmacAuthentication();

        return httpRequestExecutor.execute(
            config.httpRequestUrl(),
            config.httpMethod(),
            hmacAuthenticationConfig,
            httpRequestBaseParams,
            httpRequestStaticHeaders,
            config.httpRequestStaticBody(),
            config.httpRequestPathMappingRules(),
            config.httpRequestHeaderMappingRules(),
            config.httpRequestBodyMappingRules());
      }
    }

    HttpRequestStaticHeaders httpRequestStaticHeaders = new HttpRequestStaticHeaders(headers);

    return httpRequestExecutor.executeWithDynamicMapping(
        config.httpRequestUrl(),
        config.httpMethod(),
        httpRequestBaseParams,
        httpRequestStaticHeaders,
        config.httpRequestStaticBody(),
        config.httpRequestPathMappingRules(),
        config.httpRequestHeaderMappingRules(),
        config.httpRequestBodyMappingRules());
  }
}
