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

package org.idp.server.core.extension.identity.verification.application;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationApplicationContext;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationApplyingExecutionResult;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationApplyingResult;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter.AdditionalRequestParameterResolvers;
import org.idp.server.core.extension.identity.verification.application.pre_hook.verification.IdentityVerificationApplicationRequestVerifiedResult;
import org.idp.server.core.extension.identity.verification.application.pre_hook.verification.IdentityVerificationApplicationRequestVerifiers;
import org.idp.server.core.extension.identity.verification.application.validation.IdentityVerificationApplicationRequestValidator;
import org.idp.server.core.extension.identity.verification.application.validation.IdentityVerificationApplicationValidationResult;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationExecutionConfig;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationApplicationRequest;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.platform.oauth.OAuthAuthorizationResolver;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;
import org.idp.server.platform.security.type.RequestAttributes;

public class IdentityVerificationApplicationHandler {

  OAuthAuthorizationResolvers authorizationResolvers;
  IdentityVerificationApplicationRequestVerifiers requestVerifiers;
  AdditionalRequestParameterResolvers additionalRequestParameterResolvers;
  HttpRequestExecutor httpRequestExecutor;

  public IdentityVerificationApplicationHandler() {
    this.authorizationResolvers = new OAuthAuthorizationResolvers();
    this.requestVerifiers = new IdentityVerificationApplicationRequestVerifiers();
    this.additionalRequestParameterResolvers = new AdditionalRequestParameterResolvers();
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
  }

  public IdentityVerificationApplyingResult executeRequest(
      Tenant tenant,
      User user,
      IdentityVerificationApplication currentApplication,
      IdentityVerificationApplications previousApplications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);

    IdentityVerificationApplicationRequestValidator applicationValidator =
        new IdentityVerificationApplicationRequestValidator(processConfig, request);
    IdentityVerificationApplicationValidationResult requestValidationResult =
        applicationValidator.validate();

    if (requestValidationResult.isError()) {
      return IdentityVerificationApplyingResult.requestError(requestValidationResult);
    }

    IdentityVerificationApplicationRequestVerifiedResult verifyResult =
        requestVerifiers.verifyAll(
            tenant,
            user,
            currentApplication,
            previousApplications,
            type,
            processes,
            request,
            requestAttributes,
            verificationConfiguration);

    if (verifyResult.isError()) {

      return IdentityVerificationApplyingResult.requestVerificationError(
          requestValidationResult, verifyResult);
    }

    Map<String, Object> requestBaseParams =
        resolveBaseParams(
            tenant,
            user,
            currentApplication,
            previousApplications,
            type,
            processes,
            request,
            requestAttributes,
            verificationConfiguration);

    HttpRequestResult executionResult =
        execute(new HttpRequestBaseParams(requestBaseParams), processes, verificationConfiguration);

    IdentityVerificationApplyingExecutionResult identityVerificationApplyingExecutionResult =
        new IdentityVerificationApplyingExecutionResult(executionResult);

    if (!executionResult.isSuccess()) {
      return IdentityVerificationApplyingResult.executionError(
          requestValidationResult, verifyResult, identityVerificationApplyingExecutionResult);
    }

    IdentityVerificationApplicationContext applicationContext =
        new IdentityVerificationApplicationContext(requestBaseParams, executionResult);

    return new IdentityVerificationApplyingResult(
        applicationContext,
        requestValidationResult,
        verifyResult,
        identityVerificationApplyingExecutionResult);
  }

  private Map<String, Object> resolveBaseParams(
      Tenant tenant,
      User user,
      IdentityVerificationApplication currentApplication,
      IdentityVerificationApplications previousApplications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfiguration verificationConfiguration) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("request_body", request.toMap());
    parameters.put("request_attributes", requestAttributes.toMap());
    parameters.put("user", user.toMap());
    if (currentApplication.exists()) {
      parameters.put("application", currentApplication.toMap());
    }
    Map<String, Object> additionalParameters =
        additionalRequestParameterResolvers.resolve(
            tenant,
            user,
            previousApplications,
            type,
            processes,
            request,
            requestAttributes,
            verificationConfiguration);
    parameters.putAll(additionalParameters);
    return parameters;
  }

  // TODO to be more simply
  private HttpRequestResult execute(
      HttpRequestBaseParams httpRequestBaseParams,
      IdentityVerificationProcess processes,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    IdentityVerificationExecutionConfig executionConfig = processConfig.execution();

    // TODO to be more correct
    if (!executionConfig.exists()) {
      return new HttpRequestResult(200, Map.of(), JsonNodeWrapper.empty());
    }

    Map<String, String> headers = new HashMap<>(executionConfig.httpRequestStaticHeaders().toMap());

    switch (executionConfig.httpRequestAuthType()) {
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

        return httpRequestExecutor.execute(
            executionConfig.httpRequestUrl(),
            executionConfig.httpMethod(),
            hmacAuthenticationConfig,
            httpRequestBaseParams,
            httpRequestStaticHeaders,
            executionConfig.httpRequestStaticBody(),
            executionConfig.httpRequestPathMappingRules(),
            executionConfig.httpRequestHeaderMappingRules(),
            executionConfig.httpRequestBodyMappingRules());
      }
    }

    return httpRequestExecutor.executeWithDynamicMapping(
        executionConfig.httpRequestUrl(),
        executionConfig.httpMethod(),
        httpRequestBaseParams,
        new HttpRequestStaticHeaders(headers),
        executionConfig.httpRequestStaticBody(),
        executionConfig.httpRequestPathMappingRules(),
        executionConfig.httpRequestHeaderMappingRules(),
        executionConfig.httpRequestBodyMappingRules());
  }
}
