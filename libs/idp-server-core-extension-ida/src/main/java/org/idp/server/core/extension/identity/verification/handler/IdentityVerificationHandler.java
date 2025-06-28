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
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplicationIdParam;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplyingExecutionResult;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplyingResult;
import org.idp.server.core.extension.identity.verification.delegation.request.AdditionalRequestParameterResolvers;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationRequestValidator;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationResponseValidator;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationValidationResult;
import org.idp.server.core.extension.identity.verification.verifier.IdentityVerificationRequestVerificationResult;
import org.idp.server.core.extension.identity.verification.verifier.IdentityVerificationRequestVerifiers;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.http.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.platform.oauth.OAuthAuthorizationResolver;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;

public class IdentityVerificationHandler {

  OAuthAuthorizationResolvers authorizationResolvers;
  IdentityVerificationRequestVerifiers requestVerifiers;
  AdditionalRequestParameterResolvers additionalRequestParameterResolvers;
  HttpRequestExecutor httpRequestExecutor;

  public IdentityVerificationHandler() {
    this.authorizationResolvers = new OAuthAuthorizationResolvers();
    this.requestVerifiers = new IdentityVerificationRequestVerifiers();
    this.additionalRequestParameterResolvers = new AdditionalRequestParameterResolvers();
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
  }

  public ExternalWorkflowApplyingResult handleRequest(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);

    IdentityVerificationRequestValidator applicationValidator =
        new IdentityVerificationRequestValidator(processConfig, request);
    IdentityVerificationValidationResult requestValidationResult = applicationValidator.validate();

    if (requestValidationResult.isError()) {
      return ExternalWorkflowApplyingResult.requestError(requestValidationResult);
    }

    IdentityVerificationRequestVerificationResult verifyResult =
        requestVerifiers.verify(
            tenant, user, applications, type, processes, request, verificationConfiguration);

    if (verifyResult.isError()) {

      return ExternalWorkflowApplyingResult.requestVerificationError(
          requestValidationResult, verifyResult);
    }

    OAuthAuthorizationConfiguration oAuthAuthorizationConfig =
        verificationConfiguration.oauthAuthorization();
    HttpRequestStaticHeaders httpRequestStaticHeaders =
        createHttpRequestHeaders(processConfig.httpRequestHeaders(), oAuthAuthorizationConfig);

    HttpRequestStaticBody httpRequestStaticBody =
        resolveStaticBody(
            processConfig.httpRequestStaticBody(),
            tenant,
            user,
            applications,
            type,
            processes,
            request,
            verificationConfiguration);

    HttpRequestResult executionResult =
        httpRequestExecutor.execute(
            processConfig.httpRequestUrl(),
            processConfig.httpMethod(),
            httpRequestStaticHeaders,
            new HttpRequestBaseParams(request.toMap()),
            processConfig.httpRequestDynamicBodyKeys(),
            httpRequestStaticBody);

    ExternalWorkflowApplyingExecutionResult externalWorkflowApplyingExecutionResult =
        new ExternalWorkflowApplyingExecutionResult(executionResult);
    if (!executionResult.isSuccess()) {
      return ExternalWorkflowApplyingResult.executionError(
          requestValidationResult, verifyResult, externalWorkflowApplyingExecutionResult);
    }

    IdentityVerificationResponseValidator responseValidator =
        new IdentityVerificationResponseValidator(processConfig, executionResult.body());
    IdentityVerificationValidationResult responseValidationResult = responseValidator.validate();

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
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {
    Map<String, Object> parameters = new HashMap<>(staticBody.toMap());
    Map<String, Object> additionalParameters =
        additionalRequestParameterResolvers.resolve(
            tenant, user, applications, type, processes, request, verificationConfiguration);
    parameters.putAll(additionalParameters);
    return new HttpRequestStaticBody(parameters);
  }

  private HttpRequestStaticHeaders createHttpRequestHeaders(
      HttpRequestStaticHeaders httpRequestStaticHeaders,
      OAuthAuthorizationConfiguration oAuthAuthorizationConfig) {
    Map<String, String> values = new HashMap<>(httpRequestStaticHeaders.toMap());

    if (oAuthAuthorizationConfig.exists()) {
      OAuthAuthorizationResolver resolver =
          authorizationResolvers.get(oAuthAuthorizationConfig.type());
      String accessToken = resolver.resolve(oAuthAuthorizationConfig);
      values.put("Authorization", "Bearer " + accessToken);
    }

    return new HttpRequestStaticHeaders(values);
  }
}
