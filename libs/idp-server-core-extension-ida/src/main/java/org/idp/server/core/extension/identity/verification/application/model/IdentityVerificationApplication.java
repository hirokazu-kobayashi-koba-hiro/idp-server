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

package org.idp.server.core.extension.identity.verification.application.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.extension.identity.verification.IdentityVerificationContext;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplyingResult;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessConfiguration;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class IdentityVerificationApplication {

  IdentityVerificationApplicationIdentifier identifier;
  IdentityVerificationType identityVerificationType;
  TenantIdentifier tenantIdentifier;
  RequestedClientId requestedClientId;
  UserIdentifier userIdentifier;
  IdentityVerificationApplicationDetails applicationDetails;
  IdentityVerificationApplicationProcessResults processes;
  IdentityVerificationApplicationAttributes attributes;
  IdentityVerificationApplicationStatus status;
  LocalDateTime requestedAt;

  public IdentityVerificationApplication() {}

  public IdentityVerificationApplication(
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType identityVerificationType,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      UserIdentifier userIdentifier,
      IdentityVerificationApplicationDetails applicationDetails,
      IdentityVerificationApplicationProcessResults processes,
      IdentityVerificationApplicationAttributes attributes,
      IdentityVerificationApplicationStatus status,
      LocalDateTime requestedAt) {
    this.identifier = identifier;
    this.identityVerificationType = identityVerificationType;
    this.tenantIdentifier = tenantIdentifier;
    this.requestedClientId = requestedClientId;
    this.userIdentifier = userIdentifier;
    this.applicationDetails = applicationDetails;
    this.processes = processes;
    this.attributes = attributes;
    this.status = status;
    this.requestedAt = requestedAt;
  }

  public static IdentityVerificationApplication create(
      Tenant tenant,
      RequestedClientId requestedClientId,
      User user,
      IdentityVerificationType verificationType,
      IdentityVerificationContext applicationContext,
      IdentityVerificationProcess process,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationApplicationIdentifier identifier =
        new IdentityVerificationApplicationIdentifier(UUID.randomUUID().toString());
    TenantIdentifier tenantIdentifier = tenant.identifier();
    UserIdentifier userIdentifier = user.userIdentifier();

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(process);
    List<MappingRule> mappingRules = processConfig.store().applicationDetailsMappingRules();
    IdentityVerificationApplicationDetails details =
        IdentityVerificationApplicationDetails.create(applicationContext, mappingRules);

    LocalDateTime requestedAt = SystemDateTime.now();
    IdentityVerificationApplicationProcessResult applicationProcess =
        new IdentityVerificationApplicationProcessResult(1, 1, 0);
    IdentityVerificationApplicationProcessResults processes =
        new IdentityVerificationApplicationProcessResults(
            Map.of(process.name(), applicationProcess));
    IdentityVerificationApplicationAttributes attributes =
        IdentityVerificationApplicationAttributes.fromMap(verificationConfiguration.attributes());

    return new IdentityVerificationApplication(
        identifier,
        verificationType,
        tenantIdentifier,
        requestedClientId,
        userIdentifier,
        details,
        processes,
        attributes,
        IdentityVerificationApplicationStatus.REQUESTED,
        requestedAt);
  }

  public IdentityVerificationApplication updateProcessWith(
      IdentityVerificationProcess process,
      IdentityVerificationApplyingResult applyingResult,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(process);
    IdentityVerificationApplicationDetails mergedApplicationDetails =
        applicationDetails.merge(
            applyingResult.applicationContext(),
            processConfig.store().applicationDetailsMappingRules());

    Map<String, IdentityVerificationApplicationProcessResult> resultMap = processes.toMap();
    if (processes.contains(process.name())) {

      IdentityVerificationApplicationProcessResult foundResult = processes.get(process.name());
      IdentityVerificationApplicationProcessResult updatedResult =
          foundResult.updateWith(applyingResult);
      resultMap.remove(process.name());
      resultMap.put(process.name(), updatedResult);

    } else {

      int successCount = applyingResult.isSuccess() ? 1 : 0;
      int failureCount = applyingResult.isSuccess() ? 0 : 1;
      IdentityVerificationApplicationProcessResult result =
          new IdentityVerificationApplicationProcessResult(1, successCount, failureCount);
      resultMap.put(process.name(), result);
    }

    IdentityVerificationApplicationProcessResults processResults =
        new IdentityVerificationApplicationProcessResults(resultMap);

    IdentityVerificationApplicationStatus status =
        IdentityVerificationApplicationStatusEvaluator.evaluateOnProcess(
            processConfig, applyingResult.applicationContext());

    return new IdentityVerificationApplication(
        identifier,
        identityVerificationType,
        tenantIdentifier,
        requestedClientId,
        userIdentifier,
        mergedApplicationDetails,
        processResults,
        attributes,
        status,
        requestedAt);
  }

  public IdentityVerificationApplication updateCallbackWith(
      IdentityVerificationProcess process,
      IdentityVerificationContext context,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(process);

    IdentityVerificationApplicationDetails mergedApplicationDetails =
        applicationDetails.merge(context, processConfig.store().applicationDetailsMappingRules());

    // TODO to be more correct
    Map<String, IdentityVerificationApplicationProcessResult> resultMap = processes.toMap();
    if (processes.contains(process.name())) {

      IdentityVerificationApplicationProcessResult foundResult = processes.get(process.name());
      IdentityVerificationApplicationProcessResult updatedResult = foundResult.updateSuccess();
      resultMap.remove(process.name());
      resultMap.put(process.name(), updatedResult);

    } else {

      int successCount = 1;
      int failureCount = 0;
      IdentityVerificationApplicationProcessResult result =
          new IdentityVerificationApplicationProcessResult(1, successCount, failureCount);
      resultMap.put(process.name(), result);
    }
    IdentityVerificationApplicationProcessResults processResults =
        new IdentityVerificationApplicationProcessResults(resultMap);

    IdentityVerificationApplicationStatus status =
        IdentityVerificationApplicationStatusEvaluator.evaluateOnCallback(processConfig, context);

    return new IdentityVerificationApplication(
        identifier,
        identityVerificationType,
        tenantIdentifier,
        requestedClientId,
        userIdentifier,
        mergedApplicationDetails,
        processResults,
        attributes,
        status,
        requestedAt);
  }

  public IdentityVerificationApplicationIdentifier identifier() {
    return identifier;
  }

  public IdentityVerificationType identityVerificationType() {
    return identityVerificationType;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenantIdentifier;
  }

  public RequestedClientId requestedClientId() {
    return requestedClientId;
  }

  public IdentityVerificationApplicationDetails applicationDetails() {
    return applicationDetails;
  }

  public UserIdentifier userIdentifier() {
    return userIdentifier;
  }

  public IdentityVerificationApplicationProcessResults processes() {
    return processes;
  }

  public Map<String, Object> processesAsMapObject() {
    return processes.toMapAsObject();
  }

  public IdentityVerificationApplicationStatus status() {
    return status;
  }

  public IdentityVerificationApplicationAttributes attributes() {
    return attributes;
  }

  public boolean hasAttributes() {
    return attributes != null && attributes.exists();
  }

  public boolean isRunning() {
    return status.isRunning();
  }

  public LocalDateTime requestedAt() {
    return requestedAt;
  }

  public boolean isApproved() {
    return status.isApproved();
  }

  public boolean exists() {
    return identifier != null && identifier.exists();
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> map = new HashMap<>();
    map.put("id", identifier.value());
    map.put("type", identityVerificationType.name());
    map.put("tenant_id", tenantIdentifier.value());
    map.put("client_id", requestedClientId.value());
    map.put("user_id", userIdentifier.value());
    map.put("application_details", applicationDetails.toMap());
    map.put("status", status.value());
    map.put("processes", processes.toMapAsObject());
    if (hasAttributes()) map.put("attributes", attributes.toMap());
    map.put("requested_at", requestedAt.toString());
    return map;
  }
}
