package org.idp.server.core.identity.trustframework.delegation;

import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationResponse;
import org.idp.server.core.identity.trustframework.validation.IdentityVerificationValidationResult;

public class ExternalWorkflowApplyingResult {

  IdentityVerificationValidationResult requestIdValidationResult;
  ExternalWorkflowApplyingExecutionResult executionResult;
  IdentityVerificationValidationResult responseValidationResult;

  public ExternalWorkflowApplyingResult() {}

  public static ExternalWorkflowApplyingResult requestError(
      IdentityVerificationValidationResult requestIdValidationResult) {
    return new ExternalWorkflowApplyingResult(
        requestIdValidationResult,
        new ExternalWorkflowApplyingExecutionResult(),
        new IdentityVerificationValidationResult());
  }

  public static ExternalWorkflowApplyingResult executionError(
      IdentityVerificationValidationResult requestIdValidationResult,
      ExternalWorkflowApplyingExecutionResult executionResult) {
    return new ExternalWorkflowApplyingResult(
        requestIdValidationResult, executionResult, new IdentityVerificationValidationResult());
  }

  public static ExternalWorkflowApplyingResult responseError(
      IdentityVerificationValidationResult requestIdValidationResult,
      ExternalWorkflowApplyingExecutionResult executionResult,
      IdentityVerificationValidationResult responseValidationResult) {
    return new ExternalWorkflowApplyingResult(
        requestIdValidationResult, executionResult, responseValidationResult);
  }

  public ExternalWorkflowApplyingResult(
      IdentityVerificationValidationResult requestIdValidationResult,
      ExternalWorkflowApplyingExecutionResult executionResult,
      IdentityVerificationValidationResult responseValidationResult) {
    this.requestIdValidationResult = requestIdValidationResult;
    this.executionResult = executionResult;
    this.responseValidationResult = responseValidationResult;
  }

  public boolean isError() {
    return requestIdValidationResult.isError()
        || executionResult.isClientError()
        || responseValidationResult.isError();
  }

  public ExternalWorkflowApplicationIdentifier extractApplicationIdentifierFromBody() {
    return new ExternalWorkflowApplicationIdentifier(
        executionResult.extractValueFromBody("application_id"));
  }

  public IdentityVerificationResponse errorResponse() {
    if (requestIdValidationResult.isError()) {
      return requestIdValidationResult.errorResponse();
    }

    if (executionResult.isClientError()) {
      return IdentityVerificationResponse.CLIENT_ERROR(executionResult.body().toMap());
    }

    if (executionResult.isServerError()) {
      return IdentityVerificationResponse.SERVER_ERROR(executionResult.body().toMap());
    }

    return responseValidationResult.errorResponse();
  }

  public JsonNodeWrapper body() {
    return executionResult.body();
  }
}
