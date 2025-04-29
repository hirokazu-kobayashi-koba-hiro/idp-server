package org.idp.server.core.identity.verification.delegation;

import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.identity.verification.io.IdentityVerificationResponse;
import org.idp.server.core.identity.verification.validation.IdentityVerificationValidationResult;
import org.idp.server.core.identity.verification.verifier.IdentityVerificationRequestVerificationResult;

public class ExternalWorkflowApplyingResult {

  ExternalWorkflowApplicationIdParam externalApplicationIdParam;
  IdentityVerificationValidationResult requestIdValidationResult;
  IdentityVerificationRequestVerificationResult verifyResult;
  ExternalWorkflowApplyingExecutionResult executionResult;
  IdentityVerificationValidationResult responseValidationResult;

  public ExternalWorkflowApplyingResult() {}

  public static ExternalWorkflowApplyingResult requestError(
      IdentityVerificationValidationResult requestIdValidationResult) {
    return new ExternalWorkflowApplyingResult(
        new ExternalWorkflowApplicationIdParam(),
        requestIdValidationResult,
        IdentityVerificationRequestVerificationResult.empty(),
        new ExternalWorkflowApplyingExecutionResult(),
        new IdentityVerificationValidationResult());
  }

  public static ExternalWorkflowApplyingResult requestVerificationError(
      IdentityVerificationValidationResult requestIdValidationResult,
      IdentityVerificationRequestVerificationResult verifyResult) {
    return new ExternalWorkflowApplyingResult(
        new ExternalWorkflowApplicationIdParam(),
        requestIdValidationResult,
        verifyResult,
        new ExternalWorkflowApplyingExecutionResult(),
        new IdentityVerificationValidationResult());
  }

  public static ExternalWorkflowApplyingResult executionError(
      IdentityVerificationValidationResult requestIdValidationResult,
      IdentityVerificationRequestVerificationResult verifyResult,
      ExternalWorkflowApplyingExecutionResult executionResult) {
    return new ExternalWorkflowApplyingResult(
        new ExternalWorkflowApplicationIdParam(),
        requestIdValidationResult,
        verifyResult,
        executionResult,
        new IdentityVerificationValidationResult());
  }

  public ExternalWorkflowApplyingResult(
      ExternalWorkflowApplicationIdParam externalApplicationIdParam,
      IdentityVerificationValidationResult requestIdValidationResult,
      IdentityVerificationRequestVerificationResult verifyResult,
      ExternalWorkflowApplyingExecutionResult executionResult,
      IdentityVerificationValidationResult responseValidationResult) {
    this.externalApplicationIdParam = externalApplicationIdParam;
    this.requestIdValidationResult = requestIdValidationResult;
    this.verifyResult = verifyResult;
    this.executionResult = executionResult;
    this.responseValidationResult = responseValidationResult;
  }

  public boolean isError() {
    return requestIdValidationResult.isError()
        || verifyResult.isError()
        || executionResult.isClientError()
        || responseValidationResult.isError();
  }

  public ExternalWorkflowApplicationIdentifier extractApplicationIdentifierFromBody() {
    return new ExternalWorkflowApplicationIdentifier(
        executionResult.extractValueFromBody(externalApplicationIdParam.value()));
  }

  public IdentityVerificationResponse errorResponse() {
    if (requestIdValidationResult.isError()) {
      return requestIdValidationResult.errorResponse();
    }

    if (verifyResult.isError()) {
      return verifyResult.errorResponse();
    }

    if (executionResult.isClientError()) {
      return IdentityVerificationResponse.CLIENT_ERROR(executionResult.body().toMap());
    }

    if (executionResult.isServerError()) {
      return IdentityVerificationResponse.SERVER_ERROR(executionResult.body().toMap());
    }

    return responseValidationResult.errorResponse();
  }

  public JsonNodeWrapper externalWorkflowResponse() {
    return executionResult.body();
  }
}
