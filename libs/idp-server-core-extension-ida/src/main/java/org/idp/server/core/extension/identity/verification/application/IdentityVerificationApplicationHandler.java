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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.execution.*;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter.AdditionalRequestParameterResolvers;
import org.idp.server.core.extension.identity.verification.application.pre_hook.verification.IdentityVerificationApplicationRequestVerifiedResult;
import org.idp.server.core.extension.identity.verification.application.pre_hook.verification.IdentityVerificationApplicationRequestVerifiers;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationExecutionConfig;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationApplicationRequest;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class IdentityVerificationApplicationHandler {

  IdentityVerificationApplicationRequestVerifiers requestVerifiers;
  AdditionalRequestParameterResolvers additionalRequestParameterResolvers;
  IdentityVerificationApplicationExecutors executors;

  public IdentityVerificationApplicationHandler() {
    this.requestVerifiers = new IdentityVerificationApplicationRequestVerifiers();
    this.additionalRequestParameterResolvers = new AdditionalRequestParameterResolvers();
    this.executors = new IdentityVerificationApplicationExecutors();
  }

  public IdentityVerificationApplyingResult executeRequest(
      Tenant tenant,
      User user,
      IdentityVerificationApplication currentApplication,
      IdentityVerificationApplications previousApplications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationApplicationRequestVerifiedResult verifyResult =
        requestVerifiers.verifyAll(
            tenant,
            user,
            currentApplication,
            previousApplications,
            type,
            processes,
            request,
            requestAttributes,
            verificationConfiguration);

    if (verifyResult.isError()) {

      return IdentityVerificationApplyingResult.requestVerificationError(verifyResult);
    }

    Map<String, Object> requestBaseParams =
        resolveBaseParams(
            tenant,
            user,
            currentApplication,
            previousApplications,
            type,
            processes,
            request,
            requestAttributes,
            verificationConfiguration);

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    IdentityVerificationExecutionConfig executionConfig = processConfig.execution();
    IdentityVerificationApplicationExecutor executor = executors.get(executionConfig.type());

    IdentityVerificationExecutionResult executionResult =
        executor.execute(requestBaseParams, processes, verificationConfiguration);

    if (!executionResult.isOk()) {
      return IdentityVerificationApplyingResult.executionError(verifyResult, executionResult);
    }

    IdentityVerificationApplicationContext applicationContext =
        new IdentityVerificationApplicationContext(requestBaseParams, executionResult.result());

    return new IdentityVerificationApplyingResult(
        applicationContext, verifyResult, executionResult);
  }

  private Map<String, Object> resolveBaseParams(
      Tenant tenant,
      User user,
      IdentityVerificationApplication currentApplication,
      IdentityVerificationApplications previousApplications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfiguration verificationConfiguration) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("request_body", request.toMap());
    parameters.put("request_attributes", requestAttributes.toMap());
    parameters.put("user", user.toMap());
    if (currentApplication.exists()) {
      parameters.put("application", currentApplication.toMap());
    }
    Map<String, Object> additionalParameters =
        additionalRequestParameterResolvers.resolve(
            tenant,
            user,
            previousApplications,
            type,
            processes,
            request,
            requestAttributes,
            verificationConfiguration);
    parameters.putAll(additionalParameters);
    return parameters;
  }
}
