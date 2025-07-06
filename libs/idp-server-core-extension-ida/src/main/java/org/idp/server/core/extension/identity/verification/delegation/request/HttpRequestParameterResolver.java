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

package org.idp.server.core.extension.identity.verification.delegation.request;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationApplicationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
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
  public boolean shouldResolve(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    Map<String, Object> additionalParameterSchema =
        processConfig.requestAdditionalParameterSchema();

    if (additionalParameterSchema == null || additionalParameterSchema.isEmpty()) {
      return false;
    }

    return additionalParameterSchema.containsKey("http_request");
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
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);

    HttpRequestStaticBody httpRequestStaticBody =
        resolveStaticBody(
            processConfig.httpRequestStaticBody(),
            tenant,
            user,
            applications,
            type,
            processes,
            request,
            requestAttributes,
            verificationConfiguration);

    Map<String, Object> additionalParameterSchema =
        processConfig.requestAdditionalParameterSchema();
    AdditionalParameterHttpRequestConfiguration configuration =
        jsonConverter.read(
            additionalParameterSchema.get("http_request"),
            AdditionalParameterHttpRequestConfiguration.class);

    HttpRequestResult executionResult =
        execute(new HttpRequestBaseParams(request.toMap()), httpRequestStaticBody, configuration);

    JsonNodeWrapper body = executionResult.body();

    return body.toMap();
  }

  private HttpRequestStaticBody resolveStaticBody(
      HttpRequestStaticBody staticBody,
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfiguration verificationConfiguration) {
    Map<String, Object> parameters = new HashMap<>(staticBody.toMap());
    parameters.putAll(requestAttributes.toMap());

    return new HttpRequestStaticBody(parameters);
  }

  private HttpRequestResult execute(
      HttpRequestBaseParams httpRequestBaseParams,
      HttpRequestStaticBody httpRequestStaticBody,
      HttpRequestExecutionConfigInterface config) {

    Map<String, String> headers = new HashMap<>(config.httpRequestHeaders().toMap());

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
            httpRequestStaticHeaders,
            httpRequestBaseParams,
            config.httpRequestDynamicBodyKeys(),
            httpRequestStaticBody);
      }
    }

    HttpRequestStaticHeaders httpRequestStaticHeaders = new HttpRequestStaticHeaders(headers);
    if (config.hasDynamicBodyKeys()) {

      return httpRequestExecutor.execute(
          config.httpRequestUrl(),
          config.httpMethod(),
          httpRequestStaticHeaders,
          httpRequestBaseParams,
          config.httpRequestDynamicBodyKeys(),
          config.httpRequestStaticBody());
    }

    return httpRequestExecutor.executeWithDynamicMapping(
        config.httpRequestUrl(),
        config.httpMethod(),
        httpRequestStaticHeaders,
        config.httpRequestHeaderMappingRules(),
        config.httpRequestHeaderMappingRules(),
        httpRequestBaseParams,
        config.httpRequestStaticBody());
  }
}
