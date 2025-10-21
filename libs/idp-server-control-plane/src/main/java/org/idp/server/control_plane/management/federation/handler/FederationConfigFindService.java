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

package org.idp.server.control_plane.management.federation.handler;

import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementStatus;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.idp.server.core.openid.federation.FederationConfigurationIdentifier;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for federation configuration retrieval operations.
 *
 * <p>Handles business logic for retrieving single federation configurations. Part of
 * Handler/Service pattern.
 */
public class FederationConfigFindService
    implements FederationConfigManagementService<FederationConfigurationIdentifier> {

  private final FederationConfigurationQueryRepository queryRepository;

  public FederationConfigFindService(FederationConfigurationQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public FederationConfigManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    FederationConfiguration configuration =
        queryRepository.findWithDisabled(tenant, identifier, true);

    if (!configuration.exists()) {
      throw new ResourceNotFoundException(
          "Federation configuration not found: " + identifier.value());
    }

    return FederationConfigManagementResult.success(
        tenant.identifier(),
        new FederationConfigManagementResponse(
            FederationConfigManagementStatus.OK, configuration.toMap()));
  }
}
