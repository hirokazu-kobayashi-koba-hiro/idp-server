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
import org.idp.server.control_plane.management.identity.verification.IdentityVerificationConfigUpdateContext;
import org.idp.server.control_plane.management.identity.verification.IdentityVerificationConfigUpdateContextCreator;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigUpdateRequest;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationIdentifier;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationCommandRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for identity verification configuration update operations.
 *
 * <p>Handles business logic for updating identity verification configurations. Part of
 * Handler/Service pattern.
 */
public class IdentityVerificationConfigUpdateService
    implements IdentityVerificationConfigManagementService<
        IdentityVerificationConfigUpdateRequest> {

  private final IdentityVerificationConfigurationQueryRepository queryRepository;
  private final IdentityVerificationConfigurationCommandRepository commandRepository;

  public IdentityVerificationConfigUpdateService(
      IdentityVerificationConfigurationQueryRepository queryRepository,
      IdentityVerificationConfigurationCommandRepository commandRepository) {
    this.queryRepository = queryRepository;
    this.commandRepository = commandRepository;
  }

  @Override
  public IdentityVerificationConfigManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    IdentityVerificationConfigurationIdentifier identifier =
        new IdentityVerificationConfigurationIdentifier(request.configurationId());
    IdentityVerificationConfiguration configuration = queryRepository.find(tenant, identifier);

    if (!configuration.exists()) {
      throw new ResourceNotFoundException(
          "Identity verification configuration not found: " + identifier.value());
    }

    IdentityVerificationConfigUpdateContextCreator contextCreator =
        new IdentityVerificationConfigUpdateContextCreator(tenant, request, configuration, dryRun);
    IdentityVerificationConfigUpdateContext context = contextCreator.create();

    if (dryRun) {
      IdentityVerificationConfigManagementResponse response = context.toResponse();
      return IdentityVerificationConfigManagementResult.success(
          tenant.identifier(), response, context);
    }

    commandRepository.update(tenant, context.afterType(), context.after());

    IdentityVerificationConfigManagementResponse response = context.toResponse();
    return IdentityVerificationConfigManagementResult.success(
        tenant.identifier(), response, context);
  }
}
