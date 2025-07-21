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

package org.idp.server.core.extension.identity.verification.application.execution;

import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.application.pre_hook.verification.IdentityVerificationApplicationRequestVerifiedResult;
import org.idp.server.core.extension.identity.verification.application.validation.IdentityVerificationApplicationValidationResult;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationApplicationResponse;
import org.idp.server.platform.json.JsonNodeWrapper;

public class IdentityVerificationApplyingResult {

  IdentityVerificationApplicationContext applicationContext;
  IdentityVerificationApplicationValidationResult requestIdValidationResult;
  IdentityVerificationApplicationRequestVerifiedResult verifyResult;
  IdentityVerificationApplyingExecutionResult executionResult;

  public IdentityVerificationApplyingResult() {}

  public static IdentityVerificationApplyingResult requestError(
      IdentityVerificationApplicationValidationResult requestIdValidationResult) {
    return new IdentityVerificationApplyingResult(
        new IdentityVerificationApplicationContext(),
        requestIdValidationResult,
        IdentityVerificationApplicationRequestVerifiedResult.empty(),
        new IdentityVerificationApplyingExecutionResult());
  }

  public static IdentityVerificationApplyingResult requestVerificationError(
      IdentityVerificationApplicationValidationResult requestIdValidationResult,
      IdentityVerificationApplicationRequestVerifiedResult verifyResult) {
    return new IdentityVerificationApplyingResult(
        new IdentityVerificationApplicationContext(),
        requestIdValidationResult,
        verifyResult,
        new IdentityVerificationApplyingExecutionResult());
  }

  public static IdentityVerificationApplyingResult executionError(
      IdentityVerificationApplicationValidationResult requestIdValidationResult,
      IdentityVerificationApplicationRequestVerifiedResult verifyResult,
      IdentityVerificationApplyingExecutionResult executionResult) {
    return new IdentityVerificationApplyingResult(
        new IdentityVerificationApplicationContext(),
        requestIdValidationResult,
        verifyResult,
        executionResult);
  }

  public IdentityVerificationApplyingResult(
      IdentityVerificationApplicationContext applicationContext,
      IdentityVerificationApplicationValidationResult requestIdValidationResult,
      IdentityVerificationApplicationRequestVerifiedResult verifyResult,
      IdentityVerificationApplyingExecutionResult executionResult) {
    this.applicationContext = applicationContext;
    this.requestIdValidationResult = requestIdValidationResult;
    this.verifyResult = verifyResult;
    this.executionResult = executionResult;
  }

  public boolean isSuccess() {
    return !isError();
  }

  public boolean isError() {
    return requestIdValidationResult.isError()
        || verifyResult.isError()
        || executionResult.isClientError();
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

    return IdentityVerificationApplicationResponse.SERVER_ERROR(executionResult.body().toMap());
  }

  public IdentityVerificationApplicationContext applicationContext() {
    return applicationContext;
  }

  public int responseStatusCode() {
    return executionResult.statusCode();
  }

  public Map<String, List<String>> responseHeaders() {
    return executionResult.headers();
  }

  public JsonNodeWrapper responseBody() {
    return executionResult.body();
  }
}
