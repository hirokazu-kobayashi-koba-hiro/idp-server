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

package org.idp.server.control_plane.management.authentication.interaction.handler;

import org.idp.server.control_plane.management.authentication.interaction.AuthenticationInteractionManagementContextBuilder;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionFindRequest;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementResponse;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementStatus;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteraction;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for finding a single authentication interaction.
 *
 * <p>Handles authentication interaction retrieval logic.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Query repository for authentication interaction
 *   <li>Validate existence
 *   <li>Build response with interaction data
 * </ul>
 */
public class AuthenticationInteractionFindService
    implements AuthenticationInteractionManagementService<AuthenticationInteractionFindRequest> {

  private final AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository;

  public AuthenticationInteractionFindService(
      AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository) {
    this.authenticationInteractionQueryRepository = authenticationInteractionQueryRepository;
  }

  @Override
  public AuthenticationInteractionManagementResponse execute(
      AuthenticationInteractionManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationInteractionFindRequest request,
      RequestAttributes requestAttributes) {

    AuthenticationInteraction interaction =
        authenticationInteractionQueryRepository.find(tenant, request.identifier(), request.type());

    if (!interaction.exists()) {
      throw new ResourceNotFoundException(
          "Authentication interaction not found: "
              + request.identifier().value()
              + ", type: "
              + request.type());
    }

    // Update context builder with result (for audit logging)
    contextBuilder.withResult(interaction);

    return new AuthenticationInteractionManagementResponse(
        AuthenticationInteractionManagementStatus.OK, interaction.toMap());
  }
}
