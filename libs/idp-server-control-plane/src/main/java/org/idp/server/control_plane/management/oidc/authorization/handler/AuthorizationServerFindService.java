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

package org.idp.server.control_plane.management.oidc.authorization.handler;

import org.idp.server.control_plane.management.oidc.authorization.AuthorizationServerManagementContextBuilder;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerFindRequest;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementResponse;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for authorization server configuration retrieval operations.
 *
 * <p>Handles business logic for retrieving authorization server configurations. Part of
 * Handler/Service pattern.
 */
public class AuthorizationServerFindService
    implements AuthorizationServerManagementService<AuthorizationServerFindRequest> {

  private final AuthorizationServerConfigurationQueryRepository queryRepository;

  public AuthorizationServerFindService(
      AuthorizationServerConfigurationQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public AuthorizationServerManagementResponse execute(
      AuthorizationServerManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthorizationServerFindRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AuthorizationServerConfiguration configuration = queryRepository.getWithDisabled(tenant, true);

    // Update context builder with before state (for audit logging)
    contextBuilder.withBefore(configuration);

    return new AuthorizationServerManagementResponse(
        AuthorizationServerManagementStatus.OK, configuration.toMap());
  }
}
