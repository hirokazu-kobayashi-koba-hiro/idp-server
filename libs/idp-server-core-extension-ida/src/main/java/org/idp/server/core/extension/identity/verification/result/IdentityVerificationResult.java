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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.extension.identity.verification.IdentityVerificationApplicationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.claims.VerifiedClaims;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.delegation.ExternalIdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.delegation.ExternalIdentityVerificationService;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class IdentityVerificationResult {

  IdentityVerificationResultIdentifier identifier;
  TenantIdentifier tenantId;
  UserIdentifier userId;
  IdentityVerificationApplicationIdentifier applicationId;
  IdentityVerificationType identityVerificationType;
  ExternalIdentityVerificationService externalIdentityVerificationService;
  ExternalIdentityVerificationApplicationIdentifier externalApplicationId;
  VerifiedClaims verifiedClaims;
  LocalDateTime verifiedAt;
  LocalDateTime verifiedUntil;
  IdentityVerificationSource source;

  public static IdentityVerificationResult create(
      IdentityVerificationApplication application,
      IdentityVerificationApplicationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationResultIdentifier identifier =
        new IdentityVerificationResultIdentifier(UUID.randomUUID().toString());
    TenantIdentifier tenantId = application.tenantIdentifier();
    UserIdentifier userIdentifier = application.userIdentifier();
    IdentityVerificationApplicationIdentifier applicationId = application.identifier();
    IdentityVerificationType identityVerificationType = application.identityVerificationType();
    ExternalIdentityVerificationService externalIdentityVerificationService =
        verificationConfiguration.externalIdentityVerificationService();
    ExternalIdentityVerificationApplicationIdentifier externalApplicationId =
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
        externalIdentityVerificationService,
        externalApplicationId,
        verifiedClaims,
        verifiedAt,
        null,
        source);
  }

  public static IdentityVerificationResult createOnDirect(
      TenantIdentifier tenantId,
      IdentityVerificationType identityVerificationType,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationResultIdentifier identifier =
        new IdentityVerificationResultIdentifier(UUID.randomUUID().toString());
    UserIdentifier userIdentifier = request.userIdentifier();
    IdentityVerificationApplicationIdentifier applicationId =
        new IdentityVerificationApplicationIdentifier();
    ExternalIdentityVerificationService externalIdentityVerificationService =
        verificationConfiguration.externalIdentityVerificationService();
    ExternalIdentityVerificationApplicationIdentifier externalApplicationId =
        new ExternalIdentityVerificationApplicationIdentifier();
    VerifiedClaims verifiedClaims =
        VerifiedClaims.create(request, verificationConfiguration.verifiedClaimsConfiguration());
    LocalDateTime verifiedAt = SystemDateTime.now();
    IdentityVerificationSource source = IdentityVerificationSource.DIRECT;

    return new IdentityVerificationResult(
        identifier,
        tenantId,
        userIdentifier,
        applicationId,
        identityVerificationType,
        externalIdentityVerificationService,
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
      UserIdentifier userId,
      IdentityVerificationApplicationIdentifier applicationId,
      IdentityVerificationType identityVerificationType,
      ExternalIdentityVerificationService externalIdentityVerificationService,
      ExternalIdentityVerificationApplicationIdentifier externalApplicationId,
      VerifiedClaims verifiedClaims,
      LocalDateTime verifiedAt,
      LocalDateTime verifiedUntil,
      IdentityVerificationSource source) {
    this.identifier = identifier;
    this.tenantId = tenantId;
    this.userId = userId;
    this.applicationId = applicationId;
    this.identityVerificationType = identityVerificationType;
    this.externalIdentityVerificationService = externalIdentityVerificationService;
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

  public UserIdentifier userId() {
    return userId;
  }

  public IdentityVerificationApplicationIdentifier applicationId() {
    return applicationId;
  }

  public IdentityVerificationType identityVerificationType() {
    return identityVerificationType;
  }

  public ExternalIdentityVerificationService externalIdentityVerificationService() {
    return externalIdentityVerificationService;
  }

  public ExternalIdentityVerificationApplicationIdentifier externalApplicationId() {
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

  public boolean hasApplicationId() {
    return applicationId != null && applicationId.exists();
  }

  public boolean hasExternalApplicationId() {
    return externalApplicationId != null && externalApplicationId.exists();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", identifier.value());
    map.put("tenant_id", tenantId.value());
    map.put("user_id", userId.value());
    if (hasApplicationId()) map.put("application_id", applicationId.value());
    map.put("verification_type", identityVerificationType.name());
    map.put("external_service", externalIdentityVerificationService.name());
    if (hasExternalApplicationId())
      map.put("external_application_id", externalApplicationId.value());
    map.put("verified_claims", verifiedClaims.toMap());
    map.put("verified_at", verifiedAt);
    map.put("verified_until", verifiedUntil);
    map.put("source", source);
    return map;
  }
}
