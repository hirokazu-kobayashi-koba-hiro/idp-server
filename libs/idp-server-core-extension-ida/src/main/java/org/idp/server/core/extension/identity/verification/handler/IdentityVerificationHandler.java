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

package org.idp.server.core.extension.identity.verification.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationApplicationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplicationIdParam;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplyingExecutionResult;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplyingResult;
import org.idp.server.core.extension.identity.verification.delegation.request.AdditionalRequestParameterResolvers;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationApplicationRequestValidator;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationApplicationValidationResult;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationResponseValidator;
import org.idp.server.core.extension.identity.verification.verifier.application.IdentityVerificationApplicationRequestVerifiedResult;
import org.idp.server.core.extension.identity.verification.verifier.application.IdentityVerificationApplicationRequestVerifiers;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.http.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.platform.oauth.OAuthAuthorizationResolver;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;
import org.idp.server.platform.security.type.RequestAttributes;

public class IdentityVerificationHandler {

  OAuthAuthorizationResolvers authorizationResolvers;
  IdentityVerificationApplicationRequestVerifiers requestVerifiers;
  AdditionalRequestParameterResolvers additionalRequestParameterResolvers;
  HttpRequestExecutor httpRequestExecutor;

  public IdentityVerificationHandler() {
    this.authorizationResolvers = new OAuthAuthorizationResolvers();
    this.requestVerifiers = new IdentityVerificationApplicationRequestVerifiers();
    this.additionalRequestParameterResolvers = new AdditionalRequestParameterResolvers();
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
  }

  public ExternalWorkflowApplyingResult handleRequest(
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

    IdentityVerificationApplicationRequestValidator applicationValidator =
        new IdentityVerificationApplicationRequestValidator(processConfig, request);
    IdentityVerificationApplicationValidationResult requestValidationResult =
        applicationValidator.validate();

    if (requestValidationResult.isError()) {
      return ExternalWorkflowApplyingResult.requestError(requestValidationResult);
    }

    IdentityVerificationApplicationRequestVerifiedResult verifyResult =
        requestVerifiers.verify(
            tenant,
            user,
            applications,
            type,
            processes,
            request,
            requestAttributes,
            verificationConfiguration);

    if (verifyResult.isError()) {

      return ExternalWorkflowApplyingResult.requestVerificationError(
          requestValidationResult, verifyResult);
    }

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

    HttpRequestResult executionResult =
        execute(
            new HttpRequestBaseParams(request.toMap()),
            httpRequestStaticBody,
            processes,
            verificationConfiguration);

    ExternalWorkflowApplyingExecutionResult externalWorkflowApplyingExecutionResult =
        new ExternalWorkflowApplyingExecutionResult(executionResult);
    if (!executionResult.isSuccess()) {
      return ExternalWorkflowApplyingResult.executionError(
          requestValidationResult, verifyResult, externalWorkflowApplyingExecutionResult);
    }

    IdentityVerificationResponseValidator responseValidator =
        new IdentityVerificationResponseValidator(processConfig, executionResult.body());
    IdentityVerificationApplicationValidationResult responseValidationResult =
        responseValidator.validate();

    ExternalWorkflowApplicationIdParam externalWorkflowApplicationIdParam =
        verificationConfiguration.externalWorkflowApplicationIdParam();

    return new ExternalWorkflowApplyingResult(
        externalWorkflowApplicationIdParam,
        responseValidationResult,
        verifyResult,
        externalWorkflowApplyingExecutionResult,
        responseValidationResult);
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
    Map<String, Object> additionalParameters =
        additionalRequestParameterResolvers.resolve(
            tenant,
            user,
            applications,
            type,
            processes,
            request,
            requestAttributes,
            verificationConfiguration);
    parameters.putAll(additionalParameters);
    return new HttpRequestStaticBody(parameters);
  }

  // TODO to be more simply
  private HttpRequestResult execute(
      HttpRequestBaseParams httpRequestBaseParams,
      HttpRequestStaticBody httpRequestStaticBody,
      IdentityVerificationProcess processes,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    Map<String, String> headers = new HashMap<>(processConfig.httpRequestHeaders().toMap());

    switch (processConfig.httpRequestAuthType()) {
      case OAUTH2 -> {
        OAuthAuthorizationConfiguration oAuthAuthorizationConfig =
            verificationConfiguration.getOAuthAuthorizationConfig(processes);
        OAuthAuthorizationResolver resolver =
            authorizationResolvers.get(oAuthAuthorizationConfig.type());
        String accessToken = resolver.resolve(oAuthAuthorizationConfig);
        headers.put("Authorization", "Bearer " + accessToken);
        HttpRequestStaticHeaders httpRequestStaticHeaders = new HttpRequestStaticHeaders(headers);

        return httpRequestExecutor.execute(
            processConfig.httpRequestUrl(),
            processConfig.httpMethod(),
            httpRequestStaticHeaders,
            httpRequestBaseParams,
            processConfig.httpRequestDynamicBodyKeys(),
            httpRequestStaticBody);
      }
      case HMAC_SHA256 -> {
        HttpRequestStaticHeaders httpRequestStaticHeaders = new HttpRequestStaticHeaders(headers);
        HmacAuthenticationConfiguration hmacAuthenticationConfig =
            verificationConfiguration.getHmacAuthenticationConfig(processes);

        return httpRequestExecutor.execute(
            processConfig.httpRequestUrl(),
            processConfig.httpMethod(),
            hmacAuthenticationConfig,
            httpRequestStaticHeaders,
            httpRequestBaseParams,
            processConfig.httpRequestDynamicBodyKeys(),
            httpRequestStaticBody);
      }
      default -> {
        if (processConfig.hasDynamicBodyKeys()) {

          return httpRequestExecutor.execute(
              processConfig.httpRequestUrl(),
              processConfig.httpMethod(),
              processConfig.httpRequestHeaders(),
              httpRequestBaseParams,
              processConfig.httpRequestDynamicBodyKeys(),
              processConfig.httpRequestStaticBody());
        }

        return httpRequestExecutor.executeWithDynamicMapping(
            processConfig.httpRequestUrl(),
            processConfig.httpMethod(),
            processConfig.httpRequestHeaders(),
            processConfig.httpRequestHeaderMappingRules(),
            processConfig.httpRequestHeaderMappingRules(),
            httpRequestBaseParams,
            processConfig.httpRequestStaticBody());
      }
    }
  }
}
