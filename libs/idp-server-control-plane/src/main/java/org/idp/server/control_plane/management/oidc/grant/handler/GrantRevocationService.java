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

package org.idp.server.control_plane.management.oidc.grant.handler;

import java.util.Map;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.oidc.grant.GrantManagementContextBuilder;
import org.idp.server.control_plane.management.oidc.grant.io.GrantManagementResponse;
import org.idp.server.control_plane.management.oidc.grant.io.GrantManagementStatus;
import org.idp.server.control_plane.management.oidc.grant.io.GrantRevocationRequest;
import org.idp.server.core.openid.grant_management.AuthorizationGranted;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedQueryRepository;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for grant revocation operations.
 *
 * <p>Handles business logic for revoking authorization grants. Part of Handler/Service pattern.
 * When a grant is revoked, all associated tokens are also deleted.
 */
public class GrantRevocationService implements GrantManagementService<GrantRevocationRequest> {

  private final AuthorizationGrantedQueryRepository queryRepository;
  private final AuthorizationGrantedRepository commandRepository;
  private final OAuthTokenCommandRepository tokenCommandRepository;

  public GrantRevocationService(
      AuthorizationGrantedQueryRepository queryRepository,
      AuthorizationGrantedRepository commandRepository,
      OAuthTokenCommandRepository tokenCommandRepository) {
    this.queryRepository = queryRepository;
    this.commandRepository = commandRepository;
    this.tokenCommandRepository = tokenCommandRepository;
  }

  @Override
  public GrantManagementResponse execute(
      GrantManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      GrantRevocationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AuthorizationGranted authorizationGranted =
        queryRepository.find(tenant, request.grantIdentifier());

    if (!authorizationGranted.exists()) {
      throw new ResourceNotFoundException("Grant not found: " + request.grantIdentifier().value());
    }

    contextBuilder.withBefore(authorizationGranted);

    if (dryRun) {
      Map<String, Object> response =
          Map.of(
              "message",
              "Revocation simulated successfully",
              "grant_id",
              request.grantIdentifier().value(),
              "dry_run",
              true);
      return new GrantManagementResponse(GrantManagementStatus.OK, response);
    }

    // Delete the grant
    commandRepository.delete(tenant, request.grantIdentifier());

    // Delete all associated tokens for the same user and client
    AuthorizationGrant grant = authorizationGranted.authorizationGrant();
    tokenCommandRepository.deleteByUserAndClient(tenant, grant.user(), grant.requestedClientId());

    return new GrantManagementResponse(GrantManagementStatus.NO_CONTENT, Map.of());
  }
}
