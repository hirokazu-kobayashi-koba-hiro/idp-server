package org.idp.server.core.identity.verification.application;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplicationDetails;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplicationIdentifier;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplyingResult;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowDelegation;
import org.idp.server.core.identity.verification.trustframework.TrustFramework;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.RequestedClientId;

public class IdentityVerificationApplication {

  IdentityVerificationApplicationIdentifier identifier;
  IdentityVerificationType identityVerificationType;
  TenantIdentifier tenantIdentifier;
  RequestedClientId requestedClientId;
  String userId;
  IdentityVerificationApplicationDetails applicationDetails;
  ExternalWorkflowDelegation externalWorkflowDelegation;
  ExternalWorkflowApplicationIdentifier externalApplicationId;
  ExternalWorkflowApplicationDetails externalWorkflowApplicationDetails;
  TrustFramework trustFramework;
  IdentityVerificationApplicationProcesses processes;
  IdentityVerificationApplicationStatus status;
  LocalDateTime requestedAt;

  public IdentityVerificationApplication() {}

  public IdentityVerificationApplication(
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType identityVerificationType,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      String userId,
      IdentityVerificationApplicationDetails applicationDetails,
      ExternalWorkflowDelegation externalWorkflowDelegation,
      ExternalWorkflowApplicationIdentifier externalApplicationId,
      ExternalWorkflowApplicationDetails externalWorkflowApplicationDetails,
      TrustFramework trustFramework,
      IdentityVerificationApplicationProcesses processes,
      IdentityVerificationApplicationStatus status,
      LocalDateTime requestedAt) {
    this.identifier = identifier;
    this.identityVerificationType = identityVerificationType;
    this.tenantIdentifier = tenantIdentifier;
    this.requestedClientId = requestedClientId;
    this.userId = userId;
    this.applicationDetails = applicationDetails;
    this.externalWorkflowDelegation = externalWorkflowDelegation;
    this.externalApplicationId = externalApplicationId;
    this.externalWorkflowApplicationDetails = externalWorkflowApplicationDetails;
    this.trustFramework = trustFramework;
    this.processes = processes;
    this.status = status;
    this.requestedAt = requestedAt;
  }

  public static IdentityVerificationApplication create(
      Tenant tenant,
      RequestedClientId requestedClientId,
      User user,
      IdentityVerificationType verificationType,
      IdentityVerificationRequest request,
      ExternalWorkflowDelegation externalWorkflowDelegation,
      ExternalWorkflowApplyingResult applyingResult,
      IdentityVerificationProcess process,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationApplicationIdentifier identifier =
        new IdentityVerificationApplicationIdentifier(UUID.randomUUID().toString());
    TenantIdentifier tenantIdentifier = tenant.identifier();
    String sub = user.sub();

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
        sub,
        details,
        externalWorkflowDelegation,
        externalApplicationId,
        externalWorkflowApplicationDetails,
        trustFramework,
        processes,
        IdentityVerificationApplicationStatus.REQUESTED,
        requestedAt);
  }

  public IdentityVerificationApplication updateProcess(
      IdentityVerificationProcess process,
      IdentityVerificationRequest request,
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

    IdentityVerificationApplicationProcess applicationProcess =
        new IdentityVerificationApplicationProcess(process, SystemDateTime.now());
    IdentityVerificationApplicationProcesses addedProcesses = processes.add(applicationProcess);

    return new IdentityVerificationApplication(
        identifier,
        identityVerificationType,
        tenantIdentifier,
        requestedClientId,
        userId,
        mergedApplicationDetails,
        externalWorkflowDelegation,
        externalApplicationId,
        mergedExternalWorkflowApplicationDetails,
        trustFramework,
        addedProcesses,
        IdentityVerificationApplicationStatus.APPLYING,
        requestedAt);
  }

  public IdentityVerificationApplication updateExamination(
      IdentityVerificationProcess process, IdentityVerificationRequest request) {

    return new IdentityVerificationApplication();
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

  public String userId() {
    return userId;
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

  public boolean hasExternalApplicationDetails() {
    return externalWorkflowApplicationDetails != null
        && externalWorkflowApplicationDetails.exists();
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> map = new HashMap<>();
    map.put("id", identifier.value());
    map.put("type", identityVerificationType.name());
    map.put("tenant_id", tenantIdentifier.value());
    map.put("client_id", requestedClientId.value());
    map.put("user_id", userId);
    map.put("application_details", applicationDetails.toMap());
    map.put("external_workflow_delegation", externalWorkflowDelegation.name());
    map.put("external_application_id", externalApplicationId.value());
    map.put("external_application_details", externalWorkflowApplicationDetails.toMap());
    map.put("trust_framework", trustFramework.name());
    map.put("processes", processesAsMapList());
    map.put("status", status.value());
    map.put("requested_at", requestedAt.toString());
    return map;
  }
}
