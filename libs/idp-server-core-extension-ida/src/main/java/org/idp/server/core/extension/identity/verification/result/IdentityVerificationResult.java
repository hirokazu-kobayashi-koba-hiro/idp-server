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
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationApplicationContext;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verified.VerifiedClaims;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class IdentityVerificationResult {

  IdentityVerificationResultIdentifier identifier;
  TenantIdentifier tenantId;
  UserIdentifier userId;
  IdentityVerificationApplicationIdentifier applicationId;
  IdentityVerificationType identityVerificationType;
  VerifiedClaims verifiedClaims;
  LocalDateTime verifiedAt;
  LocalDateTime verifiedUntil;
  IdentityVerificationSourceType source;
  IdentityVerificationSourceDetails sourceDetails;

  public static IdentityVerificationResult create(
      IdentityVerificationApplication application,
      IdentityVerificationApplicationContext context,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationResultIdentifier identifier =
        new IdentityVerificationResultIdentifier(UUID.randomUUID().toString());
    TenantIdentifier tenantId = application.tenantIdentifier();
    UserIdentifier userIdentifier = application.userIdentifier();
    IdentityVerificationApplicationIdentifier applicationId = application.identifier();
    IdentityVerificationType identityVerificationType = application.identityVerificationType();

    VerifiedClaims verifiedClaims =
        VerifiedClaims.create(
            context.toMap(), verificationConfiguration.result().verifiedClaimsMappingRules());
    LocalDateTime verifiedAt = SystemDateTime.now();
    IdentityVerificationSourceType source = IdentityVerificationSourceType.APPLICATION;

    IdentityVerificationSourceDetails sourceDetails =
        IdentityVerificationSourceDetails.create(
            context.toMap(), verificationConfiguration.result().sourceDetailsMappingRules());

    return new IdentityVerificationResult(
        identifier,
        tenantId,
        userIdentifier,
        applicationId,
        identityVerificationType,
        verifiedClaims,
        verifiedAt,
        null,
        source,
        sourceDetails);
  }

  public static IdentityVerificationResult createOnCallback(
      IdentityVerificationApplication application,
      IdentityVerificationApplicationContext context,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationResultIdentifier identifier =
        new IdentityVerificationResultIdentifier(UUID.randomUUID().toString());
    TenantIdentifier tenantId = application.tenantIdentifier();
    UserIdentifier userIdentifier = application.userIdentifier();
    IdentityVerificationApplicationIdentifier applicationId = application.identifier();
    IdentityVerificationType identityVerificationType = application.identityVerificationType();

    VerifiedClaims verifiedClaims =
        VerifiedClaims.create(
            context.toMap(), verificationConfiguration.result().verifiedClaimsMappingRules());
    LocalDateTime verifiedAt = SystemDateTime.now();
    IdentityVerificationSourceType source = IdentityVerificationSourceType.APPLICATION;
    IdentityVerificationSourceDetails sourceDetails =
        IdentityVerificationSourceDetails.create(
            context.toMap(), verificationConfiguration.result().sourceDetailsMappingRules());

    return new IdentityVerificationResult(
        identifier,
        tenantId,
        userIdentifier,
        applicationId,
        identityVerificationType,
        verifiedClaims,
        verifiedAt,
        null,
        source,
        sourceDetails);
  }

  public static IdentityVerificationResult createOnDirect(
      TenantIdentifier tenantId,
      User user,
      IdentityVerificationType identityVerificationType,
      IdentityVerificationApplicationContext context,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationResultIdentifier identifier =
        new IdentityVerificationResultIdentifier(UUID.randomUUID().toString());
    UserIdentifier userIdentifier = user.userIdentifier();
    IdentityVerificationApplicationIdentifier applicationId =
        new IdentityVerificationApplicationIdentifier();
    VerifiedClaims verifiedClaims =
        VerifiedClaims.create(
            context.toMap(), verificationConfiguration.result().verifiedClaimsMappingRules());
    LocalDateTime verifiedAt = SystemDateTime.now();
    IdentityVerificationSourceType source = IdentityVerificationSourceType.DIRECT;
    IdentityVerificationSourceDetails sourceDetails =
        IdentityVerificationSourceDetails.create(
            context.toMap(), verificationConfiguration.result().sourceDetailsMappingRules());

    return new IdentityVerificationResult(
        identifier,
        tenantId,
        userIdentifier,
        applicationId,
        identityVerificationType,
        verifiedClaims,
        verifiedAt,
        null,
        source,
        sourceDetails);
  }

  public IdentityVerificationResult() {}

  public IdentityVerificationResult(
      IdentityVerificationResultIdentifier identifier,
      TenantIdentifier tenantId,
      UserIdentifier userId,
      IdentityVerificationApplicationIdentifier applicationId,
      IdentityVerificationType identityVerificationType,
      VerifiedClaims verifiedClaims,
      LocalDateTime verifiedAt,
      LocalDateTime verifiedUntil,
      IdentityVerificationSourceType source,
      IdentityVerificationSourceDetails sourceDetails) {
    this.identifier = identifier;
    this.tenantId = tenantId;
    this.userId = userId;
    this.applicationId = applicationId;
    this.identityVerificationType = identityVerificationType;
    this.verifiedClaims = verifiedClaims;
    this.verifiedAt = verifiedAt;
    this.verifiedUntil = verifiedUntil;
    this.source = source;
    this.sourceDetails = sourceDetails;
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

  public IdentityVerificationSourceType source() {
    return source;
  }

  public IdentityVerificationSourceDetails sourceDetails() {
    return sourceDetails;
  }

  public boolean hasSourceDetails() {
    return sourceDetails != null;
  }

  public boolean exists() {
    return identifier != null && identifier.exists();
  }

  public boolean hasApplicationId() {
    return applicationId != null && applicationId.exists();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", identifier.value());
    map.put("tenant_id", tenantId.value());
    map.put("user_id", userId.value());
    if (hasApplicationId()) map.put("application_id", applicationId.value());
    map.put("verification_type", identityVerificationType.name());
    map.put("verified_claims", verifiedClaims.toMap());
    map.put("verified_at", verifiedAt);
    map.put("verified_until", verifiedUntil);
    map.put("source", source);
    if (hasSourceDetails()) map.put("source_details", sourceDetails.toMap());
    return map;
  }
}
