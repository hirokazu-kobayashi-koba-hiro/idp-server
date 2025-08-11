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

import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationContext;
import org.idp.server.core.extension.identity.verification.IdentityVerificationContextBuilder;
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
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.core.openid.identity.User;
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
      IdentityVerificationRequest request,
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

    IdentityVerificationContextBuilder contextBuilder =
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

    IdentityVerificationContext context = contextBuilder.build();
    IdentityVerificationExecutionResult executionResult =
        executor.execute(context, processes, verificationConfiguration);

    if (!executionResult.isOk()) {
      return IdentityVerificationApplyingResult.executionError(verifyResult, executionResult);
    }

    contextBuilder.executionResult(executionResult.result());

    return new IdentityVerificationApplyingResult(
        contextBuilder.build(), verifyResult, executionResult);
  }

  private IdentityVerificationContextBuilder resolveBaseParams(
      Tenant tenant,
      User user,
      IdentityVerificationApplication currentApplication,
      IdentityVerificationApplications previousApplications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationContextBuilder builder = new IdentityVerificationContextBuilder();
    builder.request(request);
    builder.requestAttributes(requestAttributes);
    builder.user(user);

    if (currentApplication.exists()) {
      builder.application(currentApplication);
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
    builder.additionalParams(additionalParameters);

    return builder;
  }
}
