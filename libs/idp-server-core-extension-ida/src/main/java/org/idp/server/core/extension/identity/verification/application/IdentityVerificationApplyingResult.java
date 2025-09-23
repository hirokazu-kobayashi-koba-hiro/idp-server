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

import org.idp.server.core.extension.identity.verification.IdentityVerificationContext;
import org.idp.server.core.extension.identity.verification.IdentityVerificationContextBuilder;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationExecutionResult;
import org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter.AdditionalParameterResolveResult;
import org.idp.server.core.extension.identity.verification.application.pre_hook.verification.IdentityVerificationApplicationRequestVerifiedResult;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationApplicationResponse;

public class IdentityVerificationApplyingResult {

  IdentityVerificationContext applicationContext;
  IdentityVerificationApplicationRequestVerifiedResult verifyResult;
  IdentityVerificationExecutionResult executionResult;

  public IdentityVerificationApplyingResult() {}

  public static IdentityVerificationApplyingResult requestVerificationError(
      IdentityVerificationApplicationRequestVerifiedResult verifyResult) {
    return new IdentityVerificationApplyingResult(
        new IdentityVerificationContextBuilder().build(),
        verifyResult,
        new IdentityVerificationExecutionResult());
  }

  public static IdentityVerificationApplyingResult executionError(
      IdentityVerificationApplicationRequestVerifiedResult verifyResult,
      IdentityVerificationExecutionResult executionResult) {
    return new IdentityVerificationApplyingResult(
        new IdentityVerificationContextBuilder().build(), verifyResult, executionResult);
  }

  public static IdentityVerificationApplyingResult preHookError(
      IdentityVerificationApplicationRequestVerifiedResult verifyResult,
      AdditionalParameterResolveResult resolverResult) {
    return new IdentityVerificationApplyingResult(
        new IdentityVerificationContextBuilder().build(),
        verifyResult,
        IdentityVerificationExecutionResult.preHookError(resolverResult.getErrorDetails()));
  }

  public IdentityVerificationApplyingResult(
      IdentityVerificationContext applicationContext,
      IdentityVerificationApplicationRequestVerifiedResult verifyResult,
      IdentityVerificationExecutionResult executionResult) {
    this.applicationContext = applicationContext;
    this.verifyResult = verifyResult;
    this.executionResult = executionResult;
  }

  public boolean isSuccess() {
    return !isError();
  }

  public boolean isError() {
    return verifyResult.isError() || executionResult.isError();
  }

  public IdentityVerificationApplicationResponse errorResponse() {

    if (verifyResult.isError()) {
      return verifyResult.errorResponse();
    }

    if (executionResult.isClientError()) {
      return IdentityVerificationApplicationResponse.CLIENT_ERROR(executionResult.result());
    }

    if (executionResult.isServerError()) {
      return IdentityVerificationApplicationResponse.SERVER_ERROR(executionResult.result());
    }

    return IdentityVerificationApplicationResponse.SERVER_ERROR(executionResult.result());
  }

  public IdentityVerificationContext applicationContext() {
    return applicationContext;
  }
}
