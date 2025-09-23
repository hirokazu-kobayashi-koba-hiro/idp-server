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

package org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfig;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class AdditionalRequestParameterResolvers {

  Map<String, AdditionalRequestParameterResolver> resolvers;
  LoggerWrapper log = LoggerWrapper.getLogger(AdditionalRequestParameterResolvers.class);

  public AdditionalRequestParameterResolvers(
      Map<String, AdditionalRequestParameterResolver> additional,
      HttpRequestExecutor httpRequestExecutor) {
    this.resolvers = new ConcurrentHashMap<>();
    ContinuousCustomerDueDiligenceParameterResolver customerDueDiligence =
        new ContinuousCustomerDueDiligenceParameterResolver();
    this.resolvers.put(customerDueDiligence.type(), customerDueDiligence);
    HttpRequestParameterResolver httpRequest =
        new HttpRequestParameterResolver(httpRequestExecutor);
    this.resolvers.put(httpRequest.type(), httpRequest);
    this.resolvers.putAll(additional);
  }

  public AdditionalParameterResolveResult resolve(
      Tenant tenant,
      User user,
      IdentityVerificationApplication currentApplication,
      IdentityVerificationApplications previousApplications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    List<IdentityVerificationConfig> additionalParameterConfigs =
        processConfig.preHook().additionalParameters();

    List<Map<String, Object>> additionalParameterValues = new ArrayList<>();
    for (IdentityVerificationConfig additionalParameterConfig : additionalParameterConfigs) {

      AdditionalRequestParameterResolver resolver =
          this.resolvers.get(additionalParameterConfig.type());
      if (resolver == null) {
        log.warn(
            String.format(
                "No identity additional parameter resolver found for type %s",
                additionalParameterConfig.type()));
        continue;
      }

      log.info("Identity additional parameter resolver execute: {}", resolver.type());

      AdditionalParameterResolveResult result =
          resolver.resolve(
              tenant,
              user,
              currentApplication,
              previousApplications,
              type,
              processes,
              request,
              requestAttributes,
              additionalParameterConfig);

      // Check for fail-fast errors that should stop processing immediately
      if (result.isFailFast()) {
        log.warn("Fail-fast error occurred in resolver: {}", resolver.type());
        return result;
      }

      // For success or resilient errors, add the data to results
      if (result.getData() != null) {
        additionalParameterValues.add(result.getData());
      }
    }

    Map<String, Object> combinedData = new HashMap<>();
    combinedData.put("pre_hook_additional_parameters", additionalParameterValues);

    return AdditionalParameterResolveResult.success(combinedData);
  }
}
