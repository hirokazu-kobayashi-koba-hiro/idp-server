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

package org.idp.server.core.adapters.datasource.identity.verification.result.query;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.*;
import org.idp.server.core.extension.identity.verification.claims.VerifiedClaims;
import org.idp.server.core.extension.identity.verification.delegation.ExternalIdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.delegation.ExternalIdentityVerificationService;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResult;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResultIdentifier;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationSource;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class ModelConverter {

  static IdentityVerificationResult convert(Map<String, String> map) {

    IdentityVerificationResultIdentifier identifier =
        new IdentityVerificationResultIdentifier(map.get("id"));
    TenantIdentifier tenantIdentifier = new TenantIdentifier(map.get("tenant_id"));
    UserIdentifier sub = new UserIdentifier(map.get("user_id"));

    IdentityVerificationApplicationIdentifier applicationId =
        new IdentityVerificationApplicationIdentifier(map.get("application_id"));
    IdentityVerificationType verificationType =
        new IdentityVerificationType(map.get("verification_type"));
    ExternalIdentityVerificationService externalIdentityVerificationService =
        new ExternalIdentityVerificationService(map.get("external_service"));
    ExternalIdentityVerificationApplicationIdentifier externalApplicationId =
        new ExternalIdentityVerificationApplicationIdentifier(map.get("external_application_id"));
    VerifiedClaims verifiedClaims =
        new VerifiedClaims(JsonNodeWrapper.fromString(map.get("verified_claims")));
    LocalDateTime verifiedAt = LocalDateTimeParser.parse(map.get("verified_at"));
    LocalDateTime verifiedUntil =
        map.get("verified_until") != null
            ? LocalDateTimeParser.parse(map.get("verified_until"))
            : null;
    IdentityVerificationSource source = IdentityVerificationSource.of(map.get("source"));

    return new IdentityVerificationResult(
        identifier,
        tenantIdentifier,
        sub,
        applicationId,
        verificationType,
        externalIdentityVerificationService,
        externalApplicationId,
        verifiedClaims,
        verifiedAt,
        verifiedUntil,
        source);
  }
}
