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

import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.oidc.grant.GrantManagementContextBuilder;
import org.idp.server.control_plane.management.oidc.grant.io.GrantFindRequest;
import org.idp.server.control_plane.management.oidc.grant.io.GrantManagementResponse;
import org.idp.server.control_plane.management.oidc.grant.io.GrantManagementStatus;
import org.idp.server.core.openid.grant_management.AuthorizationGranted;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for grant retrieval operations.
 *
 * <p>Handles business logic for retrieving a single authorization grant. Part of Handler/Service
 * pattern.
 */
public class GrantFindService implements GrantManagementService<GrantFindRequest> {

  private final AuthorizationGrantedQueryRepository queryRepository;

  public GrantFindService(AuthorizationGrantedQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public GrantManagementResponse execute(
      GrantManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      GrantFindRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AuthorizationGranted authorizationGranted =
        queryRepository.find(tenant, request.grantIdentifier());

    if (!authorizationGranted.exists()) {
      throw new ResourceNotFoundException("Grant not found: " + request.grantIdentifier().value());
    }

    contextBuilder.withBefore(authorizationGranted);

    return new GrantManagementResponse(GrantManagementStatus.OK, authorizationGranted.toMap());
  }
}
