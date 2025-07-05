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

package org.idp.server.core.extension.identity.verification.result;

import java.time.LocalDateTime;
import java.util.UUID;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.claims.VerifiedClaims;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplicationIdentifier;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class IdentityVerificationResult {

  IdentityVerificationResultIdentifier identifier;
  TenantIdentifier tenantId;
  UserIdentifier userIdentifier;
  IdentityVerificationApplicationIdentifier applicationId;
  IdentityVerificationType identityVerificationType;
  ExternalWorkflowApplicationIdentifier externalApplicationId;
  VerifiedClaims verifiedClaims;
  LocalDateTime verifiedAt;
  LocalDateTime verifiedUntil;
  IdentityVerificationSource source;

  public static IdentityVerificationResult create(
      IdentityVerificationApplication application,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationResultIdentifier identifier =
        new IdentityVerificationResultIdentifier(UUID.randomUUID().toString());
    TenantIdentifier tenantId = application.tenantIdentifier();
    UserIdentifier userIdentifier = application.userIdentifier();
    IdentityVerificationApplicationIdentifier applicationId = application.identifier();
    IdentityVerificationType identityVerificationType = application.identityVerificationType();
    ExternalWorkflowApplicationIdentifier externalApplicationId =
        application.externalApplicationId();
    VerifiedClaims verifiedClaims =
        VerifiedClaims.create(request, verificationConfiguration.verifiedClaimsConfiguration());
    LocalDateTime verifiedAt = SystemDateTime.now();
    IdentityVerificationSource source = IdentityVerificationSource.APPLICATION;

    return new IdentityVerificationResult(
        identifier,
        tenantId,
        userIdentifier,
        applicationId,
        identityVerificationType,
        externalApplicationId,
        verifiedClaims,
        verifiedAt,
        null,
        source);
  }

  public IdentityVerificationResult() {}

  public IdentityVerificationResult(
      IdentityVerificationResultIdentifier identifier,
      TenantIdentifier tenantId,
      UserIdentifier userIdentifier,
      IdentityVerificationApplicationIdentifier applicationId,
      IdentityVerificationType identityVerificationType,
      ExternalWorkflowApplicationIdentifier externalApplicationId,
      VerifiedClaims verifiedClaims,
      LocalDateTime verifiedAt,
      LocalDateTime verifiedUntil,
      IdentityVerificationSource source) {
    this.identifier = identifier;
    this.tenantId = tenantId;
    this.userIdentifier = userIdentifier;
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

  public UserIdentifier userIdentifier() {
    return userIdentifier;
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
