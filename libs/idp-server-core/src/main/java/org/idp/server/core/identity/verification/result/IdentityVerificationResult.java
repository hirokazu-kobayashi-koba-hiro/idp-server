package org.idp.server.core.identity.verification.result;

import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationDetails;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplicationDetails;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplicationIdentifier;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowDelegation;
import org.idp.server.core.identity.verification.trustframework.TrustFramework;
import org.idp.server.core.identity.verification.trustframework.TrustFrameworkDetails;
import org.idp.server.core.identity.verification.verified.claims.VerifiedClaims;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.RequestedClientId;

import java.time.LocalDateTime;
import java.util.UUID;

public class IdentityVerificationResult {

  IdentityVerificationResultIdentifier identifier;
  TenantIdentifier tenantId;
  String userId;
  IdentityVerificationApplicationIdentifier applicationId;
  IdentityVerificationType identityVerificationType;
  ExternalWorkflowApplicationIdentifier externalApplicationId;
  VerifiedClaims verifiedClaims;
  LocalDateTime verifiedAt;
  LocalDateTime verifiedUntil;
  IdentityVerificationSource source;;

  public static IdentityVerificationResult create(
          IdentityVerificationApplication application, IdentityVerificationRequest request, IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationResultIdentifier identifier = new IdentityVerificationResultIdentifier(UUID.randomUUID().toString());
    TenantIdentifier tenantId = application.tenantIdentifier();
    String userId = application.userId();
    IdentityVerificationApplicationIdentifier applicationId = application.identifier();
    IdentityVerificationType identityVerificationType = application.identityVerificationType();
    ExternalWorkflowApplicationIdentifier externalApplicationId = application.externalApplicationId();
    VerifiedClaims verifiedClaims = VerifiedClaims.create(request, verificationConfiguration.verifiedClaimsSchemaAsDefinition());
    LocalDateTime verifiedAt = SystemDateTime.now();
    IdentityVerificationSource source = IdentityVerificationSource.APPLICATION;

    return new IdentityVerificationResult(identifier, tenantId, userId, applicationId, identityVerificationType, externalApplicationId, verifiedClaims, verifiedAt, null, source);
  }

  public IdentityVerificationResult() {}

  public IdentityVerificationResult(IdentityVerificationResultIdentifier identifier, TenantIdentifier tenantId, String userId, IdentityVerificationApplicationIdentifier applicationId, IdentityVerificationType identityVerificationType, ExternalWorkflowApplicationIdentifier externalApplicationId, VerifiedClaims verifiedClaims, LocalDateTime verifiedAt, LocalDateTime verifiedUntil, IdentityVerificationSource source) {
    this.identifier = identifier;
    this.tenantId = tenantId;
    this.userId = userId;
    this.applicationId = applicationId;
    this.identityVerificationType = identityVerificationType;
    this.externalApplicationId = externalApplicationId;
    this.verifiedClaims = verifiedClaims;
    this.verifiedAt = verifiedAt;
    this.verifiedUntil = verifiedUntil;
    this.source = source;
  }

  public IdentityVerificationResultIdentifier identifier() {
    return identifier;
  }

  public TenantIdentifier tenantId() {
    return tenantId;
  }

  public String userId() {
    return userId;
  }

  public IdentityVerificationApplicationIdentifier applicationId() {
    return applicationId;
  }

  public IdentityVerificationType identityVerificationType() {
    return identityVerificationType;
  }

  public ExternalWorkflowApplicationIdentifier externalApplicationId() {
    return externalApplicationId;
  }

  public VerifiedClaims verifiedClaims() {
    return verifiedClaims;
  }

  public LocalDateTime verifiedAt() {
    return verifiedAt;
  }

  public LocalDateTime verifiedUntil() {
    return verifiedUntil;
  }

  public boolean hasVerifiedUntil() {
    return verifiedUntil != null;
  }

  public IdentityVerificationSource source() {
    return source;
  }

  public boolean exists() {
    return identifier != null && identifier.exists();
  }
}
