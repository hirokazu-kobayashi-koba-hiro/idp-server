package org.idp.server.core.identity.verification.delegation;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.http.*;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationOAuthAuthorizationConfiguration;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.identity.verification.delegation.request.AdditionalRequestParameterResolvers;
import org.idp.server.core.identity.verification.validation.IdentityVerificationRequestValidator;
import org.idp.server.core.identity.verification.validation.IdentityVerificationResponseValidator;
import org.idp.server.core.identity.verification.validation.IdentityVerificationValidationResult;
import org.idp.server.core.identity.verification.verifier.IdentityVerificationRequestVerificationResult;
import org.idp.server.core.identity.verification.verifier.IdentityVerificationRequestVerifiers;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.oauth.AccessTokenEntity;

public class ExternalWorkflowDelegationClient {

  OAuthAuthorizationResolvers authorizationResolvers;
  IdentityVerificationRequestVerifiers requestVerifiers;
  AdditionalRequestParameterResolvers additionalRequestParameterResolvers;
  HttpRequestExecutor httpRequestExecutor;

  public ExternalWorkflowDelegationClient() {
    this.authorizationResolvers = new OAuthAuthorizationResolvers();
    this.requestVerifiers = new IdentityVerificationRequestVerifiers();
    this.additionalRequestParameterResolvers = new AdditionalRequestParameterResolvers();
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
  }

  public ExternalWorkflowApplyingResult execute(
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

    IdentityVerificationOAuthAuthorizationConfiguration oAuthAuthorizationConfig =
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

  public HttpRequestHeaders createHttpRequestHeaders(
      HttpRequestHeaders httpRequestHeaders,
      IdentityVerificationOAuthAuthorizationConfiguration oAuthAuthorizationConfig) {
    Map<String, String> values = new HashMap<>(httpRequestHeaders.toMap());

    if (oAuthAuthorizationConfig.exists()) {
      OAuthAuthorizationResolver resolver =
          authorizationResolvers.get(oAuthAuthorizationConfig.type());
      AccessTokenEntity accessTokenEntity = resolver.resolve(oAuthAuthorizationConfig);
      values.put("Authorization", "Bearer " + accessTokenEntity.value());
    }

    return new HttpRequestHeaders(values);
  }
}
