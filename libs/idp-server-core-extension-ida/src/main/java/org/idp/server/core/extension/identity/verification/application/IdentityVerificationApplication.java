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
import org.idp.server.core.extension.identity.verification.delegation.ExternalIdentityVerificationApplicationDetails;
import org.idp.server.core.extension.identity.verification.delegation.ExternalIdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.delegation.ExternalIdentityVerificationApplyingResult;
import org.idp.server.core.extension.identity.verification.delegation.ExternalIdentityVerificationService;
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
  ExternalIdentityVerificationService externalIdentityVerificationService;
  ExternalIdentityVerificationApplicationIdentifier externalApplicationId;
  ExternalIdentityVerificationApplicationDetails externalIdentityVerificationApplicationDetails;
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
      ExternalIdentityVerificationService externalIdentityVerificationService,
      ExternalIdentityVerificationApplicationIdentifier externalApplicationId,
      ExternalIdentityVerificationApplicationDetails externalIdentityVerificationApplicationDetails,
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
    this.externalIdentityVerificationService = externalIdentityVerificationService;
    this.externalApplicationId = externalApplicationId;
    this.externalIdentityVerificationApplicationDetails =
        externalIdentityVerificationApplicationDetails;
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
      ExternalIdentityVerificationService externalIdentityVerificationService,
      ExternalIdentityVerificationApplyingResult applyingResult,
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

    ExternalIdentityVerificationApplicationIdentifier externalApplicationId =
        applyingResult.extractApplicationIdentifierFromBody();
    ExternalIdentityVerificationApplicationDetails externalIdentityVerificationApplicationDetails =
        ExternalIdentityVerificationApplicationDetails.create(
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
        externalIdentityVerificationService,
        externalApplicationId,
        externalIdentityVerificationApplicationDetails,
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
      ExternalIdentityVerificationApplyingResult applyingResult,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(process);
    IdentityVerificationApplicationDetails mergedApplicationDetails =
        applicationDetails.merge(request, processConfig);
    ExternalIdentityVerificationApplicationDetails
        mergedExternalIdentityVerificationApplicationDetails =
            externalIdentityVerificationApplicationDetails.merge(
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
        externalIdentityVerificationService,
        externalApplicationId,
        mergedExternalIdentityVerificationApplicationDetails,
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
        externalIdentityVerificationService,
        externalApplicationId,
        externalIdentityVerificationApplicationDetails,
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
        externalIdentityVerificationService,
        externalApplicationId,
        externalIdentityVerificationApplicationDetails,
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

  public ExternalIdentityVerificationService externalWorkflowDelegation() {
    return externalIdentityVerificationService;
  }

  public ExternalIdentityVerificationApplicationIdentifier externalApplicationId() {
    return externalApplicationId;
  }

  public ExternalIdentityVerificationApplicationDetails externalApplicationDetails() {
    return externalIdentityVerificationApplicationDetails;
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
    return externalIdentityVerificationApplicationDetails != null
        && externalIdentityVerificationApplicationDetails.exists();
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
    map.put("external_workflow_delegation", externalIdentityVerificationService.name());
    map.put("external_application_id", externalApplicationId.value());
    map.put("external_application_details", externalIdentityVerificationApplicationDetails.toMap());
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
