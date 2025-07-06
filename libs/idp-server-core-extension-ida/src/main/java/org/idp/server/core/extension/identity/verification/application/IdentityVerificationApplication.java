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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.extension.identity.verification.IdentityVerificationApplicationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplicationDetails;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplyingResult;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowDelegation;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class IdentityVerificationApplication {

  IdentityVerificationApplicationIdentifier identifier;
  IdentityVerificationType identityVerificationType;
  TenantIdentifier tenantIdentifier;
  RequestedClientId requestedClientId;
  UserIdentifier userIdentifier;
  IdentityVerificationApplicationDetails applicationDetails;
  ExternalWorkflowDelegation externalWorkflowDelegation;
  ExternalWorkflowApplicationIdentifier externalApplicationId;
  ExternalWorkflowApplicationDetails externalWorkflowApplicationDetails;
  TrustFramework trustFramework;
  EvidenceDocumentType evidenceDocumentType;
  EvidenceDocumentDetail evidenceDocumentDetail;
  EvidenceMethod evidenceMethod;
  IdentityVerificationExaminationResults examinations;
  IdentityVerificationApplicationProcesses processes;
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
      ExternalWorkflowDelegation externalWorkflowDelegation,
      ExternalWorkflowApplicationIdentifier externalApplicationId,
      ExternalWorkflowApplicationDetails externalWorkflowApplicationDetails,
      TrustFramework trustFramework,
      EvidenceDocumentType evidenceDocumentType,
      EvidenceDocumentDetail evidenceDocumentDetail,
      EvidenceMethod evidenceMethod,
      IdentityVerificationExaminationResults examinations,
      IdentityVerificationApplicationProcesses processes,
      IdentityVerificationApplicationStatus status,
      LocalDateTime requestedAt) {
    this.identifier = identifier;
    this.identityVerificationType = identityVerificationType;
    this.tenantIdentifier = tenantIdentifier;
    this.requestedClientId = requestedClientId;
    this.userIdentifier = userIdentifier;
    this.applicationDetails = applicationDetails;
    this.externalWorkflowDelegation = externalWorkflowDelegation;
    this.externalApplicationId = externalApplicationId;
    this.externalWorkflowApplicationDetails = externalWorkflowApplicationDetails;
    this.trustFramework = trustFramework;
    this.evidenceDocumentType = evidenceDocumentType;
    this.evidenceDocumentDetail = evidenceDocumentDetail;
    this.evidenceMethod = evidenceMethod;
    this.examinations = examinations;
    this.processes = processes;
    this.status = status;
    this.requestedAt = requestedAt;
  }

  public static IdentityVerificationApplication create(
      Tenant tenant,
      RequestedClientId requestedClientId,
      User user,
      IdentityVerificationType verificationType,
      IdentityVerificationApplicationRequest request,
      ExternalWorkflowDelegation externalWorkflowDelegation,
      ExternalWorkflowApplyingResult applyingResult,
      IdentityVerificationProcess process,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationApplicationIdentifier identifier =
        new IdentityVerificationApplicationIdentifier(UUID.randomUUID().toString());
    TenantIdentifier tenantIdentifier = tenant.identifier();
    UserIdentifier userIdentifier = user.userIdentifier();

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(process);
    IdentityVerificationApplicationDetails details =
        IdentityVerificationApplicationDetails.create(request, processConfig);

    ExternalWorkflowApplicationIdentifier externalApplicationId =
        applyingResult.extractApplicationIdentifierFromBody();
    ExternalWorkflowApplicationDetails externalWorkflowApplicationDetails =
        ExternalWorkflowApplicationDetails.create(
            applyingResult.externalWorkflowResponse(), processConfig);

    TrustFramework trustFramework = new TrustFramework(request.extractTrustFramework());
    EvidenceDocumentType evidenceDocumentType =
        new EvidenceDocumentType(request.extractEvidenceDocumentType());
    EvidenceDocumentDetail evidenceDocumentDetail =
        EvidenceDocumentDetail.fromObject(request.extractEvidenceDocumentDetail());
    EvidenceMethod evidenceMethod = new EvidenceMethod(request.extractEvidenceMethod());
    LocalDateTime requestedAt = SystemDateTime.now();
    IdentityVerificationApplicationProcess applicationProcess =
        new IdentityVerificationApplicationProcess(process, requestedAt);
    IdentityVerificationApplicationProcesses processes =
        new IdentityVerificationApplicationProcesses(List.of(applicationProcess));

    return new IdentityVerificationApplication(
        identifier,
        verificationType,
        tenantIdentifier,
        requestedClientId,
        userIdentifier,
        details,
        externalWorkflowDelegation,
        externalApplicationId,
        externalWorkflowApplicationDetails,
        trustFramework,
        evidenceDocumentType,
        evidenceDocumentDetail,
        evidenceMethod,
        new IdentityVerificationExaminationResults(),
        processes,
        IdentityVerificationApplicationStatus.REQUESTED,
        requestedAt);
  }

  public IdentityVerificationApplication updateProcess(
      IdentityVerificationProcess process,
      IdentityVerificationApplicationRequest request,
      ExternalWorkflowApplyingResult applyingResult,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(process);
    IdentityVerificationApplicationDetails mergedApplicationDetails =
        applicationDetails.merge(request, processConfig);
    ExternalWorkflowApplicationDetails mergedExternalWorkflowApplicationDetails =
        externalWorkflowApplicationDetails.merge(
            applyingResult.externalWorkflowResponse(), processConfig);
    TrustFramework trustFramework = new TrustFramework(request.extractTrustFramework());
    EvidenceDocumentType evidenceDocumentType =
        new EvidenceDocumentType(request.extractEvidenceDocumentType());
    EvidenceDocumentDetail evidenceDocumentDetail =
        EvidenceDocumentDetail.fromObject(request.extractEvidenceDocumentDetail());
    EvidenceMethod evidenceMethod = new EvidenceMethod(request.extractEvidenceMethod());

    IdentityVerificationApplicationProcess applicationProcess =
        new IdentityVerificationApplicationProcess(process, SystemDateTime.now());
    IdentityVerificationApplicationProcesses addedProcesses = processes.add(applicationProcess);

    return new IdentityVerificationApplication(
        identifier,
        identityVerificationType,
        tenantIdentifier,
        requestedClientId,
        userIdentifier,
        mergedApplicationDetails,
        externalWorkflowDelegation,
        externalApplicationId,
        mergedExternalWorkflowApplicationDetails,
        trustFramework,
        evidenceDocumentType,
        evidenceDocumentDetail,
        evidenceMethod,
        new IdentityVerificationExaminationResults(),
        addedProcesses,
        IdentityVerificationApplicationStatus.APPLYING,
        requestedAt);
  }

  public IdentityVerificationApplication updateExamination(
      IdentityVerificationProcess process,
      IdentityVerificationApplicationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(process);

    IdentityVerificationExaminationResult identityVerificationExaminationResult =
        IdentityVerificationExaminationResult.create(request, processConfig);
    IdentityVerificationExaminationResults addExaminations =
        examinations.add(identityVerificationExaminationResult);
    IdentityVerificationApplicationProcess applicationProcess =
        new IdentityVerificationApplicationProcess(process, SystemDateTime.now());
    IdentityVerificationApplicationProcesses addedProcesses = processes.add(applicationProcess);

    IdentityVerificationApplicationStatus status =
        IdentityVerificationApplicationStatus.isRejected(request, processConfig)
            ? IdentityVerificationApplicationStatus.REJECTED
            : IdentityVerificationApplicationStatus.EXAMINATION_PROCESSING;

    return new IdentityVerificationApplication(
        identifier,
        identityVerificationType,
        tenantIdentifier,
        requestedClientId,
        userIdentifier,
        applicationDetails,
        externalWorkflowDelegation,
        externalApplicationId,
        externalWorkflowApplicationDetails,
        trustFramework,
        evidenceDocumentType,
        evidenceDocumentDetail,
        evidenceMethod,
        addExaminations,
        addedProcesses,
        status,
        requestedAt);
  }

  public IdentityVerificationApplication completeExamination(
      IdentityVerificationProcess process,
      IdentityVerificationApplicationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(process);

    IdentityVerificationExaminationResult identityVerificationExaminationResult =
        IdentityVerificationExaminationResult.create(request, processConfig);
    IdentityVerificationExaminationResults addExaminations =
        examinations.add(identityVerificationExaminationResult);
    IdentityVerificationApplicationProcess applicationProcess =
        new IdentityVerificationApplicationProcess(process, SystemDateTime.now());
    IdentityVerificationApplicationProcesses addedProcesses = processes.add(applicationProcess);

    IdentityVerificationApplicationStatus status = IdentityVerificationApplicationStatus.APPROVED;

    return new IdentityVerificationApplication(
        identifier,
        identityVerificationType,
        tenantIdentifier,
        requestedClientId,
        userIdentifier,
        applicationDetails,
        externalWorkflowDelegation,
        externalApplicationId,
        externalWorkflowApplicationDetails,
        trustFramework,
        evidenceDocumentType,
        evidenceDocumentDetail,
        evidenceMethod,
        addExaminations,
        addedProcesses,
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

  public ExternalWorkflowDelegation externalWorkflowDelegation() {
    return externalWorkflowDelegation;
  }

  public ExternalWorkflowApplicationIdentifier externalApplicationId() {
    return externalApplicationId;
  }

  public ExternalWorkflowApplicationDetails externalApplicationDetails() {
    return externalWorkflowApplicationDetails;
  }

  public TrustFramework trustFramework() {
    return trustFramework;
  }

  public IdentityVerificationExaminationResults examinationResults() {
    return examinations;
  }

  public List<Map<String, Object>> examinationResultsAsMapList() {
    return examinations.toMapList();
  }

  public IdentityVerificationApplicationProcesses processes() {
    return processes;
  }

  public List<IdentityVerificationApplicationProcess> processesAsList() {
    return processes.toList();
  }

  public List<Map<String, Object>> processesAsMapList() {
    return processes.toMapList();
  }

  public IdentityVerificationApplicationStatus status() {
    return status;
  }

  public boolean isRunning() {
    return status.isRunning();
  }

  public LocalDateTime requestedAt() {
    return requestedAt;
  }

  public boolean hasTrustFramework() {
    return trustFramework != null && trustFramework.exists();
  }

  public EvidenceDocumentType evidenceDocumentType() {
    return evidenceDocumentType;
  }

  public boolean hasEvidenceDocumentType() {
    return evidenceDocumentType != null && evidenceDocumentType.exists();
  }

  public EvidenceDocumentDetail evidenceDocumentDetail() {
    return evidenceDocumentDetail;
  }

  public boolean hasEvidenceDocumentDetail() {
    return evidenceDocumentDetail != null && evidenceDocumentDetail.exists();
  }

  public EvidenceMethod evidenceMethod() {
    return evidenceMethod;
  }

  public boolean hasEvidenceMethod() {
    return evidenceMethod != null && evidenceMethod.exists();
  }

  public boolean hasExternalApplicationDetails() {
    return externalWorkflowApplicationDetails != null
        && externalWorkflowApplicationDetails.exists();
  }

  public boolean hasExaminationResults() {
    return examinations != null && examinations.exists();
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> map = new HashMap<>();
    map.put("id", identifier.value());
    map.put("type", identityVerificationType.name());
    map.put("tenant_id", tenantIdentifier.value());
    map.put("client_id", requestedClientId.value());
    map.put("user_id", userIdentifier.value());
    map.put("application_details", applicationDetails.toMap());
    map.put("external_workflow_delegation", externalWorkflowDelegation.name());
    map.put("external_application_id", externalApplicationId.value());
    map.put("external_application_details", externalWorkflowApplicationDetails.toMap());
    map.put("trust_framework", trustFramework.name());
    if (hasTrustFramework()) map.put("trustFramework", trustFramework.name());
    if (hasEvidenceDocumentType())
      map.put("evidence_document_details", evidenceDocumentDetail.toMap());
    if (hasEvidenceDocumentDetail()) map.put("evidence_method", evidenceMethod.name());
    if (hasEvidenceMethod()) map.put("examination_results", examinationResultsAsMapList());
    map.put("processes", processesAsMapList());
    map.put("status", status.value());
    map.put("requested_at", requestedAt.toString());
    return map;
  }
}
