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

package org.idp.server.control_plane.management.identity.verification.handler;

import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.identity.verification.IdentityVerificationConfigManagementContextBuilder;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementStatus;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationIdentifier;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for identity verification configuration retrieval operations.
 *
 * <p>Handles business logic for retrieving a single identity verification configuration. Part of
 * Handler/Service pattern.
 */
public class IdentityVerificationConfigFindService
    implements IdentityVerificationConfigManagementService<
        IdentityVerificationConfigurationIdentifier> {

  private final IdentityVerificationConfigurationQueryRepository queryRepository;

  public IdentityVerificationConfigFindService(
      IdentityVerificationConfigurationQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public IdentityVerificationConfigManagementResponse execute(
      IdentityVerificationConfigManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    IdentityVerificationConfiguration configuration = queryRepository.find(tenant, identifier);

    if (!configuration.exists()) {
      throw new ResourceNotFoundException(
          "Identity verification configuration not found: " + identifier.value());
    }

    // Populate builder with retrieved configuration (as "before" for read operations)
    builder.withBefore(configuration);

    return new IdentityVerificationConfigManagementResponse(
        IdentityVerificationConfigManagementStatus.OK, configuration.toMap());
  }
}
