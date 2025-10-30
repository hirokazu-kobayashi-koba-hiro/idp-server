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

package org.idp.server.core.extension.identity.verification.application.execution.executor;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationContext;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationApplicationExecutor;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationExecutionResult;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationExecutionStatus;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationExecutionConfig;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationHttpRequestConfig;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationErrorDetails;
import org.idp.server.platform.http.*;

public class IdentityVerificationApplicationHttpRequestExecutor
    implements IdentityVerificationApplicationExecutor {

  HttpRequestExecutor httpRequestExecutor;

  public IdentityVerificationApplicationHttpRequestExecutor(
      HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
  }

  @Override
  public String type() {
    return "http_request";
  }

  @Override
  public IdentityVerificationExecutionResult execute(
      IdentityVerificationContext context,
      IdentityVerificationProcess processes,
      IdentityVerificationConfiguration verificationConfiguration) {
    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    IdentityVerificationExecutionConfig executionConfig = processConfig.execution();
    IdentityVerificationHttpRequestConfig httpRequestConfig = executionConfig.httpRequest();

    HttpRequestBaseParams httpRequestBaseParams = new HttpRequestBaseParams(context.toMap());

    HttpRequestResult httpRequestResult =
        httpRequestExecutor.execute(httpRequestConfig, httpRequestBaseParams);

    return new IdentityVerificationExecutionResult(
        resolveStatus(httpRequestResult),
        httpRequestResult.statusCode(),
        resolveResult(httpRequestResult));
  }

  private IdentityVerificationExecutionStatus resolveStatus(HttpRequestResult httpRequestResult) {
    if (httpRequestResult.isClientError()) {
      return IdentityVerificationExecutionStatus.CLIENT_ERROR;
    }

    if (httpRequestResult.isServerError()) {
      return IdentityVerificationExecutionStatus.SERVER_ERROR;
    }

    return IdentityVerificationExecutionStatus.OK;
  }

  private Map<String, Object> resolveResult(HttpRequestResult httpRequestResult) {
    if (httpRequestResult.isSuccess()) {
      // Success case: return safe response data
      return createSuccessResponse(httpRequestResult);
    } else {
      // Error case: return sanitized error information
      return createErrorResponse(httpRequestResult);
    }
  }

  /** Creates success response with safe data extraction. */
  private Map<String, Object> createSuccessResponse(HttpRequestResult httpRequestResult) {
    Map<String, Object> result = new HashMap<>();
    result.put("status", "success");
    result.putAll(httpRequestResult.toMap());

    return result;
  }

  /**
   * Creates sanitized error response using unified error format. Prevents internal system
   * information leakage.
   */
  private Map<String, Object> createErrorResponse(HttpRequestResult httpRequestResult) {
    String statusCategory = httpRequestResult.isClientError() ? "client_error" : "server_error";

    IdentityVerificationErrorDetails.Builder builder =
        IdentityVerificationErrorDetails.builder()
            .error(IdentityVerificationErrorDetails.ErrorTypes.EXECUTION_FAILED)
            .errorDescription("Identity verification execution failed")
            .addErrorDetail("execution_type", "http_request")
            .addErrorDetail("status_category", statusCategory)
            .addErrorMessage("External verification service returned an error")
            .addErrorDetail("status_code", httpRequestResult.statusCode());

    if (httpRequestResult.body() != null) {
      if (httpRequestResult.body().isArray()) {
        builder.addErrorDetail("response_body", httpRequestResult.body().toListAsMap());
      } else if (httpRequestResult.body().isObject()) {
        builder.addErrorDetail("response_body", httpRequestResult.body().toMap());
      }
    }

    IdentityVerificationErrorDetails errorDetails = builder.build();

    return errorDetails.toMap();
  }
}
