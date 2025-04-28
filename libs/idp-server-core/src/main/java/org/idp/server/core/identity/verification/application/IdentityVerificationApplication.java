package org.idp.server.core.identity.verification.application;

import java.time.LocalDateTime;
import java.util.UUID;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplicationDetails;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplicationIdentifier;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplyingResult;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowDelegation;
import org.idp.server.core.identity.verification.trustframework.TrustFramework;
import org.idp.server.core.identity.verification.trustframework.TrustFrameworkDetails;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.RequestedClientId;

public class IdentityVerificationApplication {

  IdentityVerificationApplicationIdentifier identifier;
  IdentityVerificationType identityVerificationType;
  TenantIdentifier tenantIdentifier;
  RequestedClientId requestedClientId;
  IdentityVerificationApplicationDetails applicationDetails;
  String sub;
  ExternalWorkflowDelegation externalWorkflowDelegation;
  ExternalWorkflowApplicationIdentifier externalApplicationId;
  ExternalWorkflowApplicationDetails externalWorkflowApplicationDetails;

  TrustFramework trustFramework;
  TrustFrameworkDetails trustFrameworkDetails;
  IdentityVerificationApplicationStatus status;
  LocalDateTime requestedAt;

  public IdentityVerificationApplication() {}

  public IdentityVerificationApplication(
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType identityVerificationType,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      IdentityVerificationApplicationDetails applicationDetails,
      String sub,
      ExternalWorkflowDelegation externalWorkflowDelegation,
      ExternalWorkflowApplicationIdentifier externalApplicationId,
      ExternalWorkflowApplicationDetails externalWorkflowApplicationDetails,
      TrustFramework trustFramework,
      TrustFrameworkDetails trustFrameworkDetails,
      IdentityVerificationApplicationStatus status,
      LocalDateTime requestedAt) {
    this.identifier = identifier;
    this.identityVerificationType = identityVerificationType;
    this.tenantIdentifier = tenantIdentifier;
    this.requestedClientId = requestedClientId;
    this.applicationDetails = applicationDetails;
    this.sub = sub;
    this.externalWorkflowDelegation = externalWorkflowDelegation;
    this.externalApplicationId = externalApplicationId;
    this.externalWorkflowApplicationDetails = externalWorkflowApplicationDetails;
    this.trustFramework = trustFramework;
    this.trustFrameworkDetails = trustFrameworkDetails;
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
      ExternalWorkflowApplyingResult applyingResult) {

    IdentityVerificationApplicationIdentifier identifier =
        new IdentityVerificationApplicationIdentifier(UUID.randomUUID().toString());
    TenantIdentifier tenantIdentifier = tenant.identifier();
    String sub = user.sub();
    IdentityVerificationApplicationDetails details =
        new IdentityVerificationApplicationDetails(JsonNodeWrapper.fromObject(request.toMap()));

    ExternalWorkflowApplicationIdentifier externalApplicationId =
        applyingResult.extractApplicationIdentifierFromBody();
    ExternalWorkflowApplicationDetails externalWorkflowApplicationDetails =
        new ExternalWorkflowApplicationDetails(applyingResult.body());

    TrustFramework trustFramework = new TrustFramework(request.extractTrustFramework());
    TrustFrameworkDetails trustFrameworkDetails =
        new TrustFrameworkDetails(
            JsonNodeWrapper.fromObject(request.extractTrustFrameworkDetails()));

    LocalDateTime requestedAt = SystemDateTime.now();

    return new IdentityVerificationApplication(
        identifier,
        verificationType,
        tenantIdentifier,
        requestedClientId,
        details,
        sub,
        externalWorkflowDelegation,
        externalApplicationId,
        externalWorkflowApplicationDetails,
        trustFramework,
        trustFrameworkDetails,
        IdentityVerificationApplicationStatus.REQUESTED,
        requestedAt);
  }

  public IdentityVerificationApplication updateProcess(
      IdentityVerificationProcess process,
      IdentityVerificationRequest request,
      ExternalWorkflowApplyingResult applyingResult) {

    // TODO merge
    IdentityVerificationApplicationDetails details =
        new IdentityVerificationApplicationDetails(JsonNodeWrapper.fromObject(request.toMap()));
    ExternalWorkflowApplicationDetails externalWorkflowApplicationDetails =
        new ExternalWorkflowApplicationDetails(applyingResult.body());

    TrustFramework trustFramework = new TrustFramework(request.extractTrustFramework());
    TrustFrameworkDetails trustFrameworkDetails =
        new TrustFrameworkDetails(
            JsonNodeWrapper.fromObject(request.extractTrustFrameworkDetails()));

    return new IdentityVerificationApplication(
        identifier,
        identityVerificationType,
        tenantIdentifier,
        requestedClientId,
        details,
        sub,
        externalWorkflowDelegation,
        externalApplicationId,
        externalWorkflowApplicationDetails,
        trustFramework,
        trustFrameworkDetails,
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

  public String sub() {
    return sub;
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

  public TrustFrameworkDetails trustFrameworkDetails() {
    return trustFrameworkDetails;
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

  public boolean hasTrustFrameworkDetails() {
    return trustFrameworkDetails != null && trustFrameworkDetails.exists();
  }

  public boolean hasExternalApplicationDetails() {
    return externalWorkflowApplicationDetails != null
        && externalWorkflowApplicationDetails.exists();
  }
}
