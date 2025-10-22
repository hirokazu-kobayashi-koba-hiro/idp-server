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
import org.idp.server.control_plane.management.federation.FederationConfigUpdateContext;
import org.idp.server.control_plane.management.federation.FederationConfigUpdateContextCreator;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.idp.server.core.openid.federation.repository.FederationConfigurationCommandRepository;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for federation configuration update operations.
 *
 * <p>Handles business logic for updating federation configurations. Part of Handler/Service
 * pattern.
 */
public class FederationConfigUpdateService
    implements FederationConfigManagementService<FederationConfigUpdateRequest> {

  private final FederationConfigurationQueryRepository queryRepository;
  private final FederationConfigurationCommandRepository commandRepository;

  public FederationConfigUpdateService(
      FederationConfigurationQueryRepository queryRepository,
      FederationConfigurationCommandRepository commandRepository) {
    this.queryRepository = queryRepository;
    this.commandRepository = commandRepository;
  }

  @Override
  public FederationConfigManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Find existing configuration
    FederationConfiguration before =
        queryRepository.findWithDisabled(tenant, request.identifier(), true);

    if (!before.exists()) {
      throw new ResourceNotFoundException(
          "Federation configuration not found: " + request.identifier().value());
    }

    // Create context
    FederationConfigUpdateContextCreator contextCreator =
        new FederationConfigUpdateContextCreator(tenant, before, request.request(), dryRun);
    FederationConfigUpdateContext context = contextCreator.create();

    if (dryRun) {
      FederationConfigManagementResponse response = context.toResponse();
      return FederationConfigManagementResult.success(tenant.identifier(), response, context);
    }

    // Update configuration
    commandRepository.update(tenant, context.afterConfiguration());

    FederationConfigManagementResponse response = context.toResponse();
    return FederationConfigManagementResult.success(tenant.identifier(), response, context);
  }
}
