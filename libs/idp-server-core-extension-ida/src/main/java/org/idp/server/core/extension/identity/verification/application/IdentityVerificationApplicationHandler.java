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
import org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter.AdditionalParameterResolveResult;
import org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter.AdditionalRequestParameterResolver;
import org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter.AdditionalRequestParameterResolvers;
import org.idp.server.core.extension.identity.verification.application.pre_hook.verification.IdentityVerificationApplicationRequestVerifiedResult;
import org.idp.server.core.extension.identity.verification.application.pre_hook.verification.IdentityVerificationApplicationRequestVerifiers;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationExecutionConfig;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class IdentityVerificationApplicationHandler {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(IdentityVerificationApplicationHandler.class);

  IdentityVerificationApplicationRequestVerifiers requestVerifiers;
  AdditionalRequestParameterResolvers additionalRequestParameterResolvers;
  IdentityVerificationApplicationExecutors executors;

  public IdentityVerificationApplicationHandler(
      Map<String, AdditionalRequestParameterResolver> additional,
      HttpRequestExecutor httpRequestExecutor,
      IdentityVerificationConfigurationQueryRepository configurationRepository) {
    this.requestVerifiers =
        new IdentityVerificationApplicationRequestVerifiers(configurationRepository);
    this.additionalRequestParameterResolvers =
        new AdditionalRequestParameterResolvers(additional, httpRequestExecutor);
    this.executors = new IdentityVerificationApplicationExecutors(httpRequestExecutor);
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

    AdditionalParameterResolveResult resolverResult =
        additionalRequestParameterResolvers.resolve(
            tenant,
            user,
            currentApplication,
            previousApplications,
            type,
            processes,
            request,
            requestAttributes,
            verificationConfiguration);

    // Check for fail-fast errors in pre-hook phase
    if (resolverResult.isFailFast()) {
      log.error(
          "Fail-fast error detected in pre-hook phase: {}",
          resolverResult.getErrorDetails().toMap());
      return IdentityVerificationApplyingResult.preHookError(verifyResult, resolverResult);
    }

    IdentityVerificationContextBuilder contextBuilder =
        buildContext(
            user, currentApplication, request, requestAttributes, resolverResult.getData());

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

  private IdentityVerificationContextBuilder buildContext(
      User user,
      IdentityVerificationApplication currentApplication,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes,
      Map<String, Object> additionalParams) {

    IdentityVerificationContextBuilder builder = new IdentityVerificationContextBuilder();
    builder.request(request);
    builder.requestAttributes(requestAttributes);
    builder.user(user);

    if (currentApplication.exists()) {
      builder.application(currentApplication);
    }

    builder.additionalParams(additionalParams);

    return builder;
  }
}
