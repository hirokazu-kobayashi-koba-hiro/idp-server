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
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
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

      // Check if condition is specified and evaluate it
      if (additionalParameterConfig.hasCondition()) {
        JsonPathWrapper jsonPath =
            createJsonPathContext(tenant, user, currentApplication, request, requestAttributes);
        if (!additionalParameterConfig.condition().evaluate(jsonPath)) {
          log.debug(
              "Skipping additional parameter due to condition evaluation: type={}",
              additionalParameterConfig.type());
          continue;
        }
      }

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

      // For success or resilient errors, always add the data to results
      // (getData() is guaranteed to be non-null by constructor)
      additionalParameterValues.add(result.getData());
    }

    Map<String, Object> combinedData = new HashMap<>();
    combinedData.put("pre_hook_additional_parameters", additionalParameterValues);

    return AdditionalParameterResolveResult.success(combinedData);
  }

  private JsonPathWrapper createJsonPathContext(
      Tenant tenant,
      User user,
      IdentityVerificationApplication application,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    Map<String, Object> context = new HashMap<>();

    // Add user information (consistent with execution context)
    context.put("user", user.toMap());

    // Add application information (consistent with execution context)
    if (application != null && application.exists()) {
      context.put("application", application.toMap());
    }

    // Add request information (consistent with execution context)
    context.put("request_body", request.toMap());

    // Add request attributes (consistent with execution context)
    context.put("request_attributes", requestAttributes.toMap());

    return new JsonPathWrapper(JsonNodeWrapper.fromMap(context).toJson());
  }
}
