package org.idp.server.core.extension.identity.verification.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.http.*;
import org.idp.server.basic.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.basic.oauth.OAuthAuthorizationResolver;
import org.idp.server.basic.oauth.OAuthAuthorizationResolvers;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplicationIdParam;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplyingExecutionResult;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplyingResult;
import org.idp.server.core.identity.User;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.delegation.request.AdditionalRequestParameterResolvers;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationRequestValidator;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationResponseValidator;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationValidationResult;
import org.idp.server.core.extension.identity.verification.verifier.IdentityVerificationRequestVerificationResult;
import org.idp.server.core.extension.identity.verification.verifier.IdentityVerificationRequestVerifiers;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

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
    HttpRequestHeaders httpRequestHeaders =
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
            httpRequestHeaders,
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
