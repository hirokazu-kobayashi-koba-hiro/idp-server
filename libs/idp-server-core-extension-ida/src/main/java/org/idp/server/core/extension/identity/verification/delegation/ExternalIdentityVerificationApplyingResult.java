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

package org.idp.server.core.extension.identity.verification.delegation;

import org.idp.server.core.extension.identity.verification.io.IdentityVerificationApplicationResponse;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationApplicationValidationResult;
import org.idp.server.core.extension.identity.verification.verifier.application.IdentityVerificationApplicationRequestVerifiedResult;
import org.idp.server.platform.json.JsonNodeWrapper;

public class ExternalIdentityVerificationApplyingResult {

  ExternalIdentityVerificationApplicationIdParam externalApplicationIdParam;
  IdentityVerificationApplicationValidationResult requestIdValidationResult;
  IdentityVerificationApplicationRequestVerifiedResult verifyResult;
  ExternalIdentityVerificationApplyingExecutionResult executionResult;
  IdentityVerificationApplicationValidationResult responseValidationResult;

  public ExternalIdentityVerificationApplyingResult() {}

  public static ExternalIdentityVerificationApplyingResult requestError(
      IdentityVerificationApplicationValidationResult requestIdValidationResult) {
    return new ExternalIdentityVerificationApplyingResult(
        new ExternalIdentityVerificationApplicationIdParam(),
        requestIdValidationResult,
        IdentityVerificationApplicationRequestVerifiedResult.empty(),
        new ExternalIdentityVerificationApplyingExecutionResult(),
        new IdentityVerificationApplicationValidationResult());
  }

  public static ExternalIdentityVerificationApplyingResult requestVerificationError(
      IdentityVerificationApplicationValidationResult requestIdValidationResult,
      IdentityVerificationApplicationRequestVerifiedResult verifyResult) {
    return new ExternalIdentityVerificationApplyingResult(
        new ExternalIdentityVerificationApplicationIdParam(),
        requestIdValidationResult,
        verifyResult,
        new ExternalIdentityVerificationApplyingExecutionResult(),
        new IdentityVerificationApplicationValidationResult());
  }

  public static ExternalIdentityVerificationApplyingResult executionError(
      IdentityVerificationApplicationValidationResult requestIdValidationResult,
      IdentityVerificationApplicationRequestVerifiedResult verifyResult,
      ExternalIdentityVerificationApplyingExecutionResult executionResult) {
    return new ExternalIdentityVerificationApplyingResult(
        new ExternalIdentityVerificationApplicationIdParam(),
        requestIdValidationResult,
        verifyResult,
        executionResult,
        new IdentityVerificationApplicationValidationResult());
  }

  public ExternalIdentityVerificationApplyingResult(
      ExternalIdentityVerificationApplicationIdParam externalApplicationIdParam,
      IdentityVerificationApplicationValidationResult requestIdValidationResult,
      IdentityVerificationApplicationRequestVerifiedResult verifyResult,
      ExternalIdentityVerificationApplyingExecutionResult executionResult,
      IdentityVerificationApplicationValidationResult responseValidationResult) {
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

  public ExternalIdentityVerificationApplicationIdentifier extractApplicationIdentifierFromBody() {
    return new ExternalIdentityVerificationApplicationIdentifier(
        executionResult.extractValueFromBody(externalApplicationIdParam.value()));
  }

  public IdentityVerificationApplicationResponse errorResponse() {
    if (requestIdValidationResult.isError()) {
      return requestIdValidationResult.errorResponse();
    }

    if (verifyResult.isError()) {
      return verifyResult.errorResponse();
    }

    if (executionResult.isClientError()) {
      return IdentityVerificationApplicationResponse.CLIENT_ERROR(executionResult.body().toMap());
    }

    if (executionResult.isServerError()) {
      return IdentityVerificationApplicationResponse.SERVER_ERROR(executionResult.body().toMap());
    }

    return responseValidationResult.errorResponse();
  }

  public JsonNodeWrapper externalWorkflowResponse() {
    return executionResult.body();
  }
}
